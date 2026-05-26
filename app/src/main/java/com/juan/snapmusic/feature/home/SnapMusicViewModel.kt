package com.juan.snapmusic.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.SnapMusicGraph
import com.juan.snapmusic.core.model.AppThemeMode
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.ConversionRequest
import com.juan.snapmusic.core.model.DownloadBadgeState
import com.juan.snapmusic.core.model.HistoryEntry
import com.juan.snapmusic.core.model.IncomingShareItem
import com.juan.snapmusic.core.model.IncomingSharePayload
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
import com.juan.snapmusic.core.platform.MergedPlaybackUri
import com.juan.snapmusic.core.platform.PlaybackNotificationRouteStore
import com.juan.snapmusic.core.platform.PlaybackNotificationRouteTarget
import com.juan.snapmusic.core.platform.PlaybackSessionStateStore
import com.juan.snapmusic.core.platform.validateYouTubeUrl
import com.juan.snapmusic.data.persistence.QueueEntity
import com.juan.snapmusic.data.persistence.toDownloadSelection
import com.juan.snapmusic.feature.youtube.nextQueueIndex
import com.juan.snapmusic.feature.youtube.nextQueueItem
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

@Immutable
data class YouTubeChromeState(
    val showPlayer: Boolean = false,
    val showMiniPlayer: Boolean = false,
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val compactMiniPlayer: Boolean = false,
)

@Immutable
data class PreviewChromeState(
    val preview: PreviewState = PreviewState(),
    val detailVisible: Boolean = false,
    val miniVisible: Boolean = false,
)

@Immutable
data class YouTubeMiniPlayerState(
    val visible: Boolean = false,
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val compact: Boolean = false,
)

@Immutable
data class PreviewMiniPlayerState(
    val visible: Boolean = false,
    val preview: PreviewState = PreviewState(),
)

@Immutable
data class PreviewRestoreState(
    val canRestore: Boolean = false,
)

enum class PlaybackNotificationTarget {
    PREVIEW,
    YOUTUBE,
    NONE,
}

@Immutable
data class YouTubePictureInPictureState(
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
)

@Immutable
data class PreviewPictureInPictureState(
    val preview: PreviewState = PreviewState(),
)

@Immutable
data class HomeSearchState(
    val query: String = "",
    val isOverlayVisible: Boolean = false,
)

@Immutable
data class IncomingShareSelectionState(
    val visible: Boolean = false,
    val items: List<IncomingShareItem> = emptyList(),
)

enum class DownloadSearchUiMode {
    POPULAR,
    SUGGESTIONS,
}

@Immutable
data class DownloadSearchState(
    val query: String = "",
    val isOverlayVisible: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val popularQueries: List<String> = emptyList(),
)

@Immutable
data class HomeSearchSuggestionState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val popularQueries: List<String> = emptyList(),
    val mode: DownloadSearchUiMode = DownloadSearchUiMode.POPULAR,
)

@Immutable
data class DownloadSearchSuggestionUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val popularQueries: List<String> = emptyList(),
    val mode: DownloadSearchUiMode = DownloadSearchUiMode.POPULAR,
)

@Immutable
data class PreviewScreenState(
    val preview: PreviewState = PreviewState(),
    val library: List<LocalMediaItem> = emptyList(),
    val detailVisible: Boolean = false,
)

@Immutable
data class PreviewLibraryUiState(
    val items: List<LocalMediaItem> = emptyList(),
)

