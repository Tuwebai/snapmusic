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

fun SnapMusicViewModel.openPreviewFromQueue(item: QueueEntry) {
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
            isVideo = item.container == ContainerFormat.MP4 || outputUri.isPreviewVideoLikeUri(),
        ),
    )
    _selectedPreview.value = PreviewState(
        title = item.title,
        subtitle = item.variantLabel,
        thumbnailUrl = item.thumbnailUrl,
        fileUri = outputUri,
        isReady = true,
        isVideo = item.container == ContainerFormat.MP4 || outputUri.isPreviewVideoLikeUri(),
    )
    _previewDetailVisible.value = true
    _previewMiniPlayerVisible.value = false
    persistCurrentPreviewSnapshot()
}

fun SnapMusicViewModel.openPreviewFromDevice(item: LocalMediaItem) {
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
        isVideo = item.isVideo,
    )
    _previewDetailVisible.value = true
    _previewMiniPlayerVisible.value = false
    persistCurrentPreviewSnapshot()
}

fun SnapMusicViewModel.playPreviousPreviewInLibrary() {
    openAdjacentPreviewInLibrary(-1)
}

fun SnapMusicViewModel.playNextPreviewInLibrary() {
    openAdjacentPreviewInLibrary(1)
}

fun SnapMusicViewModel.syncPreviewPlaybackItem(fileUri: String, positionMs: Long) {
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
        isVideo = nextItem.isVideo,
    )
    persistCurrentPreviewSnapshot()
}

fun SnapMusicViewModel.syncPreviewPlaybackProgress(
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

fun SnapMusicViewModel.closePreviewDetail() {
    _previewDetailVisible.value = false
    _previewMiniPlayerVisible.value = previewState.value.isReady
    persistCurrentPreviewSnapshot()
}

fun SnapMusicViewModel.dismissPreviewPlayer() {
    _previewDetailVisible.value = false
    _previewMiniPlayerVisible.value = false
    _previewCurrentPositionMs.value = 0L
    _previewResumePositionMs.value = 0L
    _previewPlaybackQueueOverride.value = emptyList()
    viewModelScope.launch {
        graph.preferencesRepository.clearPreviewPlaybackSnapshot()
    }
}

fun SnapMusicViewModel.minimizePreviewPlayer() {
    if (!previewState.value.isReady) return
    _previewDetailVisible.value = false
    _previewMiniPlayerVisible.value = true
    persistCurrentPreviewSnapshot()
}

fun SnapMusicViewModel.restorePreviewPlayer() {
    if (!previewState.value.isReady) return
    _previewDetailVisible.value = true
    _previewMiniPlayerVisible.value = false
    persistCurrentPreviewSnapshot()
}

fun SnapMusicViewModel.restorePreviewPlaybackShell() {
    closeTransientHomePlaybackLayers()
    restorePreviewPlayer()
}

suspend fun SnapMusicViewModel.resolvePlaybackNotificationTarget(): PlaybackNotificationTarget {
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
    if (withContext(Dispatchers.IO) { graph.preferencesRepository.readYouTubePlaybackSnapshot() } != null) {
        return PlaybackNotificationTarget.YOUTUBE
    }
    if (withContext(Dispatchers.IO) { graph.preferencesRepository.readPreviewPlaybackSnapshot() } != null) {
        return PlaybackNotificationTarget.PREVIEW
    }
    return PlaybackNotificationTarget.NONE
}

suspend fun SnapMusicViewModel.restorePreviewPlaybackSnapshot(showDetail: Boolean = true): Boolean {
    val snapshot = graph.preferencesRepository.readPreviewPlaybackSnapshot() ?: return false
    applyPreviewPlaybackSnapshot(snapshot, showDetail)
    ensureLocalPreviewLibraryLoaded()
    return true
}

fun SnapMusicViewModel.ensureLocalPreviewLibraryLoaded() {
    ensureHistoryObservationStarted()
    if (_previewLibrary.value.isEmpty()) {
        refreshLocalPreviewLibrary(forceRefresh = false)
    }
}

fun SnapMusicViewModel.refreshLocalPreviewLibrary(forceRefresh: Boolean = false) {
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
            val exactHistoryMatch = outputUriMatch ?: fileNameMatch
            val titleMatch = if (exactHistoryMatch == null && !item.hasGenericLocalVideoTitle()) {
                historyByTitle[normalizeMediaLookupKey(item.title)]?.firstOrNull()
            } else {
                null
            }
            val historyMatch = exactHistoryMatch ?: titleMatch
            val preferHistoryThumbnail = exactHistoryMatch != null
            val resolvedThumbnail = when {
                preferHistoryThumbnail && !historyMatch?.thumbnailUrl.isNullOrBlank() -> historyMatch.thumbnailUrl
                item.thumbnailUrl.isLocalArtworkSource() -> item.thumbnailUrl
                !historyMatch?.thumbnailUrl.isNullOrBlank() -> historyMatch.thumbnailUrl
                else -> item.thumbnailUrl
            }
            item.copy(
                title = exactHistoryMatch?.title?.takeIf(String::isNotBlank)
                    ?: item.resolvedLocalMediaTitle(),
                subtitle = exactHistoryMatch?.toLocalMediaSubtitle(item.subtitle) ?: item.subtitle,
                thumbnailUrl = resolvedThumbnail,
            )
        }
    }
}

internal fun SnapMusicViewModel.openAdjacentPreviewInLibrary(step: Int) {
    val library = _previewLibrary.value
    if (library.isEmpty()) return
    val currentFileUri = previewState.value.fileUri
    val currentIndex = library.indexOfFirst { it.contentUri == currentFileUri }
    if (currentIndex == -1) return
    val targetIndex = (currentIndex + step).coerceIn(0, library.lastIndex)
    if (targetIndex == currentIndex) return
    openPreviewFromDevice(library[targetIndex])
}

internal fun SnapMusicViewModel.requestPreviewAutoplay() {
    _previewAutoPlayRequestId.value = _previewAutoPlayRequestId.value + 1L
}

internal fun SnapMusicViewModel.persistCurrentPreviewSnapshot(
    positionMs: Long = _previewCurrentPositionMs.value,
) {
    val snapshot = buildCurrentPreviewPlaybackSnapshot(positionMs) ?: return
    viewModelScope.launch {
        graph.preferencesRepository.savePreviewPlaybackSnapshot(snapshot)
    }
}

internal fun SnapMusicViewModel.buildCurrentPreviewPlaybackSnapshot(
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

internal fun SnapMusicViewModel.buildPreviewPlaybackQueue(
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

internal fun SnapMusicViewModel.applyPreviewPlaybackSnapshot(
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
        isVideo = currentItem.isVideo,
    )
    _previewDetailVisible.value = showDetail
    _previewMiniPlayerVisible.value = !showDetail && snapshot.showMiniPlayer
}

internal fun SnapMusicViewModel.prefetchFeedItems(items: List<YouTubeFeedItem>) {
    if (startupPrefetchDone) return
    val current = _youtubeState.value
    if (current.showPlayer || current.showMiniPlayer) return
    startupPrefetchDone = true
    youtubeFeedPrefetchJob?.cancel()
    val itemsToPrefetch = items.asSequence()
        .filterNot { youTubeResolveCache.containsKey(it.url) }
        .take(2)
        .toList()
    if (itemsToPrefetch.isEmpty()) return
    youtubeFeedPrefetchJob = viewModelScope.launch {
        delay(1_500L)
        itemsToPrefetch.forEach { item ->
            launch(Dispatchers.IO) {
                runCatching { resolveFeaturedVideo(item) }
            }
        }
    }
}
