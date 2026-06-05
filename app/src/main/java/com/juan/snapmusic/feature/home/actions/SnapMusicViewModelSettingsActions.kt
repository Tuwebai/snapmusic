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

internal fun SnapMusicViewModel.restoreYouTubeHomeFeedCache() {
    youTubeHomeFeedCacheRestoreStarted = true
    viewModelScope.launch(Dispatchers.IO) {
        val cachedItems = graph.preferencesRepository.readYouTubeHomeFeedCache()
        if (cachedItems.isEmpty()) return@launch
        cachedYouTubeHomeFeed = cachedItems
        val current = _youtubeState.value
        if (current.items.isEmpty() && current.playbackQueue.isEmpty()) {
            val primeItems = cachedItems.take(YOUTUBE_HOME_CACHE_PRIME_COUNT)
            _youtubeState.value = current.copy(
                items = primeItems,
                nextCursor = null,
                hasMoreSearchResults = false,
            )
            prepareHomeCursorFromCache(
                seededItems = primeItems,
                expectedItems = primeItems,
            )
            delay(3_000L)
            prefetchFeedItems(cachedItems)
        }
    }
}

internal fun SnapMusicViewModel.ensureYouTubeHomeFeedCacheRestored() {
    if (youTubeHomeFeedCacheRestoreStarted) return
    youTubeHomeFeedCacheRestoreStarted = true
    restoreYouTubeHomeFeedCache()
}

internal fun SnapMusicViewModel.ensureYouTubePlaybackSnapshotRestored() {
    if (youTubePlaybackSnapshotRestoreStarted) return
    youTubePlaybackSnapshotRestoreStarted = true
    restoreYouTubePlaybackSnapshot()
}

internal fun SnapMusicViewModel.onHomeYouTubeTabOpened() {
    hasOpenedYouTubeHomeTab = true
    ensureYouTubeHomeFeedCacheRestored()
    primeYouTubeHomeFeedFromCacheIfNeeded()
}

internal fun SnapMusicViewModel.primeYouTubeHomeFeedFromCacheIfNeeded() {
    val cachedItems = cachedYouTubeHomeFeed
    if (cachedItems.isEmpty()) return
    val current = _youtubeState.value
    if (current.query.isNotBlank()) return
    if (current.playbackQueue.isNotEmpty()) return
    if (current.items.isNotEmpty()) return
    val primeItems = cachedItems.take(YOUTUBE_HOME_CACHE_PRIME_COUNT)
    _youtubeState.value = current.copy(
        items = primeItems,
        isLoading = false,
        isLoadingMore = false,
        nextCursor = null,
        hasMoreSearchResults = false,
        errorMessage = null,
    )
    prepareHomeCursorFromCache(
        seededItems = primeItems,
        expectedItems = primeItems,
    )
}

internal fun SnapMusicViewModel.restoreYoutubeHomeFeedAfterSearch() {
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
        prepareHomeCursorFromCache(
            seededItems = cachedItems,
            expectedItems = cachedItems,
        )
        startupPrefetchDone = false
        prefetchFeedItems(cachedItems)
    } else {
        _youtubeState.value = current.copy(query = "", hasMoreSearchResults = false, errorMessage = null)
        refreshYoutubeHome()
    }
}

private fun SnapMusicViewModel.prepareHomeCursorFromCache(
    seededItems: List<YouTubeFeedItem>,
    expectedItems: List<YouTubeFeedItem>,
) {
    if (seededItems.isEmpty()) return
    val expectedUrls = expectedItems.map(YouTubeFeedItem::url)
    viewModelScope.launch(Dispatchers.IO) {
        val cursor = graph.musicHomeFeedRepository.startHomeFeedPagingSession(
            sessionSeed = youTubeFeedSessionSeed,
            seededItems = seededItems,
        )
        val latest = _youtubeState.value
        if (
            latest.query.isBlank() &&
            latest.playbackQueue.isEmpty() &&
            latest.items.map(YouTubeFeedItem::url) == expectedUrls
        ) {
            _youtubeState.value = latest.copy(nextCursor = cursor)
        }
    }
}

internal fun SnapMusicViewModel.normalizeMediaLookupKey(value: String): String {
    return value
        .trim()
        .lowercase()
        .replace("\\s+".toRegex(), " ")
        .removePrefix("content://")
}

internal fun SnapMusicViewModel.normalizeMediaFileKey(value: String): String {
    val sanitized = android.net.Uri.decode(value)
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .substringBeforeLast('.', missingDelimiterValue = value)
        .replace("\\s+\\(\\d+\\)$".toRegex(), "")
    return normalizeMediaLookupKey(sanitized)
}

internal fun SnapMusicViewModel.expectedHistoryFileName(title: String, formatExtension: String): String {
    return com.juan.snapmusic.core.platform.sanitizeFileName(title) + ".${formatExtension.trimStart('.')}"
}

internal fun String?.isLocalArtworkSource(): Boolean {
    val normalized = this.orEmpty().trim().lowercase()
    return normalized.startsWith("content://") ||
        normalized.startsWith("file://") ||
        normalized.startsWith("android.resource://")
}

