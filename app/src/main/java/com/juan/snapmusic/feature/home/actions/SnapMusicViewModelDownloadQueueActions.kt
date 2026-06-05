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

internal fun SnapMusicViewModel.ensureQueueObservationStarted() {
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

internal fun SnapMusicViewModel.ensureHistoryObservationStarted() {
    if (historyObservationStarted) return
    historyObservationStarted = true
    viewModelScope.launch(Dispatchers.IO) {
        graph.historyRepository.observeHistory().collectLatest { items ->
            _history.value = items
        }
    }
}

fun SnapMusicViewModel.selectHomeTab(index: Int) {
    val normalizedIndex = index.coerceIn(0, HOME_TAB_YOUTUBE_INDEX)
    _homeSelectedTab.value = normalizedIndex
    when (normalizedIndex) {
        HOME_TAB_YOUTUBE_INDEX -> onHomeYouTubeTabOpened()
    }
}

fun SnapMusicViewModel.selectHomeSearchTab() {
    selectHomeTab(0)
}

fun SnapMusicViewModel.selectHomeYouTubeTab() {
    if (_downloadSearchState.value.query.isBlank() && _youtubeState.value.query.isNotBlank()) {
        restoreYoutubeHomeFeedAfterSearch()
    }
    selectHomeTab(1)
}

fun SnapMusicViewModel.openDownloadSearchOverlay() {
    val current = _downloadSearchState.value
    _downloadSearchState.value = current.copy(isOverlayVisible = true)
    if (current.query.isNotBlank()) {
        scheduleDownloadSearchSuggestions(current.query)
    } else if (current.popularQueries.isEmpty()) {
        refreshPopularDownloadSearches()
    }
}

fun SnapMusicViewModel.closeDownloadSearchOverlay() {
    val current = _downloadSearchState.value
    if (!current.isOverlayVisible) return
    _downloadSearchState.value = current.copy(isOverlayVisible = false)
}

fun SnapMusicViewModel.onDownloadSearchQueryChange(query: String) {
    _downloadSearchState.value = _downloadSearchState.value.copy(query = query)
    scheduleDownloadSearchSuggestions(query)
}

fun SnapMusicViewModel.clearDownloadSearchQuery() {
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

fun SnapMusicViewModel.submitDownloadSearch() {
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

fun SnapMusicViewModel.selectDownloadSearchSuggestion(value: String) {
    val query = value.trim()
    if (query.isBlank()) return
    _downloadSearchState.value = _downloadSearchState.value.copy(query = query)
    submitDownloadSearch()
}

fun SnapMusicViewModel.selectPopularDownloadSearch(value: String) {
    selectDownloadSearchSuggestion(value)
}

fun SnapMusicViewModel.applyIncomingSharePayload(payload: IncomingSharePayload) {
    when {
        payload.items.isEmpty() -> {
            _incomingShareSelectionState.value = IncomingShareSelectionState()
        }
        payload.items.size == 1 -> {
            _incomingShareSelectionState.value = IncomingShareSelectionState()
            applyIncomingSharedItem(payload.items.first())
        }
        else -> {
            _incomingShareSelectionState.value = IncomingShareSelectionState(
                visible = true,
                items = payload.items,
            )
        }
    }
}

fun SnapMusicViewModel.dismissIncomingShareSelection() {
    if (!_incomingShareSelectionState.value.visible) return
    _incomingShareSelectionState.value = IncomingShareSelectionState()
}

fun SnapMusicViewModel.selectIncomingShareItem(item: IncomingShareItem) {
    _incomingShareSelectionState.value = IncomingShareSelectionState()
    applyIncomingSharedItem(item)
}

internal fun SnapMusicViewModel.applyIncomingSharedItem(item: IncomingShareItem) {
    when (item.provider) {
        IncomingShareProvider.YOUTUBE -> applyIncomingSharedUrl(item.url)
        IncomingShareProvider.INSTAGRAM -> applyIncomingInstagramUrl(item.url)
    }
}

fun SnapMusicViewModel.applyIncomingSharedUrl(rawUrl: String) {
    val validation = validateYouTubeUrl(rawUrl)
    if (validation.normalizedUrl == null) {
        return
    }
    selectHomeYouTubeTab()
    _youtubeState.value = _youtubeState.value.copy(query = validation.normalizedUrl)
    searchYoutube()
}

internal fun SnapMusicViewModel.applyIncomingInstagramUrl(url: String) {
    selectHomeSearchTab()
    _youtubeDownloadSheet.value = YouTubeDownloadSheetState(
        media = pendingInstagramMedia(url),
        visible = true,
        allowedKinds = setOf(MediaKind.VIDEO),
    )
    viewModelScope.launch {
        runCatching { graph.resolverRepository.resolve(url) }
            .onSuccess { media ->
                val sheet = _youtubeDownloadSheet.value
                if (!sheet.visible || sheet.media?.sourceUrl != url) return@onSuccess
                if (media.videoVariants.isEmpty()) {
                    return@onSuccess
                } else {
                    _youtubeDownloadSheet.value = YouTubeDownloadSheetState(
                        media = media,
                        visible = true,
                        allowedKinds = setOf(MediaKind.VIDEO),
                    )
                }
            }
            .onFailure { error ->
                Log.w(YOUTUBE_PLAYBACK_LOG_TAG, instagramUserFacingError(error.message))
            }
    }
}

fun SnapMusicViewModel.enqueueYoutubeVariant(variantId: String) {
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

fun SnapMusicViewModel.cancelQueue(id: String) {
    graph.downloadCoordinator.cancelByQueueId(id)
}

fun SnapMusicViewModel.pauseQueue(id: String) {
    graph.downloadCoordinator.pauseByQueueId(id)
}

fun SnapMusicViewModel.resumeQueue(id: String) {
    graph.downloadCoordinator.resumeByQueueId(id)
}

fun SnapMusicViewModel.removeQueueItem(id: String) {
    viewModelScope.launch {
        graph.queueRepository.remove(id)
    }
}

fun SnapMusicViewModel.deleteDownloadedItem(item: QueueEntry) {
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

fun SnapMusicViewModel.deleteLocalMediaItem(item: LocalMediaItem) {
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

fun SnapMusicViewModel.deleteLocalMediaItems(items: List<LocalMediaItem>) {
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

fun SnapMusicViewModel.confirmLocalMediaDeleted(items: List<LocalMediaItem>) {
    if (items.isEmpty()) return
    val deletedUris = items.map(LocalMediaItem::contentUri).toSet()
    graph.storageRepository.invalidateLocalMediaCache()
    if (_selectedPreview.value?.fileUri in deletedUris) {
        _selectedPreview.value = PreviewState()
        _previewDetailVisible.value = false
        _previewMiniPlayerVisible.value = false
        _previewPlaybackQueueOverride.value = emptyList()
    }
    _previewLibrary.value = _previewLibrary.value.filterNot { it.contentUri in deletedUris }
    refreshLocalPreviewLibrary(forceRefresh = true)
    _queueFeedback.value = if (items.size == 1) {
        "Se eliminó del dispositivo."
    } else {
        "Se eliminaron ${items.size} archivos."
    }
}

fun SnapMusicViewModel.renameLocalMediaItem(
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

fun SnapMusicViewModel.queuePreviewItemNext(item: LocalMediaItem) {
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

fun SnapMusicViewModel.retryQueueItem(id: String) {
    viewModelScope.launch {
        val source = graph.queueRepository.get(id) ?: run {
            _queueFeedback.value = "No encontramos esa descarga para reintentarla."
            return@launch
        }
        enqueueRequest(source.toRetryRequest(), allowDuplicate = true)
        _queueFeedback.value = "Volvimos a poner esa descarga en la cola."
    }
}

fun SnapMusicViewModel.consumeQueueFeedback() {
    _queueFeedback.value = null
}

fun SnapMusicViewModel.requestOpenPreviewDownloads() {
    ensureQueueObservationStarted()
    _previewDownloadsRequestId.value = _previewDownloadsRequestId.value + 1L
}

fun SnapMusicViewModel.cancelActiveDownloads() {
    ensureQueueObservationStarted()
    queue.value
        .filter {
            it.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                it.status == com.juan.snapmusic.core.model.QueueStatus.PENDING ||
                it.status == com.juan.snapmusic.core.model.QueueStatus.PAUSED
        }
        .forEach { item ->
            graph.downloadCoordinator.cancelByQueueId(item.id)
        }
}
