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

fun SnapMusicViewModel.onYoutubeQueryChange(value: String) {
    _youtubeState.value = _youtubeState.value.copy(query = value)
    scheduleYouTubeSuggestions(value)
}

fun SnapMusicViewModel.searchYoutubeSuggestion(query: String) {
    _youtubeState.value = _youtubeState.value.copy(query = query)
    clearYouTubeSuggestions()
    searchYoutube()
}

fun SnapMusicViewModel.searchArtist(author: String) {
    val query = author.trim()
    if (query.isBlank()) return
    val current = _youtubeState.value
    _youtubeState.value = current.copy(showPlayer = false, isFullscreen = false, showMiniPlayer = current.showMiniPlayer || current.featured.isReady, compactMiniPlayer = false)
    searchYoutubeSuggestion(query)
}

fun SnapMusicViewModel.clearYoutubeQuery() {
    _youtubeState.value = _youtubeState.value.copy(query = "")
    clearYouTubeSuggestions()
}

fun SnapMusicViewModel.ensureYoutubeLoaded() {
    onHomeYouTubeTabOpened()
    val state = _youtubeState.value
    if (state.isLoading) return
    if (state.items.isEmpty()) {
        deferredYoutubeHomeRefreshJob?.cancel()
        deferredYoutubeHomeRefreshJob = viewModelScope.launch {
            delay(180L)
            val latest = _youtubeState.value
            if (_homeSelectedTab.value != HOME_TAB_YOUTUBE_INDEX) return@launch
            if (latest.isLoading || latest.items.isNotEmpty()) return@launch
            refreshYoutubeHome()
        }
    }
}

fun SnapMusicViewModel.refreshYoutubeByPull() {
    val state = _youtubeState.value
    if (state.isLoading || state.isLoadingMore) return
    if (state.showPlayer && state.featured.isReady) {
        refreshWatchNextByPull(state)
        return
    }
    val activeQuery = _downloadSearchState.value.query.trim()
    if (activeQuery.isBlank()) {
        if (state.query.isNotBlank()) {
            _youtubeState.value = state.copy(query = "")
        }
        refreshYoutubeHome()
    } else {
        if (state.query != activeQuery) {
            _youtubeState.value = state.copy(query = activeQuery)
        }
        searchYoutube()
    }
}

