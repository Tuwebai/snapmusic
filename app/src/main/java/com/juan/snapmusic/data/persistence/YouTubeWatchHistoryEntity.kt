package com.juan.snapmusic.data.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry

@Entity(
    tableName = "youtube_watch_history",
    indices = [Index(value = ["watchedAt"])],
)
data class YouTubeWatchHistoryEntity(
    @PrimaryKey val sourceUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long?,
    val publishedText: String?,
    val description: String?,
    val watchedAt: Long,
)

fun YouTubeWatchHistoryEntity.toModel() = YouTubeWatchHistoryEntry(
    sourceUrl = sourceUrl,
    title = title,
    author = author,
    thumbnailUrl = thumbnailUrl,
    durationSeconds = durationSeconds,
    viewCount = viewCount,
    publishedText = publishedText,
    description = description,
    watchedAt = watchedAt,
)
