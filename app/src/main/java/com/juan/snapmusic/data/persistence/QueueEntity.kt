package com.juan.snapmusic.data.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.core.model.QueueStatus

@Entity(tableName = "queue_entries")
data class QueueEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val variantLabel: String,
    val container: ContainerFormat,
    val directUrl: String,
    val secondaryUrl: String?,
    val destinationLabel: String,
    val destinationTreeUri: String?,
    val status: QueueStatus,
    val progress: Int,
    val outputUri: String?,
    val createdAt: Long,
    val errorMessage: String?,
    val requiresTranscode: Boolean,
    val requiresMux: Boolean,
)

fun QueueEntity.toModel() = QueueEntry(
    id = id,
    title = title,
    author = author,
    sourceUrl = sourceUrl,
    thumbnailUrl = thumbnailUrl,
    variantLabel = variantLabel,
    container = container,
    status = status,
    progress = progress,
    outputUri = outputUri,
    createdAt = createdAt,
    errorMessage = errorMessage,
)
