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

fun SnapMusicViewModel.applyYoutubePreset(query: String) {
    _youtubeState.value = _youtubeState.value.copy(query = query)
    clearYouTubeSuggestions()
    searchYoutube()
}

internal fun SnapMusicViewModel.scheduleYouTubeSuggestions(rawQuery: String) {
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
        val localSuggestions = filterSuggestionCorpus(query, _recentSearchQueries.value, 4)
        val fallbackSuggestions = buildFallbackSearchSuggestions(query)
        val merged = buildList {
            add(query)
            addAll(localSuggestions)
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

internal fun SnapMusicViewModel.scheduleDownloadSearchSuggestions(rawQuery: String) {
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
        val localSuggestions = filterSuggestionCorpus(query, _recentSearchQueries.value, 4)
        val fallbackSuggestions = filterSuggestionCorpus(query, searchSuggestionCorpus.value, 8)
        val merged = buildList {
            add(query)
            addAll(localSuggestions)
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

internal fun SnapMusicViewModel.ensurePopularDownloadSearchesLoaded() {
    if (_downloadSearchState.value.popularQueries.isNotEmpty()) {
        hasLoadedPopularDownloadQueries = true
        return
    }
    refreshPopularDownloadSearches()
}

internal fun SnapMusicViewModel.refreshPopularDownloadSearches(force: Boolean = false) {
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

internal fun SnapMusicViewModel.clearYouTubeSuggestions() {
    youtubeSuggestionJob?.cancel()
    _youtubeSearchSuggestionsLoading.value = false
    _youtubeSearchSuggestions.value = emptyList()
}

internal fun SnapMusicViewModel.defaultPopularDownloadQueries(): List<String> = listOf(
    "María Becerra oficial",
    "Jere Klein oficial",
    "Callejero Fino oficial",
    "Khea oficial",
    "Q' Lokura oficial",
    "Música argentina oficial",
    "Lanzamientos latinos",
    "Cumbia oficial",
)

internal fun SnapMusicViewModel.buildFallbackSearchSuggestions(query: String): List<String> {
    val normalized = query.trim()
    if (normalized.isBlank()) return emptyList()
    return filterSuggestionCorpus(normalized, searchSuggestionCorpus.value, 8)
}

internal fun SnapMusicViewModel.rememberSearchQuery(query: String) {
    val normalized = query.trim()
    if (normalized.isBlank()) return
    _recentSearchQueries.value = (listOf(normalized) + _recentSearchQueries.value)
        .distinctBy { it.lowercase() }
        .take(20)
    viewModelScope.launch(Dispatchers.IO) {
        _recentSearchQueries.value = graph.preferencesRepository.rememberRecentSearchQuery(normalized)
    }
}

internal fun SnapMusicViewModel.filterSuggestionCorpus(
    query: String,
    corpus: List<String>,
    limit: Int,
): List<String> {
    return corpus.asSequence()
        .filter { value -> value.contains(query, ignoreCase = true) }
        .take(limit)
        .toList()
}
