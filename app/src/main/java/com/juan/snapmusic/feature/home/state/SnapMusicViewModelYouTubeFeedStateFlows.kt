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
import com.juan.snapmusic.core.model.QueueStatus
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

internal fun SnapMusicViewModel.createHomeSearchFlow() = _downloadSearchState
    .map { state ->
        HomeSearchState(
            query = state.query,
            isOverlayVisible = state.isOverlayVisible,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeSearchState(),
    )

internal fun SnapMusicViewModel.createDownloadSearchSuggestionsFlow() = _downloadSearchState
    .map { state ->
        DownloadSearchSuggestionUiState(
            query = state.query,
            suggestions = state.suggestions,
            isLoading = state.isLoadingSuggestions,
            popularQueries = state.popularQueries,
            mode = if (state.query.isBlank()) DownloadSearchUiMode.POPULAR else DownloadSearchUiMode.SUGGESTIONS,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadSearchSuggestionUiState(),
    )

internal fun SnapMusicViewModel.createHomeSearchSuggestionsFlow() = downloadSearchSuggestions
    .map { state ->
        HomeSearchSuggestionState(
            query = state.query,
            suggestions = state.suggestions,
            isLoading = state.isLoading,
            popularQueries = state.popularQueries,
            mode = state.mode,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeSearchSuggestionState(),
    )

internal fun SnapMusicViewModel.createYoutubeScreenFlow() = youtubeState
    .map { state ->
        YouTubeScreenState(
            query = state.query,
            isLoading = state.isLoading,
            isRefreshingVideo = state.isRefreshingVideo,
            showPlayer = state.showPlayer,
            featured = state.featured,
            items = state.items,
            autoplayEnabled = state.autoplayEnabled,
            nextUpItem = state.nextUpItem,
            openDownloadSheet = state.openDownloadSheet,
            errorMessage = state.errorMessage,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubeScreenState(),
    )

internal fun SnapMusicViewModel.createYoutubeRouteVisibilityFlow() = youtubeState
    .map { state ->
        YouTubeRouteVisibilityState(
            showPlayer = state.showPlayer,
            showMiniPlayer = state.showMiniPlayer,
            hasActiveItem = state.featured.sourceUrl.isNotBlank(),
            isReady = state.featured.isReady,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubeRouteVisibilityState(),
    )

internal fun SnapMusicViewModel.createHomeYouTubeTabsVisibleFlow() = youtubeRouteVisibility
    .map { visibility -> !visibility.showPlayer }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = true,
    )

internal fun SnapMusicViewModel.createYoutubePictureInPictureEligibilityFlow() = youtubeState
    .map { state ->
        PictureInPictureEligibilityState(
            eligible = (state.showPlayer || state.showMiniPlayer) && state.featured.isReady,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PictureInPictureEligibilityState(),
    )

internal fun SnapMusicViewModel.createPreviewPictureInPictureEligibilityFlow() = combine(
    previewState,
    _previewDetailVisible,
    _previewMiniPlayerVisible,
) { preview, detailVisible, miniVisible ->
    PictureInPictureEligibilityState(
        eligible = preview.isReady &&
            preview.fileUri?.let { isPreviewVideoUri(it) } == true &&
            (detailVisible || miniVisible),
    )
}
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PictureInPictureEligibilityState(),
    )

internal fun SnapMusicViewModel.createYoutubePlayerMountEnabledFlow() = combine(
    youtubeRouteVisibility,
    youtubePictureInPictureEligibility,
) { visibility, pip ->
    visibility.isReady && (
        visibility.showPlayer ||
            visibility.showMiniPlayer ||
            pip.eligible
        )
}
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

internal fun SnapMusicViewModel.createPreviewPlayerMountEnabledFlow() = combine(
    previewRouteVisibility,
    previewPictureInPictureEligibility,
) { visibility, pip ->
    visibility.isReady && (
        visibility.detailVisible ||
            visibility.miniVisible ||
            pip.eligible
        )
}
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

internal fun SnapMusicViewModel.createYoutubeFeedProjectionFlow() = youtubeState
    .map { state ->
        YouTubeFeedProjection(
            query = state.query,
            isLoading = state.isLoading,
            isLoadingMore = state.isLoadingMore,
            showPlayer = state.showPlayer,
            isWatchTransitioning = state.showPlayer && state.pendingTransition,
            featuredSourceUrl = state.featured.sourceUrl,
            watchNextItems = state.watchNextItems,
            playbackQueue = state.playbackQueue,
            nextCursor = state.nextCursor,
            hasMoreSearchResults = state.hasMoreSearchResults,
            canLoadMoreWatchNext = state.canLoadMoreWatchNext,
            items = state.items,
            errorMessage = state.errorMessage,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubeFeedProjection(),
    )

internal fun SnapMusicViewModel.createYoutubeFeedScreenFlow() = youtubeFeedProjection
    .map { state ->
        YouTubeFeedState(
            query = state.query,
            isLoading = state.isLoading,
            isLoadingMore = state.isLoadingMore,
            showSearchPanel = !state.showPlayer,
            items = if (state.showPlayer) {
                state.playbackQueue
                    .ifEmpty { state.items }
                    .filterNot { item -> item.url == state.featuredSourceUrl }
            } else {
                state.items
            },
            canLoadMore = state.canLoadMoreSuggestions(),
            errorMessage = state.errorMessage,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubeFeedState(),
    )

internal fun SnapMusicViewModel.createYoutubeSuggestionsScreenFlow() = combine(
    youtubeFeedProjection,
    createDownloadedQualityLabelsFlow(),
    createWatchProgressFractionsFlow(),
) { state, downloadedQualityLabels, watchProgressFractions ->
        YouTubeSuggestionsUiState(
            query = state.query,
            isPlayerVisible = state.showPlayer,
            isWatchTransitioning = state.isWatchTransitioning,
            items = if (state.showPlayer) {
                state.watchNextItems
            } else {
                state.items
            },
            downloadedQualityLabels = downloadedQualityLabels,
            watchProgressFractions = watchProgressFractions,
            isRefreshing = state.isLoading,
            isLoadingMore = state.isLoadingMore,
            canLoadMore = state.canLoadMoreSuggestions(),
            errorMessage = state.errorMessage,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubeSuggestionsUiState(),
    )

private fun SnapMusicViewModel.createDownloadedQualityLabelsFlow(): Flow<Map<String, String>> {
    val queueDownloads = graph.queueRepository.observeQueue()
        .map { entries ->
            entries.asSequence()
                .filter { entry -> entry.status == QueueStatus.SUCCESS }
                .mapNotNull(QueueEntry::toDownloadedQualityEntry)
                .toMap()
        }
    val historyDownloads = graph.historyRepository.observeHistory()
        .map { entries ->
            entries.asSequence()
                .mapNotNull(HistoryEntry::toDownloadedQualityEntry)
                .toMap()
        }
    return combine(queueDownloads, historyDownloads) { queueLabels, historyLabels ->
        queueLabels + historyLabels
}.distinctUntilChanged()
}

private fun QueueEntry.toDownloadedQualityEntry(): Pair<String, String>? {
    val source = sourceUrl.trim().takeIf(String::isNotBlank) ?: return null
    return source to variantLabel.toDownloadedQualityLabel(container)
}

private fun HistoryEntry.toDownloadedQualityEntry(): Pair<String, String>? {
    val source = sourceUrl.trim().takeIf(String::isNotBlank) ?: return null
    return source to qualityLabel.toDownloadedQualityLabel(format)
}

private fun String.toDownloadedQualityLabel(container: ContainerFormat): String {
    val cleaned = trim().replace("·", " ").replace(Regex("\\s+"), " ")
    val resolution = Regex("""(?i)\b(2160|1440|1080|720|480|360|240|144)\s*p\b""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { "${it}p" }
    val bitrate = Regex("""(?i)\b([0-9]{2,3})\s*kbps\b""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.let { "${it}k" }
    return when (container) {
        ContainerFormat.MP4 -> resolution ?: container.name
        ContainerFormat.MP3,
        ContainerFormat.M4A,
        ContainerFormat.WEBM -> listOfNotNull(container.name, bitrate).joinToString(" ")
    }
}

private fun SnapMusicViewModel.createWatchProgressFractionsFlow(): Flow<Map<String, Float>> {
    return graph.youtubeWatchHistoryRepository.observeHistory()
        .map { entries ->
            entries.asSequence()
                .mapNotNull { entry ->
                    val url = entry.sourceUrl.trim()
                    val progress = entry.toWatchProgressFraction()
                    if (url.isBlank() || progress <= 0f) null else url to progress
                }
                .toMap()
        }
        .distinctUntilChanged()
}

private fun YouTubeWatchHistoryEntry.toWatchProgressFraction(): Float {
    val durationMs = durationSeconds * 1_000L
    if (durationMs <= 0L || lastPositionMs <= 0L) return 0f
    return (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

internal fun SnapMusicViewModel.createSearchSuggestionCorpusFlow() = combine(
    youtubeState.map { it.items }.distinctUntilChanged(),
    _downloadSearchState.map { it.popularQueries }.distinctUntilChanged(),
    _recentSearchQueries,
) { items, popularQueries, recentQueries ->
    buildSearchSuggestionCorpus(
        popularQueries = recentQueries + popularQueries,
        items = items,
    )
}
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
