package com.juan.snapmusic.feature.home

import android.os.SystemClock
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
import com.juan.snapmusic.core.performance.SnapMusicFeedPagingTelemetry
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
import kotlinx.coroutines.withTimeoutOrNull

private const val YOUTUBE_WATCH_NEXT_POST_FRAME_IDLE_MS = 250L

internal fun SnapMusicViewModel.enrichWatchNextQueue(
    item: YouTubeFeedItem,
    requireWarmPlayback: Boolean = true,
) {
    watchNextEnrichmentJob?.cancel()
    watchNextEnrichmentJob = viewModelScope.launch {
        if (requireWarmPlayback) {
            delay(YOUTUBE_WATCH_NEXT_ENRICH_DELAY_MS)
        } else {
            var attempts = 0
            while (attempts < 40) {
                val warmState = _youtubeState.value
                if (
                    warmState.featured.sourceUrl == item.url &&
                    !warmState.isRefreshingVideo &&
                    !warmState.pendingTransition
                ) {
                    break
                }
                delay(150L)
                attempts += 1
            }
            delay(YOUTUBE_WATCH_NEXT_POST_FRAME_IDLE_MS)
        }
        val startupState = _youtubeState.value
        if (
            startupState.featured.sourceUrl != item.url ||
            !startupState.showPlayer ||
            startupState.isRefreshingVideo ||
            startupState.pendingTransition
        ) {
            return@launch
        }
        val relatedPage = runCatching {
            withTimeoutOrNull(YOUTUBE_FEED_PAGE_TIMEOUT_MS) {
                graph.musicHomeFeedRepository.recommendWatchNextPage(
                    currentItem = item,
                    limit = YOUTUBE_WATCH_NEXT_PAGE_SIZE,
                )
            } ?: com.juan.snapmusic.core.model.MusicHomeFeedState()
        }.getOrDefault(com.juan.snapmusic.core.model.MusicHomeFeedState())
        val related = relatedPage.items
        val current = _youtubeState.value
        if (current.featured.sourceUrl != item.url) return@launch
        val existingQueue = current.playbackQueue.ifEmpty { current.items }.ifEmpty { listOf(item) }
        val currentIndex = resolveCurrentQueueIndex(current, existingQueue)
        val existingWatchNext = current.watchNextItems.ifEmpty {
            initialWatchNextItems(existingQueue, currentIndex, current.queueOrigin)
        }
        val blockedQueueItems = relatedQueueBlocklist(existingQueue, currentIndex, current.queueOrigin)
        val fallbackCandidates = resolveWatchNextRecoveryCandidates(
            currentItem = item,
            state = current,
            existingQueue = existingQueue,
            blockedQueueItems = blockedQueueItems,
            existingWatchNext = existingWatchNext,
            requestedLimit = YOUTUBE_WATCH_NEXT_PAGE_SIZE + YOUTUBE_WATCH_NEXT_LOOKAHEAD_SIZE,
        )
        val appendedRelated = (related + fallbackCandidates).filterNot { candidate ->
            candidate.url == item.url ||
                blockedQueueItems.any { queued -> queued.url == candidate.url } ||
                existingWatchNext.any { queued -> queued.url == candidate.url }
        }
        val mergedCandidates = (existingWatchNext + appendedRelated)
            .filter { candidate -> candidate.url != item.url }
            .distinctBy(YouTubeFeedItem::url)
        if (mergedCandidates.isEmpty()) {
            _youtubeState.value = current.copy(
                watchNextItems = existingWatchNext,
                canLoadMoreWatchNext = relatedPage.nextCursor != null,
                watchNextCursor = relatedPage.nextCursor,
                isLoadingMore = false,
                errorMessage = null,
            )
            return@launch
        }
        val rankedWatchNext = graph.musicHomeFeedRepository.rankWatchNextCandidates(
            currentItem = item,
            candidates = mergedCandidates,
            limit = mergedCandidates.size.coerceAtLeast(existingWatchNext.size),
        )
        val (queueItems, watchNextItems) = rebuildQueueWithWatchNext(
            queueItems = existingQueue,
            currentIndex = currentIndex,
            rankedWatchNext = rankedWatchNext.ifEmpty { existingWatchNext },
        )
        if (watchNextItems == existingWatchNext && appendedRelated.isEmpty()) {
            _youtubeState.value = current.copy(
                watchNextItems = existingWatchNext,
                canLoadMoreWatchNext = relatedPage.nextCursor != null,
                watchNextCursor = relatedPage.nextCursor,
                isLoadingMore = false,
            )
            return@launch
        }
        val addedItems = (watchNextItems.size - existingWatchNext.size).coerceAtLeast(0)
        _youtubeState.value = current.copy(
            playbackQueue = queueItems,
            watchNextItems = watchNextItems,
            nextUpItem = if (current.autoplayEnabled) {
                nextQueueItem(queueItems, currentIndex, current.continuationMode)
            } else {
                null
            },
            canLoadMoreWatchNext = relatedPage.nextCursor != null,
            watchNextCursor = relatedPage.nextCursor,
            preloadedNextFeatured = nextQueueItem(queueItems, currentIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
        )
        persistCurrentYouTubeSnapshot()
        preResolveNextQueueItem(queueItems, currentIndex, current.continuationMode)
    }
}

internal fun SnapMusicViewModel.refreshWatchNextByPull(snapshot: YouTubeUiState) {
    val featuredItem = currentYouTubeQueueItem(snapshot) ?: return
    val existingQueue = snapshot.playbackQueue.ifEmpty { snapshot.items }.ifEmpty { listOf(featuredItem) }
    val currentIndex = resolveCurrentQueueIndex(snapshot, existingQueue)
    val seededWatchNext = snapshot.watchNextItems.ifEmpty {
        initialWatchNextItems(existingQueue, currentIndex, seedOriginForWatchNext(snapshot))
    }
    val blockedQueueItems = relatedQueueBlocklist(existingQueue, currentIndex, snapshot.queueOrigin)
    watchNextEnrichmentJob?.cancel()
    youtubeLoadMoreJob?.cancel()
    _youtubeState.value = snapshot.copy(
        isLoading = true,
        isLoadingMore = true,
        watchNextItems = seededWatchNext,
        errorMessage = null,
    )
    watchNextEnrichmentJob = viewModelScope.launch {
        val relatedPage = runCatching {
            withTimeoutOrNull(YOUTUBE_FEED_PAGE_TIMEOUT_MS) {
                graph.musicHomeFeedRepository.recommendWatchNextPage(
                    currentItem = featuredItem,
                    limit = YOUTUBE_WATCH_NEXT_PAGE_SIZE + 8,
                )
            } ?: com.juan.snapmusic.core.model.MusicHomeFeedState()
        }.getOrDefault(com.juan.snapmusic.core.model.MusicHomeFeedState())
        val related = relatedPage.items
        val latest = _youtubeState.value
        if (latest.featured.sourceUrl != featuredItem.url) return@launch
        val fallbackCandidates = resolveWatchNextRecoveryCandidates(
            currentItem = featuredItem,
            state = latest,
            existingQueue = existingQueue,
            blockedQueueItems = blockedQueueItems,
            existingWatchNext = seededWatchNext,
            requestedLimit = YOUTUBE_WATCH_NEXT_PAGE_SIZE + YOUTUBE_WATCH_NEXT_LOOKAHEAD_SIZE,
        )
        val refreshedCandidates = (related + seededWatchNext + fallbackCandidates)
            .filterNot { candidate ->
                candidate.url == featuredItem.url ||
                    blockedQueueItems.any { blocked -> blocked.url == candidate.url }
            }
            .distinctBy(YouTubeFeedItem::url)
        val rankedWatchNext = graph.musicHomeFeedRepository.rankWatchNextCandidates(
            currentItem = featuredItem,
            candidates = refreshedCandidates,
            limit = refreshedCandidates.size.coerceAtLeast(seededWatchNext.size),
        ).ifEmpty { seededWatchNext + fallbackCandidates }
        val (updatedQueue, updatedWatchNext) = rebuildQueueWithWatchNext(
            queueItems = existingQueue,
            currentIndex = currentIndex,
            rankedWatchNext = rankedWatchNext,
        )
        _youtubeState.value = latest.copy(
            playbackQueue = updatedQueue,
            watchNextItems = updatedWatchNext,
            nextUpItem = if (latest.autoplayEnabled) {
                nextQueueItem(updatedQueue, currentIndex, latest.continuationMode)
            } else {
                null
            },
            preloadedNextFeatured = nextQueueItem(updatedQueue, currentIndex, latest.continuationMode)
                ?.let { youTubeResolveCache[it.url] },
            isLoading = false,
            isLoadingMore = false,
            canLoadMoreWatchNext = relatedPage.nextCursor != null,
            watchNextCursor = relatedPage.nextCursor,
            errorMessage = null,
        )
        if (updatedWatchNext.isNotEmpty()) {
            startupPrefetchDone = false
            prefetchFeedItems(updatedWatchNext.take(YOUTUBE_WATCH_NEXT_PAGE_SIZE))
        }
        persistCurrentYouTubeSnapshot()
        preResolveNextQueueItem(updatedQueue, currentIndex, latest.continuationMode)
    }
}

internal fun SnapMusicViewModel.loadMoreWatchNextQueue(): Job? {
    val current = _youtubeState.value
    val featuredItem = currentYouTubeQueueItem(current) ?: return null
    val existingQueue = current.playbackQueue.ifEmpty { listOf(featuredItem) }
    val currentIndex = resolveCurrentQueueIndex(current, existingQueue)
    val existingWatchNext = current.watchNextItems.ifEmpty {
        initialWatchNextItems(existingQueue, currentIndex, current.queueOrigin)
    }
    val blockedQueueItems = relatedQueueBlocklist(existingQueue, currentIndex, current.queueOrigin)
    _youtubeState.value = current.copy(isLoadingMore = true)
    return viewModelScope.launch {
        val startedAt = SystemClock.elapsedRealtime()
        val requestLimit = YOUTUBE_WATCH_NEXT_PAGE_SIZE + YOUTUBE_WATCH_NEXT_LOOKAHEAD_SIZE
        runCatching {
            withTimeoutOrNull(YOUTUBE_FEED_PAGE_TIMEOUT_MS) {
                graph.musicHomeFeedRepository.recommendWatchNextPage(
                    currentItem = featuredItem,
                    cursor = current.watchNextCursor,
                    limit = requestLimit,
                )
            } ?: com.juan.snapmusic.core.model.MusicHomeFeedState()
        }
            .onSuccess { page ->
                val requestCursor = current.watchNextCursor
                val related = page.items
                val latest = _youtubeState.value
                if (latest.featured.sourceUrl != featuredItem.url) {
                    _youtubeState.value = latest.copy(isLoadingMore = false)
                    SnapMusicFeedPagingTelemetry.loadMore(
                        kind = "watch",
                        session = requestCursor ?: featuredItem.url,
                        cursor = requestCursor,
                        lane = "watch-load-more",
                        added = 0,
                        duplicates = 0,
                        exhausted = false,
                        durationMs = SystemClock.elapsedRealtime() - startedAt,
                        resultCursor = page.nextCursor,
                    )
                    return@onSuccess
                }
                val newRelated = related.filterNot { candidate ->
                    candidate.url == featuredItem.url ||
                        blockedQueueItems.any { existing -> existing.url == candidate.url } ||
                        existingWatchNext.any { existing -> existing.url == candidate.url }
                }
                val fallbackCandidates = resolveWatchNextRecoveryCandidates(
                    currentItem = featuredItem,
                    state = latest,
                    existingQueue = existingQueue,
                    blockedQueueItems = blockedQueueItems,
                    existingWatchNext = existingWatchNext,
                    requestedLimit = requestLimit,
                )
                val mergedCandidates = (existingWatchNext + newRelated + fallbackCandidates)
                    .filter { candidate -> candidate.url != featuredItem.url }
                    .distinctBy(YouTubeFeedItem::url)
                val rankedWatchNext = graph.musicHomeFeedRepository.rankWatchNextCandidates(
                    currentItem = featuredItem,
                    candidates = mergedCandidates,
                    limit = mergedCandidates.size.coerceAtLeast(existingWatchNext.size),
                )
                val (updatedQueue, updatedWatchNext) = rebuildQueueWithWatchNext(
                    queueItems = existingQueue,
                    currentIndex = currentIndex,
                    rankedWatchNext = rankedWatchNext.ifEmpty { existingWatchNext },
                )
                val addedItems = (updatedWatchNext.size - existingWatchNext.size).coerceAtLeast(0)
                val duplicates = (related.size - newRelated.size).coerceAtLeast(0)
                _youtubeState.value = latest.copy(
                    playbackQueue = updatedQueue,
                    watchNextItems = updatedWatchNext,
                    nextUpItem = if (latest.autoplayEnabled) {
                        nextQueueItem(updatedQueue, currentIndex, latest.continuationMode)
                    } else {
                        null
                    },
                    preloadedNextFeatured = nextQueueItem(updatedQueue, currentIndex, latest.continuationMode)?.let { youTubeResolveCache[it.url] },
                    isLoadingMore = false,
                    canLoadMoreWatchNext = page.nextCursor != null,
                    watchNextCursor = page.nextCursor,
                )
                SnapMusicFeedPagingTelemetry.loadMore(
                    kind = "watch",
                    session = page.nextCursor ?: requestCursor ?: featuredItem.url,
                    cursor = requestCursor,
                    lane = "watch-load-more",
                    added = addedItems,
                    duplicates = duplicates,
                    exhausted = page.nextCursor == null,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    resultCursor = page.nextCursor,
                )
                if (addedItems > 0) {
                    startupPrefetchDone = false
                    prefetchFeedItems(updatedWatchNext.drop(existingWatchNext.size).take(YOUTUBE_WATCH_NEXT_PAGE_SIZE))
                }
                persistCurrentYouTubeSnapshot()
                preResolveNextQueueItem(updatedQueue, currentIndex, latest.continuationMode)
            }
            .onFailure { error ->
                _youtubeState.value = _youtubeState.value.copy(isLoadingMore = false, canLoadMoreWatchNext = false)
                SnapMusicFeedPagingTelemetry.loadMore(
                    kind = "watch",
                    session = current.watchNextCursor ?: featuredItem.url,
                    cursor = current.watchNextCursor,
                    lane = "watch-load-more",
                    added = 0,
                    duplicates = 0,
                    exhausted = false,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    error = error.message,
                )
            }
    }
}

internal fun SnapMusicViewModel.maybeRecordFastSkip(
    current: YouTubeUiState,
    target: YouTubeFeedItem,
) {
    val currentItem = currentYouTubeQueueItem(current) ?: return
    if (currentItem.url == target.url) return
    if (current.currentPositionMs in 1L until 10_000L) {
        recordPlaybackSignal(currentItem, MusicSignalType.SKIP_FAST)
    }
}

internal fun SnapMusicViewModel.maybeRecordPlaybackMilestones(
    featured: YouTubeFeaturedVideo,
    positionMs: Long,
) {
    if (!featured.isReady) return
    val durationMs = featured.durationSeconds.coerceAtLeast(0L) * 1_000L
    val milestones = youTubePlaybackMilestones.getOrPut(featured.sourceUrl) { mutableSetOf() }
    if (positionMs >= 30_000L && milestones.add(MusicSignalType.PLAY_30S)) {
        currentYouTubeQueueItem()?.let { recordPlaybackSignal(it, MusicSignalType.PLAY_30S) }
    }
    if (durationMs > 0L && positionMs >= (durationMs * 0.7).toLong() && milestones.add(MusicSignalType.PLAY_70_PERCENT)) {
        currentYouTubeQueueItem()?.let { recordPlaybackSignal(it, MusicSignalType.PLAY_70_PERCENT) }
    }
}

internal fun SnapMusicViewModel.currentYouTubeQueueItem(
    state: YouTubeUiState = _youtubeState.value,
): YouTubeFeedItem? {
    val queueItems = state.playbackQueue.ifEmpty { state.items }
    return queueItems.getOrNull(resolveCurrentQueueIndex(state, queueItems))
}

internal fun YouTubeWatchHistoryEntry.toYouTubeFeedItem(): YouTubeFeedItem {
    return YouTubeFeedItem(
        url = sourceUrl,
        title = title,
        author = author,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        publishedText = publishedText,
        description = description,
    )
}

internal fun SnapMusicViewModel.consumePendingYouTubeHistoryResumePosition(item: YouTubeFeedItem): Long {
    val pendingPosition = pendingYouTubeHistoryResumePositions.remove(item.url) ?: return 0L
    return normalizedYouTubeResumePosition(
        positionMs = pendingPosition,
        durationSeconds = item.durationSeconds,
    )
}

internal fun SnapMusicViewModel.normalizedYouTubeResumePosition(
    positionMs: Long,
    durationSeconds: Long,
): Long {
    val safePosition = positionMs.coerceAtLeast(0L)
    val durationMs = durationSeconds.coerceAtLeast(0L) * 1_000L
    if (safePosition <= 0L || durationMs <= 10_000L) return safePosition
    return if (safePosition >= durationMs - 5_000L) {
        0L
    } else {
        safePosition.coerceAtMost(durationMs - 5_000L)
    }
}

internal fun SnapMusicViewModel.hasDownloadVariants(media: ResolvedMedia?): Boolean {
    return media != null && (media.audioVariants.isNotEmpty() || media.videoVariants.isNotEmpty())
}
