package com.juan.snapmusic.feature.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.SnapMusicGraph
import com.juan.snapmusic.core.model.AppThemeMode
import com.juan.snapmusic.core.model.CacheCleanupUiState
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.ConversionRequest
import com.juan.snapmusic.core.model.DownloadBadgeState
import com.juan.snapmusic.core.model.DownloadCompleteSound
import com.juan.snapmusic.core.model.HistoryEntry
import com.juan.snapmusic.core.model.IncomingShareItem
import com.juan.snapmusic.core.model.IncomingSharePayload
import com.juan.snapmusic.core.model.IncomingShareProvider
import com.juan.snapmusic.core.model.LocalMediaItem
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.MusicSignalType
import com.juan.snapmusic.core.model.PlaybackContinuationMode
import com.juan.snapmusic.core.model.PreviewPlaybackQueueItem
import com.juan.snapmusic.core.model.PreviewPlaybackRenderState
import com.juan.snapmusic.core.model.PreviewPlaybackSnapshot
import com.juan.snapmusic.core.model.PreviewState
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.YouTubePlayerSeekState
import com.juan.snapmusic.core.model.YouTubePlayerSessionState
import com.juan.snapmusic.core.model.YouTubeAdvanceReason
import com.juan.snapmusic.core.model.UserPreferences
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.model.YouTubePlaybackRenderState
import com.juan.snapmusic.core.model.YouTubePlaybackSnapshot
import com.juan.snapmusic.core.model.YouTubeQueueOrigin
import com.juan.snapmusic.core.model.YouTubeUiState
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry
import com.juan.snapmusic.core.platform.MergedPlaybackUri
import com.juan.snapmusic.core.platform.PlaybackNotificationRouteStore
import com.juan.snapmusic.core.platform.PlaybackNotificationRouteTarget
import com.juan.snapmusic.core.platform.PlaybackSessionStateStore
import com.juan.snapmusic.core.platform.validateYouTubeUrl
import com.juan.snapmusic.data.persistence.QueueEntity
import com.juan.snapmusic.data.persistence.toDownloadSelection
import com.juan.snapmusic.feature.youtube.nextQueueIndex
import com.juan.snapmusic.feature.youtube.nextQueueItem
import com.juan.snapmusic.feature.youtube.playback.YouTubeFullscreenController
import com.juan.snapmusic.feature.youtube.playback.YouTubePlaybackRecoveryPolicy
import com.juan.snapmusic.feature.youtube.playback.YouTubePlaybackSelection
import com.juan.snapmusic.feature.youtube.playback.YouTubePlaybackSourceMode
import com.juan.snapmusic.feature.youtube.playback.YouTubePlaybackSourceSelector
import com.juan.snapmusic.feature.youtube.playback.YouTubePlaybackTelemetry
import com.juan.snapmusic.feature.youtube.previousQueueIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class UiFailureKind {
    NETWORK,
    EXTRACTION,
    TRANSCODE,
    STORAGE,
}

internal fun instagramUserFacingError(raw: String?): String {
    val message = raw.orEmpty().lowercase()
    return when {
        "public" in message || "públic" in message -> {
            "No encontramos video público descargable en ese enlace de Instagram."
        }

        "timeout" in message || "network" in message || "connect" in message || "unreachable" in message -> {
            "Instagram no respondió a tiempo. Probá de nuevo en un rato."
        }

        "respond" in message || "response" in message || "http" in message -> {
            "Instagram no entregó el video directo. Probá con un reel o publicación pública."
        }

        else -> "No pudimos preparar ese video de Instagram ahora mismo."
    }
}

internal fun userFacingError(
    raw: String?,
    fallback: UiFailureKind,
): String {
    val message = raw.orEmpty().lowercase()
    return when {
        "timeout" in message || "network" in message || "connect" in message || "unreachable" in message -> {
            "Hay un problema de red. Probá de nuevo cuando tengas mejor conexión."
        }

        "newpipe" in message || "extract" in message || "json" in message || "youtube" in message || "response" in message -> {
            "YouTube no respondió como esperábamos. Probá refrescar o intentá con otro video."
        }

        "ffmpeg" in message || "transcod" in message || "mux" in message -> {
            "No pudimos convertir ese archivo al formato final."
        }

        "perm" in message || "folder" in message || "carpeta" in message || "destino" in message || "downloads" in message || "document" in message -> {
            "No pudimos guardar el archivo en esa carpeta. Revisá el permiso o elegí otro destino."
        }

        fallback == UiFailureKind.NETWORK -> "No pudimos completar esa acción ahora mismo. Probá de nuevo en un rato."
        fallback == UiFailureKind.EXTRACTION -> "No pudimos preparar ese contenido de YouTube ahora mismo."
        fallback == UiFailureKind.TRANSCODE -> "No pudimos convertir ese archivo al formato final."
        else -> "No pudimos guardar el archivo en la carpeta elegida."
    }
}

