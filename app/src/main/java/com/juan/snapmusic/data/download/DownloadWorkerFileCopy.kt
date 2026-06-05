package com.juan.snapmusic.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.net.toFile
import androidx.core.net.toUri
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
import com.juan.snapmusic.core.model.TransferSource
import com.juan.snapmusic.core.platform.NotificationHelper
import com.juan.snapmusic.core.platform.normalizeInstagramUrl
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

internal fun DownloadWorker.downloadArtworkCacheKey(
    sourceUrl: String,
    thumbnailUrl: String,
): String {
    return "${sourceUrl.trim()}|${thumbnailUrl.trim()}".sha256Hex()
}

internal class DownloadPausedException : IllegalStateException("Descarga pausada.")

internal suspend fun DownloadWorker.copyInto(
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

internal suspend fun DownloadWorker.copyStream(
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

internal fun DownloadWorker.openInput(uri: Uri): InputStream {
    return if (uri.scheme == "file") {
        FileInputStream(uri.toFile())
    } else {
        applicationContext.contentResolver.openInputStream(uri)
    } ?: error("No se pudo abrir el archivo temporal de entrada.")
}

internal fun DownloadWorker.openOutput(uri: Uri): OutputStream {
    return if (uri.scheme == "file") {
        FileOutputStream(uri.toFile(), false)
    } else {
        applicationContext.contentResolver.openOutputStream(uri, "w")
    } ?: error("No se pudo abrir el archivo destino.")
}
