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

internal class CombinedTransferProgress {
    private val mutex = Mutex()
    private var video: DownloadProgressSnapshot = DownloadProgressSnapshot(0L, 0L)
    private var audio: DownloadProgressSnapshot = DownloadProgressSnapshot(0L, 0L)

    suspend fun updateVideo(snapshot: DownloadProgressSnapshot): DownloadProgressSnapshot {
        return mutex.withLock {
            video = snapshot
            combined()
        }
    }

    suspend fun updateAudio(snapshot: DownloadProgressSnapshot): DownloadProgressSnapshot {
        return mutex.withLock {
            audio = snapshot
            combined()
        }
    }

    private fun combined(): DownloadProgressSnapshot {
        val totalBytes = listOfNotNull(video.totalBytes, audio.totalBytes).sum().takeIf { it > 0L }
        return DownloadProgressSnapshot(
            bytesDownloaded = video.bytesDownloaded + audio.bytesDownloaded,
            totalBytes = totalBytes,
            speedBytesPerSecond = video.speedBytesPerSecond + audio.speedBytesPerSecond,
            stage = DownloadStage.DOWNLOADING,
        )
    }
}
