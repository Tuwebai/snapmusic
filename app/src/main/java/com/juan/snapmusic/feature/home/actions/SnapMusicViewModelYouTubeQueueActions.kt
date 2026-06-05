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

fun SnapMusicViewModel.selectYouTubeItem(item: YouTubeFeedItem) {
    val current = _youtubeState.value
    maybeRecordFastSkip(current, item)
    if (current.featured.sourceUrl == item.url && current.featured.resolvedMedia != null) {
        val requestedResumePositionMs = consumePendingYouTubeHistoryResumePosition(item)
        val effectivePositionMs = requestedResumePositionMs.takeIf { it > 0L } ?: current.currentPositionMs
        _youtubeState.value = current.copy(
            showPlayer = true,
            showMiniPlayer = false,
            currentPositionMs = effectivePositionMs,
            playbackSeekRequestId = if (requestedResumePositionMs > 0L) {
                nextYouTubePlaybackSeekRequestId(current)
            } else {
                current.playbackSeekRequestId
            },
            shouldAutoPlayCurrent = true,
            errorMessage = null,
        )
        maybeRecordYouTubeWatchHistory(item, effectivePositionMs, force = true)
        persistCurrentYouTubeSnapshot()
        return
    }
    val sourceItems = when {
        current.showPlayer -> current.playbackQueue.ifEmpty { current.items }
        current.items.any { it.url == item.url } -> current.items
        else -> listOf(item)
    }
    val queueOrigin = when {
        current.showPlayer && current.watchNextItems.any { it.url == item.url } -> YouTubeQueueOrigin.HOME_FEED
        current.query.isBlank() -> YouTubeQueueOrigin.HOME_FEED
        else -> YouTubeQueueOrigin.SEARCH_RESULTS
    }
    val queueItems = if (queueOrigin == YouTubeQueueOrigin.SEARCH_RESULTS) {
        listOf(item)
    } else {
        sourceItems
    }
    val startIndex = queueItems.indexOfFirst { it.url == item.url }.takeIf { it >= 0 } ?: 0
    setYouTubeQueue(
        items = queueItems,
        startIndex = startIndex,
        sourceLabel = queueOrigin,
    )
    enrichWatchNextQueue(item, requireWarmPlayback = false)
}

fun SnapMusicViewModel.playYouTubeWatchHistoryItem(
    entry: YouTubeWatchHistoryEntry,
    entries: List<YouTubeWatchHistoryEntry> = listOf(entry),
) {
    val resumePositionMs = normalizedYouTubeResumePosition(
        positionMs = entry.lastPositionMs,
        durationSeconds = entry.durationSeconds,
    )
    pendingYouTubeHistoryResumePositions[entry.sourceUrl] = resumePositionMs
    val target = entry.toYouTubeFeedItem()
    selectHomeYouTubeTab()
    _youtubeState.value = _youtubeState.value.copy(
        showPlayer = true,
        showMiniPlayer = false,
        compactMiniPlayer = false,
    )
    setYouTubeQueue(
        items = listOf(target),
        startIndex = 0,
        sourceLabel = YouTubeQueueOrigin.RESTORED_SESSION,
    )
    enrichWatchNextQueue(target, requireWarmPlayback = false)
}

fun SnapMusicViewModel.prepareYouTubeDownload(item: YouTubeFeedItem) {
    val current = _youtubeState.value
    if (current.featured.sourceUrl == item.url && hasDownloadVariants(current.featured.resolvedMedia)) {
        _youtubeDownloadSheet.value = YouTubeDownloadSheetState(
            media = current.featured.resolvedMedia,
            visible = true,
        )
        return
    }
    resolveYouTubeDownloadSheet(
        item = item,
        forceRefresh = current.featured.sourceUrl == item.url,
    )
}

