package com.juan.snapmusic.feature.youtube.playback

import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.platform.MergedPlaybackUri
import kotlin.math.abs

internal enum class YouTubePlaybackSourceMode {
    ADAPTIVE,
    MERGED,
    PROGRESSIVE,
}

internal data class YouTubePlaybackSelection(
    val playbackUrl: String,
    val expectedHeight: Int?,
    val sourceMode: YouTubePlaybackSourceMode,
)

internal object YouTubePlaybackSourceSelector {
    private val stableMergedAutoHeights = listOf(720, 480, 360, 240, 144, 1080)
    private val stableProgressiveAutoHeights = listOf(720, 480, 360, 240, 144)

    fun select(media: ResolvedMedia, requestedVariantId: String): YouTubePlaybackSelection? {
        val adaptivePlaybackUrl = media.adaptivePlaybackUrl?.takeIf(::isAdaptivePlaybackUrl)
        val playbackCandidates = sortedPlaybackCandidates(media)
        val requestedHeight = requestedHeight(media, requestedVariantId, playbackCandidates)

        if (requestedVariantId == "auto") {
            val automaticHeight = preferredAutomaticHeight(media)
            return if (adaptivePlaybackUrl != null) {
                YouTubePlaybackSelection(
                    playbackUrl = adaptivePlaybackUrl,
                    expectedHeight = automaticHeight,
                    sourceMode = YouTubePlaybackSourceMode.ADAPTIVE,
                )
            } else {
                fallbackAutomatic(media)
            }
        }

        if (requestedVariantId.startsWith("adaptive-") && adaptivePlaybackUrl != null) {
            return YouTubePlaybackSelection(
                playbackUrl = adaptivePlaybackUrl,
                expectedHeight = requestedHeight,
                sourceMode = YouTubePlaybackSourceMode.ADAPTIVE,
            )
        }

        val chosen = nearestVariant(playbackCandidates, requestedHeight) ?: return null
        val playbackUrl = playbackUrlFor(chosen) ?: return null
        return YouTubePlaybackSelection(
            playbackUrl = playbackUrl,
            expectedHeight = chosen.height,
            sourceMode = sourceMode(playbackUrl, adaptivePlaybackUrl) ?: YouTubePlaybackSourceMode.PROGRESSIVE,
        )
    }

    fun fallbackAfterError(
        media: ResolvedMedia,
        currentMode: YouTubePlaybackSourceMode,
        requestedVariantId: String,
    ): YouTubePlaybackSelection? {
        val requestedHeight = requestedHeight(media, requestedVariantId, sortedPlaybackCandidates(media))
        val playbackCandidates = media.videoVariants.filter { !it.directUrl.isNullOrBlank() }
        val mergedCandidates = playbackCandidates.filter { it.requiresMux && !it.secondaryUrl.isNullOrBlank() }
        val progressiveCandidates = playbackCandidates.filterNot { it.requiresMux }
        return when (currentMode) {
            YouTubePlaybackSourceMode.ADAPTIVE -> mergedCandidates.selectionFor(requestedHeight, YouTubePlaybackSourceMode.MERGED)
                ?: fallbackProgressive(media)

            YouTubePlaybackSourceMode.MERGED -> progressiveCandidates.selectionFor(requestedHeight, YouTubePlaybackSourceMode.PROGRESSIVE)
                ?: fallbackProgressive(media)

            YouTubePlaybackSourceMode.PROGRESSIVE -> null
        }
    }

    fun stabilityFallback(
        media: ResolvedMedia,
        currentPlaybackUrl: String?,
        currentHeight: Int?,
        currentMode: YouTubePlaybackSourceMode,
    ): YouTubePlaybackSelection? {
        val playbackCandidates = media.videoVariants.filter { !it.directUrl.isNullOrBlank() }
        val resolvedHeight = currentHeight ?: currentPlaybackUrl?.let { playbackUrlHeightHint(playbackCandidates, it) }
        val mergedCandidates = playbackCandidates.filter { it.requiresMux && !it.secondaryUrl.isNullOrBlank() }
        val progressiveCandidates = playbackCandidates.filterNot { it.requiresMux }
        val variant = when (currentMode) {
            YouTubePlaybackSourceMode.ADAPTIVE -> null
            YouTubePlaybackSourceMode.MERGED -> lowerStableVariant(mergedCandidates, resolvedHeight, stableMergedAutoHeights)
                ?: stableAutomaticVariant(progressiveCandidates, stableProgressiveAutoHeights)

            YouTubePlaybackSourceMode.PROGRESSIVE -> lowerStableVariant(progressiveCandidates, resolvedHeight, stableProgressiveAutoHeights)
        } ?: return null
        return playbackSelectionForVariant(variant)
    }

