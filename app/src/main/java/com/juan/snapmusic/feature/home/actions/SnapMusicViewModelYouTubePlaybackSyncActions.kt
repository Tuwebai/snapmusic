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

fun SnapMusicViewModel.syncYouTubePlaybackTracks(
    availableHeights: List<Int>,
    height: Int?,
) {
    val current = _youtubeState.value
    if (!current.featured.isReady) return
    val actualLabel = when {
        current.featured.selectedVideoQualityId == "auto" && height != null && height > 0 -> "Automático · ${height}P"
        height != null && height > 0 -> watchPlaybackQualityLabel(height)
        current.featured.selectedVideoQualityId == "auto" -> preferredAutomaticPlaybackLabel(current.featured.resolvedMedia)
        else -> playbackLabelForSelection(
            media = current.featured.resolvedMedia,
            variantId = current.featured.selectedVideoQualityId,
            expectedHeight = current.featured.actualVideoHeight,
        )
    }
    val distinctHeights = availableHeights
        .filter { it > 0 }
        .distinct()
        .sortedDescending()
    if (
        current.featured.actualVideoHeight == height &&
        current.featured.actualPlaybackLabel == actualLabel &&
        current.featured.availablePlaybackHeights == distinctHeights
    ) return
    val updatedFeatured = current.featured.copy(
        availablePlaybackHeights = distinctHeights,
        actualVideoHeight = height,
        actualPlaybackLabel = actualLabel,
    )
    youTubeResolveCache[current.featured.sourceUrl] = updatedFeatured
    _youtubeState.value = current.copy(featured = updatedFeatured)
}

fun SnapMusicViewModel.onYouTubePlaybackRebuffer(
    positionMs: Long,
    durationMs: Long,
) {
    val current = _youtubeState.value
    val featured = current.featured
    val sourceUrl = featured.sourceUrl
    if (sourceUrl.isBlank() || featured.selectedVideoQualityId != "auto") return
    val now = System.currentTimeMillis()
    val events = youtubeRebufferEvents.getOrPut(sourceUrl) { mutableListOf() }
    events.removeAll { now - it > YouTubePlaybackRecoveryPolicy.REBUFFER_WINDOW_MS }
    events += now
    val mode = playbackSourceMode(featured) ?: return
    YouTubePlaybackTelemetry.rebuffer(sourceUrl, mode, durationMs, positionMs, events.size)
    val decision = YouTubePlaybackRecoveryPolicy.completedRebufferDecision(
        mode = mode,
        positionMs = positionMs,
        durationMs = durationMs,
        events = events.size,
    )
    if (decision.shouldRecover) {
        recoverYouTubePlaybackStall(current, mode, positionMs, durationMs, events.size, "completedRebuffer")
    }
}

fun SnapMusicViewModel.onYouTubePlaybackStalled(
    positionMs: Long,
    durationMs: Long,
) {
    val current = _youtubeState.value
    val featured = current.featured
    val sourceUrl = featured.sourceUrl
    if (sourceUrl.isBlank()) return
    val mode = playbackSourceMode(featured) ?: return
    val now = System.currentTimeMillis()
    val events = youtubeRebufferEvents.getOrPut(sourceUrl) { mutableListOf() }
    events.removeAll { now - it > YouTubePlaybackRecoveryPolicy.REBUFFER_WINDOW_MS }
    events += now
    YouTubePlaybackTelemetry.stall(sourceUrl, mode, durationMs, positionMs, events.size)
    val decision = YouTubePlaybackRecoveryPolicy.activeStallDecision(
        mode = mode,
        positionMs = positionMs,
        durationMs = durationMs,
        events = events.size,
    )
    if (decision.shouldRecover) {
        recoverYouTubePlaybackStall(current, mode, positionMs, durationMs, events.size, "activeStall")
    } else {
        YouTubePlaybackTelemetry.recoverySkipped(sourceUrl, decision.reason, mode, durationMs, positionMs)
    }
}