fun SnapMusicViewModel.savePickedFolder(treeUri: String, label: String) {
    viewModelScope.launch {
        graph.storageRepository.persistPermission(android.net.Uri.parse(treeUri))
        graph.storageRepository.setCustomTree(android.net.Uri.parse(treeUri), label)
    }
}

fun SnapMusicViewModel.resetToDefaultFolder() {
    viewModelScope.launch {
        graph.storageRepository.setCustomTree(null, "Downloads/SnapMusic")
    }
}

fun SnapMusicViewModel.cleanManualCache() {
    if (_cacheCleanupState.value.isRunning) return
    if (_queue.value.any { entry ->
            entry.status == com.juan.snapmusic.core.model.QueueStatus.RUNNING ||
                entry.status == com.juan.snapmusic.core.model.QueueStatus.PENDING ||
                entry.status == com.juan.snapmusic.core.model.QueueStatus.PAUSED
        }
    ) {
        _cacheCleanupState.value = CacheCleanupUiState(
            feedback = "Terminá o pausá las descargas activas antes de limpiar temporales.",
        )
        return
    }
    _cacheCleanupState.value = CacheCleanupUiState(isRunning = true)
    viewModelScope.launch {
        runCatching { graph.cacheCleanupRepository.cleanManualCache() }
            .onSuccess { result ->
                _cacheCleanupState.value = CacheCleanupUiState(
                    feedback = "Caché limpiada. Espacio liberado: ${formatCacheBytes(result.bytesFreed)}.",
                )
            }
            .onFailure {
                _cacheCleanupState.value = CacheCleanupUiState(
                    feedback = "No pude limpiar la caché. Intentá de nuevo.",
                )
            }
    }
}

fun SnapMusicViewModel.updateAudioFormat(format: String) {
    viewModelScope.launch {
        val target = runCatching { com.juan.snapmusic.core.model.ContainerFormat.valueOf(format) }.getOrNull() ?: return@launch
        graph.preferencesRepository.updateAudioFormat(target)
    }
}

fun SnapMusicViewModel.updateAudioQuality(value: String) {
    viewModelScope.launch { graph.preferencesRepository.updateAudioQuality(value) }
}

fun SnapMusicViewModel.updateVideoQuality(value: String) {
    viewModelScope.launch { graph.preferencesRepository.updateVideoQuality(value) }
}

fun SnapMusicViewModel.updatePreviewVolume(value: Float) {
    viewModelScope.launch { graph.preferencesRepository.updatePreviewVolume(value) }
}

fun SnapMusicViewModel.updateDownloadTaskLimits(wifi: Int, mobile: Int) {
    viewModelScope.launch { graph.preferencesRepository.updateDownloadTaskLimits(wifi, mobile) }
}

fun SnapMusicViewModel.updateDownloadSpeedLimitLabel(value: String) {
    viewModelScope.launch { graph.preferencesRepository.updateDownloadSpeedLimitLabel(value) }
}

fun SnapMusicViewModel.updateAllowMobileDataDownloads(value: Boolean) {
    viewModelScope.launch { graph.preferencesRepository.updateAllowMobileDataDownloads(value) }
}

fun SnapMusicViewModel.updateDownloadCompleteSound(value: DownloadCompleteSound) {
    viewModelScope.launch { graph.preferencesRepository.updateDownloadCompleteSound(value) }
}

fun SnapMusicViewModel.updateNotifyDownloadProgress(value: Boolean) {
    viewModelScope.launch { graph.preferencesRepository.updateNotifyDownloadProgress(value) }
}

fun SnapMusicViewModel.updateNotifyDownloadCompleted(value: Boolean) {
    viewModelScope.launch { graph.preferencesRepository.updateNotifyDownloadCompleted(value) }
}

fun SnapMusicViewModel.updateNotifyRecommendedContent(value: Boolean) {
    viewModelScope.launch { graph.preferencesRepository.updateNotifyRecommendedContent(value) }
}

fun SnapMusicViewModel.updateNotifyToolUpdates(value: Boolean) {
    viewModelScope.launch { graph.preferencesRepository.updateNotifyToolUpdates(value) }
}

fun SnapMusicViewModel.updateNotifyToolbarAccess(value: Boolean) {
    viewModelScope.launch { graph.preferencesRepository.updateNotifyToolbarAccess(value) }
}

fun SnapMusicViewModel.updateThemeMode(value: AppThemeMode) {
    viewModelScope.launch {
        graph.preferencesRepository.updateThemeMode(value)
        graph.launchPreferencesRepository.setThemeMode(value)
    }
}

internal fun SnapMusicViewModel.formatCacheBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val mb = safeBytes / (1024.0 * 1024.0)
    return when {
        safeBytes < 1024L -> "$safeBytes B"
        safeBytes < 1024L * 1024L -> "${safeBytes / 1024L} KB"
        mb < 10.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        else -> "${mb.toLong()} MB"
    }
}