@Immutable
data class PreviewDetailUiState(
    val preview: PreviewState = PreviewState(),
    val detailVisible: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

@Immutable
data class PreviewDownloadsState(
    val activeItems: List<QueueEntry> = emptyList(),
    val completedCount: Int = 0,
)

@Immutable
data class PreviewDownloadsShellState(
    val hasActiveDownloads: Boolean = false,
    val completedCount: Int = 0,
    val openRequestId: Long = 0L,
)

@Immutable
data class PictureInPictureEligibilityState(
    val eligible: Boolean = false,
)

@Immutable
data class AppPictureInPictureConfigState(
    val eligible: Boolean = false,
    val shouldAutoPlay: Boolean = false,
)

@Immutable
data class NavHostPlaybackState(
    val youtubeCanRestore: Boolean = false,
    val previewCanRestore: Boolean = false,
    val youtubeShowPlayer: Boolean = false,
    val youtubeReady: Boolean = false,
    val previewDetailVisible: Boolean = false,
    val previewReady: Boolean = false,
    val youtubePipEligible: Boolean = false,
    val previewPipEligible: Boolean = false,
)

@Immutable
data class BottomBarUiState(
    val youtubeCanRestore: Boolean = false,
    val previewCanRestore: Boolean = false,
    val activeDownloadCount: Int = 0,
)

@Immutable
data class YouTubeRouteVisibilityState(
    val showPlayer: Boolean = false,
    val showMiniPlayer: Boolean = false,
    val hasActiveItem: Boolean = false,
    val isReady: Boolean = false,
)

@Immutable
data class YouTubeFeedState(
    val query: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val showSearchPanel: Boolean = true,
    val items: List<YouTubeFeedItem> = emptyList(),
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
private data class YouTubeFeedProjection(
    val query: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val showPlayer: Boolean = false,
    val featuredSourceUrl: String = "",
    val watchNextItems: List<YouTubeFeedItem> = emptyList(),
    val playbackQueue: List<YouTubeFeedItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMoreSearchResults: Boolean = false,
    val canLoadMoreWatchNext: Boolean = false,
    val items: List<YouTubeFeedItem> = emptyList(),
    val errorMessage: String? = null,
)

@Immutable
data class YouTubeSuggestionsUiState(
    val isPlayerVisible: Boolean = false,
    val items: List<YouTubeFeedItem> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
data class YouTubeWatchNextUiState(
    val visible: Boolean = false,
    val commentText: String = "",
)

@Immutable
data class YouTubePlaybackPanelState(
    val showPlayer: Boolean = false,
    val isRefreshingVideo: Boolean = false,
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val autoplayEnabled: Boolean = true,
    val nextUpTitle: String? = null,
    val openDownloadSheet: Boolean = false,
)

@Immutable
data class YouTubeDownloadSheetState(
    val media: ResolvedMedia? = null,
    val visible: Boolean = false,
    val isPreparing: Boolean = false,
)

@Immutable
data class YouTubeScreenState(
    val query: String = "",
    val isLoading: Boolean = false,
    val isRefreshingVideo: Boolean = false,
    val showPlayer: Boolean = false,
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val items: List<YouTubeFeedItem> = emptyList(),
    val autoplayEnabled: Boolean = true,
    val nextUpItem: YouTubeFeedItem? = null,
    val openDownloadSheet: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
data class PreviewRouteVisibilityState(
    val detailVisible: Boolean = false,
    val miniVisible: Boolean = false,
    val isReady: Boolean = false,
)

@Immutable
data class PreviewPerformanceUiState(
    val isReady: Boolean = false,
    val isVideo: Boolean = false,
)

private fun isPreviewVideoUri(uri: String): Boolean {
    val normalized = uri.lowercase()
    return normalized.contains("/video/") ||
        normalized.contains("video/media") ||
        normalized.endsWith(".mp4") ||
        normalized.endsWith(".mkv") ||
        normalized.endsWith(".webm") ||
        normalized.endsWith(".mov")
}

class SnapMusicViewModel(
    private val graph: SnapMusicGraph,
) : ViewModel() {
    private companion object {
        const val YOUTUBE_HOME_FEED_LIMIT = 18
        const val YOUTUBE_HOME_FEED_PAGE_SIZE = 18
        const val YOUTUBE_HOME_CACHE_PRIME_COUNT = 8
        const val YOUTUBE_WATCH_NEXT_PAGE_SIZE = 18
        const val YOUTUBE_WATCH_NEXT_ENRICH_DELAY_MS = 4_500L
        const val YOUTUBE_NEXT_PRE_RESOLVE_MIN_POSITION_MS = 20_000L
        const val HOME_TAB_YOUTUBE_INDEX = 1
        const val HOME_TAB_CONVERT_INDEX = 2
        const val PRESET_MP3_320 = "preset_mp3_320"
        const val PRESET_M4A = "preset_m4a"
        const val PRESET_MP4_720 = "preset_mp4_720"
        const val YOUTUBE_WATCH_COMMENT_FALLBACK = "Elegí un formato y mandalo a la cola sin salir de esta pantalla."
    }

    private val buildSearchSuggestionCorpus = BuildSearchSuggestionCorpusUseCase()
    private val buildWatchNextProjection = BuildWatchNextProjectionUseCase()
    private val _homeState = MutableStateFlow(HomeUiState())
    private val _homeSelectedTab = MutableStateFlow(0)
    private val _downloadSearchState = MutableStateFlow(DownloadSearchState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()
    val homeSelectedTab: StateFlow<Int> = _homeSelectedTab.asStateFlow()
    private val _incomingShareSelectionState = MutableStateFlow(IncomingShareSelectionState())
    val incomingShareSelectionState: StateFlow<IncomingShareSelectionState> = _incomingShareSelectionState.asStateFlow()
    private val _selectedPreview = MutableStateFlow<PreviewState?>(null)
    private val _previewLibrary = MutableStateFlow<List<LocalMediaItem>>(emptyList())
    private val _previewDetailVisible = MutableStateFlow(false)
    private val _previewMiniPlayerVisible = MutableStateFlow(false)
    private val _queue = MutableStateFlow<List<QueueEntry>>(emptyList())
    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private var youTubeFeedSessionSeed = System.currentTimeMillis()
    private var youtubeSuggestionJob: Job? = null
    private var watchNextEnrichmentJob: Job? = null
    private var nextQueuePreResolveJob: Job? = null
    private var downloadSearchSuggestionJob: Job? = null
    private var popularDownloadSearchesJob: Job? = null
    private var cachedYouTubePrefetchJob: Job? = null
    private var youtubeFeedPrefetchJob: Job? = null
    private var deferredYoutubeHomeRefreshJob: Job? = null
    private var startupPrefetchDone = false
    private var hasOpenedYouTubeHomeTab = false
    private var hasLoadedPopularDownloadQueries = false
    private var youTubeHomeFeedCacheRestoreStarted = false
    private var youTubePlaybackSnapshotRestoreStarted = false
    private val _previewAutoPlayRequestId = MutableStateFlow(0L)
    private val _previewCurrentPositionMs = MutableStateFlow(0L)
    private val _previewResumePositionMs = MutableStateFlow(0L)
    private val _previewPlaybackQueueOverride = MutableStateFlow<List<PreviewPlaybackQueueItem>>(emptyList())
    private val _previewDownloadsRequestId = MutableStateFlow(0L)
    private val _queueFeedback = MutableStateFlow<String?>(null)
    private val _youtubeState = MutableStateFlow(YouTubeUiState())
    private val _youtubeDownloadSheet = MutableStateFlow(YouTubeDownloadSheetState())
    private val _youtubeSearchSuggestions = MutableStateFlow<List<String>>(emptyList())
    private val _youtubeSearchSuggestionsLoading = MutableStateFlow(false)
    private val youTubeResolveCache = object : LinkedHashMap<String, YouTubeFeaturedVideo>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTubeFeaturedVideo>?): Boolean {
            return size > 50
        }
    }
    private val youTubePlaybackMilestones = linkedMapOf<String, MutableSet<MusicSignalType>>()
    private var lastFailureFallbackSourceUrl: String? = null
    private var lastExpiredStreamRetrySourceUrl: String? = null
    private var cachedYouTubeHomeFeed: List<YouTubeFeedItem> = emptyList()
    private var queueObservationStarted = false
    private var historyObservationStarted = false
    private var interruptedDownloadRestoreStarted = false

    val queueFeedback: StateFlow<String?> = _queueFeedback.asStateFlow()
    val previewDownloadsRequestId: StateFlow<Long> = _previewDownloadsRequestId.asStateFlow()
    val youtubeState: StateFlow<YouTubeUiState> = _youtubeState.asStateFlow()
    val youtubeDownloadSheet: StateFlow<YouTubeDownloadSheetState> = _youtubeDownloadSheet.asStateFlow()
    val previewLibrary: StateFlow<List<LocalMediaItem>> = _previewLibrary.asStateFlow()
    val previewDetailVisible: StateFlow<Boolean> = _previewDetailVisible.asStateFlow()
    val previewMiniPlayerVisible: StateFlow<Boolean> = _previewMiniPlayerVisible.asStateFlow()
    val previewAutoPlayRequestId: StateFlow<Long> = _previewAutoPlayRequestId.asStateFlow()
    
    fun consumePreviewAutoplayRequest(requestId: Long) {
        if (_previewAutoPlayRequestId.value == requestId) {
            _previewAutoPlayRequestId.value = 0L
        }
    }

    val preferences = graph.preferencesRepository.preferences.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPreferences(),
    )

    val queue: StateFlow<List<QueueEntry>> = _queue.asStateFlow()

    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    val downloadBadgeState = queue
        .map { items ->
            DownloadBadgeState(
                activeCount = items.count {
                    it.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                        it.status == com.juan.snapmusic.core.model.QueueStatus.PENDING
                },
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DownloadBadgeState(),
        )

    val activeDownloadCount = downloadBadgeState
        .map { badge -> badge.activeCount }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    val previewState = combine(
        _selectedPreview,
        graph.historyRepository.observeLatest().map { latest -> latest?.toPreviewState() },
    ) { selected, latest ->
        selected ?: latest ?: PreviewState()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PreviewState(),
    )

    private val previewReadyState = previewState
        .map { preview -> preview.isReady }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val previewPerformanceState = previewState
        .map { preview ->
            PreviewPerformanceUiState(
                isReady = preview.isReady,
                isVideo = preview.fileUri?.let(::isPreviewVideoUri) ?: false,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewPerformanceUiState(),
        )

    val youtubeChrome = youtubeState
        .map { state ->
            YouTubeChromeState(
                showPlayer = state.showPlayer,
                showMiniPlayer = state.showMiniPlayer,
                featured = state.featured,
                compactMiniPlayer = state.compactMiniPlayer,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubeChromeState(),
        )

    val previewChrome = combine(
        previewState,
        _previewDetailVisible,
        _previewMiniPlayerVisible,
    ) { preview, detailVisible, miniVisible ->
        PreviewChromeState(
            preview = preview,
            detailVisible = detailVisible,
            miniVisible = miniVisible,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewChromeState(),
        )

    val previewRouteVisibility = combine(
        previewReadyState,
        _previewDetailVisible,
        _previewMiniPlayerVisible,
    ) { isReady, detailVisible, miniVisible ->
        PreviewRouteVisibilityState(
            detailVisible = detailVisible,
            miniVisible = miniVisible,
            isReady = isReady,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewRouteVisibilityState(),
        )

    val previewScreen = combine(
        previewState,
        previewLibrary,
        _previewDetailVisible,
    ) { preview, library, detailVisible ->
        PreviewScreenState(
            preview = preview,
            library = library,
            detailVisible = detailVisible,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewScreenState(),
        )

    val previewLibraryScreen = previewLibrary
        .map { library ->
            PreviewLibraryUiState(items = library)
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewLibraryUiState(),
        )

    val previewDetailScreen = combine(
        previewState,
        previewLibrary,
        _previewDetailVisible,
    ) { preview, library, detailVisible ->
        val currentIndex = preview.fileUri?.let { currentUri ->
            library.indexOfFirst { it.contentUri == currentUri }
        } ?: -1
        PreviewDetailUiState(
            preview = preview,
            detailVisible = detailVisible,
            canGoPrevious = currentIndex > 0,
            canGoNext = currentIndex in 0 until library.lastIndex,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewDetailUiState(),
        )

    val previewDownloadsState = queue
        .map { items ->
            PreviewDownloadsState(
                activeItems = items.filter {
                    it.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                        it.status == com.juan.snapmusic.core.model.QueueStatus.PENDING
                },
                completedCount = items.count { it.status == com.juan.snapmusic.core.model.QueueStatus.SUCCESS },
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewDownloadsState(),
        )

    val previewActiveDownloadCount = previewDownloadsState
        .map { it.activeItems.size }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    val previewCompletedDownloadsCount = previewDownloadsState
        .map { it.completedCount }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )

    val previewDownloadsShellState = combine(
        previewDownloadsState,
        _previewDownloadsRequestId,
    ) { downloadsState, openRequestId ->
        PreviewDownloadsShellState(
            hasActiveDownloads = downloadsState.activeItems.isNotEmpty(),
            completedCount = downloadsState.completedCount,
            openRequestId = openRequestId,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewDownloadsShellState(),
        )

    val homeSearch = _downloadSearchState
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

    val downloadSearchSuggestions = _downloadSearchState
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

    val homeSearchSuggestions = downloadSearchSuggestions
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

    val youtubeScreen = youtubeState
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

    val youtubeRouteVisibility = youtubeState
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

    val homeYouTubeTabsVisible = youtubeRouteVisibility
        .map { visibility -> !visibility.showPlayer }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true,
        )

    val youtubePictureInPictureEligibility = youtubeState
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

    val previewPictureInPictureEligibility = combine(
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

    val youtubePlayerMountEnabled = combine(
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

    val previewPlayerMountEnabled = combine(
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

    private val youtubeFeedProjection = youtubeState
        .map { state ->
            YouTubeFeedProjection(
                query = state.query,
                isLoading = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                showPlayer = state.showPlayer,
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

    val youtubeFeedScreen = youtubeFeedProjection
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
                canLoadMore = if (state.showPlayer) {
                    state.canLoadMoreWatchNext
                } else {
                    state.query.isBlank() && state.nextCursor != null || state.query.isNotBlank() && state.hasMoreSearchResults
                },
                errorMessage = state.errorMessage,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubeFeedState(),
        )

    val youtubeSuggestionsScreen = youtubeFeedProjection
        .map { state ->
            YouTubeSuggestionsUiState(
                isPlayerVisible = state.showPlayer,
                items = if (state.showPlayer) {
                    state.watchNextItems
                } else {
                    state.items
                },
                isRefreshing = state.isLoading,
                isLoadingMore = state.isLoadingMore,
                canLoadMore = if (state.showPlayer) {
                    state.canLoadMoreWatchNext
                } else {
                    state.query.isBlank() && state.nextCursor != null || state.query.isNotBlank() && state.hasMoreSearchResults
                },
                errorMessage = state.errorMessage,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubeSuggestionsUiState(),
        )

    private val searchSuggestionCorpus = combine(
        youtubeState.map { it.items }.distinctUntilChanged(),
        _downloadSearchState.map { it.popularQueries }.distinctUntilChanged(),
    ) { items, popularQueries ->
        buildSearchSuggestionCorpus(
            popularQueries = popularQueries,
            items = items,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val youtubeWatchNextScreen = youtubeState
        .map { state ->
            YouTubeWatchNextUiState(
                visible = state.showPlayer && state.featured.isReady,
                commentText = state.featured.description
                    ?.takeIf { it.isNotBlank() }
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?: YOUTUBE_WATCH_COMMENT_FALLBACK,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubeWatchNextUiState(),
        )

    val youtubePlaybackPanel = youtubeState
        .map { state ->
            YouTubePlaybackPanelState(
                showPlayer = state.showPlayer,
                isRefreshingVideo = state.isRefreshingVideo,
                featured = state.featured,
                autoplayEnabled = state.autoplayEnabled,
                nextUpTitle = state.nextUpItem?.title,
                openDownloadSheet = state.openDownloadSheet,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubePlaybackPanelState(),
        )

    val youtubeMiniPlayerState = youtubeState
        .map { state ->
            YouTubeMiniPlayerState(
                visible = state.showMiniPlayer && state.featured.sourceUrl.isNotBlank(),
                featured = state.featured,
                compact = state.compactMiniPlayer,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubeMiniPlayerState(),
        )

    val previewMiniPlayerState = combine(
        previewState,
        _previewMiniPlayerVisible,
    ) { preview, miniVisible ->
        PreviewMiniPlayerState(
            visible = miniVisible && preview.isReady,
            preview = preview,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewMiniPlayerState(),
        )

    val previewRestoreState = combine(
        previewReadyState,
        _previewDetailVisible,
        _previewMiniPlayerVisible,
    ) { isReady, detailVisible, miniVisible ->
        PreviewRestoreState(canRestore = isReady && (miniVisible || detailVisible))
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewRestoreState(),
        )

    val youtubePictureInPictureState = youtubeState
        .map { state -> YouTubePictureInPictureState(featured = state.featured) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubePictureInPictureState(),
        )

    val previewPictureInPictureState = previewState
        .map { preview -> PreviewPictureInPictureState(preview = preview) }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewPictureInPictureState(),
        )

    val youtubePlayerSessionState = youtubeState
        .map { state ->
            YouTubePlayerSessionState(
                featured = state.featured,
                preloadedNextFeatured = state.preloadedNextFeatured,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubePlayerSessionState(),
        )

    val youtubePlayerSeekState = youtubeState
        .map { state ->
            YouTubePlayerSeekState(
                requestId = state.playbackSeekRequestId,
                positionMs = state.currentPositionMs,
            )
        }
        .distinctUntilChangedBy(YouTubePlayerSeekState::requestId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = YouTubePlayerSeekState(),
        )

    val youtubePlaybackAutoPlay = youtubeState
        .map { state -> state.shouldAutoPlayCurrent }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    val appPictureInPictureConfig = combine(
        youtubePictureInPictureEligibility,
        previewPictureInPictureEligibility,
        youtubePlaybackAutoPlay,
    ) { youtubePiP, previewPiP, shouldAutoPlay ->
        AppPictureInPictureConfigState(
            eligible = youtubePiP.eligible || previewPiP.eligible,
            shouldAutoPlay = shouldAutoPlay,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppPictureInPictureConfigState(),
        )

    val navHostPlaybackState = combine(
        youtubeRouteVisibility,
        previewRouteVisibility,
        previewRestoreState,
        youtubePictureInPictureEligibility,
        previewPictureInPictureEligibility,
    ) { youtubeVisibility, previewVisibility, previewRestore, youtubePiP, previewPiP ->
        NavHostPlaybackState(
            youtubeCanRestore = youtubeVisibility.hasActiveItem &&
                (youtubeVisibility.showMiniPlayer || youtubeVisibility.showPlayer),
            previewCanRestore = previewRestore.canRestore,
            youtubeShowPlayer = youtubeVisibility.showPlayer,
            youtubeReady = youtubeVisibility.isReady,
            previewDetailVisible = previewVisibility.detailVisible,
            previewReady = previewVisibility.isReady,
            youtubePipEligible = youtubePiP.eligible,
            previewPipEligible = previewPiP.eligible,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NavHostPlaybackState(),
        )

    val bottomBarUiState = combine(
        youtubeRouteVisibility,
        previewRestoreState,
        downloadBadgeState,
    ) { youtubeVisibility, previewRestore, badge ->
        BottomBarUiState(
            youtubeCanRestore = youtubeVisibility.hasActiveItem &&
                (youtubeVisibility.showMiniPlayer || youtubeVisibility.showPlayer),
            previewCanRestore = previewRestore.canRestore,
            activeDownloadCount = badge.activeCount,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BottomBarUiState(),
        )

    val previewPlaybackRenderState = combine(
        previewState,
        _previewAutoPlayRequestId,
        _previewLibrary,
        _previewResumePositionMs,
    ) { preview, autoPlayRequestId, previewLibrary, resumePositionMs ->
        PreviewPlaybackRenderState(
            preview = preview,
            autoPlayRequestId = autoPlayRequestId,
            playlist = buildPreviewPlaybackQueue(
                preview = preview,
                library = previewLibrary,
            ),
            resumePositionMs = resumePositionMs,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PreviewPlaybackRenderState(),
        )

    val previewActiveFileUri = previewState
        .map { it.fileUri.orEmpty() }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = "",
        )

    init {
        viewModelScope.launch {
            graph.preferencesRepository.preferences.collect { prefs ->
                val current = _youtubeState.value
                if (current.autoplayEnabled != prefs.youtubeAutoplayEnabled) {
                    _youtubeState.value = current.copy(autoplayEnabled = prefs.youtubeAutoplayEnabled)
                }
            }
        }
        viewModelScope.launch {
            youtubeState.collect { state ->
                val queueItems = state.playbackQueue.ifEmpty { state.items }
                val hasActivePlayback =
                    state.featured.isReady &&
                        state.featured.sourceUrl.isNotBlank() &&
                        (state.showPlayer || state.showMiniPlayer)
                if (!hasActivePlayback || queueItems.isEmpty()) {
                    PlaybackSessionStateStore.updateYouTubeTransport(
                        hasPrevious = false,
                        hasNext = false,
                    )
                } else {
                    val currentIndex = resolveCurrentQueueIndex(state, queueItems)
                    PlaybackSessionStateStore.updateYouTubeTransport(
                        hasPrevious = previousQueueIndex(
                            queueSize = queueItems.size,
                            currentIndex = currentIndex,
                            currentPositionMs = state.currentPositionMs,
                        ) != null,
                        hasNext = nextQueueIndex(
                            queueSize = queueItems.size,
                            currentIndex = currentIndex,
                            continuationMode = state.continuationMode,
                        ) != null,
                    )
                }
            }
        }
    }

    private fun ensureQueueObservationStarted() {
        if (queueObservationStarted) return
        queueObservationStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            graph.queueRepository.observeQueue().collectLatest { items ->
                _queue.value = items
            }
        }
        if (!interruptedDownloadRestoreStarted) {
            interruptedDownloadRestoreStarted = true
            viewModelScope.launch(Dispatchers.IO) {
                graph.queueRepository.restoreInterruptedDownloads()
            }
        }
    }

    private fun ensureHistoryObservationStarted() {
        if (historyObservationStarted) return
        historyObservationStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            graph.historyRepository.observeHistory().collectLatest { items ->
                _history.value = items
            }
        }
    }

    fun selectHomeTab(index: Int) {
        val normalizedIndex = index.coerceIn(0, 2)
        _homeSelectedTab.value = normalizedIndex
        when (normalizedIndex) {
            HOME_TAB_YOUTUBE_INDEX -> onHomeYouTubeTabOpened()
            HOME_TAB_CONVERT_INDEX -> ensurePopularDownloadSearchesLoaded()
        }
    }

    fun selectHomeSearchTab() {
        selectHomeTab(0)
    }

    fun selectHomeYouTubeTab() {
        if (_downloadSearchState.value.query.isBlank() && _youtubeState.value.query.isNotBlank()) {
            restoreYoutubeHomeFeedAfterSearch()
        }
        selectHomeTab(1)
    }

    fun selectHomeConvertTab() {
        selectHomeTab(HOME_TAB_CONVERT_INDEX)
    }

    fun openDownloadSearchOverlay() {
        val current = _downloadSearchState.value
        _downloadSearchState.value = current.copy(isOverlayVisible = true)
        if (current.query.isNotBlank()) {
            scheduleDownloadSearchSuggestions(current.query)
        } else if (current.popularQueries.isEmpty()) {
            refreshPopularDownloadSearches()
        }
    }

    fun closeDownloadSearchOverlay() {
        val current = _downloadSearchState.value
        if (!current.isOverlayVisible) return
        _downloadSearchState.value = current.copy(isOverlayVisible = false)
    }

    fun onDownloadSearchQueryChange(query: String) {
        _downloadSearchState.value = _downloadSearchState.value.copy(query = query)
        scheduleDownloadSearchSuggestions(query)
    }

    fun clearDownloadSearchQuery() {
        downloadSearchSuggestionJob?.cancel()
        val popularQueries = _downloadSearchState.value.popularQueries
        val shouldRestoreHomeFeed = _youtubeState.value.query.isNotBlank()
        _downloadSearchState.value = _downloadSearchState.value.copy(
            query = "",
            suggestions = emptyList(),
            isLoadingSuggestions = false,
            popularQueries = popularQueries,
        )
        if (shouldRestoreHomeFeed) {
            restoreYoutubeHomeFeedAfterSearch()
        }
        if (popularQueries.isEmpty()) {
            refreshPopularDownloadSearches()
        }
    }

    fun submitDownloadSearch() {
        val query = _downloadSearchState.value.query.trim()
        if (query.isBlank()) return
        downloadSearchSuggestionJob?.cancel()
        _downloadSearchState.value = _downloadSearchState.value.copy(
            isOverlayVisible = false,
            isLoadingSuggestions = false,
            suggestions = emptyList(),
        )
        _youtubeState.value = _youtubeState.value.copy(query = query)
        selectHomeYouTubeTab()
        searchYoutube()
    }

    fun selectDownloadSearchSuggestion(value: String) {
        val query = value.trim()
        if (query.isBlank()) return
        _downloadSearchState.value = _downloadSearchState.value.copy(query = query)
        submitDownloadSearch()
    }

    fun selectPopularDownloadSearch(value: String) {
        selectDownloadSearchSuggestion(value)
    }

    fun onUrlChange(value: String) {
        _homeState.value = _homeState.value.copy(
            url = value,
            errorMessage = null,
            resolvedMedia = null,
            autoOpenFormats = false,
        )
    }

    fun analyze(openFormatsOnSuccess: Boolean = false) {
        val validation = validateYouTubeUrl(_homeState.value.url)
        if (validation.normalizedUrl == null) {
            _homeState.value = _homeState.value.copy(errorMessage = validation.message, autoOpenFormats = false)
            return
        }
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isAnalyzing = true, errorMessage = null, autoOpenFormats = false)
            runCatching { graph.resolverRepository.resolve(validation.normalizedUrl) }
                .onSuccess { media ->
                    _homeState.value = _homeState.value.copy(
                        isAnalyzing = false,
                        resolvedMedia = media,
                        url = validation.normalizedUrl,
                        autoOpenFormats = openFormatsOnSuccess,
                    )
                }
                .onFailure { error ->
                    _homeState.value = _homeState.value.copy(
                        isAnalyzing = false,
                        errorMessage = userFacingError(error.message, UiFailureKind.EXTRACTION),
                        autoOpenFormats = false,
                    )
                }
        }
    }

    fun consumeAutoOpenFormats() {
        _homeState.value = _homeState.value.copy(autoOpenFormats = false)
    }

    fun applyIncomingSharePayload(payload: IncomingSharePayload) {
        selectHomeConvertTab()
        when {
            payload.items.isEmpty() -> {
                _incomingShareSelectionState.value = IncomingShareSelectionState()
                _homeState.value = _homeState.value.copy(
                    errorMessage = "No encontramos links compatibles en lo que compartiste.",
                )
            }
            payload.items.size == 1 -> {
                _incomingShareSelectionState.value = IncomingShareSelectionState()
                applyIncomingSharedUrl(payload.items.first().url)
            }
            else -> {
                _incomingShareSelectionState.value = IncomingShareSelectionState(
                    visible = true,
                    items = payload.items,
                )
            }
        }
    }

    fun dismissIncomingShareSelection() {
        if (!_incomingShareSelectionState.value.visible) return
        _incomingShareSelectionState.value = IncomingShareSelectionState()
    }

    fun selectIncomingShareItem(item: IncomingShareItem) {
        _incomingShareSelectionState.value = IncomingShareSelectionState()
        applyIncomingSharedUrl(item.url)
    }

    fun applyIncomingSharedUrl(rawUrl: String) {
        val validation = validateYouTubeUrl(rawUrl)
        if (validation.normalizedUrl == null) {
            _homeState.value = _homeState.value.copy(errorMessage = validation.message)
            return
        }
        selectHomeConvertTab()
        _homeState.value = _homeState.value.copy(
            url = validation.normalizedUrl,
            errorMessage = null,
            resolvedMedia = null,
            autoOpenFormats = false,
        )
        analyze(openFormatsOnSuccess = true)
    }

    fun inspectClipboardCandidate(rawValue: String?) {
        val candidate = rawValue
            ?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { validateYouTubeUrl(it).normalizedUrl != null }
            ?.let { validateYouTubeUrl(it).normalizedUrl }

        _homeState.value = _homeState.value.copy(
            clipboardCandidateUrl = candidate?.takeIf { it != _homeState.value.url },
        )
    }

    fun useClipboardCandidate(analyzeImmediately: Boolean) {
        val candidate = _homeState.value.clipboardCandidateUrl ?: return
        _homeState.value = _homeState.value.copy(
            url = candidate,
            clipboardCandidateUrl = null,
            errorMessage = null,
            resolvedMedia = null,
            autoOpenFormats = false,
        )
        if (analyzeImmediately) {
            analyze()
        }
    }

    fun enqueueHomePresetMp3320(): Boolean = enqueueHomePreset(PRESET_MP3_320)

    fun enqueueHomePresetM4a(): Boolean = enqueueHomePreset(PRESET_M4A)

    fun enqueueHomePresetMp4720(): Boolean = enqueueHomePreset(PRESET_MP4_720)

    fun enqueueVariant(variantId: String) {
        enqueueFromMedia(
            media = _homeState.value.resolvedMedia,
            variantId = variantId,
            onUnsupported = {
                _homeState.value = _homeState.value.copy(
                    errorMessage = "Esa opción todavía no está lista. Por ahora usá M4A o MP4 directo.",
                )
            },
        )
    }

    fun enqueueYoutubeVariant(variantId: String) {
        currentYouTubeQueueItem()?.let { recordPlaybackSignal(it, MusicSignalType.DOWNLOAD) }
        enqueueFromMedia(
            media = _youtubeDownloadSheet.value.media ?: _youtubeState.value.featured.resolvedMedia,
            variantId = variantId,
            onUnsupported = {
                _youtubeState.value = _youtubeState.value.copy(
                    errorMessage = "Ese formato todavía no está listo. Elegí una opción directa para seguir.",
                )
            },
        )
        dismissYouTubeDownloadSheet()
    }

    fun cancelQueue(id: String) {
        graph.downloadCoordinator.cancelByQueueId(id)
    }

    fun removeQueueItem(id: String) {
        viewModelScope.launch {
            graph.queueRepository.remove(id)
        }
    }

    fun deleteDownloadedItem(item: QueueEntry) {
        ensureHistoryObservationStarted()
        val outputUri = item.outputUri ?: return
        viewModelScope.launch {
            val deleted = graph.storageRepository.deleteOutput(outputUri)
            if (deleted) {
                graph.storageRepository.invalidateLocalMediaCache()
                graph.historyRepository.remove(item.id)
                graph.queueRepository.remove(item.id)
                if (_selectedPreview.value?.fileUri == outputUri) {
                    _selectedPreview.value = null
                }
                _previewLibrary.value = _previewLibrary.value.filterNot { local -> local.contentUri == outputUri }
                _queueFeedback.value = "Se eliminó del dispositivo."
            } else {
                _queueFeedback.value = "No pudimos borrar ese archivo."
            }
        }
    }

    fun deleteLocalMediaItem(item: LocalMediaItem) {
        viewModelScope.launch {
            val deleted = graph.storageRepository.deleteOutput(item.contentUri)
            if (deleted) {
                graph.storageRepository.invalidateLocalMediaCache()
                if (_selectedPreview.value?.fileUri == item.contentUri) {
                    _selectedPreview.value = PreviewState()
                    _previewDetailVisible.value = false
                    _previewMiniPlayerVisible.value = false
                    _previewPlaybackQueueOverride.value = emptyList()
                }
                refreshLocalPreviewLibrary(forceRefresh = true)
                _queueFeedback.value = "Se eliminó del dispositivo."
            } else {
                _queueFeedback.value = "No pudimos eliminar ese archivo."
            }
        }
    }

    fun deleteLocalMediaItems(items: List<LocalMediaItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            var deletedCount = 0
            items.forEach { item ->
                val deleted = graph.storageRepository.deleteOutput(item.contentUri)
                if (deleted) {
                    deletedCount += 1
                    if (_selectedPreview.value?.fileUri == item.contentUri) {
                        _selectedPreview.value = PreviewState()
                        _previewDetailVisible.value = false
                        _previewMiniPlayerVisible.value = false
                        _previewPlaybackQueueOverride.value = emptyList()
                    }
                }
            }
            if (deletedCount > 0) {
                graph.storageRepository.invalidateLocalMediaCache()
                refreshLocalPreviewLibrary(forceRefresh = true)
                _queueFeedback.value = if (deletedCount == 1) {
                    "Se eliminó 1 archivo."
                } else {
                    "Se eliminaron $deletedCount archivos."
                }
            } else {
                _queueFeedback.value = "No pudimos eliminar esos archivos."
            }
        }
    }

    fun renameLocalMediaItem(
        item: LocalMediaItem,
        newTitle: String,
    ) {
        val sanitized = newTitle.trim()
        if (sanitized.isBlank()) {
            _queueFeedback.value = "Escribí un nombre válido."
            return
        }
        viewModelScope.launch {
            val renamed = graph.storageRepository.renameLocalMedia(
                uriString = item.contentUri,
                currentFileName = item.fileName,
                requestedTitle = sanitized,
            )
            if (renamed) {
                graph.storageRepository.invalidateLocalMediaCache()
                refreshLocalPreviewLibrary(forceRefresh = true)
                if (_selectedPreview.value?.fileUri == item.contentUri) {
                    _selectedPreview.value = _selectedPreview.value?.copy(title = sanitized)
                }
                _queueFeedback.value = "Nombre actualizado."
            } else {
                _queueFeedback.value = "No pudimos renombrar ese archivo."
            }
        }
    }

    fun queuePreviewItemNext(item: LocalMediaItem) {
        val currentPreview = previewState.value
        if (!currentPreview.isReady || currentPreview.fileUri.isNullOrBlank()) {
            openPreviewFromDevice(item)
            _queueFeedback.value = "Abrimos ese archivo en Reproducir."
            return
        }
        val queue = buildPreviewPlaybackQueue(currentPreview, _previewLibrary.value).toMutableList()
        val currentFileUri = currentPreview.fileUri ?: return
        val candidate = item.toPreviewPlaybackQueueItem()
        val filtered = queue.filterNot { it.fileUri == candidate.fileUri }.toMutableList()
        val currentIndex = filtered.indexOfFirst { it.fileUri == currentFileUri }.let { if (it >= 0) it else 0 }
        val insertIndex = (currentIndex + 1).coerceAtMost(filtered.size)
        filtered.add(insertIndex, candidate)
        _previewPlaybackQueueOverride.value = filtered
        persistCurrentPreviewSnapshot()
        _queueFeedback.value = "Lo usamos como siguiente en cola."
    }

    fun retryQueueItem(id: String) {
        viewModelScope.launch {
            val source = graph.queueRepository.get(id) ?: run {
                _queueFeedback.value = "No encontramos esa descarga para reintentarla."
                return@launch
            }
            enqueueRequest(source.toRetryRequest(), allowDuplicate = true)
            _queueFeedback.value = "Volvimos a poner esa descarga en la cola."
        }
    }

    fun consumeQueueFeedback() {
        _queueFeedback.value = null
    }

    fun requestOpenPreviewDownloads() {
        ensureQueueObservationStarted()
        _previewDownloadsRequestId.value = _previewDownloadsRequestId.value + 1L
    }

    fun cancelActiveDownloads() {
        ensureQueueObservationStarted()
        queue.value
            .filter {
                it.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                    it.status == com.juan.snapmusic.core.model.QueueStatus.PENDING
            }
            .forEach { item ->
                graph.downloadCoordinator.cancelByQueueId(item.id)
            }
    }

    fun onYoutubeQueryChange(value: String) {
        _youtubeState.value = _youtubeState.value.copy(query = value)
        scheduleYouTubeSuggestions(value)
    }

    fun searchYoutubeSuggestion(query: String) {
        _youtubeState.value = _youtubeState.value.copy(query = query)
        clearYouTubeSuggestions()
        searchYoutube()
    }

    fun clearYoutubeQuery() {
        _youtubeState.value = _youtubeState.value.copy(query = "")
        clearYouTubeSuggestions()
    }

    fun ensureYoutubeLoaded() {
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

    fun refreshYoutubeByPull() {
        val state = _youtubeState.value
        if (state.isLoading || state.isLoadingMore) return
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

    fun enterYouTubeFeed() {
        val current = _youtubeState.value
        _youtubeState.value = current.copy(
            showPlayer = false,
            showMiniPlayer = current.featured.isReady && current.playbackQueue.isNotEmpty(),
            errorMessage = null,
        )
        persistCurrentYouTubeSnapshot()
    }

    fun dismissYouTubePlayer() {
        watchNextEnrichmentJob?.cancel()
        nextQueuePreResolveJob?.cancel()
        lastExpiredStreamRetrySourceUrl = null
        val current = _youtubeState.value
        _youtubeState.value = current.copy(
            showPlayer = false,
            showMiniPlayer = false,
            featured = YouTubeFeaturedVideo(),
            watchNextItems = emptyList(),
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

    fun minimizeYouTubePlayer() {
        val current = _youtubeState.value
        if (!current.featured.isReady) return
        _youtubeState.value = current.copy(
            showPlayer = false,
            showMiniPlayer = true,
            compactMiniPlayer = false,
            errorMessage = null,
        )
        persistCurrentYouTubeSnapshot()
    }

    fun restoreYouTubePlayer() {
        val current = _youtubeState.value
        if (!current.featured.isReady) {
            val queueItems = current.playbackQueue.ifEmpty { current.items }
            if (queueItems.isEmpty()) return
            _youtubeState.value = current.copy(
                showPlayer = true,
                showMiniPlayer = false,
                compactMiniPlayer = false,
                errorMessage = null,
            )
            playYouTubeQueueItem(resolveCurrentQueueIndex(current, queueItems), userInitiated = false)
            return
        }
        _youtubeState.value = current.copy(showPlayer = true, showMiniPlayer = false, errorMessage = null)
        persistCurrentYouTubeSnapshot()
    }

    fun restoreYouTubePlaybackShell() {
        closeTransientHomePlaybackLayers()
        selectHomeTab(1)
        restoreYouTubePlayer()
    }

    fun toggleYouTubeMiniPlayerMode() {
        val current = _youtubeState.value
        if (!current.showMiniPlayer || !current.featured.isReady) return
        _youtubeState.value = current.copy(compactMiniPlayer = !current.compactMiniPlayer)
        persistCurrentYouTubeSnapshot()
    }

    fun refreshYoutubeHome(silent: Boolean = false) {
        viewModelScope.launch {
            watchNextEnrichmentJob?.cancel()
            if (!silent) {
                youTubeFeedSessionSeed = System.currentTimeMillis()
            }
            val shouldShowLoader = _youtubeState.value.items.isEmpty() && !silent
            _youtubeState.value = _youtubeState.value.copy(
                isLoading = shouldShowLoader,
                isLoadingMore = false,
                watchNextItems = emptyList(),
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

    fun searchYoutube() {
        val query = _youtubeState.value.query.trim()
        watchNextEnrichmentJob?.cancel()
        clearYouTubeSuggestions()
        if (query.isBlank()) {
            refreshYoutubeHome()
            return
        }
        viewModelScope.launch {
            _youtubeState.value = _youtubeState.value.copy(
                isLoading = true,
                isLoadingMore = false,
                watchNextItems = emptyList(),
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

    fun loadMoreYoutubeSuggestions() {
        val current = _youtubeState.value
        if (current.isLoading || current.isLoadingMore) return
        when {
            current.showPlayer && current.featured.isReady && current.canLoadMoreWatchNext -> loadMoreWatchNextQueue()
            current.query.isBlank() && current.nextCursor != null -> loadMoreYoutubeHome()
            current.query.isNotBlank() && current.hasMoreSearchResults -> loadMoreYoutubeSearchResults()
        }
    }

    private fun loadMoreYoutubeHome() {
        val current = _youtubeState.value
        val cursor = current.nextCursor ?: return
        _youtubeState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            runCatching {
                graph.musicHomeFeedRepository.loadMusicHomeFeed(
                    sessionSeed = youTubeFeedSessionSeed,
                    cursor = cursor,
                    limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
                )
            }
                .onSuccess { state ->
                    val latest = _youtubeState.value
                    val merged = (latest.items + state.items).distinctBy(YouTubeFeedItem::url)
                    _youtubeState.value = latest.copy(
                        items = merged,
                        isLoadingMore = false,
                        nextCursor = state.nextCursor,
                    )
                    startupPrefetchDone = false
                    prefetchFeedItems(state.items)
                }
                .onFailure {
                    _youtubeState.value = _youtubeState.value.copy(isLoadingMore = false)
                }
        }
    }

    private fun loadMoreYoutubeSearchResults() {
        val current = _youtubeState.value
        val query = current.query.trim()
        val cursor = current.nextCursor
        if (query.isBlank() || cursor.isNullOrBlank()) return
        _youtubeState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            runCatching {
                graph.resolverRepository.searchVideosPage(
                    query = query,
                    limit = YOUTUBE_HOME_FEED_PAGE_SIZE,
                    cursor = cursor,
                )
            }
                .onSuccess { page ->
                    val latest = _youtubeState.value
                    val merged = (latest.items + page.items).distinctBy(YouTubeFeedItem::url)
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

    fun applyYoutubePreset(query: String) {
        _youtubeState.value = _youtubeState.value.copy(query = query)
        clearYouTubeSuggestions()
        searchYoutube()
    }

    private fun scheduleYouTubeSuggestions(rawQuery: String) {
        val query = rawQuery.trim()
        youtubeSuggestionJob?.cancel()
        if (query.isBlank()) {
            clearYouTubeSuggestions()
            return
        }
        youtubeSuggestionJob = viewModelScope.launch {
            delay(180)
            _youtubeSearchSuggestionsLoading.value = true
            val remoteSuggestions = runCatching {
                graph.resolverRepository.searchSuggestions(query)
            }.getOrDefault(emptyList())
            val fallbackSuggestions = buildFallbackSearchSuggestions(query)
            val merged = buildList {
                add(query)
                addAll(remoteSuggestions)
                addAll(fallbackSuggestions)
            }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(12)
            if (_youtubeState.value.query.trim() == query) {
                _youtubeSearchSuggestions.value = merged
            }
            _youtubeSearchSuggestionsLoading.value = false
        }
    }

    private fun scheduleDownloadSearchSuggestions(rawQuery: String) {
        val query = rawQuery.trim()
        downloadSearchSuggestionJob?.cancel()
        if (query.isBlank()) {
            _downloadSearchState.value = _downloadSearchState.value.copy(
                suggestions = emptyList(),
                isLoadingSuggestions = false,
            )
            if (_downloadSearchState.value.popularQueries.isEmpty()) {
                refreshPopularDownloadSearches()
            }
            return
        }
        downloadSearchSuggestionJob = viewModelScope.launch {
            delay(160)
            _downloadSearchState.value = _downloadSearchState.value.copy(isLoadingSuggestions = true)
            val remoteSuggestions = runCatching { graph.resolverRepository.searchSuggestions(query) }.getOrDefault(emptyList())
            val fallbackSuggestions = filterSuggestionCorpus(query, searchSuggestionCorpus.value, 8)
            val merged = buildList {
                add(query)
                addAll(remoteSuggestions)
                addAll(fallbackSuggestions)
            }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(12)
            if (_downloadSearchState.value.query.trim() == query) {
                _downloadSearchState.value = _downloadSearchState.value.copy(
                    suggestions = merged,
                    isLoadingSuggestions = false,
                )
            } else {
                _downloadSearchState.value = _downloadSearchState.value.copy(isLoadingSuggestions = false)
            }
        }
    }

    private fun ensurePopularDownloadSearchesLoaded() {
        if (_downloadSearchState.value.popularQueries.isNotEmpty()) {
            hasLoadedPopularDownloadQueries = true
            return
        }
        refreshPopularDownloadSearches()
    }

    private fun refreshPopularDownloadSearches(force: Boolean = false) {
        if (!force) {
            if (_downloadSearchState.value.popularQueries.isNotEmpty()) {
                hasLoadedPopularDownloadQueries = true
                return
            }
            if (hasLoadedPopularDownloadQueries || popularDownloadSearchesJob?.isActive == true) {
                return
            }
        }
        popularDownloadSearchesJob?.cancel()
        popularDownloadSearchesJob = viewModelScope.launch(Dispatchers.IO) {
            val popular = runCatching { graph.musicHomeFeedRepository.loadPopularMusicQueries(limit = 8) }.getOrDefault(defaultPopularDownloadQueries())
            _downloadSearchState.value = _downloadSearchState.value.copy(popularQueries = popular)
            hasLoadedPopularDownloadQueries = true
        }
    }

    private fun clearYouTubeSuggestions() {
        youtubeSuggestionJob?.cancel()
        _youtubeSearchSuggestionsLoading.value = false
        _youtubeSearchSuggestions.value = emptyList()
    }

    private fun defaultPopularDownloadQueries(): List<String> = listOf(
        "María Becerra",
        "Jere Klein",
        "Callejero Fino",
        "Khea",
        "Cuarteto en vivo",
        "Enganchados",
        "Cumbia 2026",
        "Q' Lokura",
    )

    private fun buildFallbackSearchSuggestions(query: String): List<String> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        return filterSuggestionCorpus(normalized, searchSuggestionCorpus.value, 8)
    }

    private fun filterSuggestionCorpus(
        query: String,
        corpus: List<String>,
        limit: Int,
    ): List<String> {
        return corpus.asSequence()
            .filter { value -> value.contains(query, ignoreCase = true) }
            .take(limit)
            .toList()
    }

    fun selectYouTubeItem(item: YouTubeFeedItem) {
        val current = _youtubeState.value
        maybeRecordFastSkip(current, item)
        if (current.featured.sourceUrl == item.url && current.featured.resolvedMedia != null) {
            _youtubeState.value = current.copy(
                showPlayer = true,
                showMiniPlayer = false,
                shouldAutoPlayCurrent = true,
                errorMessage = null,
            )
            persistCurrentYouTubeSnapshot()
            return
        }
        val queueItems = when {
            current.showPlayer -> current.playbackQueue.ifEmpty { current.items }
            current.items.any { it.url == item.url } -> current.items
            else -> listOf(item)
        }
        val startIndex = queueItems.indexOfFirst { it.url == item.url }.takeIf { it >= 0 } ?: 0
        setYouTubeQueue(
            items = queueItems,
            startIndex = startIndex,
            sourceLabel = if (current.query.isBlank()) YouTubeQueueOrigin.HOME_FEED else YouTubeQueueOrigin.SEARCH_RESULTS,
        )
        enrichWatchNextQueue(item)
    }

    fun prepareYouTubeDownload(item: YouTubeFeedItem) {
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

    fun setYouTubeQueue(
        items: List<YouTubeFeedItem>,
        startIndex: Int,
        sourceLabel: YouTubeQueueOrigin,
    ) {
        if (items.isEmpty()) return
        watchNextEnrichmentJob?.cancel()
        val normalizedIndex = startIndex.coerceIn(0, items.lastIndex)
        val current = _youtubeState.value
        val target = items[normalizedIndex]
        val seededWatchNextItems = initialWatchNextItems(items, normalizedIndex)
        if (
            current.playbackQueue.map(YouTubeFeedItem::url) == items.map(YouTubeFeedItem::url) &&
            current.currentQueueIndex == normalizedIndex &&
            current.featured.sourceUrl == target.url &&
            current.featured.isReady
        ) {
            _youtubeState.value = current.copy(
                showPlayer = true,
                showMiniPlayer = false,
                watchNextItems = seededWatchNextItems,
                canLoadMoreWatchNext = true,
                shouldAutoPlayCurrent = true,
                errorMessage = null,
            )
            persistCurrentYouTubeSnapshot()
            return
        }
        _youtubeState.value = current.copy(
            playbackQueue = items,
            watchNextItems = seededWatchNextItems,
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

    fun playYouTubeQueueItem(
        index: Int,
        userInitiated: Boolean,
    ) {
        watchNextEnrichmentJob?.cancel()
        val current = _youtubeState.value
        val queueItems = current.playbackQueue.ifEmpty { current.items }
        if (queueItems.isEmpty()) return
        val normalizedIndex = index.coerceIn(0, queueItems.lastIndex)
        val target = queueItems[normalizedIndex]
        val keepMiniPlayer = current.showMiniPlayer && !current.showPlayer
        val seededWatchNextItems = initialWatchNextItems(queueItems, normalizedIndex)
        if (current.featured.sourceUrl == target.url && current.featured.isReady) {
            _youtubeState.value = current.copy(
                playbackQueue = queueItems,
                watchNextItems = seededWatchNextItems,
                currentQueueIndex = normalizedIndex,
                nextUpItem = if (current.autoplayEnabled) {
                    nextQueueItem(queueItems, normalizedIndex, current.continuationMode)
                } else {
                    null
                },
                preloadedNextFeatured = nextQueueItem(queueItems, normalizedIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
                currentPositionMs = 0L,
                playbackSeekRequestId = nextYouTubePlaybackSeekRequestId(current),
                isRefreshingVideo = false,
                pendingTransition = false,
                showPlayer = !keepMiniPlayer,
                showMiniPlayer = keepMiniPlayer,
                shouldAutoPlayCurrent = userInitiated,
                errorMessage = null,
            )
            recordPlaybackSignal(target, MusicSignalType.REPLAY)
            persistCurrentYouTubeSnapshot()
            preResolveNextQueueItem(queueItems, normalizedIndex, current.continuationMode)
            return
        }

        _youtubeState.value = current.copy(
            playbackQueue = queueItems,
            watchNextItems = seededWatchNextItems,
            currentQueueIndex = normalizedIndex,
            isRefreshingVideo = true,
            pendingTransition = true,
            featured = target.toLoadingFeaturedVideo(),
            showPlayer = !keepMiniPlayer,
            showMiniPlayer = keepMiniPlayer,
            currentPositionMs = 0L,
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
                    val latest = _youtubeState.value
                    youTubePlaybackMilestones[target.url] = mutableSetOf()
                    _youtubeState.value = latest.copy(
                        featured = featured,
                        isRefreshingVideo = false,
                        pendingTransition = false,
                        showPlayer = !keepMiniPlayer,
                        showMiniPlayer = keepMiniPlayer,
                        watchNextItems = seededWatchNextItems,
                        currentQueueIndex = normalizedIndex,
                        nextUpItem = if (latest.autoplayEnabled) {
                            nextQueueItem(queueItems, normalizedIndex, latest.continuationMode)
                        } else {
                            null
                        },
                        preloadedNextFeatured = nextQueueItem(queueItems, normalizedIndex, latest.continuationMode)?.let { youTubeResolveCache[it.url] },
                        currentPositionMs = 0L,
                        playbackSeekRequestId = latest.playbackSeekRequestId,
                        shouldAutoPlayCurrent = userInitiated,
                        errorMessage = null,
                    )
                    recordPlaybackSignal(target, MusicSignalType.PLAY_START)
                    persistCurrentYouTubeSnapshot()
                    preResolveNextQueueItem(queueItems, normalizedIndex, latest.continuationMode)
                }
                .onFailure { error ->
                    handleYouTubePlaybackFailure(
                        currentIndex = normalizedIndex,
                        rawMessage = error.message,
                    )
                }
        }
    }

    fun playNextYouTubeItem(reason: YouTubeAdvanceReason = YouTubeAdvanceReason.USER_NEXT) {
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

    fun playPreviousYouTubeItem() {
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

    fun toggleYouTubePlayPause() {
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

    fun toggleYouTubeAutoplay() {
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
            persistCurrentYouTubeSnapshot()
        }
    }

    fun switchYouTubePlaybackQuality(variantId: String) {
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
            actualVideoHeight = if (variantId == "auto") playbackSelection.expectedHeight else null,
            actualPlaybackLabel = if (variantId == "auto") {
                playbackSelection.expectedHeight?.let { "Automático · ${it}P" } ?: preferredAutomaticPlaybackLabel(resolved)
            } else {
                null
            },
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

    fun syncYouTubePlaybackTracks(
        availableHeights: List<Int>,
        height: Int?,
    ) {
        val current = _youtubeState.value
        if (!current.featured.isReady) return
        val actualLabel = when {
            current.featured.selectedVideoQualityId == "auto" && height != null && height > 0 -> "Automático · ${height}P"
            height != null && height > 0 -> watchPlaybackQualityLabel(height)
            current.featured.selectedVideoQualityId == "auto" -> preferredAutomaticPlaybackLabel(current.featured.resolvedMedia)
            else -> null
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

    fun requestYouTubeDownloadSheet() {
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

    fun consumeYouTubeDownloadSheet() {
        dismissYouTubeDownloadSheet()
    }

    fun dismissYouTubeDownloadSheet() {
        if (!_youtubeDownloadSheet.value.visible && !_youtubeDownloadSheet.value.isPreparing) return
        _youtubeDownloadSheet.value = YouTubeDownloadSheetState()
    }

    fun syncYouTubePlaybackProgress(
        positionMs: Long,
        playWhenReady: Boolean,
        persist: Boolean = false,
    ) {
        val current = _youtubeState.value
        if (!current.featured.isReady) return
        val safePosition = positionMs.coerceAtLeast(0L)
        val shouldAutoPlay = playWhenReady
        val shouldCheckpoint = kotlin.math.abs(current.currentPositionMs - safePosition) >= 10_000L
        val shouldUpdateState =
            current.shouldAutoPlayCurrent != shouldAutoPlay ||
                persist ||
                shouldCheckpoint
        if (shouldUpdateState) {
            _youtubeState.value = current.copy(
                currentPositionMs = safePosition,
                shouldAutoPlayCurrent = shouldAutoPlay,
            )
        }
        if (
            safePosition >= YOUTUBE_NEXT_PRE_RESOLVE_MIN_POSITION_MS &&
            current.autoplayEnabled &&
            current.preloadedNextFeatured == null &&
            nextQueuePreResolveJob?.isActive != true
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
        maybeRecordPlaybackMilestones(current.featured, safePosition)
        if (persist || shouldCheckpoint) {
            persistCurrentYouTubeSnapshot()
        }
    }

    fun restoreYouTubePlaybackSnapshot() {
        youTubePlaybackSnapshotRestoreStarted = true
        viewModelScope.launch {
            val snapshot = graph.preferencesRepository.readYouTubePlaybackSnapshot() ?: return@launch
            if (snapshot.queue.isEmpty()) return@launch
            val currentItem = snapshot.queue.getOrNull(snapshot.currentQueueIndex) ?: return@launch
            lastFailureFallbackSourceUrl = null
            lastExpiredStreamRetrySourceUrl = null
            val restoredState = _youtubeState.value
            _youtubeState.value = restoredState.copy(
                query = snapshot.query,
                isLoading = false,
                isLoadingMore = false,
                isRefreshingVideo = false,
                items = snapshot.queue,
                nextCursor = null,
                hasMoreSearchResults = false,
                watchNextItems = initialWatchNextItems(snapshot.queue, snapshot.currentQueueIndex),
                playbackQueue = snapshot.queue,
                currentQueueIndex = snapshot.currentQueueIndex,
                autoplayEnabled = snapshot.autoplayEnabled,
                continuationMode = snapshot.continuationMode,
                featured = currentItem.toLoadingFeaturedVideo(),
                showPlayer = false,
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

    fun clearYouTubePlaybackSnapshot() {
        viewModelScope.launch {
            graph.preferencesRepository.clearYouTubePlaybackSnapshot()
        }
    }

    fun onYouTubePlaybackEnded() {
        currentYouTubeQueueItem()?.let { recordPlaybackSignal(it, MusicSignalType.PLAY_COMPLETE) }
        playNextYouTubeItem(YouTubeAdvanceReason.AUTO_ENDED)
    }

    fun onYouTubePlaybackError(rawMessage: String?) {
        onYouTubePlaybackError(rawMessage, shouldRetryExpiredStream = false)
    }

    fun onYouTubePlaybackError(
        rawMessage: String?,
        shouldRetryExpiredStream: Boolean,
    ) {
        if (shouldRetryExpiredStream && retryExpiredYouTubeStream()) return
        handleYouTubePlaybackFailure(
            currentIndex = resolveCurrentQueueIndex(_youtubeState.value),
            rawMessage = rawMessage,
        )
    }

    fun syncYouTubeMediaTransition(
        mediaId: String,
        positionMs: Long,
        playWhenReady: Boolean,
    ) {
        val current = _youtubeState.value
        if (mediaId != current.featured.sourceUrl) {
            lastExpiredStreamRetrySourceUrl = null
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
            watchNextItems = initialWatchNextItems(queueItems, nextIndex),
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

    private fun resolveCurrentQueueIndex(
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

    private fun initialWatchNextItems(
        queueItems: List<YouTubeFeedItem>,
        currentIndex: Int,
    ): List<YouTubeFeedItem> {
        if (queueItems.isEmpty()) return emptyList()
        val normalizedIndex = currentIndex.coerceIn(0, queueItems.lastIndex)
        return queueItems.drop((normalizedIndex + 1).coerceAtMost(queueItems.size))
    }

    private fun retryExpiredYouTubeStream(): Boolean {
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

    private fun handleYouTubePlaybackFailure(
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

    private fun enrichWatchNextQueue(item: YouTubeFeedItem) {
        watchNextEnrichmentJob?.cancel()
        watchNextEnrichmentJob = viewModelScope.launch {
            delay(YOUTUBE_WATCH_NEXT_ENRICH_DELAY_MS)
            val startupState = _youtubeState.value
            if (
                startupState.featured.sourceUrl != item.url ||
                !startupState.showPlayer ||
                startupState.isRefreshingVideo ||
                startupState.pendingTransition ||
                startupState.currentPositionMs < 10_000L
            ) {
                return@launch
            }
            val related = runCatching {
                graph.musicHomeFeedRepository.recommendWatchNext(
                    currentItem = item,
                    limit = YOUTUBE_WATCH_NEXT_PAGE_SIZE,
                )
            }.getOrDefault(emptyList())
            if (related.isEmpty()) return@launch
            val current = _youtubeState.value
            if (current.featured.sourceUrl != item.url) return@launch
            val existingQueue = current.playbackQueue.ifEmpty { current.items }.ifEmpty { listOf(item) }
            val currentIndex = resolveCurrentQueueIndex(current, existingQueue)
            val existingWatchNext = current.watchNextItems.ifEmpty { initialWatchNextItems(existingQueue, currentIndex) }
            val appendedRelated = related.filterNot { candidate ->
                candidate.url == item.url ||
                    existingQueue.any { queued -> queued.url == candidate.url } ||
                    existingWatchNext.any { queued -> queued.url == candidate.url }
            }
            if (appendedRelated.isEmpty()) {
                _youtubeState.value = current.copy(
                    watchNextItems = existingWatchNext,
                    canLoadMoreWatchNext = related.size >= YOUTUBE_WATCH_NEXT_PAGE_SIZE,
                )
                return@launch
            }
            val queueItems = existingQueue + appendedRelated
            val watchNextItems = existingWatchNext + appendedRelated
            _youtubeState.value = current.copy(
                playbackQueue = queueItems,
                watchNextItems = watchNextItems,
                nextUpItem = if (current.autoplayEnabled) {
                    nextQueueItem(queueItems, currentIndex, current.continuationMode)
                } else {
                    null
                },
                canLoadMoreWatchNext = related.size >= YOUTUBE_WATCH_NEXT_PAGE_SIZE,
                preloadedNextFeatured = nextQueueItem(queueItems, currentIndex, current.continuationMode)?.let { youTubeResolveCache[it.url] },
            )
            persistCurrentYouTubeSnapshot()
            preResolveNextQueueItem(queueItems, currentIndex, current.continuationMode)
        }
    }

    private fun loadMoreWatchNextQueue() {
        val current = _youtubeState.value
        val featuredItem = currentYouTubeQueueItem(current) ?: return
        val existingQueue = current.playbackQueue.ifEmpty { listOf(featuredItem) }
        val currentIndex = resolveCurrentQueueIndex(current, existingQueue)
        val existingWatchNext = current.watchNextItems.ifEmpty { initialWatchNextItems(existingQueue, currentIndex) }
        _youtubeState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            val currentRelatedCount = existingWatchNext.size
            val requestLimit = (currentRelatedCount + 8)
                .coerceAtLeast(YOUTUBE_WATCH_NEXT_PAGE_SIZE)
                .coerceAtMost(YOUTUBE_WATCH_NEXT_PAGE_SIZE + 12)
            runCatching {
                graph.musicHomeFeedRepository.recommendWatchNext(
                    currentItem = featuredItem,
                    limit = requestLimit,
                )
            }
                .onSuccess { related ->
                    val latest = _youtubeState.value
                    if (latest.featured.sourceUrl != featuredItem.url) return@onSuccess
                    val newRelated = related.filterNot { candidate ->
                        candidate.url == featuredItem.url ||
                            existingQueue.any { existing -> existing.url == candidate.url } ||
                            existingWatchNext.any { existing -> existing.url == candidate.url }
                    }
                    val updatedQueue = if (newRelated.isEmpty()) {
                        existingQueue
                    } else {
                        existingQueue + newRelated
                    }
                    val updatedWatchNext = if (newRelated.isEmpty()) {
                        existingWatchNext
                    } else {
                        existingWatchNext + newRelated
                    }
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
                        canLoadMoreWatchNext = newRelated.isNotEmpty() || related.size >= requestLimit,
                    )
                    if (newRelated.isNotEmpty()) {
                        startupPrefetchDone = false
                        prefetchFeedItems(newRelated)
                    }
                    persistCurrentYouTubeSnapshot()
                    preResolveNextQueueItem(updatedQueue, currentIndex, latest.continuationMode)
                }
                .onFailure {
                    _youtubeState.value = _youtubeState.value.copy(isLoadingMore = false, canLoadMoreWatchNext = false)
                }
        }
    }

    private fun maybeRecordFastSkip(
        current: YouTubeUiState,
        target: YouTubeFeedItem,
    ) {
        val currentItem = currentYouTubeQueueItem(current) ?: return
        if (currentItem.url == target.url) return
        if (current.currentPositionMs in 1L until 10_000L) {
            recordPlaybackSignal(currentItem, MusicSignalType.SKIP_FAST)
        }
    }

    private fun maybeRecordPlaybackMilestones(
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

    private fun currentYouTubeQueueItem(
        state: YouTubeUiState = _youtubeState.value,
    ): YouTubeFeedItem? {
        val queueItems = state.playbackQueue.ifEmpty { state.items }
        return queueItems.getOrNull(resolveCurrentQueueIndex(state, queueItems))
    }

    private fun hasDownloadVariants(media: ResolvedMedia?): Boolean {
        return media != null && (media.audioVariants.isNotEmpty() || media.videoVariants.isNotEmpty())
    }

    private fun resolveYouTubeDownloadSheet(
        item: YouTubeFeedItem,
        forceRefresh: Boolean,
    ) {
        _youtubeDownloadSheet.value = YouTubeDownloadSheetState(isPreparing = true)
        viewModelScope.launch {
            runCatching { resolveFeaturedVideo(item, forceRefresh = forceRefresh) }
                .onSuccess { featured ->
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
                    _youtubeDownloadSheet.value = YouTubeDownloadSheetState()
                    _queueFeedback.value = userFacingError(error.message, UiFailureKind.EXTRACTION)
                }
        }
    }

    private fun recordPlaybackSignal(
        item: YouTubeFeedItem,
        type: MusicSignalType,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { graph.musicHomeFeedRepository.recordPlaybackSignal(type, item) }
        }
    }

    private fun preResolveNextQueueItem(
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
        if (
            warmState.currentPositionMs < YOUTUBE_NEXT_PRE_RESOLVE_MIN_POSITION_MS ||
            warmState.isRefreshingVideo ||
            warmState.pendingTransition ||
            (!warmState.showPlayer && !warmState.showMiniPlayer)
        ) {
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

    private fun nextYouTubePlaybackSeekRequestId(state: YouTubeUiState): Long {
        return (state.playbackSeekRequestId + 1L).coerceAtLeast(1L)
    }

    private fun persistCurrentYouTubeSnapshot() {
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

    private suspend fun resolveFeaturedVideo(
        item: YouTubeFeedItem,
        forceRefresh: Boolean = false,
    ): YouTubeFeaturedVideo {
        if (!forceRefresh) {
            youTubeResolveCache[item.url]?.let { return it }
        }
        return item.toFeaturedVideo().also { featured ->
            if (featured.isReady) {
                youTubeResolveCache[item.url] = featured
            }
        }
    }

    fun openPreviewFromQueue(item: QueueEntry) {
        val outputUri = item.outputUri ?: return
        dismissYouTubePlayer()
        requestPreviewAutoplay()
        _previewCurrentPositionMs.value = 0L
        _previewResumePositionMs.value = 0L
        _previewPlaybackQueueOverride.value = listOf(
            PreviewPlaybackQueueItem(
                title = item.title,
                subtitle = item.variantLabel,
                thumbnailUrl = item.thumbnailUrl,
                fileUri = outputUri,
            ),
        )
        _selectedPreview.value = PreviewState(
            title = item.title,
            subtitle = item.variantLabel,
            thumbnailUrl = item.thumbnailUrl,
            fileUri = outputUri,
            isReady = true,
        )
        _previewDetailVisible.value = true
        _previewMiniPlayerVisible.value = false
        persistCurrentPreviewSnapshot()
    }

    fun openPreviewFromDevice(item: LocalMediaItem) {
        dismissYouTubePlayer()
        requestPreviewAutoplay()
        _previewCurrentPositionMs.value = 0L
        _previewResumePositionMs.value = 0L
        _previewPlaybackQueueOverride.value = _previewLibrary.value
            .ifEmpty { listOf(item) }
            .map(LocalMediaItem::toPreviewPlaybackQueueItem)
        _selectedPreview.value = PreviewState(
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
            fileUri = item.contentUri,
            isReady = true,
        )
        _previewDetailVisible.value = true
        _previewMiniPlayerVisible.value = false
        persistCurrentPreviewSnapshot()
    }

    fun playPreviousPreviewInLibrary() {
        openAdjacentPreviewInLibrary(-1)
    }

    fun playNextPreviewInLibrary() {
        openAdjacentPreviewInLibrary(1)
    }

    fun syncPreviewPlaybackItem(fileUri: String, positionMs: Long) {
        if (fileUri.isBlank() || previewState.value.fileUri == fileUri) return
        val nextItem = _previewLibrary.value.firstOrNull { it.contentUri == fileUri }?.toPreviewPlaybackQueueItem()
            ?: _previewPlaybackQueueOverride.value.firstOrNull { it.fileUri == fileUri }
            ?: return
        _previewCurrentPositionMs.value = positionMs.coerceAtLeast(0L)
        _previewResumePositionMs.value = positionMs.coerceAtLeast(0L)
        _selectedPreview.value = PreviewState(
            title = nextItem.title,
            subtitle = nextItem.subtitle,
            thumbnailUrl = nextItem.thumbnailUrl,
            fileUri = nextItem.fileUri,
            isReady = true,
        )
        persistCurrentPreviewSnapshot()
    }

    fun syncPreviewPlaybackProgress(
        positionMs: Long,
        playWhenReady: Boolean,
        persist: Boolean = false,
    ) {
        if (!previewState.value.isReady) return
        val safePosition = positionMs.coerceAtLeast(0L)
        val shouldCheckpoint = kotlin.math.abs(_previewCurrentPositionMs.value - safePosition) >= 10_000L
        _previewCurrentPositionMs.value = safePosition
        if (persist || !playWhenReady || shouldCheckpoint) {
            persistCurrentPreviewSnapshot(positionMs = safePosition)
        }
    }

    fun closePreviewDetail() {
        _previewDetailVisible.value = false
        _previewMiniPlayerVisible.value = previewState.value.isReady
        persistCurrentPreviewSnapshot()
    }

    fun dismissPreviewPlayer() {
        _previewDetailVisible.value = false
        _previewMiniPlayerVisible.value = false
        _previewCurrentPositionMs.value = 0L
        _previewResumePositionMs.value = 0L
        _previewPlaybackQueueOverride.value = emptyList()
        viewModelScope.launch {
            graph.preferencesRepository.clearPreviewPlaybackSnapshot()
        }
    }

    fun minimizePreviewPlayer() {
        if (!previewState.value.isReady) return
        _previewDetailVisible.value = false
        _previewMiniPlayerVisible.value = true
        persistCurrentPreviewSnapshot()
    }

    fun restorePreviewPlayer() {
        if (!previewState.value.isReady) return
        _previewDetailVisible.value = true
        _previewMiniPlayerVisible.value = false
        persistCurrentPreviewSnapshot()
    }

    fun restorePreviewPlaybackShell() {
        closeTransientHomePlaybackLayers()
        restorePreviewPlayer()
    }

    suspend fun resolvePlaybackNotificationTarget(): PlaybackNotificationTarget {
        when (PlaybackNotificationRouteStore.currentTarget()) {
            PlaybackNotificationRouteTarget.YOUTUBE -> return PlaybackNotificationTarget.YOUTUBE
            PlaybackNotificationRouteTarget.PREVIEW -> return PlaybackNotificationTarget.PREVIEW
            PlaybackNotificationRouteTarget.NONE -> Unit
        }
        val currentYouTube = _youtubeState.value
        if (currentYouTube.featured.isReady && (currentYouTube.showPlayer || currentYouTube.showMiniPlayer)) {
            return PlaybackNotificationTarget.YOUTUBE
        }
        if (previewState.value.isReady && (_previewDetailVisible.value || _previewMiniPlayerVisible.value)) {
            return PlaybackNotificationTarget.PREVIEW
        }
        if (graph.preferencesRepository.readYouTubePlaybackSnapshot() != null) {
            return PlaybackNotificationTarget.YOUTUBE
        }
        if (graph.preferencesRepository.readPreviewPlaybackSnapshot() != null) {
            return PlaybackNotificationTarget.PREVIEW
        }
        return PlaybackNotificationTarget.NONE
    }

    suspend fun restorePreviewPlaybackSnapshot(showDetail: Boolean = true): Boolean {
        val snapshot = graph.preferencesRepository.readPreviewPlaybackSnapshot() ?: return false
        applyPreviewPlaybackSnapshot(snapshot, showDetail)
        ensureLocalPreviewLibraryLoaded()
        return true
    }

    fun ensureLocalPreviewLibraryLoaded() {
        ensureHistoryObservationStarted()
        if (_previewLibrary.value.isEmpty()) {
            refreshLocalPreviewLibrary(forceRefresh = false)
        }
    }

    fun refreshLocalPreviewLibrary(forceRefresh: Boolean = false) {
        ensureHistoryObservationStarted()
        viewModelScope.launch(Dispatchers.IO) {
            val rawLibrary = graph.storageRepository.listLocalMedia(forceRefresh = forceRefresh)
            val historyByOutputUri = history.value.associateBy { normalizeMediaLookupKey(it.outputUri) }
            val historyByExpectedFileName = history.value
                .groupBy { normalizeMediaFileKey(expectedHistoryFileName(it.title, it.format.name.lowercase())) }
                .mapValues { (_, entries) -> entries.sortedByDescending { it.createdAt } }
            val historyByTitle = history.value
                .groupBy { normalizeMediaLookupKey(it.title) }
                .mapValues { (_, entries) -> entries.sortedByDescending { it.createdAt } }

            _previewLibrary.value = rawLibrary.map { item ->
                val outputUriMatch = historyByOutputUri[normalizeMediaLookupKey(item.contentUri)]
                val fileNameMatch = historyByExpectedFileName[normalizeMediaFileKey(item.fileName)]?.firstOrNull()
                val titleMatch = historyByTitle[normalizeMediaLookupKey(item.title)]?.firstOrNull()
                val historyMatch = outputUriMatch ?: fileNameMatch ?: titleMatch
                val preferHistoryThumbnail = outputUriMatch != null || fileNameMatch != null
                val resolvedThumbnail = when {
                    preferHistoryThumbnail && !historyMatch?.thumbnailUrl.isNullOrBlank() -> historyMatch.thumbnailUrl
                    item.thumbnailUrl.isLocalArtworkSource() -> item.thumbnailUrl
                    !historyMatch?.thumbnailUrl.isNullOrBlank() -> historyMatch.thumbnailUrl
                    else -> item.thumbnailUrl
                }
                item.copy(thumbnailUrl = resolvedThumbnail)
            }
        }
    }

    private fun openAdjacentPreviewInLibrary(step: Int) {
        val library = _previewLibrary.value
        if (library.isEmpty()) return
        val currentFileUri = previewState.value.fileUri
        val currentIndex = library.indexOfFirst { it.contentUri == currentFileUri }
        if (currentIndex == -1) return
        val targetIndex = (currentIndex + step).coerceIn(0, library.lastIndex)
        if (targetIndex == currentIndex) return
        openPreviewFromDevice(library[targetIndex])
    }

    private fun requestPreviewAutoplay() {
        _previewAutoPlayRequestId.value = _previewAutoPlayRequestId.value + 1L
    }

    private fun persistCurrentPreviewSnapshot(
        positionMs: Long = _previewCurrentPositionMs.value,
    ) {
        val snapshot = buildCurrentPreviewPlaybackSnapshot(positionMs) ?: return
        viewModelScope.launch {
            graph.preferencesRepository.savePreviewPlaybackSnapshot(snapshot)
        }
    }

    private fun buildCurrentPreviewPlaybackSnapshot(
        positionMs: Long,
    ): PreviewPlaybackSnapshot? {
        val preview = previewState.value.takeIf { it.isReady } ?: return null
        val queue = buildPreviewPlaybackQueue(preview = preview, library = _previewLibrary.value)
        if (queue.isEmpty()) return null
        val currentIndex = queue.indexOfFirst { it.fileUri == preview.fileUri }.coerceAtLeast(0)
        return PreviewPlaybackSnapshot(
            queue = queue,
            currentQueueIndex = currentIndex.coerceIn(0, queue.lastIndex),
            lastPositionMs = positionMs.coerceAtLeast(0L),
            showMiniPlayer = _previewMiniPlayerVisible.value && !_previewDetailVisible.value,
        )
    }

    private fun buildPreviewPlaybackQueue(
        preview: PreviewState,
        library: List<LocalMediaItem>,
    ): List<PreviewPlaybackQueueItem> {
        val currentFileUri = preview.fileUri ?: return emptyList()
        return if (_previewPlaybackQueueOverride.value.any { it.fileUri == currentFileUri }) {
            _previewPlaybackQueueOverride.value
        } else if (library.any { it.contentUri == currentFileUri }) {
            library.map(LocalMediaItem::toPreviewPlaybackQueueItem)
        } else {
            listOfNotNull(preview.toPreviewPlaybackQueueItem())
        }
    }

    private fun applyPreviewPlaybackSnapshot(
        snapshot: PreviewPlaybackSnapshot,
        showDetail: Boolean,
    ) {
        val currentItem = snapshot.queue.getOrNull(snapshot.currentQueueIndex) ?: return
        _previewCurrentPositionMs.value = snapshot.lastPositionMs.coerceAtLeast(0L)
        _previewResumePositionMs.value = snapshot.lastPositionMs.coerceAtLeast(0L)
        _previewPlaybackQueueOverride.value = snapshot.queue
        _selectedPreview.value = PreviewState(
            title = currentItem.title,
            subtitle = currentItem.subtitle,
            thumbnailUrl = currentItem.thumbnailUrl,
            fileUri = currentItem.fileUri,
            isReady = true,
        )
        _previewDetailVisible.value = showDetail
        _previewMiniPlayerVisible.value = !showDetail && snapshot.showMiniPlayer
    }

    private fun prefetchFeedItems(items: List<YouTubeFeedItem>) {
        if (startupPrefetchDone) return
        startupPrefetchDone = true
        youtubeFeedPrefetchJob?.cancel()
        val itemsToPrefetch = items.asSequence()
            .filterNot { youTubeResolveCache.containsKey(it.url) }
            .take(2)
            .toList()
        if (itemsToPrefetch.isEmpty()) return
        youtubeFeedPrefetchJob = viewModelScope.launch {
            itemsToPrefetch.forEach { item ->
                launch(Dispatchers.IO) {
                    runCatching { resolveFeaturedVideo(item) }
                }
            }
        }
    }

    private fun restoreYouTubeHomeFeedCache() {
        youTubeHomeFeedCacheRestoreStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            val cachedItems = graph.preferencesRepository.readYouTubeHomeFeedCache()
            if (cachedItems.isEmpty()) return@launch
            cachedYouTubeHomeFeed = cachedItems
            val current = _youtubeState.value
            if (current.items.isEmpty() && current.playbackQueue.isEmpty()) {
                _youtubeState.value = current.copy(items = cachedItems.take(YOUTUBE_HOME_CACHE_PRIME_COUNT))
                delay(3_000L)
                prefetchFeedItems(cachedItems)
            }
        }
    }

    private fun ensureYouTubeHomeFeedCacheRestored() {
        if (youTubeHomeFeedCacheRestoreStarted) return
        youTubeHomeFeedCacheRestoreStarted = true
        restoreYouTubeHomeFeedCache()
    }

    private fun ensureYouTubePlaybackSnapshotRestored() {
        if (youTubePlaybackSnapshotRestoreStarted) return
        youTubePlaybackSnapshotRestoreStarted = true
        restoreYouTubePlaybackSnapshot()
    }

    private fun onHomeYouTubeTabOpened() {
        hasOpenedYouTubeHomeTab = true
        ensureYouTubeHomeFeedCacheRestored()
        ensureYouTubePlaybackSnapshotRestored()
        primeYouTubeHomeFeedFromCacheIfNeeded()
        refreshYouTubeHomeFromCachePrimeIfNeeded()
    }

    private fun primeYouTubeHomeFeedFromCacheIfNeeded() {
        val cachedItems = cachedYouTubeHomeFeed
        if (cachedItems.isEmpty()) return
        val current = _youtubeState.value
        if (current.query.isNotBlank()) return
        if (current.playbackQueue.isNotEmpty()) return
        if (current.items.isNotEmpty()) return
        _youtubeState.value = current.copy(
            items = cachedItems.take(YOUTUBE_HOME_CACHE_PRIME_COUNT),
            isLoading = false,
            isLoadingMore = false,
            nextCursor = null,
            hasMoreSearchResults = false,
            errorMessage = null,
        )
    }

    private fun refreshYouTubeHomeFromCachePrimeIfNeeded() {
        val cachedItems = cachedYouTubeHomeFeed
        if (cachedItems.size <= YOUTUBE_HOME_CACHE_PRIME_COUNT) return
        val current = _youtubeState.value
        val primedItems = cachedItems.take(YOUTUBE_HOME_CACHE_PRIME_COUNT)
        if (current.items != primedItems) return
        if (current.isLoading || current.isLoadingMore) return
        viewModelScope.launch {
            delay(900L)
            val latest = _youtubeState.value
            if (_homeSelectedTab.value != HOME_TAB_YOUTUBE_INDEX) return@launch
            if (latest.items != primedItems) return@launch
            if (latest.query.isNotBlank()) return@launch
            refreshYoutubeHome(silent = true)
        }
    }

    private fun scheduleCachedYouTubePrefetchIfVisible() {
        cachedYouTubePrefetchJob?.cancel()
        if (!hasOpenedYouTubeHomeTab) return
        val cachedItems = cachedYouTubeHomeFeed
        val current = _youtubeState.value
        if (cachedItems.isEmpty()) return
        if (current.query.isNotBlank()) return
        if (current.playbackQueue.isNotEmpty()) return
        if (current.items != cachedItems) return
        cachedYouTubePrefetchJob = viewModelScope.launch {
            delay(700L)
            val latest = _youtubeState.value
            if (!hasOpenedYouTubeHomeTab) return@launch
            if (latest.query.isNotBlank()) return@launch
            if (latest.playbackQueue.isNotEmpty()) return@launch
            if (latest.items != cachedItems) return@launch
            prefetchFeedItems(cachedItems)
        }
    }

    private fun restoreYoutubeHomeFeedAfterSearch() {
        val current = _youtubeState.value
        val cachedItems = cachedYouTubeHomeFeed
        if (cachedItems.isNotEmpty()) {
            _youtubeState.value = current.copy(
                query = "",
                items = cachedItems,
                isLoading = false,
                isLoadingMore = false,
                nextCursor = null,
                hasMoreSearchResults = false,
                canLoadMoreWatchNext = current.canLoadMoreWatchNext,
                errorMessage = null,
            )
            startupPrefetchDone = false
            prefetchFeedItems(cachedItems)
        } else {
            _youtubeState.value = current.copy(query = "", hasMoreSearchResults = false, errorMessage = null)
            refreshYoutubeHome()
        }
    }

    private fun normalizeMediaLookupKey(value: String): String {
        return value
            .trim()
            .lowercase()
            .replace("\\s+".toRegex(), " ")
            .removePrefix("content://")
    }

    private fun normalizeMediaFileKey(value: String): String {
        val sanitized = android.net.Uri.decode(value)
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.', missingDelimiterValue = value)
            .replace("\\s+\\(\\d+\\)$".toRegex(), "")
        return normalizeMediaLookupKey(sanitized)
    }

    private fun expectedHistoryFileName(title: String, formatExtension: String): String {
        return com.juan.snapmusic.core.platform.sanitizeFileName(title) + ".${formatExtension.trimStart('.')}"
    }

    private fun String?.isLocalArtworkSource(): Boolean {
        val normalized = this.orEmpty().trim().lowercase()
        return normalized.startsWith("content://") ||
            normalized.startsWith("file://") ||
            normalized.startsWith("android.resource://")
    }

    fun savePickedFolder(treeUri: String, label: String) {
        viewModelScope.launch {
            graph.storageRepository.persistPermission(android.net.Uri.parse(treeUri))
            graph.storageRepository.setCustomTree(android.net.Uri.parse(treeUri), label)
        }
    }

    fun resetToDefaultFolder() {
        viewModelScope.launch {
            graph.storageRepository.setCustomTree(null, "Downloads/SnapMusic")
        }
    }

    fun updateAudioFormat(format: String) {
        viewModelScope.launch {
            val target = runCatching { com.juan.snapmusic.core.model.ContainerFormat.valueOf(format) }.getOrNull() ?: return@launch
            graph.preferencesRepository.updateAudioFormat(target)
        }
    }

    fun updateAudioQuality(value: String) {
        viewModelScope.launch { graph.preferencesRepository.updateAudioQuality(value) }
    }

    fun updateVideoQuality(value: String) {
        viewModelScope.launch { graph.preferencesRepository.updateVideoQuality(value) }
    }

    fun updatePreviewVolume(value: Float) {
        viewModelScope.launch { graph.preferencesRepository.updatePreviewVolume(value) }
    }

    fun updateDownloadTaskLimits(wifi: Int, mobile: Int) {
        viewModelScope.launch { graph.preferencesRepository.updateDownloadTaskLimits(wifi, mobile) }
    }

    fun updateDownloadSpeedLimitLabel(value: String) {
        viewModelScope.launch { graph.preferencesRepository.updateDownloadSpeedLimitLabel(value) }
    }

    fun updateAllowMobileDataDownloads(value: Boolean) {
        viewModelScope.launch { graph.preferencesRepository.updateAllowMobileDataDownloads(value) }
    }

    fun updateNotifyDownloadProgress(value: Boolean) {
        viewModelScope.launch { graph.preferencesRepository.updateNotifyDownloadProgress(value) }
    }

    fun updateNotifyDownloadCompleted(value: Boolean) {
        viewModelScope.launch { graph.preferencesRepository.updateNotifyDownloadCompleted(value) }
    }

    fun updateNotifyRecommendedContent(value: Boolean) {
        viewModelScope.launch { graph.preferencesRepository.updateNotifyRecommendedContent(value) }
    }

    fun updateNotifyToolUpdates(value: Boolean) {
        viewModelScope.launch { graph.preferencesRepository.updateNotifyToolUpdates(value) }
    }

    fun updateNotifyToolbarAccess(value: Boolean) {
        viewModelScope.launch { graph.preferencesRepository.updateNotifyToolbarAccess(value) }
    }

    fun updateThemeMode(value: AppThemeMode) {
        viewModelScope.launch { graph.preferencesRepository.updateThemeMode(value) }
    }

    private fun enqueueHomePreset(preset: String): Boolean {
        val media = _homeState.value.resolvedMedia ?: return false
        val variant = when (preset) {
            PRESET_MP3_320 -> media.audioVariants.closestAudioVariant(
                container = com.juan.snapmusic.core.model.ContainerFormat.MP3,
                targetBitrate = 320,
            )

            PRESET_M4A -> media.audioVariants.closestAudioVariant(
                container = com.juan.snapmusic.core.model.ContainerFormat.M4A,
                targetBitrate = media.audioVariants
                    .filter { it.container == com.juan.snapmusic.core.model.ContainerFormat.M4A }
                    .maxOfOrNull { it.bitrateKbps ?: 0 }
                    ?.takeIf { it > 0 },
            )

            PRESET_MP4_720 -> media.videoVariants.closestVideoVariant(720)

            else -> null
        } ?: run {
            _homeState.value = _homeState.value.copy(errorMessage = "No encontramos ese atajo para este video.")
            return false
        }

        val prefs = preferences.value
        enqueueRequest(
            ConversionRequest(
                sourceUrl = media.sourceUrl,
                title = media.title,
                author = media.author,
                thumbnailUrl = media.thumbnailUrl,
                selectedVariant = variant,
                destinationLabel = prefs.defaultDestinationLabel,
                destinationTreeUri = prefs.customTreeUri,
            ),
        )
        return true
    }

    private fun enqueueFromMedia(
        media: ResolvedMedia?,
        variantId: String,
        onUnsupported: () -> Unit,
    ) {
        val sourceMedia = media ?: return
        val variant = (sourceMedia.audioVariants + sourceMedia.videoVariants).firstOrNull { it.id == variantId } ?: return
        val prefs = preferences.value
        enqueueRequest(
            ConversionRequest(
                sourceUrl = sourceMedia.sourceUrl,
                title = sourceMedia.title,
                author = sourceMedia.author,
                thumbnailUrl = sourceMedia.thumbnailUrl,
                selectedVariant = variant,
                destinationLabel = prefs.defaultDestinationLabel,
                destinationTreeUri = prefs.customTreeUri,
            ),
        )
    }

    private fun enqueueRequest(
        request: ConversionRequest,
        allowDuplicate: Boolean = false,
    ): Boolean {
        viewModelScope.launch {
            ensureQueueObservationStarted()
            val queuedId = graph.downloadCoordinator.enqueue(request, allowDuplicate = allowDuplicate)
            if (queuedId == null) {
                _queueFeedback.value = "Ese formato ya existe en la cola o ya se descargó en esa carpeta."
            }
        }
        return true
    }

    private suspend fun YouTubeFeedItem.toFeaturedVideo(): YouTubeFeaturedVideo {
        val resolved = graph.resolverRepository.resolve(url)
        val playback = fallbackAutomaticPlaybackUrl(resolved)
        return YouTubeFeaturedVideo(
            sourceUrl = url,
            title = resolved.title,
            author = resolved.author,
            thumbnailUrl = resolved.thumbnailUrl,
            playbackUrl = playback,
            adaptivePlaybackUrl = resolved.adaptivePlaybackUrl,
            selectedVideoQualityId = "auto",
            durationSeconds = resolved.durationSeconds,
            publishedText = publishedText,
            description = description,
            resolvedMedia = resolved,
            isReady = playback != null,
        )
    }

    private fun YouTubeFeedItem.toLoadingFeaturedVideo(): YouTubeFeaturedVideo {
        return YouTubeFeaturedVideo(
            sourceUrl = url,
            title = title,
            author = author,
            thumbnailUrl = thumbnailUrl,
            selectedVideoQualityId = "auto",
            durationSeconds = durationSeconds,
            publishedText = publishedText,
            description = description,
            isReady = false,
        )
    }

    private fun isAdaptivePlaybackUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains(".mpd") ||
            lower.contains("manifest.googlevideo.com") ||
            lower.contains(".m3u8") ||
            lower.contains("/manifest/") ||
            lower.startsWith("https://manifest")
    }

    private fun fallbackAutomaticPlaybackUrl(resolved: com.juan.snapmusic.core.model.ResolvedMedia): String? {
        val playbackCandidates = resolved.videoVariants.filter { !it.directUrl.isNullOrBlank() }
        val fallbackVariant = resolveAutomaticPlaybackVariant(playbackCandidates)
            ?: return resolved.playbackUrl
            ?: playbackCandidates.firstOrNull { !it.requiresMux }?.directUrl
            ?: playbackCandidates.firstOrNull()?.directUrl
        return fallbackPlaybackUrl(fallbackVariant)
    }

    private data class PlaybackSelection(
        val playbackUrl: String,
        val expectedHeight: Int?,
    )

    private fun resolvePlaybackSelection(
        media: com.juan.snapmusic.core.model.ResolvedMedia,
        requestedVariantId: String,
    ): PlaybackSelection? {
        val playbackCandidates = media.videoVariants
            .filter { !it.directUrl.isNullOrBlank() }
            .sortedWith(
                compareByDescending<com.juan.snapmusic.core.model.MediaVariant> {
                    it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0
                }.thenByDescending { !it.requiresMux },
            )
        val requestedHeight = when {
            requestedVariantId == "auto" -> null
            requestedVariantId.startsWith("adaptive-") -> requestedVariantId.removePrefix("adaptive-").toIntOrNull()
            else -> playbackCandidates
                .firstOrNull { it.id == requestedVariantId }
                ?.resolution
                ?.substringBefore('p')
                ?.toIntOrNull()
        }

        if (requestedVariantId == "auto") {
            val automaticHeight = preferredAutomaticPlaybackHeight(media)
            return PlaybackSelection(
                playbackUrl = fallbackAutomaticPlaybackUrl(media) ?: return null,
                expectedHeight = automaticHeight,
            )
        }

        val chosenProgressive = resolveNearestPlaybackVariant(playbackCandidates, requestedHeight) ?: return null
        return PlaybackSelection(
            playbackUrl = fallbackPlaybackUrl(chosenProgressive) ?: return null,
            expectedHeight = chosenProgressive.resolution?.substringBefore('p')?.toIntOrNull(),
        )
    }

    private fun fallbackPlaybackUrl(
        variant: com.juan.snapmusic.core.model.MediaVariant,
    ): String? {
        if (variant.directUrl.isBlank()) return null
        if (!variant.requiresMux) return variant.directUrl
        val audioUrl = variant.secondaryUrl?.takeIf { it.isNotBlank() } ?: return null
        return MergedPlaybackUri.build(
            videoUrl = variant.directUrl,
            audioUrl = audioUrl,
        )
    }

    private fun preferredAutomaticPlaybackHeight(
        media: com.juan.snapmusic.core.model.ResolvedMedia?,
    ): Int? {
        val heights = media?.videoVariants
            ?.mapNotNull { it.resolution?.substringBefore('p')?.toIntOrNull() }
            ?.distinct()
            ?.sortedDescending()
            .orEmpty()
        return when {
            720 in heights -> 720
            1080 in heights -> 1080
            480 in heights -> 480
            360 in heights -> 360
            else -> heights.firstOrNull()
        }
    }

    private fun preferredAutomaticPlaybackLabel(
        media: com.juan.snapmusic.core.model.ResolvedMedia?,
    ): String? {
        return preferredAutomaticPlaybackHeight(media)?.let { "Automático · ${it}P" } ?: "Automático"
    }

    private fun resolveNearestPlaybackHeight(
        candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
        requestedHeight: Int?,
    ): Int? {
        return resolveNearestPlaybackVariant(candidates, requestedHeight)
            ?.resolution
            ?.substringBefore('p')
            ?.toIntOrNull()
    }

    private fun resolveNearestPlaybackVariant(
        candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
        requestedHeight: Int?,
    ): com.juan.snapmusic.core.model.MediaVariant? {
        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedWith(
            compareByDescending<com.juan.snapmusic.core.model.MediaVariant> {
                it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0
            }.thenBy { it.requiresMux },
        )
        if (requestedHeight == null) return sorted.firstOrNull()
        return sorted.firstOrNull { (it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0) <= requestedHeight }
            ?: sorted.minByOrNull { kotlin.math.abs((it.resolution?.substringBefore('p')?.toIntOrNull() ?: requestedHeight) - requestedHeight) }
    }

    private fun resolveAutomaticPlaybackVariant(
        candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
    ): com.juan.snapmusic.core.model.MediaVariant? {
        if (candidates.isEmpty()) return null
        val candidatesByHeight = candidates
            .mapNotNull { variant ->
                val height = variant.resolution?.substringBefore('p')?.toIntOrNull() ?: return@mapNotNull null
                height to variant
            }
            .groupBy({ it.first }, { it.second })
        listOf(720, 1080, 480, 360, 240, 144).forEach { preferredHeight ->
            candidatesByHeight[preferredHeight]
                ?.sortedBy { it.requiresMux }
                ?.firstOrNull()
                ?.let { return it }
        }
        return candidates.maxWithOrNull(
            compareBy<com.juan.snapmusic.core.model.MediaVariant> {
                it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0
            }.thenBy {
                !it.requiresMux
            },
        )
    }

    private fun watchPlaybackQualityLabel(height: Int): String = when {
        height >= 1080 -> "Muy alto · ${height}P HD"
        height >= 720 -> "Alta · ${height}P HD"
        height >= 480 -> "Media · ${height}P"
        else -> "Baja · ${height}P"
    }

    private fun closeTransientHomePlaybackLayers() {
        if (_incomingShareSelectionState.value.visible) {
            _incomingShareSelectionState.value = IncomingShareSelectionState()
        }
        val currentSearch = _downloadSearchState.value
        if (currentSearch.isOverlayVisible || currentSearch.isLoadingSuggestions) {
            _downloadSearchState.value = currentSearch.copy(
                isOverlayVisible = false,
                isLoadingSuggestions = false,
            )
        }
    }
}

private enum class UiFailureKind {
    NETWORK,
    EXTRACTION,
    TRANSCODE,
    STORAGE,
}

private fun userFacingError(
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

private fun com.juan.snapmusic.core.model.HistoryEntry.toPreviewState(): PreviewState {
    return PreviewState(
        title = title,
        subtitle = qualityLabel,
        thumbnailUrl = thumbnailUrl,
        fileUri = outputUri,
        isReady = true,
    )
}

private fun LocalMediaItem.toPreviewPlaybackQueueItem(): PreviewPlaybackQueueItem {
    return PreviewPlaybackQueueItem(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        fileUri = contentUri,
    )
}

private fun PreviewState.toPreviewPlaybackQueueItem(): PreviewPlaybackQueueItem? {
    val currentFileUri = fileUri ?: return null
    return PreviewPlaybackQueueItem(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        fileUri = currentFileUri,
    )
}

private fun QueueEntity.toRetryRequest(): ConversionRequest {
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

private fun List<MediaVariant>.closestAudioVariant(
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

private fun List<MediaVariant>.closestVideoVariant(
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

class SnapMusicViewModelFactory(
    private val graph: SnapMusicGraph,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SnapMusicViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SnapMusicViewModel(graph) as T
        }
        error("Factory no soportada: ${modelClass.name}")
    }
}
