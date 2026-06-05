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

internal fun SnapMusicViewModel.recoverYouTubePlaybackStall(
    current: YouTubeUiState,
    mode: YouTubePlaybackSourceMode,
    positionMs: Long,
    durationMs: Long,
    events: Int,
    reason: String,
): Boolean {
    val featured = current.featured
    val sourceUrl = featured.sourceUrl
    if (sourceUrl.isBlank()) return false
    if (current.isRefreshingVideo || current.pendingTransition) return false
    if (
        mode == YouTubePlaybackSourceMode.MERGED &&
        applyAdaptiveRecovery(current, mode, positionMs, durationMs, events)
    ) {
        return true
    }
    YouTubePlaybackTelemetry.recoverySkipped(sourceUrl, reason, mode, durationMs, positionMs)
    return false
}

internal fun SnapMusicViewModel.applyYouTubeStabilityFallback(
    current: YouTubeUiState,
    mode: YouTubePlaybackSourceMode,
    positionMs: Long,
    durationMs: Long,
    events: Int,
) {
    val featured = current.featured
    val media = featured.resolvedMedia ?: return
    val sourceUrl = featured.sourceUrl
    val fallbackSelection = resolveStabilityPlaybackSelection(
        media = media,
        featured = featured,
        currentMode = mode,
    ) ?: return
    if (fallbackSelection.playbackUrl == featured.playbackUrl) return
    val fallbackKey = "${mode.name}:${featured.actualVideoHeight ?: 0}->${fallbackSelection.sourceMode.name}:${fallbackSelection.expectedHeight ?: 0}"
    val appliedFallbacks = playbackStabilityFallbacks.getOrPut(sourceUrl) { linkedSetOf() }
    if (!appliedFallbacks.add(fallbackKey)) return
    Log.w(
        YOUTUBE_PLAYBACK_LOG_TAG,
        "source=$sourceUrl stabilityFallback=$fallbackKey durationMs=$durationMs positionMs=$positionMs events=$events",
    )
    val updatedFeatured = featured.copy(
        playbackUrl = fallbackSelection.playbackUrl,
        actualVideoHeight = fallbackSelection.expectedHeight,
        actualPlaybackLabel = playbackLabelForSelection(media, "auto", fallbackSelection.expectedHeight),
        isReady = true,
    )
    youTubeResolveCache[sourceUrl] = updatedFeatured
    _youtubeState.value = current.copy(
        featured = updatedFeatured,
        currentPositionMs = positionMs.coerceAtLeast(0L),
        isRefreshingVideo = false,
        pendingTransition = false,
        shouldAutoPlayCurrent = true,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
}

internal fun SnapMusicViewModel.applyAdaptiveRecovery(
    current: YouTubeUiState,
    mode: YouTubePlaybackSourceMode,
    positionMs: Long,
    durationMs: Long,
    events: Int,
): Boolean {
    val featured = current.featured
    val media = featured.resolvedMedia ?: return false
    val recoverySelection = YouTubePlaybackSourceSelector.adaptiveRecovery(
        media = media,
        currentHeight = featured.actualVideoHeight,
    ) ?: return false
    if (featured.playbackUrl == recoverySelection.playbackUrl) return false
    val sourceUrl = featured.sourceUrl
    val fallbackKey = "${mode.name}->ADAPTIVE:${recoverySelection.expectedHeight ?: 0}"
    val appliedFallbacks = playbackStabilityFallbacks.getOrPut(sourceUrl) { linkedSetOf() }
    if (!appliedFallbacks.add(fallbackKey)) return false
    YouTubePlaybackTelemetry.adaptiveRecovery(sourceUrl, mode, recoverySelection, durationMs, positionMs)
    val updatedFeatured = featured.copy(
        playbackUrl = recoverySelection.playbackUrl,
        adaptivePlaybackUrl = recoverySelection.playbackUrl,
        autoMaxVideoHeight = recoverySelection.expectedHeight,
        actualVideoHeight = recoverySelection.expectedHeight,
        actualPlaybackLabel = playbackLabelForSelection(media, "auto", recoverySelection.expectedHeight),
        isReady = true,
    )
    youTubeResolveCache[sourceUrl] = updatedFeatured
    _youtubeState.value = current.copy(
        featured = updatedFeatured,
        currentPositionMs = positionMs.coerceAtLeast(0L),
        isRefreshingVideo = false,
        pendingTransition = false,
        shouldAutoPlayCurrent = true,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
    return true
}

internal fun SnapMusicViewModel.applyAdaptiveAutoHeightCap(
    current: YouTubeUiState,
    positionMs: Long,
    durationMs: Long,
    events: Int,
): Boolean {
    val featured = current.featured
    val media = featured.resolvedMedia ?: return false
    val sourceUrl = featured.sourceUrl
    val currentCap = featured.autoMaxVideoHeight ?: preferredAutomaticPlaybackHeight(media) ?: return false
    val targetHeight = lowerStableAdaptiveHeight(media, currentCap) ?: return false
    if (targetHeight >= currentCap) return false
    val fallbackKey = "ADAPTIVE_CAP:$currentCap->$targetHeight"
    val appliedFallbacks = playbackStabilityFallbacks.getOrPut(sourceUrl) { linkedSetOf() }
    if (!appliedFallbacks.add(fallbackKey)) return false
    Log.w(
        YOUTUBE_PLAYBACK_LOG_TAG,
        "source=$sourceUrl adaptiveCap=$fallbackKey durationMs=$durationMs positionMs=$positionMs events=$events",
    )
    val updatedFeatured = featured.copy(
        autoMaxVideoHeight = targetHeight,
        actualVideoHeight = targetHeight,
        actualPlaybackLabel = playbackLabelForSelection(media, "auto", targetHeight),
        isReady = true,
    )
    youTubeResolveCache[sourceUrl] = updatedFeatured
    _youtubeState.value = current.copy(
        featured = updatedFeatured,
        currentPositionMs = positionMs.coerceAtLeast(0L),
        isRefreshingVideo = false,
        pendingTransition = false,
        shouldAutoPlayCurrent = true,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
    return true
}

internal fun SnapMusicViewModel.refreshAdaptiveYouTubePlaybackSource(
    current: YouTubeUiState,
    rawMessage: String?,
): Boolean {
    val sourceUrl = current.featured.sourceUrl
    if (sourceUrl.isBlank()) return false
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    val currentIndex = resolveCurrentQueueIndex(current, queueItems)
    val currentItem = queueItems.getOrNull(currentIndex) ?: return false
    YouTubePlaybackTelemetry.refreshAdaptive(sourceUrl, rawMessage)
    _youtubeState.value = current.copy(
        isRefreshingVideo = true,
        pendingTransition = false,
        errorMessage = null,
    )
    viewModelScope.launch {
        youTubeResolveCache.remove(currentItem.url)
        runCatching { resolveFeaturedVideo(currentItem, forceRefresh = true) }
            .onSuccess { resolvedFeatured ->
                val latest = _youtubeState.value
                if (latest.featured.sourceUrl != currentItem.url) return@onSuccess
                val selectedQuality = current.featured.selectedVideoQualityId
                val refreshedFeatured = applyResolvedPlaybackSelection(
                    featured = resolvedFeatured,
                    variantId = selectedQuality,
                )
                _youtubeState.value = latest.copy(
                    featured = refreshedFeatured,
                    isRefreshingVideo = false,
                    pendingTransition = false,
                    shouldAutoPlayCurrent = true,
                    errorMessage = null,
                    preloadedNextFeatured = nextQueueItem(queueItems, currentIndex, latest.continuationMode)?.let { youTubeResolveCache[it.url] },
                )
                youTubeResolveCache[currentItem.url] = refreshedFeatured
                persistCurrentYouTubeSnapshot()
                preResolveNextQueueItem(queueItems, currentIndex, latest.continuationMode)
            }
            .onFailure {
                if (_youtubeState.value.featured.sourceUrl == currentItem.url) {
                    if (!retryYouTubePlaybackSource(it.message)) {
                        handleYouTubePlaybackFailure(
                            currentIndex = currentIndex,
                            rawMessage = it.message,
                        )
                    }
                }
            }
    }
    return true
}

internal fun SnapMusicViewModel.retryExpiredYouTubeStream(): Boolean {
    val current = _youtubeState.value
    val sourceUrl = current.featured.sourceUrl
    if (sourceUrl.isBlank() || lastExpiredStreamRetrySourceUrl == sourceUrl) return false
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    val currentIndex = resolveCurrentQueueIndex(current, queueItems)
    val currentItem = queueItems.getOrNull(currentIndex) ?: return false
    lastExpiredStreamRetrySourceUrl = sourceUrl
    _youtubeState.value = current.copy(
        isRefreshingVideo = true,
        pendingTransition = false,
        errorMessage = null,
    )
    viewModelScope.launch {
        youTubeResolveCache.remove(currentItem.url)
        runCatching { resolveFeaturedVideo(currentItem, forceRefresh = true) }
            .onSuccess { featured ->
                val latest = _youtubeState.value
                if (latest.featured.sourceUrl != currentItem.url) return@onSuccess
                _youtubeState.value = latest.copy(
                    featured = featured,
                    isRefreshingVideo = false,
                    pendingTransition = false,
                    shouldAutoPlayCurrent = true,
                    errorMessage = null,
                    preloadedNextFeatured = nextQueueItem(queueItems, currentIndex, latest.continuationMode)?.let { youTubeResolveCache[it.url] },
                )
                persistCurrentYouTubeSnapshot()
                preResolveNextQueueItem(queueItems, currentIndex, latest.continuationMode)
            }
            .onFailure {
                if (_youtubeState.value.featured.sourceUrl == currentItem.url) {
                    lastExpiredStreamRetrySourceUrl = null
                    handleYouTubePlaybackFailure(
                        currentIndex = currentIndex,
                        rawMessage = it.message,
                    )
                }
            }
    }
    return true
}

internal fun SnapMusicViewModel.handleYouTubePlaybackFailure(
    currentIndex: Int,
    rawMessage: String?,
) {
    val current = _youtubeState.value
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    val nextIndex = nextQueueIndex(queueItems.size, currentIndex, current.continuationMode)
    val startedPlaybackForCurrentItem = current.currentPositionMs >= 1_500L
    if (
        current.autoplayEnabled &&
        startedPlaybackForCurrentItem &&
        nextIndex != null &&
        current.featured.sourceUrl.isNotBlank() &&
        lastFailureFallbackSourceUrl != current.featured.sourceUrl
    ) {
        lastFailureFallbackSourceUrl = current.featured.sourceUrl
        _queueFeedback.value = "Ese stream falló. SnapMusic intentó seguir con el siguiente."
        playYouTubeQueueItem(index = nextIndex, userInitiated = false)
        return
    }
    lastFailureFallbackSourceUrl = null
    _youtubeState.value = current.copy(
        isRefreshingVideo = false,
        pendingTransition = false,
        shouldAutoPlayCurrent = false,
        preloadedNextFeatured = null,
        errorMessage = userFacingError(rawMessage, UiFailureKind.NETWORK),
    )
    persistCurrentYouTubeSnapshot()
}