fun SnapMusicViewModel.requestYouTubeDownloadSheet() {
    val current = _youtubeState.value
    val currentItem = currentYouTubeQueueItem(current)
    if (hasDownloadVariants(current.featured.resolvedMedia)) {
        _youtubeDownloadSheet.value = YouTubeDownloadSheetState(
            media = current.featured.resolvedMedia,
            visible = true,
        )
        return
    }
    currentItem ?: return
    resolveYouTubeDownloadSheet(
        item = currentItem,
        forceRefresh = true,
    )
}

fun SnapMusicViewModel.consumeYouTubeDownloadSheet() {
    dismissYouTubeDownloadSheet()
}

fun SnapMusicViewModel.dismissYouTubeDownloadSheet() {
    if (!_youtubeDownloadSheet.value.visible && !_youtubeDownloadSheet.value.isPreparing) return
    _youtubeDownloadSheet.value = YouTubeDownloadSheetState()
}

fun SnapMusicViewModel.syncYouTubePlaybackProgress(
    positionMs: Long,
    playWhenReady: Boolean,
    persist: Boolean = false,
) {
    val current = _youtubeState.value
    if (!current.featured.isReady) return
    val shouldAutoPlay = playWhenReady
    val effectivePosition = YouTubePlaybackRecoveryPolicy.sanitizeProgressPosition(
        reportedPositionMs = positionMs,
        previousStablePositionMs = current.currentPositionMs,
        persist = persist,
        playWhenReady = shouldAutoPlay,
    )
    val shouldCheckpoint = kotlin.math.abs(current.currentPositionMs - effectivePosition) >= 10_000L
    val shouldUpdateState =
        current.shouldAutoPlayCurrent != shouldAutoPlay ||
            persist ||
            shouldCheckpoint
    if (shouldUpdateState) {
        _youtubeState.value = current.copy(
            currentPositionMs = effectivePosition,
            shouldAutoPlayCurrent = shouldAutoPlay,
        )
    }
    if (
        effectivePosition >= YOUTUBE_NEXT_PRE_RESOLVE_MIN_POSITION_MS &&
        current.autoplayEnabled &&
        current.preloadedNextFeatured == null &&
        nextQueuePreResolveJob?.isActive != true &&
        isYouTubePlaybackStableForPreResolve(current)
    ) {
        val queueItems = current.playbackQueue.ifEmpty { current.items }
        if (queueItems.isNotEmpty()) {
            preResolveNextQueueItem(
                queueItems = queueItems,
                currentIndex = resolveCurrentQueueIndex(current, queueItems),
                continuationMode = current.continuationMode,
                allowNetwork = true,
            )
        }
    }
    if (shouldAutoPlay || youtubeWatchHistoryLastRecordedPositions.containsKey(current.featured.sourceUrl)) {
        currentYouTubeQueueItem(current)?.let { item ->
            maybeRecordYouTubeWatchHistory(item, effectivePosition, force = persist || shouldCheckpoint)
        }
    }
    maybeRecordPlaybackMilestones(current.featured, effectivePosition)
    if (persist || shouldCheckpoint) {
        persistCurrentYouTubeSnapshot()
    }
}

