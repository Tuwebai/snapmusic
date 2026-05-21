package com.juan.snapmusic.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.juan.snapmusic.core.model.ConversionRequest
import com.juan.snapmusic.core.model.QueueStatus
import com.juan.snapmusic.core.platform.NotificationHelper
import com.juan.snapmusic.data.persistence.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class DownloadCoordinator(
    private val context: Context,
    private val queueRepository: QueueRepository,
) {
    private val notifications = NotificationHelper(context)

    fun enqueue(request: ConversionRequest, allowDuplicate: Boolean = false): UUID? {
        val id = request.id
        val inserted = if (allowDuplicate) {
            runBlocking(Dispatchers.IO) {
                queueRepository.insertDirect(request)
            }
            true
        } else {
            runBlocking(Dispatchers.IO) {
                queueRepository.insertIfAbsent(request)
            }
        }
        if (!inserted) return null
        notifications.showQueued(
            queueId = id.toString(),
            title = request.title,
            variantLabel = request.selectedVariant.label,
            thumbnailUrl = request.thumbnailUrl,
        )

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_QUEUE_ID to id.toString()))
            .addTag(id.toString())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "snapmusic_queue",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest,
        )
        return id
    }

    fun cancel(id: UUID) {
        cancelByQueueId(id.toString())
    }

    fun cancelByQueueId(queueId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(queueId)
        CoroutineScope(Dispatchers.IO).launch {
            queueRepository.updateStatus(queueId, QueueStatus.CANCELLED, 0, errorMessage = "La descarga se canceló.")
        }
    }
}