fun SnapMusicViewModel.setYouTubeQueue(
    items: List<YouTubeFeedItem>,
    startIndex: Int,
    sourceLabel: YouTubeQueueOrigin,
) {
    if (items.isEmpty()) return
    watchNextEnrichmentJob?.cancel()
    val normalizedIndex = startIndex.coerceIn(0, items.lastIndex)
    val current = _youtubeState.value
    val target = items[normalizedIndex]
    val seededWatchNextItems = initialWatchNextItems(items, normalizedIndex, sourceLabel)
    if (
        current.playbackQueue.map(YouTubeFeedItem::url) == items.map(YouTubeFeedItem::url) &&
        current.currentQueueIndex == normalizedIndex &&
        current.featured.sourceUrl == target.url &&
        current.featured.isReady
    ) {
        val resumePositionMs = consumePendingYouTubeHistoryResumePosition(target)
        _youtubeState.value = current.copy(
            showPlayer = true,
            showMiniPlayer = false,
            watchNextItems = seededWatchNextItems,
            watchNextCursor = null,
            canLoadMoreWatchNext = true,
            currentPositionMs = resumePositionMs.takeIf { it > 0L } ?: current.currentPositionMs,
            playbackSeekRequestId = if (resumePositionMs > 0L) {
                nextYouTubePlaybackSeekRequestId(current)
            } else {
                current.playbackSeekRequestId
            },
            shouldAutoPlayCurrent = true,
            errorMessage = null,
        )
        persistCurrentYouTubeSnapshot()
        return
    }
    _youtubeState.value = current.copy(
        playbackQueue = items,
        watchNextItems = seededWatchNextItems,
        watchNextCursor = null,
        currentQueueIndex = normalizedIndex,
        queueOrigin = sourceLabel,
        nextUpItem = if (current.autoplayEnabled) {
            nextQueueItem(items, normalizedIndex, current.continuationMode)
        } else {
            null
        },
        canLoadMoreWatchNext = true,
        preloadedNextFeatured = nextQueueItem(items, normalizedIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
        currentPositionMs = 0L,
        compactMiniPlayer = false,
    )
    playYouTubeQueueItem(normalizedIndex, userInitiated = true)
}

fun SnapMusicViewModel.playYouTubeQueueItem(
    index: Int,
    userInitiated: Boolean,
) {
    watchNextEnrichmentJob?.cancel()
    youtubeFeedPrefetchJob?.cancel()
    youtubeLoadMoreJob?.cancel()
    val current = _youtubeState.value
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    if (queueItems.isEmpty()) return
    val normalizedIndex = index.coerceIn(0, queueItems.lastIndex)
    val target = queueItems[normalizedIndex]
    val requestedResumePositionMs = consumePendingYouTubeHistoryResumePosition(target)
    val resumePositionMs = requestedResumePositionMs.takeIf { it > 0L } ?: 0L
    val keepMiniPlayer = current.showMiniPlayer && !current.showPlayer
    val seededWatchNextItems = initialWatchNextItems(
        queueItems,
        normalizedIndex,
        seedOriginForWatchNext(current),
    )
    if (current.featured.sourceUrl == target.url && current.featured.isReady) {
        val effectivePositionMs = requestedResumePositionMs.takeIf { it > 0L } ?: current.currentPositionMs
        resetPlaybackFallbacks(target.url)
        _youtubeState.value = current.copy(
            playbackQueue = queueItems,
            watchNextItems = seededWatchNextItems,
            watchNextCursor = null,
            currentQueueIndex = normalizedIndex,
            nextUpItem = if (current.autoplayEnabled) {
                nextQueueItem(queueItems, normalizedIndex, current.continuationMode)
            } else {
                null
            },
            preloadedNextFeatured = nextQueueItem(queueItems, normalizedIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
            currentPositionMs = effectivePositionMs,
            playbackSeekRequestId = if (requestedResumePositionMs > 0L) {
                nextYouTubePlaybackSeekRequestId(current)
            } else {
                current.playbackSeekRequestId
            },
            isRefreshingVideo = false,
            pendingTransition = false,
            showPlayer = !keepMiniPlayer,
            showMiniPlayer = keepMiniPlayer,
            shouldAutoPlayCurrent = userInitiated,
            errorMessage = null,
        )
        recordPlaybackSignal(target, MusicSignalType.REPLAY)
        maybeRecordYouTubeWatchHistory(target, effectivePositionMs, force = true)
        persistCurrentYouTubeSnapshot()
        return
    }

    _youtubeState.value = current.copy(
        playbackQueue = queueItems,
        watchNextItems = seededWatchNextItems,
        watchNextCursor = null,
        currentQueueIndex = normalizedIndex,
        isRefreshingVideo = true,
        pendingTransition = true,
        featured = target.toLoadingFeaturedVideo(),
        showPlayer = !keepMiniPlayer,
        showMiniPlayer = keepMiniPlayer,
        currentPositionMs = resumePositionMs,
        playbackSeekRequestId = nextYouTubePlaybackSeekRequestId(current),
        shouldAutoPlayCurrent = userInitiated,
        nextUpItem = if (current.autoplayEnabled) {
            nextQueueItem(queueItems, normalizedIndex, current.continuationMode)
        } else {
            null
        },
        preloadedNextFeatured = nextQueueItem(queueItems, normalizedIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
        errorMessage = null,
    )
    viewModelScope.launch {
        runCatching { resolveFeaturedVideo(target) }
            .onSuccess { featured ->
                lastFailureFallbackSourceUrl = null
                lastExpiredStreamRetrySourceUrl = null
                resetPlaybackFallbacks(target.url)
                val latest = _youtubeState.value
                youTubePlaybackMilestones[target.url] = mutableSetOf()
                _youtubeState.value = latest.copy(
                    featured = featured,
                    isRefreshingVideo = false,
                    pendingTransition = false,
                    showPlayer = !keepMiniPlayer,
                    showMiniPlayer = keepMiniPlayer,
                    watchNextItems = seededWatchNextItems,
                    watchNextCursor = null,
                    currentQueueIndex = normalizedIndex,
                    nextUpItem = if (latest.autoplayEnabled) {
                        nextQueueItem(queueItems, normalizedIndex, latest.continuationMode)
                    } else {
                        null
                    },
                    preloadedNextFeatured = nextQueueItem(queueItems, normalizedIndex, latest.continuationMode)?.let { youTubeResolveCache[it.url] },
                    currentPositionMs = resumePositionMs,
                    playbackSeekRequestId = if (resumePositionMs > 0L) {
                        nextYouTubePlaybackSeekRequestId(latest)
                    } else {
                        latest.playbackSeekRequestId
                    },
                    shouldAutoPlayCurrent = userInitiated,
                    errorMessage = null,
                )
                recordPlaybackSignal(target, MusicSignalType.PLAY_START)
                maybeRecordYouTubeWatchHistory(target, resumePositionMs, force = true)
                persistCurrentYouTubeSnapshot()
            }
            .onFailure { error ->
                handleYouTubePlaybackFailure(
                    currentIndex = normalizedIndex,
                    rawMessage = error.message,
                )
            }
    }
}

fun SnapMusicViewModel.playNextYouTubeItem(reason: YouTubeAdvanceReason = YouTubeAdvanceReason.USER_NEXT) {
    val current = _youtubeState.value
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    if (queueItems.isEmpty()) return
    val currentIndex = resolveCurrentQueueIndex(current, queueItems)
    if (reason == YouTubeAdvanceReason.AUTO_ENDED && !current.autoplayEnabled) {
        _youtubeState.value = current.copy(
            pendingTransition = false,
            shouldAutoPlayCurrent = false,
            currentPositionMs = 0L,
            playbackSeekRequestId = nextYouTubePlaybackSeekRequestId(current),
            nextUpItem = null,
            preloadedNextFeatured = null,
        )
        persistCurrentYouTubeSnapshot()
        return
    }
    val nextIndex = nextQueueIndex(queueItems.size, currentIndex, current.continuationMode)
    if (nextIndex == null) {
        _youtubeState.value = current.copy(
            pendingTransition = false,
            shouldAutoPlayCurrent = false,
            currentPositionMs = 0L,
            playbackSeekRequestId = nextYouTubePlaybackSeekRequestId(current),
            nextUpItem = null,
            preloadedNextFeatured = null,
        )
        persistCurrentYouTubeSnapshot()
        return
    }
    playYouTubeQueueItem(
        index = nextIndex,
        userInitiated = reason != YouTubeAdvanceReason.AUTO_ENDED,
    )
}

fun SnapMusicViewModel.playPreviousYouTubeItem() {
    val current = _youtubeState.value
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    if (queueItems.isEmpty()) return
    val currentIndex = resolveCurrentQueueIndex(current, queueItems)
    val previousIndex = previousQueueIndex(
        queueSize = queueItems.size,
        currentIndex = currentIndex,
        currentPositionMs = current.currentPositionMs,
    ) ?: return
    playYouTubeQueueItem(
        index = previousIndex,
        userInitiated = true,
    )
}

fun SnapMusicViewModel.toggleYouTubePlayPause() {
    val current = _youtubeState.value
    if (!current.featured.isReady) {
        val queueItems = current.playbackQueue.ifEmpty { current.items }
        if (queueItems.isEmpty()) return
        playYouTubeQueueItem(
            index = resolveCurrentQueueIndex(current, queueItems),
            userInitiated = true,
        )
        return
    }
    val isCurrentlyPlaying = PlaybackSessionStateStore.state.value.showPauseButton
    _youtubeState.value = current.copy(
        shouldAutoPlayCurrent = !isCurrentlyPlaying,
    )
    persistCurrentYouTubeSnapshot()
}

fun SnapMusicViewModel.toggleYouTubeAutoplay() {
    val current = _youtubeState.value
    val updated = !current.autoplayEnabled
    _youtubeState.value = current.copy(
        autoplayEnabled = updated,
        nextUpItem = if (updated) {
            nextQueueItem(current.playbackQueue.ifEmpty { current.items }, resolveCurrentQueueIndex(current), current.continuationMode)
        } else {
            null
        },
        preloadedNextFeatured = if (updated) {
            nextQueueItem(current.playbackQueue.ifEmpty { current.items }, resolveCurrentQueueIndex(current), current.continuationMode)
                ?.let { youTubeResolveCache[it.url] }
        } else {
            null
        },
    )
    viewModelScope.launch {
        graph.preferencesRepository.updateYouTubeAutoplayEnabled(updated)
        graph.launchPreferencesRepository.setYouTubeAutoplayEnabled(updated)
        persistCurrentYouTubeSnapshot()
    }
}

fun SnapMusicViewModel.switchYouTubePlaybackQuality(variantId: String) {
    val current = _youtubeState.value
    val resolved = current.featured.resolvedMedia ?: return
    val playbackSelection = resolvePlaybackSelection(
        media = resolved,
        requestedVariantId = variantId,
    ) ?: return
    val playbackUrl = playbackSelection.playbackUrl
    if (playbackUrl == current.featured.playbackUrl && current.featured.selectedVideoQualityId == variantId) return

    val updatedFeatured = current.featured.copy(
        playbackUrl = playbackUrl,
        adaptivePlaybackUrl = resolved.adaptivePlaybackUrl,
        selectedVideoQualityId = variantId,
        availablePlaybackHeights = current.featured.availablePlaybackHeights,
        autoMaxVideoHeight = if (variantId == "auto") playbackSelection.expectedHeight else null,
        actualVideoHeight = playbackSelection.expectedHeight,
        actualPlaybackLabel = playbackLabelForSelection(resolved, variantId, playbackSelection.expectedHeight),
        isReady = true,
    )
    youTubeResolveCache[current.featured.sourceUrl] = updatedFeatured
    _youtubeState.value = current.copy(
        featured = updatedFeatured,
        shouldAutoPlayCurrent = true,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
}
