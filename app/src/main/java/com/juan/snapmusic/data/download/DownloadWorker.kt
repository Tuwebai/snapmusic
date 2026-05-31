package com.juan.snapmusic.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.juan.snapmusic.SnapMusicApplication
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadProgressSnapshot
import com.juan.snapmusic.core.model.DownloadStage
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.QueueStatus
import com.juan.snapmusic.core.platform.NotificationHelper
import com.juan.snapmusic.core.platform.sanitizeFileName
import com.juan.snapmusic.data.persistence.QueueEntity
import com.juan.snapmusic.data.persistence.toDownloadSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_QUEUE_ID = "queue_id"
    }

    private val graph = (appContext as SnapMusicApplication).appGraph
    private val notifications = NotificationHelper(appContext)
    private var lastPublishedProgress = -1
    private var lastPublishedAtMs = 0L

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val queueId = inputData.getString(KEY_QUEUE_ID) ?: return@withContext Result.failure()
        val entry = graph.queueRepository.get(queueId) ?: return@withContext Result.failure()
        lastPublishedProgress = -1
        lastPublishedAtMs = 0L
        var targetUri: Uri? = null

        return@withContext try {
            setForeground(createForegroundInfo(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, 0))
            updateQueueProgress(
                queueId = queueId,
                title = entry.title,
                variantLabel = entry.variantLabel,
                thumbnailUrl = entry.thumbnailUrl,
                status = QueueStatus.RUNNING,
                progress = 0,
                snapshot = DownloadProgressSnapshot(0L, null, 0L, DownloadStage.PREPARING),
            )

            val reservedTargetUri = graph.storageRepository.createDestinationUri(
                preferences = graph.currentPreferences(),
                fileName = buildFileName(entry.title, entry.container),
                mimeType = mimeTypeFor(entry.container),
            )
            targetUri = reservedTargetUri

            val resolvedVariantLabel = executeDownloadWithFreshSources(queueId, entry, reservedTargetUri)
            graph.storageRepository.publishOutput(reservedTargetUri)
            val localThumbnailUrl = downloadThumbnailForHistory(entry.sourceUrl, entry.thumbnailUrl)

            graph.queueRepository.updateStatus(
                queueId,
                QueueStatus.SUCCESS,
                100,
                outputUri = reservedTargetUri.toString(),
                variantLabel = resolvedVariantLabel,
            )
            graph.historyRepository.append(
                id = queueId,
                title = entry.title,
                author = entry.author,
                sourceUrl = entry.sourceUrl,
                thumbnailUrl = localThumbnailUrl,
                outputUri = reservedTargetUri.toString(),
                format = entry.container,
                qualityLabel = resolvedVariantLabel,
            )
            notifications.showSuccess(queueId, entry.title, resolvedVariantLabel, localThumbnailUrl)
            Result.success()
        } catch (cancelled: Throwable) {
            targetUri?.let { graph.storageRepository.deleteOutput(it.toString()) }
            graph.storageRepository.invalidateLocalMediaCache()
            if (isStopped) {
                graph.queueRepository.updateStatus(queueId, QueueStatus.CANCELLED, 0, errorMessage = "Cancelado por el usuario")
                Result.failure()
            } else {
                val safeMessage = friendlyErrorMessage(cancelled.message)
                graph.queueRepository.updateStatus(queueId, QueueStatus.ERROR, 0, errorMessage = safeMessage)
                notifications.showError(queueId, entry.title, safeMessage, entry.thumbnailUrl)
                Result.failure()
            }
        }
    }

    private suspend fun executeDownloadWithFreshSources(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
    ): String {
        return runCatching {
            executeResolvedDownload(queueId, entry, targetUri)
        }.recoverCatching { error ->
            if (!shouldRetryWithFreshSources(error)) throw error
            executeResolvedDownload(queueId, entry, targetUri)
        }.getOrThrow()
    }

    private suspend fun executeResolvedDownload(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
    ): String {
        val selection = entry.toDownloadSelection()
        val plan = graph.resolverRepository.resolveDownloadPlan(entry.sourceUrl, selection)
        val resolvedVariantLabel = plan.displayLabel
        when (plan) {
            is DownloadExecutionPlan.Direct -> processDirectDownload(queueId, entry, targetUri, plan, resolvedVariantLabel)
            is DownloadExecutionPlan.AudioTranscode -> processAudioTranscode(queueId, entry, targetUri, plan, resolvedVariantLabel)
            is DownloadExecutionPlan.MuxVideoAudio -> processMuxDownload(queueId, entry, targetUri, plan, resolvedVariantLabel)
        }
        publishStage(queueId, entry, resolvedVariantLabel, DownloadStage.VALIDATING, plan.selection.strategy)
        graph.downloadOutputValidator.validate(targetUri, entry.container)
        return resolvedVariantLabel
    }

    private suspend fun processDirectDownload(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
        plan: DownloadExecutionPlan.Direct,
        variantLabel: String,
    ) {
        val tempFile = graph.httpTransferEngine.download(
            source = plan.source,
            requestId = "$queueId-direct",
            policy = HttpTransferPolicy(maxParallelConnections = graph.downloadNetworkPolicy.fileParallelism()),
        ) { snapshot ->
            updateQueueProgress(
                queueId = queueId,
                title = entry.title,
                variantLabel = variantLabel,
                thumbnailUrl = entry.thumbnailUrl,
                status = QueueStatus.RUNNING,
                progress = progressFor(plan.selection.strategy, snapshot),
                snapshot = snapshot,
            )
        }
        try {
            copyInto(Uri.fromFile(tempFile), targetUri, plan.selection.strategy, queueId, entry, variantLabel)
        } finally {
            tempFile.delete()
        }
    }

    private suspend fun processAudioTranscode(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
        plan: DownloadExecutionPlan.AudioTranscode,
        variantLabel: String,
    ) {
        val sourceFile = graph.httpTransferEngine.download(
            source = plan.source,
            requestId = "$queueId-audio-source",
            policy = HttpTransferPolicy(maxParallelConnections = graph.downloadNetworkPolicy.fileParallelism()),
        ) { snapshot ->
            updateQueueProgress(
                queueId = queueId,
                title = entry.title,
                variantLabel = variantLabel,
                thumbnailUrl = entry.thumbnailUrl,
                status = QueueStatus.RUNNING,
                progress = progressFor(plan.selection.strategy, snapshot),
                snapshot = snapshot,
            )
        }
        var transcodedUri: Uri? = null
        try {
            publishStage(queueId, entry, variantLabel, DownloadStage.TRANSCODING, plan.selection.strategy)
            transcodedUri = graph.transcodeEngine.extractAudio(Uri.fromFile(sourceFile), entry.container, variantLabel)
            graph.downloadOutputValidator.validate(transcodedUri, entry.container)
            copyInto(transcodedUri, targetUri, plan.selection.strategy, queueId, entry, variantLabel)
        } finally {
            sourceFile.delete()
            transcodedUri?.takeIf { it.scheme == "file" }?.toFile()?.delete()
        }
    }

    private suspend fun processMuxDownload(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
        plan: DownloadExecutionPlan.MuxVideoAudio,
        variantLabel: String,
    ) = coroutineScope {
        val progressTracker = CombinedTransferProgress()
        val muxParallelism = graph.downloadNetworkPolicy.muxSourceParallelism()
        val videoDeferred = async {
            graph.httpTransferEngine.download(
                source = plan.videoSource,
                requestId = "$queueId-video-source",
                policy = HttpTransferPolicy(maxParallelConnections = muxParallelism),
            ) { snapshot ->
                val combined = progressTracker.updateVideo(snapshot)
                updateQueueProgress(
                    queueId = queueId,
                    title = entry.title,
                    variantLabel = variantLabel,
                    thumbnailUrl = entry.thumbnailUrl,
                    status = QueueStatus.RUNNING,
                    progress = progressFor(plan.selection.strategy, combined),
                    snapshot = combined,
                )
            }
        }
        val audioDeferred = async {
            graph.httpTransferEngine.download(
                source = plan.audioSource,
                requestId = "$queueId-audio-source",
                policy = HttpTransferPolicy(maxParallelConnections = muxParallelism),
            ) { snapshot ->
                val combined = progressTracker.updateAudio(snapshot)
                updateQueueProgress(
                    queueId = queueId,
                    title = entry.title,
                    variantLabel = variantLabel,
                    thumbnailUrl = entry.thumbnailUrl,
                    status = QueueStatus.RUNNING,
                    progress = progressFor(plan.selection.strategy, combined),
                    snapshot = combined,
                )
            }
        }
        val videoFile = videoDeferred.await()
        val audioFile = audioDeferred.await()
        var muxedUri: Uri? = null
        try {
            publishStage(queueId, entry, variantLabel, DownloadStage.MUXING, plan.selection.strategy)
            muxedUri = graph.transcodeEngine.muxVideo(Uri.fromFile(videoFile), Uri.fromFile(audioFile), variantLabel)
            graph.downloadOutputValidator.validate(muxedUri, entry.container)
            copyInto(muxedUri, targetUri, plan.selection.strategy, queueId, entry, variantLabel)
        } finally {
            videoFile.delete()
            audioFile.delete()
            muxedUri?.takeIf { it.scheme == "file" }?.toFile()?.delete()
        }
    }

    private suspend fun publishStage(
        queueId: String,
        entry: QueueEntity,
        variantLabel: String,
        stage: DownloadStage,
        strategy: DownloadStrategy,
    ) {
        val snapshot = DownloadProgressSnapshot(
            bytesDownloaded = 0L,
            totalBytes = null,
            speedBytesPerSecond = 0L,
            stage = stage,
        )
        updateQueueProgress(
            queueId = queueId,
            title = entry.title,
            variantLabel = variantLabel,
            thumbnailUrl = entry.thumbnailUrl,
            status = QueueStatus.RUNNING,
            progress = progressFor(strategy, snapshot),
            snapshot = snapshot,
        )
    }

    private suspend fun updateQueueProgress(
        queueId: String,
        title: String,
        variantLabel: String,
        thumbnailUrl: String,
        status: QueueStatus,
        progress: Int,
        snapshot: DownloadProgressSnapshot,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val shouldPublish =
            safeProgress == 0 ||
                safeProgress == 100 ||
                lastPublishedProgress < 0 ||
                safeProgress - lastPublishedProgress >= 2 ||
                now - lastPublishedAtMs >= 1_000L
        if (!shouldPublish && status == QueueStatus.RUNNING) return
        lastPublishedProgress = safeProgress
        lastPublishedAtMs = now
        graph.queueRepository.updateStatus(queueId, status, safeProgress, variantLabel = variantLabel)
        setProgress(
            workDataOf(
                "progress" to safeProgress,
                "bytesDownloaded" to snapshot.bytesDownloaded,
                "totalBytes" to (snapshot.totalBytes ?: -1L),
                "speedBytesPerSecond" to snapshot.speedBytesPerSecond,
                "stage" to snapshot.stage.name,
            ),
        )
        setForeground(createForegroundInfo(queueId, title, variantLabel, thumbnailUrl, safeProgress))
    }

    private suspend fun createForegroundInfo(
        queueId: String,
        title: String,
        variantLabel: String,
        thumbnailUrl: String,
        progress: Int,
    ): ForegroundInfo {
        val notification = notifications.buildProgress(queueId, title, variantLabel, progress, thumbnailUrl)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                queueId.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(queueId.hashCode(), notification)
        }
    }

    private suspend fun downloadThumbnailForHistory(
        sourceUrl: String,
        thumbnailUrl: String,
    ): String {
        if (thumbnailUrl.isBlank()) return thumbnailUrl
        val artworkDir = File(applicationContext.filesDir, "download-artwork").apply { mkdirs() }
        val artworkFile = File(artworkDir, "${sourceUrl.sha256Hex()}.jpg")
        if (artworkFile.exists() && artworkFile.length() > 0L) {
            return Uri.fromFile(artworkFile).toString()
        }
        return runCatching {
            val request = Request.Builder().url(thumbnailUrl).build()
            graph.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("No se pudo descargar la miniatura.")
                val body = response.body ?: error("La miniatura llegó vacía.")
                FileOutputStream(artworkFile, false).use { output ->
                    body.byteStream().use { input -> input.copyTo(output, 128 * 1024) }
                }
            }
            Uri.fromFile(artworkFile).toString()
        }.getOrElse {
            artworkFile.delete()
            thumbnailUrl
        }
    }

    private suspend fun copyInto(
        sourceUri: Uri,
        targetUri: Uri,
        strategy: DownloadStrategy,
        queueId: String,
        entry: QueueEntity,
        variantLabel: String,
    ) {
        openInput(sourceUri).use { input ->
            val size = sourceUri.takeIf { it.scheme == "file" }?.toFile()?.length()?.coerceAtLeast(1L) ?: 1L
            openOutput(targetUri).use { output ->
                copyStream(input, output, size) { snapshot ->
                    updateQueueProgress(
                        queueId = queueId,
                        title = entry.title,
                        variantLabel = variantLabel,
                        thumbnailUrl = entry.thumbnailUrl,
                        status = QueueStatus.RUNNING,
                        progress = progressFor(strategy, snapshot),
                        snapshot = snapshot,
                    )
                }
            }
        }
    }

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        contentLength: Long,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit,
    ) {
        val buffer = ByteArray(512 * 1024)
        var copied = 0L
        val startedAt = System.currentTimeMillis()
        var lastPublishedAt = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (isStopped) error("Cancelado por el usuario")
            output.write(buffer, 0, read)
            copied += read
            val now = System.currentTimeMillis()
            if (now - lastPublishedAt >= 250L) {
                lastPublishedAt = now
                val elapsed = (now - startedAt).coerceAtLeast(1L)
                onProgress(
                    DownloadProgressSnapshot(
                        bytesDownloaded = copied,
                        totalBytes = contentLength,
                        speedBytesPerSecond = copied * 1000L / elapsed,
                        stage = DownloadStage.COPYING,
                    ),
                )
            }
        }
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        onProgress(DownloadProgressSnapshot(copied, contentLength, copied * 1000L / elapsed, DownloadStage.COPYING))
    }

    private fun openInput(uri: Uri): InputStream {
        return if (uri.scheme == "file") {
            FileInputStream(uri.toFile())
        } else {
            applicationContext.contentResolver.openInputStream(uri)
        } ?: error("No se pudo abrir el archivo temporal de entrada.")
    }

    private fun openOutput(uri: Uri): OutputStream {
        return if (uri.scheme == "file") {
            FileOutputStream(uri.toFile(), false)
        } else {
            applicationContext.contentResolver.openOutputStream(uri, "w")
        } ?: error("No se pudo abrir el archivo destino.")
    }

}
