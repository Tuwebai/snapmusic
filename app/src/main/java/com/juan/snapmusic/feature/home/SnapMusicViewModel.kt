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

internal fun isPreviewVideoUri(uri: String): Boolean {
    val normalized = uri.lowercase()
    return normalized.contains("/video/") ||
        normalized.contains("video/media") ||
        normalized.endsWith(".mp4") ||
        normalized.endsWith(".mkv") ||
        normalized.endsWith(".webm") ||
        normalized.endsWith(".mov")
}

internal typealias PlaybackSelection = YouTubePlaybackSelection
internal val YOUTUBE_STABLE_MERGED_AUTO_HEIGHTS = listOf(720, 480, 360, 240, 144, 1080)
internal val YOUTUBE_STABLE_PROGRESSIVE_AUTO_HEIGHTS = listOf(720, 480, 360, 240, 144)

internal const val YOUTUBE_HOME_FEED_LIMIT = 18
internal const val YOUTUBE_HOME_FEED_PAGE_SIZE = 18
internal const val YOUTUBE_HOME_CACHE_PRIME_COUNT = YOUTUBE_HOME_FEED_PAGE_SIZE
internal const val YOUTUBE_WATCH_NEXT_PAGE_SIZE = 18
internal const val YOUTUBE_WATCH_NEXT_LOOKAHEAD_SIZE = 12
internal const val YOUTUBE_WATCH_NEXT_ENRICH_DELAY_MS = 1_500L
internal const val YOUTUBE_NEXT_PRE_RESOLVE_MIN_POSITION_MS = 30_000L
internal const val YOUTUBE_NEXT_PRE_RESOLVE_STABLE_WINDOW_MS = 30_000L
internal const val YOUTUBE_FEED_PAGE_TIMEOUT_MS = 12_000L
internal const val HOME_TAB_YOUTUBE_INDEX = 1
internal const val YOUTUBE_WATCH_COMMENT_FALLBACK = "Eleg? un formato y mandalo a la cola sin salir de esta pantalla."
internal const val INSTAGRAM_FAST_VIDEO_VARIANT_ID = "instagram-fast-video-mp4"
internal const val YOUTUBE_FEED_DUPLICATE_PAGE_RETRY_LIMIT = 2
internal const val YOUTUBE_PLAYBACK_LOG_TAG = "SnapMusicYouTube"

