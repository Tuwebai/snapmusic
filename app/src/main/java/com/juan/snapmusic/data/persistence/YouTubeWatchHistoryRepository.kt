package com.juan.snapmusic.data.persistence

import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class YouTubeWatchHistoryRepository(
    private val dao: SnapMusicDao,
) {
    fun observeHistory(): Flow<List<YouTubeWatchHistoryEntry>> {
        return dao.observeYouTubeWatchHistory().map { list -> list.map { it.toModel() } }
    }

    suspend fun record(item: YouTubeFeedItem) {
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
                watchedAt = System.currentTimeMillis(),
            ),
        )
    }
}
