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

internal fun SnapMusicViewModel.resolveYouTubeDownloadSheet(
    item: YouTubeFeedItem,
    forceRefresh: Boolean,
) {
    _youtubeDownloadSheet.value = YouTubeDownloadSheetState(
        media = item.toPendingResolvedMedia(),
        visible = true,
        isPreparing = true,
    )
    viewModelScope.launch {
        runCatching { resolveFeaturedVideo(item, forceRefresh = forceRefresh) }
            .onSuccess { featured ->
                val sheet = _youtubeDownloadSheet.value
                if (!sheet.isPreparing || sheet.media?.sourceUrl != item.url) return@onSuccess
                if (hasDownloadVariants(featured.resolvedMedia)) {
                    _youtubeDownloadSheet.value = YouTubeDownloadSheetState(
                        media = featured.resolvedMedia,
                        visible = true,
                    )
                } else {
                    _youtubeDownloadSheet.value = YouTubeDownloadSheetState()
                    _queueFeedback.value = "No encontramos formatos para descargar ese video."
                }
            }
            .onFailure { error ->
                val sheet = _youtubeDownloadSheet.value
                if (!sheet.isPreparing || sheet.media?.sourceUrl != item.url) return@onFailure
                _youtubeDownloadSheet.value = YouTubeDownloadSheetState()
                _queueFeedback.value = userFacingError(error.message, UiFailureKind.EXTRACTION)
            }
    }
}

internal fun SnapMusicViewModel.pendingInstagramMedia(url: String) = ResolvedMedia(
    sourceUrl = url,
    title = "Video de Instagram",
    author = "Instagram",
    durationSeconds = 0L,
    thumbnailUrl = "",
    audioVariants = emptyList(),
    videoVariants = listOf(
        MediaVariant(
            id = INSTAGRAM_FAST_VIDEO_VARIANT_ID,
            label = "Video MP4",
            kind = MediaKind.VIDEO,
            container = ContainerFormat.MP4,
            resolution = "Video",
            directUrl = url,
            sourceId = INSTAGRAM_FAST_VIDEO_VARIANT_ID,
            sourceContainerHint = "MP4",
        ),
    ),
)

internal fun SnapMusicViewModel.recordPlaybackSignal(
    item: YouTubeFeedItem,
    type: MusicSignalType,
) {
    viewModelScope.launch(Dispatchers.IO) {
        runCatching { graph.musicHomeFeedRepository.recordPlaybackSignal(type, item) }
    }
}

internal fun SnapMusicViewModel.maybeRecordYouTubeWatchHistory(
    item: YouTubeFeedItem,
    positionMs: Long,
    force: Boolean,
) {
    val lastPosition = youtubeWatchHistoryLastRecordedPositions[item.url]
    if (!force && lastPosition != null && kotlin.math.abs(positionMs - lastPosition) < 2_000L) return
    youtubeWatchHistoryLastRecordedPositions[item.url] = positionMs
    recordYouTubeWatchHistory(item, positionMs)
}

internal fun SnapMusicViewModel.recordYouTubeWatchHistory(
    item: YouTubeFeedItem,
    positionMs: Long,
) {
    viewModelScope.launch(Dispatchers.IO) {
        runCatching { graph.youtubeWatchHistoryRepository.record(item, positionMs) }
    }
}

internal fun SnapMusicViewModel.preResolveNextQueueItem(
    queueItems: List<YouTubeFeedItem>,
    currentIndex: Int,
    continuationMode: PlaybackContinuationMode,
    allowNetwork: Boolean = false,
) {
    nextQueuePreResolveJob?.cancel()
    val nextItem = nextQueueItem(queueItems, currentIndex, continuationMode) ?: return
    youTubeResolveCache[nextItem.url]?.let { cached ->
        val current = _youtubeState.value
        if (current.nextUpItem?.url == nextItem.url && current.preloadedNextFeatured?.sourceUrl != nextItem.url) {
            _youtubeState.value = current.copy(preloadedNextFeatured = cached)
        }
        return
    }
    if (!allowNetwork) return
    val warmState = _youtubeState.value
    if (!isYouTubePlaybackStableForPreResolve(warmState)) {
        return
    }
    nextQueuePreResolveJob = viewModelScope.launch(Dispatchers.IO) {
        val latest = _youtubeState.value
        if (latest.nextUpItem?.url != nextItem.url) return@launch
        runCatching { resolveFeaturedVideo(nextItem) }
            .onSuccess { featured ->
                val current = _youtubeState.value
                if (current.nextUpItem?.url == nextItem.url) {
                    _youtubeState.value = current.copy(preloadedNextFeatured = featured)
                }
        }
    }
}

internal fun SnapMusicViewModel.isYouTubePlaybackStableForPreResolve(state: YouTubeUiState): Boolean {
    val sourceUrl = state.featured.sourceUrl
    return sourceUrl.isNotBlank() &&
        state.featured.isReady &&
        state.currentPositionMs >= YOUTUBE_NEXT_PRE_RESOLVE_MIN_POSITION_MS &&
        !state.isRefreshingVideo &&
        !state.pendingTransition &&
        (state.showPlayer || state.showMiniPlayer) &&
        !hasRecentYouTubeRebuffer(sourceUrl)
}

internal fun SnapMusicViewModel.hasRecentYouTubeRebuffer(sourceUrl: String): Boolean {
    val events = youtubeRebufferEvents[sourceUrl] ?: return false
    val now = System.currentTimeMillis()
    events.removeAll { now - it > YOUTUBE_NEXT_PRE_RESOLVE_STABLE_WINDOW_MS }
    return events.isNotEmpty()
}

internal fun SnapMusicViewModel.nextYouTubePlaybackSeekRequestId(state: YouTubeUiState): Long {
    return (state.playbackSeekRequestId + 1L).coerceAtLeast(1L)
}

internal fun SnapMusicViewModel.persistCurrentYouTubeSnapshot() {
    val current = _youtubeState.value
    if (current.playbackQueue.isEmpty() || !current.featured.isReady) return
    val snapshot = YouTubePlaybackSnapshot(
        queue = current.playbackQueue,
        currentQueueIndex = resolveCurrentQueueIndex(current),
        query = current.query,
        autoplayEnabled = current.autoplayEnabled,
        continuationMode = current.continuationMode,
        lastPositionMs = current.currentPositionMs,
        origin = current.queueOrigin,
        showMiniPlayer = current.showMiniPlayer,
    )
    viewModelScope.launch {
        graph.preferencesRepository.saveYouTubePlaybackSnapshot(snapshot)
    }
}

internal suspend fun SnapMusicViewModel.resolveFeaturedVideo(
    item: YouTubeFeedItem,
    forceRefresh: Boolean = false,
): YouTubeFeaturedVideo {
    if (!forceRefresh) {
        youTubeResolveCache[item.url]?.let { return it }
    }
    return toFeaturedVideo(item).also { featured ->
        if (featured.isReady) {
            youTubeResolveCache[item.url] = featured
        }
    }
}
