package com.juan.snapmusic.data.download

import android.content.Context
import androidx.work.BackoffPolicy
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
import com.juan.snapmusic.data.storage.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.UUID

class DownloadCoordinator(
    private val context: Context,
    private val queueRepository: QueueRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    private val notifications = NotificationHelper(context)
    private val networkPolicy = DownloadNetworkPolicy(context)

    suspend fun enqueue(request: ConversionRequest, allowDuplicate: Boolean = false): UUID? {
        val id = request.id
        val parallelSlots = resolveParallelSlots()
        val inserted = if (allowDuplicate) {
            withContext(Dispatchers.IO) {
                queueRepository.insertDirect(request, parallelSlots)
            }
            true
        } else {
            withContext(Dispatchers.IO) {
                queueRepository.insertIfAbsent(request, parallelSlots)
            }
        }
        if (!inserted) return null
        val queueEntry = withContext(Dispatchers.IO) { queueRepository.get(id.toString()) } ?: return null
        notifications.showQueued(
            queueId = id.toString(),
            title = request.title,
            variantLabel = request.selectedVariant.label,
            thumbnailUrl = request.thumbnailUrl,
        )

        scheduleWork(queueId = id.toString(), laneIndex = queueEntry.laneIndex)
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

    fun pauseByQueueId(queueId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val current = queueRepository.get(queueId) ?: return@launch
            if (current.status != QueueStatus.RUNNING && current.status != QueueStatus.PENDING) return@launch
            queueRepository.updateStatus(
                id = queueId,
                status = QueueStatus.PAUSED,
                progress = current.progress,
                errorMessage = "Descarga pausada.",
            )
            WorkManager.getInstance(context).cancelAllWorkByTag(queueId)
        }
    }

    fun resumeByQueueId(queueId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val current = queueRepository.get(queueId) ?: return@launch
            if (current.status != QueueStatus.PAUSED) return@launch
            queueRepository.updateStatus(
                id = queueId,
                status = QueueStatus.PENDING,
                progress = current.progress,
                errorMessage = null,
            )
            scheduleWork(queueId = queueId, laneIndex = current.laneIndex)
        }
    }

    private fun scheduleWork(queueId: String, laneIndex: Int) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_QUEUE_ID to queueId))
            .addTag(queueId)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "snapmusic_queue_$laneIndex",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            workRequest,
        )
    }

    private suspend fun resolveParallelSlots(): Int {
        return withContext(Dispatchers.IO) {
            val prefs = preferencesRepository.preferences.first()
            val preferred = if (networkPolicy.isUnmeteredConnection()) {
                prefs.downloadTasksWifi
            } else {
                prefs.downloadTasksMobile
            }
            preferred.coerceIn(1, 4)
        }
    }
}
