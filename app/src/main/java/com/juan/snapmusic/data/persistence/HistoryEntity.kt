package com.juan.snapmusic.data.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.HistoryEntry

@Entity(tableName = "history_entries")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val outputUri: String,
    val format: ContainerFormat,
    val qualityLabel: String,
    val createdAt: Long,
)

fun HistoryEntity.toModel() = HistoryEntry(
    id = id,
    title = title,
    author = author,
    sourceUrl = sourceUrl,
    thumbnailUrl = thumbnailUrl,
    outputUri = outputUri,
    format = format,
    qualityLabel = qualityLabel,
    createdAt = createdAt,
)
