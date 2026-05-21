package com.juan.snapmusic.feature.youtube

import com.juan.snapmusic.core.model.PlaybackContinuationMode
import com.juan.snapmusic.core.model.YouTubeFeedItem

internal const val PREVIOUS_RESTART_THRESHOLD_MS = 5_000L

internal fun nextQueueIndex(
    queueSize: Int,
    currentIndex: Int,
    continuationMode: PlaybackContinuationMode,
): Int? {
    if (queueSize <= 0 || currentIndex !in 0 until queueSize) return null
    return when {
        currentIndex < queueSize - 1 -> currentIndex + 1
        continuationMode == PlaybackContinuationMode.LOOP_FEED -> 0
        else -> null
    }
}

internal fun previousQueueIndex(
    queueSize: Int,
    currentIndex: Int,
    currentPositionMs: Long,
): Int? {
    if (queueSize <= 0 || currentIndex !in 0 until queueSize) return null
    if (currentPositionMs > PREVIOUS_RESTART_THRESHOLD_MS) return currentIndex
    return when {
        currentIndex > 0 -> currentIndex - 1
        else -> 0
    }
}

internal fun nextQueueItem(
    queue: List<YouTubeFeedItem>,
    currentIndex: Int,
    continuationMode: PlaybackContinuationMode,
): YouTubeFeedItem? {
    val nextIndex = nextQueueIndex(queue.size, currentIndex, continuationMode) ?: return null
    return queue.getOrNull(nextIndex)
}