internal fun com.juan.snapmusic.core.model.HistoryEntry.toPreviewState(): PreviewState {
    return PreviewState(
        title = title,
        subtitle = qualityLabel,
        thumbnailUrl = thumbnailUrl,
        fileUri = outputUri,
        isReady = true,
        isVideo = format == ContainerFormat.MP4 || outputUri.isPreviewVideoLikeUri(),
    )
}

internal fun LocalMediaItem.resolvedLocalMediaTitle(): String {
    if (!hasGenericLocalVideoTitle() && title.isNotBlank()) return title
    return fileName.substringBeforeLast('.', fileName).trim().ifBlank { title.ifBlank { "Video sin título" } }
}

internal fun LocalMediaItem.hasGenericLocalVideoTitle(): Boolean {
    if (!isVideo) return false
    return title.trim().lowercase() in setOf(
        "hd video",
        "video",
        "movie",
        "untitled",
        "untitled video",
        "video sin título",
        "snapmusic",
    )
}

internal fun HistoryEntry.toLocalMediaSubtitle(fallback: String): String {
    val duration = fallback.substringAfter(" · ", missingDelimiterValue = "").trim()
    val owner = author.ifBlank { fallback.substringBefore(" · ").ifBlank { "Video local" } }
    return if (duration.isBlank()) owner else "$owner · $duration"
}

internal fun LocalMediaItem.toPreviewPlaybackQueueItem(): PreviewPlaybackQueueItem {
    return PreviewPlaybackQueueItem(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        fileUri = contentUri,
        isVideo = isVideo,
    )
}

internal fun PreviewState.toPreviewPlaybackQueueItem(): PreviewPlaybackQueueItem? {
    val currentFileUri = fileUri ?: return null
    return PreviewPlaybackQueueItem(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        fileUri = currentFileUri,
        isVideo = isVideo || currentFileUri.isPreviewVideoLikeUri(),
    )
}

internal fun String?.isPreviewVideoLikeUri(): Boolean {
    val raw = this.orEmpty()
    val decoded = runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw)
    val normalized = decoded.substringBefore('?').lowercase()
    val encoded = raw.substringBefore('?').lowercase()
    return normalized.contains("/video/") ||
        normalized.contains("video/media") ||
        normalized.endsWith(".mp4") ||
        normalized.endsWith(".mkv") ||
        normalized.endsWith(".webm") ||
        normalized.endsWith(".mov") ||
        encoded.contains("%2fvideo%2f") ||
        encoded.contains("video%2fmedia") ||
        encoded.contains(".mp4") ||
        encoded.contains(".mkv") ||
        encoded.contains(".webm") ||
        encoded.contains(".mov")
}

internal fun QueueEntity.toRetryRequest(): ConversionRequest {
    val selection = toDownloadSelection()
    return ConversionRequest(
        sourceUrl = sourceUrl,
        title = title,
        author = author,
        thumbnailUrl = thumbnailUrl,
        selectedVariant = MediaVariant(
            id = java.util.UUID.randomUUID().toString(),
            label = variantLabel,
            kind = selection.kind,
            container = selection.targetContainer,
            bitrateKbps = selection.targetBitrateKbps,
            resolution = selection.targetResolution,
            directUrl = directUrl,
            secondaryUrl = secondaryUrl,
            requiresTranscode = requiresTranscode,
            requiresMux = requiresMux,
            isSyntheticOutput = requiresTranscode || requiresMux,
            sourceId = selection.preferredSourceId,
            sourceContainerHint = selection.sourceContainerHint,
            sourceBitrateKbps = selection.sourceBitrateKbps,
            sourceHeight = selection.sourceHeight,
            allowMuxFallback = selection.allowMuxFallback,
            allowTranscodeFallback = selection.allowTranscodeFallback,
        ),
        downloadSelection = selection,
        destinationLabel = destinationLabel,
        destinationTreeUri = destinationTreeUri,
    )
}

internal fun List<MediaVariant>.closestAudioVariant(
    container: ContainerFormat,
    targetBitrate: Int?,
): MediaVariant? {
    return filter { it.container == container }
        .minWithOrNull(
            compareBy<MediaVariant> {
                kotlin.math.abs((it.bitrateKbps ?: targetBitrate ?: 0) - (targetBitrate ?: it.bitrateKbps ?: 0))
            }.thenBy {
                if (it.isSyntheticOutput) 1 else 0
            }.thenByDescending {
                it.bitrateKbps ?: 0
            },
        )
}

internal fun List<MediaVariant>.closestVideoVariant(
    targetHeight: Int,
): MediaVariant? {
    return filter { it.container == ContainerFormat.MP4 }
        .minWithOrNull(
            compareBy<MediaVariant> {
                kotlin.math.abs(((it.sourceHeight ?: it.resolution?.substringBefore('p')?.toIntOrNull()) ?: targetHeight) - targetHeight)
            }.thenBy {
                if (it.requiresMux) 1 else 0
            }.thenByDescending {
                it.sourceHeight ?: it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0
            },
        )
}
