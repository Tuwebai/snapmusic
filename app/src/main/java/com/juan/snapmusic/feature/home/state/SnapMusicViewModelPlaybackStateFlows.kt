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

internal fun SnapMusicViewModel.createYoutubeWatchNextScreenFlow() = youtubeState
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

internal fun SnapMusicViewModel.createYoutubePlaybackPanelFlow() = youtubeState
    .map { state ->
        YouTubePlaybackPanelState(
            showPlayer = state.showPlayer,
            isFullscreen = state.isFullscreen,
            isRefreshingVideo = state.isRefreshingVideo,
            featured = state.featured,
            watchNextItems = state.watchNextItems,
            canLoadMoreWatchNext = state.canLoadMoreWatchNext,
            isLoadingMoreWatchNext = state.isLoadingMore,
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

internal fun SnapMusicViewModel.createYoutubeMiniPlayerStateFlow() = youtubeState
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

internal fun SnapMusicViewModel.createPreviewMiniPlayerStateFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewRestoreStateFlow() = combine(
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

internal fun SnapMusicViewModel.createYoutubePictureInPictureStateFlow() = youtubeState
    .map { state -> YouTubePictureInPictureState(featured = state.featured) }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubePictureInPictureState(),
    )

internal fun SnapMusicViewModel.createPreviewPictureInPictureStateFlow() = previewState
    .map { preview -> PreviewPictureInPictureState(preview = preview) }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PreviewPictureInPictureState(),
    )

internal fun SnapMusicViewModel.createYoutubePlayerSessionStateFlow() = youtubeState
    .map { state ->
        YouTubePlayerSessionState(
            featured = state.featured,
            preloadedNextFeatured = state.preloadedNextFeatured,
            currentPositionMs = state.currentPositionMs,
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = YouTubePlayerSessionState(),
    )

internal fun SnapMusicViewModel.createYoutubePlayerSeekStateFlow() = youtubeState
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

internal fun SnapMusicViewModel.createYoutubePlaybackAutoPlayFlow() = youtubeState
    .map { state -> state.shouldAutoPlayCurrent }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

internal fun SnapMusicViewModel.createAppPictureInPictureConfigFlow() = combine(
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

internal fun SnapMusicViewModel.createNavHostPlaybackStateFlow() = combine(
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

internal fun SnapMusicViewModel.createBottomBarUiStateFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewPlaybackRenderStateFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewActiveFileUriFlow() = previewState
    .map { it.fileUri.orEmpty() }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = "",
    )
