package com.juan.snapmusic.data.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.MediaKind
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
    val speedBytesPerSecond: Long,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val outputUri: String?,
    val createdAt: Long,
    val errorMessage: String?,
    val requiresTranscode: Boolean,
    val requiresMux: Boolean,
    val selectionKind: MediaKind,
    val selectionTargetContainer: ContainerFormat,
    val selectionTargetBitrateKbps: Int?,
    val selectionTargetResolution: String?,
    val selectionStrategy: DownloadStrategy,
    val preferredSourceId: String?,
    val sourceContainerHint: String?,
    val sourceBitrateKbps: Int?,
    val sourceHeight: Int?,
    val allowMuxFallback: Boolean,
    val allowTranscodeFallback: Boolean,
    val laneIndex: Int,
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
    speedBytesPerSecond = speedBytesPerSecond,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    outputUri = outputUri,
    createdAt = createdAt,
    errorMessage = errorMessage,
)

fun QueueEntity.toDownloadSelection() = DownloadSelection(
    kind = selectionKind,
    targetContainer = selectionTargetContainer,
    targetBitrateKbps = selectionTargetBitrateKbps,
    targetResolution = selectionTargetResolution,
    strategy = selectionStrategy,
    preferredSourceId = preferredSourceId,
    sourceContainerHint = sourceContainerHint,
    sourceBitrateKbps = sourceBitrateKbps,
    sourceHeight = sourceHeight,
    allowMuxFallback = allowMuxFallback,
    allowTranscodeFallback = allowTranscodeFallback,
)