class SnapMusicViewModel(
    internal val graph: SnapMusicGraph,
) : ViewModel() {


    internal val buildSearchSuggestionCorpus = BuildSearchSuggestionCorpusUseCase()
    internal val buildWatchNextProjection = BuildWatchNextProjectionUseCase()
    internal val _homeSelectedTab = MutableStateFlow(0)
    internal val _downloadSearchState = MutableStateFlow(DownloadSearchState())
    val homeSelectedTab: StateFlow<Int> = _homeSelectedTab.asStateFlow()
    internal val _incomingShareSelectionState = MutableStateFlow(IncomingShareSelectionState())
    val incomingShareSelectionState: StateFlow<IncomingShareSelectionState> = _incomingShareSelectionState.asStateFlow()
    internal val _selectedPreview = MutableStateFlow<PreviewState?>(null)
    internal val _previewLibrary = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    internal val _previewDetailVisible = MutableStateFlow(false)
    internal val _previewMiniPlayerVisible = MutableStateFlow(false)
    internal val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    internal val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    internal var youTubeFeedSessionSeed = System.currentTimeMillis()
    internal var youtubeSuggestionJob: Job? = null
    internal var watchNextEnrichmentJob: Job? = null
    internal var nextQueuePreResolveJob: Job? = null
    internal var downloadSearchSuggestionJob: Job? = null
    internal var popularDownloadSearchesJob: Job? = null
    internal var cachedYouTubePrefetchJob: Job? = null
    internal var youtubeFeedPrefetchJob: Job? = null
    internal var youtubeLoadMoreJob: Job? = null
    internal var deferredYoutubeHomeRefreshJob: Job? = null
    internal var startupPrefetchDone = false
    internal var hasOpenedYouTubeHomeTab = false
    internal var hasLoadedPopularDownloadQueries = false
    internal var youTubeHomeFeedCacheRestoreStarted = false
    internal var youTubePlaybackSnapshotRestoreStarted = false
    internal val _previewAutoPlayRequestId = MutableStateFlow(0L)
    internal val _previewCurrentPositionMs = MutableStateFlow(0L)
    internal val _previewResumePositionMs = MutableStateFlow(0L)
    internal val _previewPlaybackQueueOverride = MutableStateFlow<List<PreviewPlaybackQueueItem>>(emptyList())
    internal val _previewDownloadsRequestId = MutableStateFlow(0L)
    internal val _queueFeedback = MutableStateFlow<String?>(null)
    internal val _youtubeState = MutableStateFlow(YouTubeUiState())
    internal val _youtubeDownloadSheet = MutableStateFlow(YouTubeDownloadSheetState())
    internal val _youtubeSearchSuggestions = MutableStateFlow<List<String>>(emptyList())
    internal val _youtubeSearchSuggestionsLoading = MutableStateFlow(false)
    internal val _recentSearchQueries = MutableStateFlow<List<String>>(emptyList())
    internal val youTubeResolveCache = object : LinkedHashMap<String, YouTubeFeaturedVideo>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTubeFeaturedVideo>?): Boolean {
            return size > 50
        }
    }
    internal val youTubePlaybackMilestones = linkedMapOf<String, MutableSet<MusicSignalType>>()
    internal var lastFailureFallbackSourceUrl: String? = null
    internal var lastExpiredStreamRetrySourceUrl: String? = null
    internal val refreshedAdaptivePlaybackSources = linkedSetOf<String>()
    internal val playbackFallbackModes = linkedMapOf<String, MutableSet<YouTubePlaybackSourceMode>>()
    internal val playbackStabilityFallbacks = linkedMapOf<String, MutableSet<String>>()
    internal val youtubeRebufferEvents = linkedMapOf<String, MutableList<Long>>()
    internal val youtubeFullscreenController = YouTubeFullscreenController()
    internal val youtubeWatchHistoryLastRecordedPositions = linkedMapOf<String, Long>()
    internal val pendingYouTubeHistoryResumePositions = linkedMapOf<String, Long>()
    internal var cachedYouTubeHomeFeed: List<YouTubeFeedItem> = emptyList()
    internal var queueObservationStarted = false
    internal var historyObservationStarted = false
    internal var interruptedDownloadRestoreStarted = false

    val queueFeedback: StateFlow<String?> = _queueFeedback.asStateFlow()
    val previewDownloadsRequestId: StateFlow<Long> = _previewDownloadsRequestId.asStateFlow()
    internal val _cacheCleanupState = MutableStateFlow(CacheCleanupUiState())
    val cacheCleanupState: StateFlow<CacheCleanupUiState> = _cacheCleanupState.asStateFlow()
    val youtubeState: StateFlow<YouTubeUiState> = _youtubeState.asStateFlow()
    val youtubeDownloadSheet: StateFlow<YouTubeDownloadSheetState> = _youtubeDownloadSheet.asStateFlow()
    val previewLibrary: StateFlow<List<LocalMediaItem>> = _previewLibrary.asStateFlow()
    val previewDetailVisible: StateFlow<Boolean> = _previewDetailVisible.asStateFlow()
    val previewMiniPlayerVisible: StateFlow<Boolean> = _previewMiniPlayerVisible.asStateFlow()
    val previewAutoPlayRequestId: StateFlow<Long> = _previewAutoPlayRequestId.asStateFlow()
    

    val preferences = graph.preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    val youtubeWatchHistory: StateFlow<List<YouTubeWatchHistoryEntry>> = graph.youtubeWatchHistoryRepository
        .observeHistory()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val downloadBadgeState = createDownloadBadgeStateFlow()
    val activeDownloadCount = createActiveDownloadCountFlow()
    val previewState = createPreviewStateFlow()
    internal val previewReadyState = createPreviewReadyStateFlow()
    val previewPerformanceState = createPreviewPerformanceStateFlow()
    val youtubeChrome = createYoutubeChromeFlow()
    val previewChrome = createPreviewChromeFlow()
    val previewRouteVisibility = createPreviewRouteVisibilityFlow()
    val previewScreen = createPreviewScreenFlow()
    val previewLibraryScreen = createPreviewLibraryScreenFlow()
    val previewDetailScreen = createPreviewDetailScreenFlow()
    val previewDownloadsState = createPreviewDownloadsStateFlow()
    val previewActiveDownloadCount = createPreviewActiveDownloadCountFlow()
    val previewCompletedDownloadsCount = createPreviewCompletedDownloadsCountFlow()
    val previewDownloadsShellState = createPreviewDownloadsShellStateFlow()
    val homeSearch = createHomeSearchFlow()
    val downloadSearchSuggestions = createDownloadSearchSuggestionsFlow()
    val homeSearchSuggestions = createHomeSearchSuggestionsFlow()
    val youtubeScreen = createYoutubeScreenFlow()
    val youtubeRouteVisibility = createYoutubeRouteVisibilityFlow()
    val homeYouTubeTabsVisible = createHomeYouTubeTabsVisibleFlow()
    val youtubePictureInPictureEligibility = createYoutubePictureInPictureEligibilityFlow()
    val previewPictureInPictureEligibility = createPreviewPictureInPictureEligibilityFlow()
    val youtubePlayerMountEnabled = createYoutubePlayerMountEnabledFlow()
    val previewPlayerMountEnabled = createPreviewPlayerMountEnabledFlow()
    internal val youtubeFeedProjection = createYoutubeFeedProjectionFlow()
    val youtubeFeedScreen = createYoutubeFeedScreenFlow()
    val youtubeSuggestionsScreen = createYoutubeSuggestionsScreenFlow()
    internal val searchSuggestionCorpus = createSearchSuggestionCorpusFlow()
    val youtubeWatchNextScreen = createYoutubeWatchNextScreenFlow()
    val youtubePlaybackPanel = createYoutubePlaybackPanelFlow()
    val youtubeMiniPlayerState = createYoutubeMiniPlayerStateFlow()
    val previewMiniPlayerState = createPreviewMiniPlayerStateFlow()
    val previewRestoreState = createPreviewRestoreStateFlow()
    val youtubePictureInPictureState = createYoutubePictureInPictureStateFlow()
    val previewPictureInPictureState = createPreviewPictureInPictureStateFlow()
    val youtubePlayerSessionState = createYoutubePlayerSessionStateFlow()
    val youtubePlayerSeekState = createYoutubePlayerSeekStateFlow()
    val youtubePlaybackAutoPlay = createYoutubePlaybackAutoPlayFlow()
    val appPictureInPictureConfig = createAppPictureInPictureConfigFlow()
    val navHostPlaybackState = createNavHostPlaybackStateFlow()
    val bottomBarUiState = createBottomBarUiStateFlow()
    val previewPlaybackRenderState = createPreviewPlaybackRenderStateFlow()
    val previewActiveFileUri = createPreviewActiveFileUriFlow()



    init {
        viewModelScope.launch(Dispatchers.IO) {
            _recentSearchQueries.value = graph.preferencesRepository.readRecentSearchQueries()
        }
        viewModelScope.launch {
            graph.launchPreferencesRepository.youtubeAutoplayEnabled.collect { youtubeAutoplayEnabled ->
                val current = _youtubeState.value
                if (current.autoplayEnabled != youtubeAutoplayEnabled) {
                    _youtubeState.value = current.copy(autoplayEnabled = youtubeAutoplayEnabled)
                }
            }
        }
        viewModelScope.launch {
            youtubeState
                .map { state ->
                    val queueItems = state.playbackQueue.ifEmpty { state.items }
                    val hasActivePlayback =
                        state.featured.isReady &&
                            state.featured.sourceUrl.isNotBlank() &&
                            (state.showPlayer || state.showMiniPlayer)
                    if (!hasActivePlayback || queueItems.isEmpty()) {
                        false to false
                    } else {
                        val currentIndex = resolveCurrentQueueIndex(state, queueItems)
                        val hasPrevious =
                            previousQueueIndex(
                                queueSize = queueItems.size,
                                currentIndex = currentIndex,
                                currentPositionMs = state.currentPositionMs,
                            ) != null
                        val hasNext =
                            nextQueueIndex(
                                queueSize = queueItems.size,
                                currentIndex = currentIndex,
                                continuationMode = state.continuationMode,
                            ) != null
                        hasPrevious to hasNext
                    }
                }
                .distinctUntilChanged()
                .collectLatest { (hasPrevious, hasNext) ->
                    PlaybackSessionStateStore.updateYouTubeTransport(
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                    )
                }
        }
    }

}


class SnapMusicViewModelFactory(
    internal val graph: SnapMusicGraph,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnapMusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SnapMusicViewModel(graph) as T
        }
        error("Factory no soportada: ${modelClass.name}")
    }
}