    fun adaptiveRecovery(
        media: ResolvedMedia,
        currentHeight: Int?,
    ): YouTubePlaybackSelection? {
        val adaptiveUrl = media.adaptivePlaybackUrl?.takeIf(::isAdaptivePlaybackUrl) ?: return null
        val targetHeight = stableAdaptiveHeight(media, currentHeight ?: preferredAutomaticHeight(media))
            ?: preferredAutomaticHeight(media)
        return YouTubePlaybackSelection(
            playbackUrl = adaptiveUrl,
            expectedHeight = targetHeight,
            sourceMode = YouTubePlaybackSourceMode.ADAPTIVE,
        )
    }

    fun sourceMode(playbackUrl: String?, adaptivePlaybackUrl: String?): YouTubePlaybackSourceMode? {
        val url = playbackUrl ?: return null
        return when {
            adaptivePlaybackUrl?.let(::isAdaptivePlaybackUrl) == true && url == adaptivePlaybackUrl -> {
                YouTubePlaybackSourceMode.ADAPTIVE
            }
            url.startsWith("snapmusic-merged://") -> YouTubePlaybackSourceMode.MERGED
            else -> YouTubePlaybackSourceMode.PROGRESSIVE
        }
    }

    fun isAdaptivePlaybackUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.startsWith("data:application/dash+xml") ||
            lower.contains(".mpd") ||
            lower.contains("dash") ||
            lower.contains(".m3u8")
    }

    fun preferredAutomaticHeight(media: ResolvedMedia?): Int? {
        val heights = media.extractHeights()
        return heights.filter { it <= 720 }.maxOrNull()
            ?: heights.minByOrNull { abs(it - 720) }
    }

    fun availableHeights(media: ResolvedMedia): List<Int> {
        return media.extractHeights()
            .filter { it <= 720 }
            .ifEmpty { media.extractHeights() }
    }

    fun labelFor(media: ResolvedMedia?, variantId: String, expectedHeight: Int?): String? {
        val resolved = media ?: return expectedHeight?.let(::qualityLabel)
        return if (variantId == "auto") {
            expectedHeight?.let { "Automático · ${it}P" } ?: preferredAutomaticLabel(resolved)
        } else {
            val selectedHeight = expectedHeight ?: requestedHeight(resolved, variantId, sortedPlaybackCandidates(resolved))
            selectedHeight?.let(::qualityLabel)
        }
    }

    fun preferredAutomaticLabel(media: ResolvedMedia?): String {
        return preferredAutomaticHeight(media)?.let { "Automático · ${it}P" } ?: "Automático"
    }

    fun qualityLabel(height: Int): String = "${height}P"

    private fun fallbackAutomatic(media: ResolvedMedia): YouTubePlaybackSelection? {
        val playbackCandidates = media.videoVariants.filter { !it.directUrl.isNullOrBlank() }
        val merged = stableAutomaticVariant(
            playbackCandidates.filter { it.requiresMux && !it.secondaryUrl.isNullOrBlank() },
            stableMergedAutoHeights,
        )
        return merged?.let(::playbackSelectionForVariant) ?: fallbackProgressive(media)
    }

    private fun fallbackProgressive(media: ResolvedMedia): YouTubePlaybackSelection? {
        val progressiveCandidates = media.videoVariants
            .filter { !it.directUrl.isNullOrBlank() && !it.requiresMux }
        val fallbackVariant = stableAutomaticVariant(progressiveCandidates, stableProgressiveAutoHeights)
        val playbackUrl = fallbackVariant?.let(::playbackUrlFor)
            ?: media.playbackUrl
            ?: progressiveCandidates.firstOrNull()?.directUrl
            ?: return null
        return YouTubePlaybackSelection(
            playbackUrl = playbackUrl,
            expectedHeight = fallbackVariant?.height ?: playbackUrlHeightHint(progressiveCandidates, playbackUrl),
            sourceMode = YouTubePlaybackSourceMode.PROGRESSIVE,
        )
    }

    private fun List<MediaVariant>.selectionFor(
        requestedHeight: Int?,
        mode: YouTubePlaybackSourceMode,
    ): YouTubePlaybackSelection? {
        val variant = nearestVariant(this, requestedHeight) ?: stableAutomaticVariant(this, stableMergedAutoHeights)
        val playbackUrl = variant?.let(::playbackUrlFor) ?: return null
        return YouTubePlaybackSelection(
            playbackUrl = playbackUrl,
            expectedHeight = variant.height,
            sourceMode = mode,
        )
    }

    private fun playbackSelectionForVariant(variant: MediaVariant): YouTubePlaybackSelection? {
        val playbackUrl = playbackUrlFor(variant) ?: return null
        return YouTubePlaybackSelection(
            playbackUrl = playbackUrl,
            expectedHeight = variant.height,
            sourceMode = if (variant.requiresMux) YouTubePlaybackSourceMode.MERGED else YouTubePlaybackSourceMode.PROGRESSIVE,
        )
    }

    private fun playbackUrlFor(variant: MediaVariant): String? {
        if (variant.directUrl.isBlank()) return null
        if (!variant.requiresMux) return variant.directUrl
        val audioUrl = variant.secondaryUrl?.takeIf { it.isNotBlank() } ?: return null
        return MergedPlaybackUri.build(videoUrl = variant.directUrl, audioUrl = audioUrl)
    }

    private fun sortedPlaybackCandidates(media: ResolvedMedia): List<MediaVariant> {
        return media.videoVariants
            .filter { !it.directUrl.isNullOrBlank() }
            .sortedWith(compareByDescending<MediaVariant> { it.height ?: 0 }.thenByDescending { !it.requiresMux })
    }

    private fun requestedHeight(
        media: ResolvedMedia,
        requestedVariantId: String,
        playbackCandidates: List<MediaVariant>,
    ): Int? {
        return when {
            requestedVariantId == "auto" -> preferredAutomaticHeight(media)
            requestedVariantId.startsWith("adaptive-") -> requestedVariantId.removePrefix("adaptive-").toIntOrNull()
            else -> playbackCandidates.firstOrNull { it.id == requestedVariantId }?.height
        }
    }

    private fun nearestVariant(candidates: List<MediaVariant>, requestedHeight: Int?): MediaVariant? {
        if (candidates.isEmpty()) return null
        if (requestedHeight == null) return stableAutomaticVariant(candidates, stableMergedAutoHeights)
        return candidates.firstOrNull { it.height == requestedHeight }
            ?: candidates.filter { (it.height ?: 0) <= requestedHeight }.maxByOrNull { it.height ?: 0 }
            ?: candidates.minByOrNull { abs((it.height ?: requestedHeight) - requestedHeight) }
    }

    private fun stableAutomaticVariant(candidates: List<MediaVariant>, preferredHeights: List<Int>): MediaVariant? {
        if (candidates.isEmpty()) return null
        preferredHeights.forEach { preferred ->
            candidates.firstOrNull { it.height == preferred }?.let { return it }
        }
        return candidates.maxByOrNull { it.height ?: 0 }
    }

    private fun lowerStableVariant(
        candidates: List<MediaVariant>,
        currentHeight: Int?,
        preferredHeights: List<Int>,
    ): MediaVariant? {
        val safeCurrent = currentHeight ?: return stableAutomaticVariant(candidates, preferredHeights)
        return candidates
            .filter { (it.height ?: 0) < safeCurrent }
            .maxByOrNull { it.height ?: 0 }
    }

    private fun stableAdaptiveHeight(media: ResolvedMedia, currentHeight: Int?): Int? {
        val heights = availableHeights(media)
        if (heights.isEmpty()) return null
        val safeCurrent = currentHeight ?: 720
        return heights.firstOrNull { it <= safeCurrent.coerceAtMost(720) } ?: heights.firstOrNull()
    }

    private fun playbackUrlHeightHint(candidates: List<MediaVariant>, playbackUrl: String): Int? {
        return candidates.firstOrNull { it.directUrl == playbackUrl }?.height
    }

    private fun ResolvedMedia?.extractHeights(): List<Int> {
        return this?.videoVariants
            ?.mapNotNull { it.height }
            ?.distinct()
            ?.sortedDescending()
            .orEmpty()
    }

    private val MediaVariant.height: Int?
        get() = resolution?.substringBefore('p')?.toIntOrNull()
}
