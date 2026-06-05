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

internal fun SnapMusicViewModel.enqueueFromMedia(
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

internal fun SnapMusicViewModel.enqueueRequest(
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

internal suspend fun SnapMusicViewModel.toFeaturedVideo(item: YouTubeFeedItem): YouTubeFeaturedVideo {
    val resolved = graph.resolverRepository.resolve(item.url)
    val playbackSelection = resolvePlaybackSelection(
        media = resolved,
        requestedVariantId = "auto",
    )
    YouTubePlaybackTelemetry.source(
        sourceUrl = item.url,
        mode = playbackSelection?.sourceMode,
        heights = resolved.videoVariants.mapNotNull { it.resolution }.distinct(),
        adaptive = resolved.adaptivePlaybackUrl?.let(::isAdaptivePlaybackUrl) == true,
    )
    return YouTubeFeaturedVideo(
        sourceUrl = item.url,
        title = resolved.title,
        author = resolved.author,
        thumbnailUrl = resolved.thumbnailUrl,
        playbackUrl = playbackSelection?.playbackUrl,
        adaptivePlaybackUrl = resolved.adaptivePlaybackUrl,
        selectedVideoQualityId = "auto",
        autoMaxVideoHeight = playbackSelection?.expectedHeight,
        actualVideoHeight = playbackSelection?.expectedHeight,
        actualPlaybackLabel = playbackSelection?.expectedHeight?.let { "Automático · ${it}P" } ?: preferredAutomaticPlaybackLabel(resolved),
        durationSeconds = resolved.durationSeconds,
        publishedText = item.publishedText,
        description = item.description,
        resolvedMedia = resolved,
        isReady = playbackSelection?.playbackUrl != null,
    )
}

internal fun YouTubeFeedItem.toLoadingFeaturedVideo(): YouTubeFeaturedVideo {
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

internal fun YouTubeFeedItem.toPendingResolvedMedia(): ResolvedMedia {
    return ResolvedMedia(
        sourceUrl = url,
        title = title,
        author = author,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
        audioVariants = emptyList(),
        videoVariants = emptyList(),
    )
}

internal fun SnapMusicViewModel.isAdaptivePlaybackUrl(url: String): Boolean {
    return YouTubePlaybackSourceSelector.isAdaptivePlaybackUrl(url)
}

internal fun SnapMusicViewModel.fallbackAutomaticPlaybackSelection(
    resolved: com.juan.snapmusic.core.model.ResolvedMedia,
): PlaybackSelection? {
    val playbackCandidates = resolved.videoVariants.filter { !it.directUrl.isNullOrBlank() }
    val mergedCandidates = playbackCandidates.filter { it.requiresMux && !it.secondaryUrl.isNullOrBlank() }
    val progressiveCandidates = playbackCandidates.filterNot { it.requiresMux }
    val fallbackVariant = resolveStableAutomaticPlaybackVariant(mergedCandidates, YOUTUBE_STABLE_MERGED_AUTO_HEIGHTS)
        ?: resolveStableAutomaticPlaybackVariant(progressiveCandidates, YOUTUBE_STABLE_PROGRESSIVE_AUTO_HEIGHTS)
    val fallbackUrl = fallbackVariant?.let(::fallbackPlaybackUrl)
        ?: resolved.playbackUrl
        ?: progressiveCandidates.firstOrNull()?.directUrl
        ?: playbackCandidates.firstOrNull()?.directUrl
        ?: return null
    return PlaybackSelection(
        playbackUrl = fallbackUrl,
        expectedHeight = fallbackVariant?.resolution?.substringBefore('p')?.toIntOrNull()
            ?: playbackUrlHeightHint(playbackCandidates, fallbackUrl),
        sourceMode = playbackSourceMode(fallbackUrl, resolved.adaptivePlaybackUrl),
    )
}

internal fun SnapMusicViewModel.fallbackProgressivePlaybackUrl(resolved: com.juan.snapmusic.core.model.ResolvedMedia): PlaybackSelection? {
    val progressiveCandidates = resolved.videoVariants
        .filter { !it.directUrl.isNullOrBlank() && !it.requiresMux }
    val fallbackVariant = resolveAutomaticPlaybackVariant(progressiveCandidates)
    val playbackUrl = fallbackVariant?.let(::fallbackPlaybackUrl)
        ?: resolved.playbackUrl
        ?: progressiveCandidates.firstOrNull()?.directUrl
        ?: return null
    return PlaybackSelection(
        playbackUrl = playbackUrl,
        expectedHeight = fallbackVariant?.resolution?.substringBefore('p')?.toIntOrNull()
            ?: resolved.playbackUrl?.let { playbackUrlHeightHint(progressiveCandidates, it) },
        sourceMode = YouTubePlaybackSourceMode.PROGRESSIVE,
    )
}

internal fun SnapMusicViewModel.resolvePlaybackSelection(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    requestedVariantId: String,
): PlaybackSelection? {
    return YouTubePlaybackSourceSelector.select(media, requestedVariantId)
}

internal fun SnapMusicViewModel.resolveStabilityPlaybackSelection(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    featured: YouTubeFeaturedVideo,
    currentMode: YouTubePlaybackSourceMode,
): PlaybackSelection? {
    return YouTubePlaybackSourceSelector.stabilityFallback(
        media = media,
        currentPlaybackUrl = featured.playbackUrl,
        currentHeight = featured.actualVideoHeight,
        currentMode = currentMode,
    )
}

internal fun SnapMusicViewModel.resolveFallbackPlaybackSelection(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    currentMode: YouTubePlaybackSourceMode,
    requestedVariantId: String,
): PlaybackSelection? {
    return YouTubePlaybackSourceSelector.fallbackAfterError(media, currentMode, requestedVariantId)
}

internal fun SnapMusicViewModel.requestedPlaybackHeight(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    requestedVariantId: String,
): Int? {
    if (requestedVariantId == "auto") return preferredAutomaticPlaybackHeight(media)
    if (requestedVariantId.startsWith("adaptive-")) {
        return requestedVariantId.removePrefix("adaptive-").toIntOrNull()
    }
    return media.videoVariants
        .firstOrNull { it.id == requestedVariantId }
        ?.resolution
        ?.substringBefore('p')
        ?.toIntOrNull()
}

internal fun SnapMusicViewModel.playbackSourceMode(
    featured: YouTubeFeaturedVideo,
): YouTubePlaybackSourceMode? {
    val playbackUrl = featured.playbackUrl ?: return null
    return playbackSourceMode(playbackUrl, featured.adaptivePlaybackUrl)
}

internal fun SnapMusicViewModel.playbackSourceMode(
    playbackUrl: String,
    adaptivePlaybackUrl: String?,
): YouTubePlaybackSourceMode {
    return YouTubePlaybackSourceSelector.sourceMode(playbackUrl, adaptivePlaybackUrl)
        ?: YouTubePlaybackSourceMode.PROGRESSIVE
}

internal fun SnapMusicViewModel.fallbackPlaybackUrl(
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

internal fun SnapMusicViewModel.playbackUrlHeightHint(
    candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
    playbackUrl: String,
): Int? {
    return candidates.firstOrNull { it.directUrl == playbackUrl }
        ?.resolution
        ?.substringBefore('p')
        ?.toIntOrNull()
}

internal fun SnapMusicViewModel.applyResolvedPlaybackSelection(
    featured: YouTubeFeaturedVideo,
    variantId: String,
): YouTubeFeaturedVideo {
    val resolved = featured.resolvedMedia ?: return featured
    val playbackSelection = resolvePlaybackSelection(
        media = resolved,
        requestedVariantId = variantId,
    ) ?: return featured
    return featured.copy(
        playbackUrl = playbackSelection.playbackUrl,
        adaptivePlaybackUrl = resolved.adaptivePlaybackUrl,
        selectedVideoQualityId = variantId,
        autoMaxVideoHeight = if (variantId == "auto") playbackSelection.expectedHeight else null,
        actualVideoHeight = playbackSelection.expectedHeight,
        actualPlaybackLabel = playbackLabelForSelection(resolved, variantId, playbackSelection.expectedHeight),
        isReady = true,
    )
}

internal fun SnapMusicViewModel.resetPlaybackFallbacks(sourceUrl: String) {
    if (sourceUrl.isBlank()) return
    refreshedAdaptivePlaybackSources.remove(sourceUrl)
    playbackFallbackModes.remove(sourceUrl)
    playbackStabilityFallbacks.remove(sourceUrl)
    youtubeRebufferEvents.remove(sourceUrl)
}

internal fun SnapMusicViewModel.playbackLabelForSelection(
    media: com.juan.snapmusic.core.model.ResolvedMedia?,
    variantId: String,
    expectedHeight: Int?,
): String? {
    return YouTubePlaybackSourceSelector.labelFor(media, variantId, expectedHeight)
}

internal fun SnapMusicViewModel.preferredAutomaticPlaybackHeight(
    media: com.juan.snapmusic.core.model.ResolvedMedia?,
): Int? {
    return YouTubePlaybackSourceSelector.preferredAutomaticHeight(media)
}

internal fun SnapMusicViewModel.stableAdaptiveHeight(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    currentHeight: Int?,
): Int? {
    val heights = availablePlaybackHeights(media)
    if (heights.isEmpty()) return null
    val safeCurrent = currentHeight ?: 720
    return heights.firstOrNull { it <= safeCurrent.coerceAtMost(720) }
        ?: heights.firstOrNull()
}

internal fun SnapMusicViewModel.lowerStableAdaptiveHeight(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    currentHeight: Int,
): Int? {
    val heights = availablePlaybackHeights(media)
    if (heights.isEmpty()) return null
    return heights.firstOrNull { it < currentHeight }
}

internal fun SnapMusicViewModel.availablePlaybackHeights(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
): List<Int> {
    return YouTubePlaybackSourceSelector.availableHeights(media)
}

internal fun SnapMusicViewModel.preferredAutomaticPlaybackLabel(
    media: com.juan.snapmusic.core.model.ResolvedMedia?,
): String? {
    return YouTubePlaybackSourceSelector.preferredAutomaticLabel(media)
}

internal fun SnapMusicViewModel.resolveNearestPlaybackHeight(
    candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
    requestedHeight: Int?,
): Int? {
    return resolveNearestPlaybackVariant(candidates, requestedHeight)
        ?.resolution
        ?.substringBefore('p')
        ?.toIntOrNull()
}

internal fun SnapMusicViewModel.resolveNearestPlaybackVariant(
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

internal fun SnapMusicViewModel.resolveAutomaticPlaybackVariant(
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

internal fun SnapMusicViewModel.resolveStableAutomaticPlaybackVariant(
    candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
    preferredHeights: List<Int>,
): com.juan.snapmusic.core.model.MediaVariant? {
    if (candidates.isEmpty()) return null
    val candidatesByHeight = candidates
        .mapNotNull { variant -> variantHeight(variant)?.let { it to variant } }
        .groupBy({ it.first }, { it.second })
    preferredHeights.forEach { preferredHeight ->
        candidatesByHeight[preferredHeight]
            ?.sortedBy { it.requiresMux }
            ?.firstOrNull()
            ?.let { return it }
    }
    return resolveAutomaticPlaybackVariant(candidates)
}

internal fun SnapMusicViewModel.resolveLowerStableVariant(
    candidates: List<com.juan.snapmusic.core.model.MediaVariant>,
    currentHeight: Int?,
    preferredHeights: List<Int>,
): com.juan.snapmusic.core.model.MediaVariant? {
    if (candidates.isEmpty()) return null
    val stableCandidates = preferredHeights.mapNotNull { preferredHeight ->
        candidates
            .filter { variantHeight(it) == preferredHeight }
            .sortedBy { it.requiresMux }
            .firstOrNull()
    }
    val belowCurrent = currentHeight?.let { height ->
        stableCandidates.firstOrNull { (variantHeight(it) ?: Int.MAX_VALUE) < height }
    }
    return belowCurrent ?: stableCandidates.firstOrNull()
}

internal fun SnapMusicViewModel.playbackSelectionForVariant(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
    variant: com.juan.snapmusic.core.model.MediaVariant,
): PlaybackSelection? {
    val playbackUrl = fallbackPlaybackUrl(variant) ?: return null
    return PlaybackSelection(
        playbackUrl = playbackUrl,
        expectedHeight = variantHeight(variant),
        sourceMode = playbackSourceMode(playbackUrl, media.adaptivePlaybackUrl),
    )
}

internal fun SnapMusicViewModel.variantHeight(variant: com.juan.snapmusic.core.model.MediaVariant): Int? {
    return variant.resolution?.substringBefore('p')?.toIntOrNull()
}

internal fun SnapMusicViewModel.watchPlaybackQualityLabel(height: Int): String = when {
    height >= 1080 -> "Muy alto · ${height}P HD"
    height >= 720 -> "Alta · ${height}P HD"
    height >= 480 -> "Media · ${height}P"
    else -> "Baja · ${height}P"
}

internal fun SnapMusicViewModel.closeTransientHomePlaybackLayers() {
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