fun SnapMusicViewModel.restoreYouTubePlaybackSnapshot() {
    youTubePlaybackSnapshotRestoreStarted = true
    viewModelScope.launch(Dispatchers.IO) {
        val snapshot = graph.preferencesRepository.readYouTubePlaybackSnapshot() ?: return@launch
        if (snapshot.queue.isEmpty()) return@launch
        val currentItem = snapshot.queue.getOrNull(snapshot.currentQueueIndex) ?: return@launch
        withContext(Dispatchers.Main.immediate) {
            lastFailureFallbackSourceUrl = null
            lastExpiredStreamRetrySourceUrl = null
            resetPlaybackFallbacks(currentItem.url)
            val restoredState = _youtubeState.value
            _youtubeState.value = restoredState.copy(
                query = snapshot.query,
                isLoading = false,
                isLoadingMore = false,
                isRefreshingVideo = false,
                items = snapshot.queue,
                nextCursor = null,
                hasMoreSearchResults = false,
                watchNextItems = initialWatchNextItems(snapshot.queue, snapshot.currentQueueIndex, snapshot.origin),
                watchNextCursor = null,
                playbackQueue = snapshot.queue,
                currentQueueIndex = snapshot.currentQueueIndex,
                autoplayEnabled = snapshot.autoplayEnabled,
                continuationMode = snapshot.continuationMode,
                featured = currentItem.toLoadingFeaturedVideo(),
                showPlayer = false,
                isFullscreen = false,
                showMiniPlayer = snapshot.showMiniPlayer,
                canLoadMoreWatchNext = true,
                nextUpItem = if (snapshot.autoplayEnabled) {
                    nextQueueItem(snapshot.queue, snapshot.currentQueueIndex, snapshot.continuationMode)
                } else {
                    null
                },
                preloadedNextFeatured = null,
                pendingTransition = false,
                currentPositionMs = snapshot.lastPositionMs,
                playbackSeekRequestId = nextYouTubePlaybackSeekRequestId(restoredState),
                shouldAutoPlayCurrent = false,
                queueOrigin = snapshot.origin,
                compactMiniPlayer = snapshot.showMiniPlayer,
                openDownloadSheet = false,
                errorMessage = null,
            )
        }
    }
}

fun SnapMusicViewModel.clearYouTubePlaybackSnapshot() {
    viewModelScope.launch {
        graph.preferencesRepository.clearYouTubePlaybackSnapshot()
    }
}

fun SnapMusicViewModel.onYouTubePlaybackEnded() {
    currentYouTubeQueueItem()?.let { recordPlaybackSignal(it, MusicSignalType.PLAY_COMPLETE) }
    playNextYouTubeItem(YouTubeAdvanceReason.AUTO_ENDED)
}

fun SnapMusicViewModel.onYouTubePlaybackError(rawMessage: String?) {
    onYouTubePlaybackError(rawMessage, shouldRetryExpiredStream = false)
}

fun SnapMusicViewModel.onYouTubePlaybackError(
    rawMessage: String?,
    shouldRetryExpiredStream: Boolean,
) {
    if (retryYouTubePlaybackSource(rawMessage)) return
    if (shouldRetryExpiredStream && retryExpiredYouTubeStream()) return
    handleYouTubePlaybackFailure(
        currentIndex = resolveCurrentQueueIndex(_youtubeState.value),
        rawMessage = rawMessage,
    )
}

fun SnapMusicViewModel.syncYouTubeMediaTransition(
    mediaId: String,
    positionMs: Long,
    playWhenReady: Boolean,
) {
    val current = _youtubeState.value
    if (mediaId != current.featured.sourceUrl) {
        lastExpiredStreamRetrySourceUrl = null
        resetPlaybackFallbacks(mediaId)
    }
    val queueItems = current.playbackQueue.ifEmpty { current.items }
    val nextIndex = queueItems.indexOfFirst { it.url == mediaId }
    if (nextIndex == -1) {
        syncYouTubePlaybackProgress(positionMs, playWhenReady)
        return
    }
    val cached = youTubeResolveCache[mediaId]
    val transitionedItem = queueItems[nextIndex]
    _youtubeState.value = current.copy(
        currentQueueIndex = nextIndex,
        featured = cached ?: transitionedItem.toLoadingFeaturedVideo(),
        watchNextItems = initialWatchNextItems(queueItems, nextIndex, seedOriginForWatchNext(current)),
        watchNextCursor = null,
        nextUpItem = if (current.autoplayEnabled) {
            nextQueueItem(queueItems, nextIndex, current.continuationMode)
        } else {
            null
        },
        preloadedNextFeatured = nextQueueItem(queueItems, nextIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
        currentPositionMs = positionMs.coerceAtLeast(0L),
        shouldAutoPlayCurrent = playWhenReady,
    )
    persistCurrentYouTubeSnapshot()
    preResolveNextQueueItem(queueItems, nextIndex, current.continuationMode)
}

