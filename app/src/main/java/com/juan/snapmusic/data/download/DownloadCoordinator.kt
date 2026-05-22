package com.juan.snapmusic.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import kotlin.math.absoluteValue
import java.util.UUID

class DownloadCoordinator(
    private val context: Context,
    private val queueRepository: QueueRepository,
    private val preferencesRepository: PreferencesRepository,
) {
    private val notifications = NotificationHelper(context)

    suspend fun enqueue(request: ConversionRequest, allowDuplicate: Boolean = false): UUID? {
        val id = request.id
        val inserted = if (allowDuplicate) {
            withContext(Dispatchers.IO) {
                queueRepository.insertDirect(request)
            }
            true
        } else {
            withContext(Dispatchers.IO) {
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
        val parallelSlots = resolveParallelSlots()
        val lane = id.hashCode().absoluteValue % parallelSlots
        WorkManager.getInstance(context).enqueueUniqueWork(
            "snapmusic_queue_$lane",
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

    private suspend fun resolveParallelSlots(): Int {
        val prefs = preferencesRepository.preferences.first()
        val preferred = if (isUnmeteredConnection()) {
            prefs.downloadTasksWifi
        } else {
            prefs.downloadTasksMobile
        }
        return preferred.coerceIn(1, 4)
    }

    private fun isUnmeteredConnection(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return true
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
