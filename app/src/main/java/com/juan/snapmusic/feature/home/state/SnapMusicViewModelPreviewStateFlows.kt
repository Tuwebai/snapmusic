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

internal fun SnapMusicViewModel.createDownloadBadgeStateFlow() = queue
    .map { items ->
        DownloadBadgeState(
            activeCount = items.count {
                it.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                    it.status == com.juan.snapmusic.core.model.QueueStatus.PENDING ||
                    it.status == com.juan.snapmusic.core.model.QueueStatus.PAUSED
            },
        )
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DownloadBadgeState(),
    )

internal fun SnapMusicViewModel.createActiveDownloadCountFlow() = downloadBadgeState
    .map { badge -> badge.activeCount }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

internal fun SnapMusicViewModel.createPreviewStateFlow() = combine(
    _selectedPreview,
    graph.historyRepository.observeLatest().map { latest -> latest?.toPreviewState() },
) { selected, latest ->
    selected ?: latest ?: PreviewState()
}.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = PreviewState(),
)

internal fun SnapMusicViewModel.createPreviewReadyStateFlow() = previewState
    .map { preview -> preview.isReady }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

internal fun SnapMusicViewModel.createPreviewPerformanceStateFlow() = previewState
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

internal fun SnapMusicViewModel.createYoutubeChromeFlow() = youtubeState
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

internal fun SnapMusicViewModel.createPreviewChromeFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewRouteVisibilityFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewScreenFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewLibraryScreenFlow() = previewLibrary
    .map { library ->
        PreviewLibraryUiState(items = library)
    }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PreviewLibraryUiState(),
    )

internal fun SnapMusicViewModel.createPreviewDetailScreenFlow() = combine(
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

internal fun SnapMusicViewModel.createPreviewDownloadsStateFlow() = queue
    .map { items ->
        PreviewDownloadsState(
            activeItems = items.filter {
                it.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                    it.status == com.juan.snapmusic.core.model.QueueStatus.PENDING ||
                    it.status == com.juan.snapmusic.core.model.QueueStatus.PAUSED
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

internal fun SnapMusicViewModel.createPreviewActiveDownloadCountFlow() = previewDownloadsState
    .map { it.activeItems.size }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

internal fun SnapMusicViewModel.createPreviewCompletedDownloadsCountFlow() = previewDownloadsState
    .map { it.completedCount }
    .distinctUntilChanged()
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

internal fun SnapMusicViewModel.createPreviewDownloadsShellStateFlow() = combine(
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
