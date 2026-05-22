package com.juan.snapmusic.core.model

import androidx.compose.runtime.Immutable

enum class DownloadStrategy {
    DIRECT,
    TRANSCODE_AUDIO,
    MUX_VIDEO_AUDIO,
}

enum class DownloadStage {
    PREPARING,
    DOWNLOADING,
    TRANSCODING,
    MUXING,
    COPYING,
    VALIDATING,
}

@Immutable
data class DownloadSelection(
    val kind: MediaKind,
    val targetContainer: ContainerFormat,
    val targetBitrateKbps: Int? = null,
    val targetResolution: String? = null,
    val strategy: DownloadStrategy,
)

@Immutable
data class TransferSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

@Immutable
data class TransferProbe(
    val contentLength: Long? = null,
    val contentType: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val acceptsRanges: Boolean = false,
)

@Immutable
data class DownloadProgressSnapshot(
    val bytesDownloaded: Long,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long = 0L,
    val stage: DownloadStage = DownloadStage.DOWNLOADING,
)

sealed interface DownloadExecutionPlan {
    val selection: DownloadSelection

    data class Direct(
        override val selection: DownloadSelection,
        val source: TransferSource,
    ) : DownloadExecutionPlan

    data class AudioTranscode(
        override val selection: DownloadSelection,
        val source: TransferSource,
    ) : DownloadExecutionPlan

    data class MuxVideoAudio(
        override val selection: DownloadSelection,
        val videoSource: TransferSource,
        val audioSource: TransferSource,
    ) : DownloadExecutionPlan
}

fun MediaVariant.toDownloadSelection(): DownloadSelection {
    val strategy = when {
        requiresMux -> DownloadStrategy.MUX_VIDEO_AUDIO
        requiresTranscode -> DownloadStrategy.TRANSCODE_AUDIO
        else -> DownloadStrategy.DIRECT
    }
    return DownloadSelection(
        kind = kind,
        targetContainer = container,
        targetBitrateKbps = bitrateKbps,
        targetResolution = resolution,
        strategy = strategy,
    )
}
