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
import com.juan.snapmusic.core.model.QueueStatus
import com.juan.snapmusic.core.platform.NotificationHelper
import com.juan.snapmusic.core.platform.sanitizeFileName
import com.juan.snapmusic.data.persistence.QueueEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

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

        return@withContext try {
            setForeground(createForegroundInfo(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, 0))
            updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, 0)

            val targetUri = graph.storageRepository.createDestinationUri(
                preferences = graph.currentPreferences(),
                fileName = buildFileName(entry.title, entry.container),
                mimeType = mimeTypeFor(entry.container),
            )

            when {
                entry.requiresMux -> processMuxDownload(queueId, entry, targetUri)
                entry.requiresTranscode -> processAudioTranscode(queueId, entry, targetUri)
                else -> {
                    downloadInto(entry.directUrl, targetUri) { progress ->
                        updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, progress)
                    }
                }
            }
            val localThumbnailUrl = downloadThumbnailForHistory(queueId, entry.thumbnailUrl)

            graph.queueRepository.updateStatus(queueId, QueueStatus.SUCCESS, 100, outputUri = targetUri.toString())
            graph.historyRepository.append(
                id = queueId,
                title = entry.title,
                author = entry.author,
                sourceUrl = entry.sourceUrl,
                thumbnailUrl = localThumbnailUrl,
                outputUri = targetUri.toString(),
                format = entry.container,
                qualityLabel = entry.variantLabel,
            )
            notifications.showSuccess(queueId.hashCode(), entry.title, entry.variantLabel, localThumbnailUrl)
            Result.success()
        } catch (cancelled: Throwable) {
            if (isStopped) {
                graph.queueRepository.updateStatus(queueId, QueueStatus.CANCELLED, 0, errorMessage = "Cancelado por el usuario")
                Result.failure()
            } else {
                val safeMessage = friendlyErrorMessage(cancelled.message)
                graph.queueRepository.updateStatus(queueId, QueueStatus.ERROR, 0, errorMessage = safeMessage)
                notifications.showError(
                    queueId.hashCode(),
                    entry.title,
                    safeMessage,
                    entry.thumbnailUrl,
                )
                Result.failure()
            }
        }
    }

    private suspend fun processAudioTranscode(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
    ) {
        val sourceFile = createTempFile("snapmusic-audio-source-", ".bin")
        val sourceUri = Uri.fromFile(sourceFile)
        var transcodedUri: Uri? = null

        try {
            downloadInto(entry.directUrl, sourceUri) { progress ->
                updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, stagedProgress(0, 55, progress))
            }
            updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, 66)

            transcodedUri = graph.transcodeEngine.extractAudio(sourceUri, entry.container, entry.variantLabel)
            updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, 88)

            copyInto(transcodedUri, targetUri) { progress ->
                updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, stagedProgress(88, 100, progress))
            }
        } finally {
            sourceFile.delete()
            transcodedUri?.takeIf { it.scheme == "file" }?.toFile()?.delete()
        }
    }

    private suspend fun processMuxDownload(
        queueId: String,
        entry: QueueEntity,
        targetUri: Uri,
    ) {
        val audioUrl = entry.secondaryUrl ?: error("Falta el audio complementario para armar el MP4 final.")
        val videoFile = createTempFile("snapmusic-video-source-", ".bin")
        val audioFile = createTempFile("snapmusic-audio-source-", ".bin")
        val videoUri = Uri.fromFile(videoFile)
        val audioUri = Uri.fromFile(audioFile)
        var muxedUri: Uri? = null

        try {
            downloadInto(entry.directUrl, videoUri) { progress ->
                updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, stagedProgress(0, 38, progress))
            }
            downloadInto(audioUrl, audioUri) { progress ->
                updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, stagedProgress(38, 72, progress))
            }
            updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, 82)

            muxedUri = graph.transcodeEngine.muxVideo(videoUri, audioUri, entry.variantLabel)
            updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, 94)

            copyInto(muxedUri, targetUri) { progress ->
                updateQueueProgress(queueId, entry.title, entry.variantLabel, entry.thumbnailUrl, QueueStatus.RUNNING, stagedProgress(94, 100, progress))
            }
        } finally {
            videoFile.delete()
            audioFile.delete()
            muxedUri?.takeIf { it.scheme == "file" }?.toFile()?.delete()
        }
    }

    private suspend fun updateQueueProgress(
        queueId: String,
        title: String,
        variantLabel: String,
        thumbnailUrl: String,
        status: QueueStatus,
        progress: Int,
    ) {
        val safeProgress = progress.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val shouldPublish =
            safeProgress == 0 ||
                safeProgress == 100 ||
                lastPublishedProgress < 0 ||
                safeProgress - lastPublishedProgress >= 2 ||
                now - lastPublishedAtMs >= 1_200L
        if (!shouldPublish && status == QueueStatus.RUNNING) return
        lastPublishedProgress = safeProgress
        lastPublishedAtMs = now
        graph.queueRepository.updateStatus(queueId, status, safeProgress)
        setProgress(workDataOf("progress" to safeProgress))
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

    private suspend fun downloadInto(url: String, targetUri: Uri, onProgress: suspend (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        graph.okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("No se pudo descargar el stream seleccionado.")
            }
            val body = response.body ?: error("La respuesta del stream llegó vacía.")
            openOutput(targetUri).use { output ->
                body.byteStream().use { input ->
                    copyStream(input, output, body.contentLength().coerceAtLeast(1L), onProgress)
                }
            }
        }
    }

    private suspend fun downloadThumbnailForHistory(
        queueId: String,
        thumbnailUrl: String,
    ): String {
        if (thumbnailUrl.isBlank()) return thumbnailUrl
        val artworkDir = File(applicationContext.filesDir, "download-artwork").apply { mkdirs() }
        val artworkFile = File(artworkDir, "$queueId-${UUID.randomUUID()}.jpg")
        return runCatching {
            downloadInto(
                url = thumbnailUrl,
                targetUri = Uri.fromFile(artworkFile),
                onProgress = {},
            )
            Uri.fromFile(artworkFile).toString()
        }.getOrElse {
            artworkFile.delete()
            thumbnailUrl
        }
    }

    private suspend fun copyInto(sourceUri: Uri, targetUri: Uri, onProgress: suspend (Int) -> Unit) {
        openInput(sourceUri).use { input ->
            val size = sourceUri.takeIf { it.scheme == "file" }?.toFile()?.length()?.coerceAtLeast(1L) ?: 1L
            openOutput(targetUri).use { output ->
                copyStream(input, output, size, onProgress)
            }
        }
    }

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        contentLength: Long,
        onProgress: suspend (Int) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var lastProgress = -1
        var read = input.read(buffer)

        while (read >= 0) {
            if (isStopped) error("Cancelado por el usuario")
            output.write(buffer, 0, read)
            copied += read
            val progress = ((copied * 100) / contentLength).toInt()
            if (progress != lastProgress) {
                lastProgress = progress
                onProgress(progress)
            }
            read = input.read(buffer)
        }
        if (lastProgress < 100) onProgress(100)
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
            FileOutputStream(uri.toFile())
        } else {
            applicationContext.contentResolver.openOutputStream(uri, "w")
        } ?: error("No se pudo abrir el archivo destino.")
    }

    private fun createTempFile(prefix: String, suffix: String): File {
        val dir = File(applicationContext.cacheDir, "downloads").apply { mkdirs() }
        return File.createTempFile(prefix, suffix, dir)
    }

    private fun stagedProgress(start: Int, end: Int, progress: Int): Int {
        val clamped = progress.coerceIn(0, 100)
        return start + ((end - start) * clamped / 100)
    }

    private fun buildFileName(title: String, format: ContainerFormat): String {
        val suffix = when (format) {
            ContainerFormat.MP3 -> ".mp3"
            ContainerFormat.M4A -> ".m4a"
            ContainerFormat.MP4 -> ".mp4"
        }
        return sanitizeFileName(title) + suffix
    }

    private fun mimeTypeFor(format: ContainerFormat): String = when (format) {
        ContainerFormat.MP3 -> "audio/mpeg"
        ContainerFormat.M4A -> "audio/mp4"
        ContainerFormat.MP4 -> "video/mp4"
    }

    private fun friendlyErrorMessage(raw: String?): String {
        val message = raw.orEmpty().lowercase()
        return when {
            "timeout" in message || "network" in message || "connect" in message -> {
                "La descarga no pudo seguir por un problema de red."
            }
            "stream" in message -> "No pude bajar ese formato. Probá con otra calidad o intentá de nuevo."
            "parcel blob" in message || "transactiontoolarge" in message || "binder" in message -> {
                "La vista previa de la descarga se trabó antes de arrancar."
            }
            "newpipe" in message || "extract" in message || "youtube" in message || "json" in message -> {
                "No pudimos preparar bien ese contenido desde YouTube."
            }
            "transcod" in message || "ffmpeg" in message -> "No pude convertir ese archivo al formato final."
            "mux" in message || "audio complementario" in message -> "No pude unir el video y el audio para armar el MP4."
            "destino" in message || "archivo" in message || "carpeta" in message || "document" in message -> {
                "No pude guardar el archivo en la carpeta elegida."
            }
            "respuesta" in message -> "La descarga empezó, pero el origen respondió vacío."
            else -> raw?.takeIf { it.isNotBlank() } ?: "La descarga se cortó antes de terminar."
        }
    }
}
