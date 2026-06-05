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
import kotlinx.coroutines.withTimeoutOrNull

internal fun SnapMusicViewModel.resolveCurrentQueueIndex(
    state: YouTubeUiState,
    queueItems: List<YouTubeFeedItem> = state.playbackQueue.ifEmpty { state.items },
): Int {
    val indexed = state.currentQueueIndex
    if (indexed in queueItems.indices && queueItems[indexed].url == state.featured.sourceUrl) {
        return indexed
    }
    val byUrl = queueItems.indexOfFirst { it.url == state.featured.sourceUrl }
    return if (byUrl >= 0) byUrl else 0
}

internal fun SnapMusicViewModel.initialWatchNextItems(
    queueItems: List<YouTubeFeedItem>,
    currentIndex: Int,
    queueOrigin: YouTubeQueueOrigin,
): List<YouTubeFeedItem> {
    if (queueItems.isEmpty()) return emptyList()
    if (queueOrigin == YouTubeQueueOrigin.SEARCH_RESULTS) return emptyList()
    val normalizedIndex = currentIndex.coerceIn(0, queueItems.lastIndex)
    val currentUrl = queueItems[normalizedIndex].url
    return queueItems
        .drop((normalizedIndex + 1).coerceAtMost(queueItems.size))
        .filter { it.url != currentUrl }
        .distinctBy(YouTubeFeedItem::url)
}

internal fun SnapMusicViewModel.seedOriginForWatchNext(state: YouTubeUiState): YouTubeQueueOrigin {
    return if (state.queueOrigin == YouTubeQueueOrigin.SEARCH_RESULTS && state.watchNextItems.isNotEmpty()) {
        YouTubeQueueOrigin.HOME_FEED
    } else {
        state.queueOrigin
    }
}

internal fun SnapMusicViewModel.watchNextFallbackCandidates(
    currentItem: YouTubeFeedItem,
    state: YouTubeUiState,
    existingQueue: List<YouTubeFeedItem>,
    blockedQueueItems: List<YouTubeFeedItem>,
    existingWatchNext: List<YouTubeFeedItem>,
): List<YouTubeFeedItem> {
    return (cachedYouTubeHomeFeed + state.items + existingQueue)
        .asSequence()
        .filter { candidate ->
            candidate.url != currentItem.url &&
                blockedQueueItems.none { blocked -> blocked.url == candidate.url } &&
                existingWatchNext.none { existing -> existing.url == candidate.url }
        }
        .distinctBy(YouTubeFeedItem::url)
        .toList()
}

internal suspend fun SnapMusicViewModel.resolveWatchNextRecoveryCandidates(
    currentItem: YouTubeFeedItem,
    state: YouTubeUiState,
    existingQueue: List<YouTubeFeedItem>,
    blockedQueueItems: List<YouTubeFeedItem>,
    existingWatchNext: List<YouTubeFeedItem>,
    requestedLimit: Int,
): List<YouTubeFeedItem> {
    val localCandidates = watchNextFallbackCandidates(
        currentItem = currentItem,
        state = state,
        existingQueue = existingQueue,
        blockedQueueItems = blockedQueueItems,
        existingWatchNext = existingWatchNext,
    )
    if (localCandidates.size >= requestedLimit.coerceAtMost(6)) return localCandidates
    val remoteCandidates = mutableListOf<YouTubeFeedItem>()
    for (query in watchNextRecoveryQueries(currentItem)) {
        val page = runCatching {
            withTimeoutOrNull(YOUTUBE_FEED_PAGE_TIMEOUT_MS) {
            graph.resolverRepository.searchVideosPage(
                query = query,
                limit = requestedLimit,
            )
            }
        }.getOrNull()
        page?.items
            .orEmpty()
            .filterNot { candidate ->
                candidate.url == currentItem.url ||
                    blockedQueueItems.any { blocked -> blocked.url == candidate.url } ||
                    existingWatchNext.any { existing -> existing.url == candidate.url } ||
                    localCandidates.any { local -> local.url == candidate.url } ||
                    remoteCandidates.any { remote -> remote.url == candidate.url }
            }
            .forEach(remoteCandidates::add)
        if ((localCandidates.size + remoteCandidates.size) >= requestedLimit) break
    }
    val homeFillCandidates = if ((localCandidates.size + remoteCandidates.size) < requestedLimit) {
        resolveWatchNextHomeFillCandidates(
            currentItem = currentItem,
            state = state,
            blockedQueueItems = blockedQueueItems,
            existingWatchNext = existingWatchNext,
            alreadySelected = localCandidates + remoteCandidates,
            requestedLimit = requestedLimit,
        )
    } else {
        emptyList()
    }
    return (localCandidates + remoteCandidates + homeFillCandidates)
        .filter { it.url != currentItem.url }
        .distinctBy(YouTubeFeedItem::url)
}

