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

internal fun progressFor(strategy: DownloadStrategy, snapshot: DownloadProgressSnapshot): Int {
    val fraction = if ((snapshot.totalBytes ?: 0L) > 0L) {
        (snapshot.bytesDownloaded.toDouble() / snapshot.totalBytes!!.toDouble()).coerceIn(0.0, 1.0)
    } else {
        null
    }
    return when (strategy) {
        DownloadStrategy.DIRECT -> when (snapshot.stage) {
            DownloadStage.PREPARING -> 0
            DownloadStage.DOWNLOADING -> scaleProgress(0, 86, fraction, snapshot.bytesDownloaded)
            DownloadStage.COPYING -> scaleProgress(86, 98, fraction, snapshot.bytesDownloaded)
            DownloadStage.VALIDATING -> 99
            else -> 99
        }

        DownloadStrategy.TRANSCODE_AUDIO -> when (snapshot.stage) {
            DownloadStage.PREPARING -> 0
            DownloadStage.DOWNLOADING -> scaleProgress(0, 56, fraction, snapshot.bytesDownloaded)
            DownloadStage.TRANSCODING -> 78
            DownloadStage.COPYING -> scaleProgress(88, 98, fraction, snapshot.bytesDownloaded)
            DownloadStage.VALIDATING -> 99
            else -> 78
        }

        DownloadStrategy.MUX_VIDEO_AUDIO -> when (snapshot.stage) {
            DownloadStage.PREPARING -> 0
            DownloadStage.DOWNLOADING -> scaleProgress(0, 72, fraction, snapshot.bytesDownloaded)
            DownloadStage.MUXING -> 90
            DownloadStage.COPYING -> scaleProgress(94, 98, fraction, snapshot.bytesDownloaded)
            DownloadStage.VALIDATING -> 99
            else -> 90
        }
    }
}

internal fun scaleProgress(start: Int, end: Int, fraction: Double?, bytesDownloaded: Long): Int {
    if (fraction != null) {
        return start + ((end - start) * fraction.coerceIn(0.0, 1.0)).toInt()
    }
    if (bytesDownloaded <= 0L) return start
    val coarseStep = (bytesDownloaded / (4L * 1024L * 1024L)).toInt().coerceAtLeast(1)
    return (start + coarseStep).coerceAtMost((end - 2).coerceAtLeast(start))
}

internal fun shouldRetryWithFreshSources(error: Throwable): Boolean {
    if (error is TransferExpiredException) return true
    if (error is TransferValidationException) return true
    val message = error.message.orEmpty().lowercase()
    return "expir" in message || "inválid" in message || "invalid" in message || "stream remoto" in message
}

internal fun buildFileName(title: String, format: ContainerFormat): String {
    val suffix = when (format) {
        ContainerFormat.MP3 -> ".mp3"
        ContainerFormat.M4A -> ".m4a"
        ContainerFormat.WEBM -> ".webm"
        ContainerFormat.MP4 -> ".mp4"
    }
    return sanitizeFileName(title) + suffix
}

internal fun mimeTypeFor(format: ContainerFormat): String = when (format) {
    ContainerFormat.MP3 -> "audio/mpeg"
    ContainerFormat.M4A -> "audio/mp4"
    ContainerFormat.WEBM -> "audio/webm"
    ContainerFormat.MP4 -> "video/mp4"
}

internal fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return buildString(digest.size * 2) {
        digest.forEach { byte -> append("%02x".format(byte)) }
    }
}

internal fun friendlyErrorMessage(raw: String?): String {
    val message = raw.orEmpty().lowercase()
    return when {
        "timeout" in message || "network" in message || "connect" in message -> {
            "La descarga no pudo seguir por un problema de red."
        }

        "stream remoto" in message || "stream seleccionado" in message -> {
            "La fuente de descarga venció o ya no está disponible. Probá de nuevo."
        }

        "compatible" in message || "mp4 final" in message || "m4a final" in message || "mp3 final" in message -> {
            "No encontramos una fuente compatible para generar ese formato final."
        }

        "inválid" in message || "invalid" in message || "reproduc" in message -> {
            "El archivo final quedó roto y se canceló antes de marcarlo como terminado."
        }

        "newpipe" in message || "extract" in message || "youtube" in message || "json" in message -> {
            "No pudimos preparar bien ese contenido desde YouTube."
        }

        "transcod" in message || "ffmpeg" in message -> "No pude convertir ese archivo al formato final."
        "mux" in message || "audio complementario" in message -> "No pude unir el video y el audio para armar el MP4."
        "destino" in message || "archivo" in message || "carpeta" in message || "document" in message -> {
            "No pude guardar el archivo en la carpeta elegida."
        }

        else -> raw?.takeIf { it.isNotBlank() } ?: "La descarga se cortó antes de terminar."
    }
}
