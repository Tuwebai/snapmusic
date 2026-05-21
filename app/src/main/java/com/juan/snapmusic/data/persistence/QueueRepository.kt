package com.juan.snapmusic.data.persistence

import com.juan.snapmusic.core.model.ConversionRequest
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.core.model.QueueStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QueueRepository(
    private val dao: SnapMusicDao,
) {
    private val enqueueMutex = Mutex()

    fun observeQueue(): Flow<List<QueueEntry>> = dao.observeQueue().map { list -> list.map { it.toModel() } }

    suspend fun insertIfAbsent(request: ConversionRequest): Boolean {
        return enqueueMutex.withLock {
            val duplicate = dao.findQueueCandidates(
                sourceUrl = request.sourceUrl.trim(),
                container = request.selectedVariant.container,
                destinationLabel = request.destinationLabel.trim(),
                destinationTreeUri = request.destinationTreeUri?.trim().takeUnless { it.isNullOrEmpty() },
            ).firstOrNull { candidate ->
                candidate.status == QueueStatus.PENDING ||
                    candidate.status == QueueStatus.RUNNING ||
                    candidate.status == QueueStatus.SUCCESS
            }?.takeIf { it.matches(request) }
            if (duplicate != null) return@withLock false
            insertDirectLocked(request)
            true
        }
    }

    suspend fun insertDirect(request: ConversionRequest) {
        enqueueMutex.withLock {
            insertDirectLocked(request)
        }
    }

    private suspend fun insertDirectLocked(request: ConversionRequest) {
        dao.upsertQueue(
            QueueEntity(
                id = request.id.toString(),
                title = request.title,
                author = request.author,
                sourceUrl = request.sourceUrl.trim(),
                thumbnailUrl = request.thumbnailUrl,
                variantLabel = request.selectedVariant.label,
                container = request.selectedVariant.container,
                directUrl = request.selectedVariant.directUrl,
                secondaryUrl = request.selectedVariant.secondaryUrl,
                destinationLabel = request.destinationLabel.trim(),
                destinationTreeUri = request.destinationTreeUri?.trim().takeUnless { it.isNullOrEmpty() },
                status = QueueStatus.PENDING,
                progress = 0,
                outputUri = null,
                createdAt = System.currentTimeMillis(),
                errorMessage = null,
                requiresTranscode = request.selectedVariant.requiresTranscode,
                requiresMux = request.selectedVariant.requiresMux,
            ),
        )
    }

    suspend fun get(id: String): QueueEntity? = dao.getQueueById(id)

    suspend fun restoreInterruptedDownloads() {
        dao.requeueInterrupted(
            runningStatus = QueueStatus.RUNNING,
            pendingStatus = QueueStatus.PENDING,
        )
    }

    suspend fun updateStatus(
        id: String,
        status: QueueStatus,
        progress: Int,
        outputUri: String? = null,
        errorMessage: String? = null,
    ) {
        val current = dao.getQueueById(id) ?: return
        dao.upsertQueue(
            current.copy(
                status = status,
                progress = progress,
                outputUri = outputUri ?: current.outputUri,
                errorMessage = errorMessage,
            ),
        )
    }

    suspend fun remove(id: String) {
        dao.deleteQueue(id)
    }

    private fun QueueEntity.matches(request: ConversionRequest): Boolean {
        if (sourceUrl.trim() != request.sourceUrl.trim()) return false
        if (container != request.selectedVariant.container) return false
        if (destinationLabel.trim() != request.destinationLabel.trim()) return false
        if (destinationTreeUri.normalizedTreeUri() != request.destinationTreeUri.normalizedTreeUri()) return false
        if (requiresMux != request.selectedVariant.requiresMux) return false
        if (requiresTranscode != request.selectedVariant.requiresTranscode) return false
        if (directUrl == request.selectedVariant.directUrl && secondaryUrl == request.selectedVariant.secondaryUrl) return true
        if (variantLabel.normalizedVariantLabel() == request.selectedVariant.label.normalizedVariantLabel()) return true
        return variantLabel.variantSignature() == request.selectedVariant.label.variantSignature()
    }

    private fun String?.normalizedTreeUri(): String? = this?.trim()?.takeUnless(String::isBlank)

    private fun String.normalizedVariantLabel(): String =
        lowercase().filter { it.isLetterOrDigit() }

    private fun String.variantSignature(): String {
        val lower = lowercase()
        val numbers = "\\d+".toRegex().findAll(lower).joinToString("_") { it.value }
        val flavor = when {
            "audio" in lower || "mp3" in lower || "m4a" in lower -> "audio"
            "video" in lower || "mp4" in lower || "webm" in lower -> "video"
            else -> "media"
        }
        return "$flavor|$numbers"
    }
}