private suspend fun SnapMusicViewModel.resolveWatchNextHomeFillCandidates(
    currentItem: YouTubeFeedItem,
    state: YouTubeUiState,
    blockedQueueItems: List<YouTubeFeedItem>,
    existingWatchNext: List<YouTubeFeedItem>,
    alreadySelected: List<YouTubeFeedItem>,
    requestedLimit: Int,
): List<YouTubeFeedItem> {
    val selected = linkedMapOf<String, YouTubeFeedItem>()
    val blockedUrls = buildSet {
        add(currentItem.url)
        blockedQueueItems.forEach { add(it.url) }
        existingWatchNext.forEach { add(it.url) }
        alreadySelected.forEach { add(it.url) }
    }
    val stableSeed = youTubeFeedSessionSeed + (currentItem.url.hashCode().toLong() * 31L)
    var cursor = (state.watchNextItems.size + alreadySelected.size)
        .coerceAtLeast(0)
        .toString()
    repeat(4) {
        val page = withTimeoutOrNull(YOUTUBE_FEED_PAGE_TIMEOUT_MS) {
            graph.musicHomeFeedRepository.loadMusicHomeFeed(
                sessionSeed = stableSeed,
                cursor = cursor,
                limit = YOUTUBE_WATCH_NEXT_PAGE_SIZE + YOUTUBE_WATCH_NEXT_LOOKAHEAD_SIZE,
            )
        } ?: return@repeat
        page.items.forEach { candidate ->
            if (candidate.url !in blockedUrls && candidate.url !in selected) {
                selected[candidate.url] = candidate
            }
        }
        if (selected.size >= requestedLimit) return selected.values.toList()
        cursor = page.nextCursor ?: (
            (cursor.toIntOrNull() ?: state.watchNextItems.size) + YOUTUBE_WATCH_NEXT_PAGE_SIZE
        ).toString()
    }
    return selected.values.toList()
}

internal fun SnapMusicViewModel.watchNextRecoveryQueries(currentItem: YouTubeFeedItem): List<String> {
    val cleanAuthor = currentItem.author.trim()
    val cleanTitle = currentItem.title
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("(?i)official|video|lyrics?|audio|remix|session|en vivo|live|hd|4k"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return listOf(
        "$cleanAuthor $cleanTitle",
        cleanAuthor,
        cleanTitle,
        "música recomendada",
        "tendencias música",
    )
        .map(String::trim)
        .filter { it.length >= 3 }
        .distinct()
}

internal fun SnapMusicViewModel.rebuildQueueWithWatchNext(
    queueItems: List<YouTubeFeedItem>,
    currentIndex: Int,
    rankedWatchNext: List<YouTubeFeedItem>,
): Pair<List<YouTubeFeedItem>, List<YouTubeFeedItem>> {
    if (queueItems.isEmpty()) return rankedWatchNext to rankedWatchNext
    val normalizedIndex = currentIndex.coerceIn(0, queueItems.lastIndex)
    val queuePrefix = queueItems.take(normalizedIndex + 1)
    val prefixUrls = queuePrefix.mapTo(linkedSetOf(), YouTubeFeedItem::url)
    val visibleWatchNext = rankedWatchNext
        .filter { it.url !in prefixUrls }
        .distinctBy(YouTubeFeedItem::url)
    return (queuePrefix + visibleWatchNext).distinctBy(YouTubeFeedItem::url) to visibleWatchNext
}

internal fun SnapMusicViewModel.relatedQueueBlocklist(
    queueItems: List<YouTubeFeedItem>,
    currentIndex: Int,
    queueOrigin: YouTubeQueueOrigin,
): List<YouTubeFeedItem> {
    if (queueOrigin != YouTubeQueueOrigin.SEARCH_RESULTS) return queueItems
    val normalizedIndex = currentIndex.coerceIn(0, queueItems.lastIndex)
    return queueItems.take(normalizedIndex + 1)
}

internal fun SnapMusicViewModel.retryYouTubePlaybackSource(rawMessage: String?): Boolean {
    val current = _youtubeState.value
    val featured = current.featured
    val sourceUrl = featured.sourceUrl
    val media = featured.resolvedMedia ?: return false
    val mode = playbackSourceMode(featured) ?: return false
    if (mode == YouTubePlaybackSourceMode.ADAPTIVE && refreshedAdaptivePlaybackSources.add(sourceUrl)) {
        return refreshAdaptiveYouTubePlaybackSource(current, rawMessage)
    }
    val fallbackModes = playbackFallbackModes.getOrPut(sourceUrl) { linkedSetOf() }
    if (!fallbackModes.add(mode)) return false
    val fallbackSelection = resolveFallbackPlaybackSelection(
        media = media,
        currentMode = mode,
        requestedVariantId = featured.selectedVideoQualityId,
    ) ?: return false
    if (fallbackSelection.playbackUrl == featured.playbackUrl) return false
    YouTubePlaybackTelemetry.fallback(sourceUrl, mode, fallbackSelection, rawMessage)
    val updatedFeatured = featured.copy(
        playbackUrl = fallbackSelection.playbackUrl,
        actualVideoHeight = fallbackSelection.expectedHeight,
        actualPlaybackLabel = playbackLabelForSelection(media, featured.selectedVideoQualityId, fallbackSelection.expectedHeight),
        isReady = true,
    )
    youTubeResolveCache[sourceUrl] = updatedFeatured
    _youtubeState.value = current.copy(
        featured = updatedFeatured,
        isRefreshingVideo = false,
        pendingTransition = false,
        shouldAutoPlayCurrent = true,
        errorMessage = null,
    )
    persistCurrentYouTubeSnapshot()
    return true
}