fun SnapMusicViewModel.enterYouTubeFeed() {
    val current = _youtubeState.value
    _youtubeState.value = current.copy(
        showPlayer = false,
        isFullscreen = false,
        showMiniPlayer = current.featured.isReady && current.playbackQueue.isNotEmpty(),
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
}

fun SnapMusicViewModel.dismissYouTubePlayer() {
    watchNextEnrichmentJob?.cancel()
    nextQueuePreResolveJob?.cancel()
    lastExpiredStreamRetrySourceUrl = null
    refreshedAdaptivePlaybackSources.clear()
    playbackFallbackModes.clear()
    playbackStabilityFallbacks.clear()
    youtubeRebufferEvents.clear()
    val current = _youtubeState.value
    _youtubeState.value = current.copy(
        showPlayer = false,
        isFullscreen = false,
        showMiniPlayer = false,
        featured = YouTubeFeaturedVideo(),
        watchNextItems = emptyList(),
        watchNextCursor = null,
        playbackQueue = emptyList(),
        currentQueueIndex = -1,
        nextUpItem = null,
        preloadedNextFeatured = null,
        pendingTransition = false,
        currentPositionMs = 0L,
        shouldAutoPlayCurrent = false,
        compactMiniPlayer = false,
        errorMessage = null,
    )
    viewModelScope.launch {
        graph.preferencesRepository.clearYouTubePlaybackSnapshot()
    }
}

fun SnapMusicViewModel.minimizeYouTubePlayer() {
    val current = _youtubeState.value
    if (!current.featured.isReady) return
    _youtubeState.value = current.copy(
        showPlayer = false,
        isFullscreen = false,
        showMiniPlayer = true,
        compactMiniPlayer = false,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
}

fun SnapMusicViewModel.enterYouTubeFullscreen() {
    val current = _youtubeState.value
    if (!current.showPlayer || !current.featured.isReady) return
    if (!youtubeFullscreenController.shouldEnter(current.isFullscreen)) return
    YouTubePlaybackTelemetry.fullscreen(current.featured.sourceUrl, true)
    _youtubeState.value = current.copy(
        isFullscreen = true,
        showMiniPlayer = false,
        compactMiniPlayer = false,
        errorMessage = null,
    )
}

fun SnapMusicViewModel.exitYouTubeFullscreen() {
    val current = _youtubeState.value
    if (!youtubeFullscreenController.shouldExit(current.isFullscreen)) return
    YouTubePlaybackTelemetry.fullscreen(current.featured.sourceUrl, false)
    _youtubeState.value = current.copy(isFullscreen = false, errorMessage = null)
}

fun SnapMusicViewModel.restoreYouTubePlayer() {
    val current = _youtubeState.value
    if (!current.featured.isReady) {
        val queueItems = current.playbackQueue.ifEmpty { current.items }
        if (queueItems.isEmpty()) return
        _youtubeState.value = current.copy(
            showPlayer = true,
            isFullscreen = false,
            showMiniPlayer = false,
            compactMiniPlayer = false,
            errorMessage = null,
        )
        playYouTubeQueueItem(resolveCurrentQueueIndex(current, queueItems), userInitiated = false)
        return
    }
    _youtubeState.value = current.copy(
        showPlayer = true,
        isFullscreen = false,
        showMiniPlayer = false,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
}

fun SnapMusicViewModel.restoreYouTubePlaybackShell() {
    closeTransientHomePlaybackLayers()
    selectHomeTab(1)
    restoreYouTubePlayer()
}

fun SnapMusicViewModel.toggleYouTubeMiniPlayerMode() {
    val current = _youtubeState.value
    if (!current.showMiniPlayer || !current.featured.isReady) return
    _youtubeState.value = current.copy(compactMiniPlayer = !current.compactMiniPlayer)
    persistCurrentYouTubeSnapshot()
}

fun SnapMusicViewModel.refreshYoutubeHome(silent: Boolean = false) {
    viewModelScope.launch {
        watchNextEnrichmentJob?.cancel()
        if (!silent) {
            youTubeFeedSessionSeed = System.currentTimeMillis()
        }
        _youtubeState.value = _youtubeState.value.copy(
            isLoading = !silent,
            isLoadingMore = false,
            watchNextItems = emptyList(),
            watchNextCursor = null,
            nextCursor = null,
            hasMoreSearchResults = false,
            canLoadMoreWatchNext = false,
            errorMessage = null,
        )
        runCatching {
            graph.musicHomeFeedRepository.loadMusicHomeFeed(
                sessionSeed = youTubeFeedSessionSeed,
                limit = YOUTUBE_HOME_FEED_LIMIT,
            )
        }
            .onSuccess { state ->
                val items = state.items
                val current = _youtubeState.value
                cachedYouTubeHomeFeed = items
                _youtubeState.value = current.copy(
                    query = "",
                    items = items,
                    isLoading = false,
                    isLoadingMore = false,
                    nextCursor = state.nextCursor,
                    hasMoreSearchResults = false,
                    errorMessage = if (items.isEmpty()) "No encontramos videos para mostrar ahora mismo." else null,
                )
                viewModelScope.launch(Dispatchers.IO) {
                    graph.preferencesRepository.saveYouTubeHomeFeedCache(items)
                }
                if (!silent) {
                    startupPrefetchDone = false
                }
                prefetchFeedItems(items)
            }
            .onFailure {
                _youtubeState.value = _youtubeState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = "No pudimos cargar videos de YouTube ahora mismo. Probá refrescar o usar una búsqueda.",
                )
            }
    }
}

fun SnapMusicViewModel.searchYoutube() {
    val query = _youtubeState.value.query.trim()
    watchNextEnrichmentJob?.cancel()
    clearYouTubeSuggestions()
    if (query.isBlank()) {
        refreshYoutubeHome()
        return
    }
    rememberSearchQuery(query)
    viewModelScope.launch {
        _youtubeState.value = _youtubeState.value.copy(
            isLoading = true,
            isLoadingMore = false,
            watchNextItems = emptyList(),
            watchNextCursor = null,
            nextCursor = null,
            hasMoreSearchResults = false,
            canLoadMoreWatchNext = false,
            errorMessage = null,
        )
        runCatching {
            graph.resolverRepository.searchVideosPage(
                query = query,
                limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
            )
        }
            .onSuccess { page ->
                val items = page.items.distinctBy(YouTubeFeedItem::url)
                val current = _youtubeState.value
                _youtubeState.value = current.copy(
                    items = items,
                    isLoading = false,
                    isLoadingMore = false,
                    nextCursor = page.nextCursor,
                    hasMoreSearchResults = !page.nextCursor.isNullOrBlank() && items.isNotEmpty(),
                    errorMessage = if (items.isEmpty()) "No hubo resultados para \"$query\"." else null,
                )
                viewModelScope.launch(Dispatchers.IO) {
                    graph.musicHomeFeedRepository.recordSearch(query)
                }
                startupPrefetchDone = false
                prefetchFeedItems(items)
            }
            .onFailure { error ->
                _youtubeState.value = _youtubeState.value.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    errorMessage = userFacingError(error.message, UiFailureKind.NETWORK),
                )
            }
    }
}

fun SnapMusicViewModel.loadMoreYoutubeSuggestions() {
    val current = _youtubeState.value
    if (current.isLoading || current.isLoadingMore) return
    if (youtubeLoadMoreJob?.isActive == true) return
    when {
        current.showPlayer && current.featured.isReady && current.canLoadMoreSuggestions() -> {
            youtubeLoadMoreJob = loadMoreWatchNextQueue()
        }
        current.query.isBlank() && current.nextCursor != null -> {
            youtubeLoadMoreJob = loadMoreYoutubeHome()
        }
        current.query.isNotBlank() && current.hasMoreSearchResults -> {
            youtubeLoadMoreJob = loadMoreYoutubeSearchResults()
        }
    }
}

internal fun SnapMusicViewModel.loadMoreYoutubeHome(): Job? {
    val current = _youtubeState.value
    val cursor = current.nextCursor ?: return null
    _youtubeState.value = current.copy(isLoadingMore = true)
    return viewModelScope.launch {
        val startedAt = SystemClock.elapsedRealtime()
        runCatching {
            var page = graph.musicHomeFeedRepository.loadMusicHomeFeed(
                sessionSeed = youTubeFeedSessionSeed,
                cursor = cursor,
                limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
            )
            val existingUrls = withContext(Dispatchers.Default) {
                current.items.mapTo(HashSet()) { it.url }
            }
            var retryCount = 0
            while (
                page.items.none { item -> item.url !in existingUrls } &&
                !page.nextCursor.isNullOrBlank() &&
                retryCount < YOUTUBE_FEED_DUPLICATE_PAGE_RETRY_LIMIT
            ) {
                retryCount += 1
                page = graph.musicHomeFeedRepository.loadMusicHomeFeed(
                    sessionSeed = youTubeFeedSessionSeed,
                    cursor = page.nextCursor,
                    limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
                )
            }
            page
        }
            .onSuccess { state ->
                val latest = _youtubeState.value
                val merged = mergeUniqueYoutubeItems(latest.items, state.items)
                val added = (merged.size - latest.items.size).coerceAtLeast(0)
                val duplicates = (state.items.size - added).coerceAtLeast(0)
                _youtubeState.value = latest.copy(
                    items = merged,
                    isLoadingMore = false,
                    nextCursor = state.nextCursor,
                )
                SnapMusicFeedPagingTelemetry.loadMore(
                    kind = "home",
                    session = state.nextCursor ?: cursor,
                    cursor = cursor,
                    lane = "home-load-more",
                    added = added,
                    duplicates = duplicates,
                    exhausted = state.nextCursor == null,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    resultCursor = state.nextCursor,
                )
                startupPrefetchDone = false
                prefetchFeedItems(state.items)
            }
            .onFailure { error ->
                _youtubeState.value = _youtubeState.value.copy(isLoadingMore = false)
                SnapMusicFeedPagingTelemetry.loadMore(
                    kind = "home",
                    session = cursor,
                    cursor = cursor,
                    lane = "home-load-more",
                    added = 0,
                    duplicates = 0,
                    exhausted = false,
                    durationMs = SystemClock.elapsedRealtime() - startedAt,
                    error = error.message,
                )
            }
    }
}

internal fun SnapMusicViewModel.loadMoreYoutubeSearchResults(): Job? {
    val current = _youtubeState.value
    val query = current.query.trim()
    val cursor = current.nextCursor
    if (query.isBlank() || cursor.isNullOrBlank()) return null
    _youtubeState.value = current.copy(isLoadingMore = true)
    return viewModelScope.launch {
        runCatching {
            var page = graph.resolverRepository.searchVideosPage(
                query = query,
                limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
                cursor = cursor,
            )
            val existingUrls = withContext(Dispatchers.Default) {
                current.items.mapTo(HashSet()) { it.url }
            }
            var retryCount = 0
            while (
                page.items.none { item -> item.url !in existingUrls } &&
                !page.nextCursor.isNullOrBlank() &&
                retryCount < YOUTUBE_FEED_DUPLICATE_PAGE_RETRY_LIMIT
            ) {
                retryCount += 1
                page = graph.resolverRepository.searchVideosPage(
                    query = query,
                    limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
                    cursor = page.nextCursor,
                )
            }
            page
        }
            .onSuccess { page ->
                val latest = _youtubeState.value
                val merged = mergeUniqueYoutubeItems(latest.items, page.items)
                _youtubeState.value = latest.copy(
                    items = merged,
                    isLoadingMore = false,
                    nextCursor = page.nextCursor,
                    hasMoreSearchResults = !page.nextCursor.isNullOrBlank() && page.items.isNotEmpty(),
                )
                if (page.items.isNotEmpty()) {
                    startupPrefetchDone = false
                    prefetchFeedItems(page.items)
                }
            }
            .onFailure {
                _youtubeState.value = _youtubeState.value.copy(
                    isLoadingMore = false,
                    nextCursor = null,
                    hasMoreSearchResults = false,
                )
            }
    }
}

