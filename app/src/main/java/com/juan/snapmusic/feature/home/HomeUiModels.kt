package com.juan.snapmusic.feature.home

import androidx.compose.runtime.Immutable
import com.juan.snapmusic.core.model.IncomingShareItem
import com.juan.snapmusic.core.model.LocalMediaItem
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.PreviewState
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo

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
data class YouTubeFeedProjection(
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
    val query: String = "",
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
    val isFullscreen: Boolean = false,
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
    val allowedKinds: Set<MediaKind> = setOf(MediaKind.AUDIO, MediaKind.VIDEO),
    val errorMessage: String? = null,
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
