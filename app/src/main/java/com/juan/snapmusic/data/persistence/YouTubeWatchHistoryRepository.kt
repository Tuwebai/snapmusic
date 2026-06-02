package com.juan.snapmusic.data.persistence

import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class YouTubeWatchHistoryRepository(
    private val dao: SnapMusicDao,
) {
    fun observeHistory(): Flow<List<YouTubeWatchHistoryEntry>> {
        return dao.observeYouTubeWatchHistory().map { list ->
            val cutoff = System.currentTimeMillis() - WATCH_HISTORY_WINDOW_MS
            list
                .asSequence()
                .filter { entry -> entry.watchedAt >= cutoff }
                .map { entry -> entry.toModel() }
                .toList()
        }
    }

    suspend fun record(item: YouTubeFeedItem, positionMs: Long) {
        if (item.url.isBlank()) return
        dao.upsertYouTubeWatchHistory(
            YouTubeWatchHistoryEntity(
                sourceUrl = item.url,
                title = item.title,
                author = item.author,
                thumbnailUrl = item.thumbnailUrl,
                durationSeconds = item.durationSeconds,
                viewCount = item.viewCount,
                publishedText = item.publishedText,
                description = item.description,
                lastPositionMs = positionMs.coerceAtLeast(0L),
                watchedAt = System.currentTimeMillis(),
            ),
        )
    }

    private companion object {
        private const val WATCH_HISTORY_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
