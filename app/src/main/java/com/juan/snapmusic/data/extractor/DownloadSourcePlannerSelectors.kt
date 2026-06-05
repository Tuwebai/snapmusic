package com.juan.snapmusic.data.extractor

import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.TransferSource
import kotlin.math.abs


internal fun syntheticAudioVariant(
    source: AudioSourceCandidate,
    container: ContainerFormat,
    bitrate: Int?,
): MediaVariant {
    val normalizedBitrate = outputBitrateFor(container, bitrate ?: source.bitrateKbps)
    return MediaVariant(
        id = "${container.name.lowercase()}-${source.id}-${normalizedBitrate ?: "direct"}",
        label = buildAudioLabel(container, normalizedBitrate, converted = true),
        kind = MediaKind.AUDIO,
        container = container,
        bitrateKbps = normalizedBitrate,
        directUrl = "",
        requiresTranscode = true,
        isSyntheticOutput = true,
        sourceId = source.id,
        sourceContainerHint = source.sourceContainerHint,
        sourceBitrateKbps = sanitizeBitrate(source.bitrateKbps),
        allowTranscodeFallback = true,
    )
}

internal fun pickBestDirectM4aSource(
    candidates: List<AudioSourceCandidate>,
    targetBitrate: Int?,
    preferredSourceId: String?,
): AudioSourceCandidate? {
    return candidates
        .filter { it.isDirectM4a }
        .minWithOrNull(
            compareBy<AudioSourceCandidate> {
                if (preferredSourceId != null && it.id == preferredSourceId) 0 else 1
            }.thenBy {
                audioTrackPriority(it)
            }.thenBy {
                audioDistance(sanitizeBitrate(it.bitrateKbps), targetBitrate)
            }.thenByDescending {
                sanitizeBitrate(it.bitrateKbps) ?: 0
            },
        )
}

internal fun pickBestDirectWebmSource(
    candidates: List<AudioSourceCandidate>,
    targetBitrate: Int?,
    preferredSourceId: String?,
): AudioSourceCandidate? {
    return candidates
        .filter { it.isDirectWebmOpus() }
        .minWithOrNull(
            compareBy<AudioSourceCandidate> {
                if (preferredSourceId != null && it.id == preferredSourceId) 0 else 1
            }.thenBy {
                audioTrackPriority(it)
            }.thenBy {
                audioDistance(sanitizeBitrate(it.bitrateKbps), targetBitrate)
            }.thenByDescending {
                sanitizeBitrate(it.bitrateKbps) ?: 0
            },
        )
}

internal fun pickBestAudioSource(
    candidates: List<AudioSourceCandidate>,
    targetBitrate: Int?,
    preferredSourceId: String?,
    preferredContainerHint: String?,
): AudioSourceCandidate? {
    return candidates.minWithOrNull(
        compareBy<AudioSourceCandidate> {
            if (preferredSourceId != null && it.id == preferredSourceId) 0 else 1
        }.thenBy {
            audioTrackPriority(it)
        }.thenBy {
            audioDistance(sanitizeBitrate(it.bitrateKbps), targetBitrate)
        }.thenBy {
            if (preferredContainerHint != null && it.sourceContainerHint.equals(preferredContainerHint, ignoreCase = true)) 0 else 1
        }.thenBy {
            if (it.isDirectM4a) 0 else 1
        }.thenBy {
            if (it.isAudioOnly) 0 else 1
        }.thenByDescending {
            sanitizeBitrate(it.bitrateKbps) ?: 0
        },
    )
}

internal fun pickBestVideoCandidate(
    directCandidates: List<VideoSourceCandidate>,
    muxCandidates: List<VideoSourceCandidate>,
    targetHeight: Int?,
    preferredSourceId: String?,
): VideoSourceCandidate? {
    return (directCandidates + muxCandidates).minWithOrNull(
        compareBy<VideoSourceCandidate> {
            if (preferredSourceId != null && it.id == preferredSourceId) 0 else 1
        }.thenBy {
            abs((normalizeHeight(it.height, it.resolution) ?: targetHeight ?: 0) - (targetHeight ?: normalizeHeight(it.height, it.resolution) ?: 0))
        }.thenBy {
            if (it.isProgressiveMp4) 0 else 1
        }.thenByDescending {
            normalizeHeight(it.height, it.resolution) ?: 0
        },
    )
}

internal fun buildAudioLabel(
    container: ContainerFormat,
    bitrateKbps: Int?,
    converted: Boolean,
): String {
    return if (bitrateKbps != null) "${container.name} ${bitrateKbps}kbps" else container.name
}

internal fun buildWebmOpusLabel(bitrateKbps: Int?): String {
    return if (bitrateKbps != null) "WEBM OPUS ${bitrateKbps}kbps" else "WEBM OPUS"
}

internal fun buildVideoLabel(height: Int?): String {
    return if (height != null) "MP4 ${height}p" else "MP4"
}

internal fun resolutionLabel(height: Int?, fallback: String?): String? {
    return height?.let { "${it}p" } ?: fallback
}

internal fun normalizeHeight(height: Int?, resolution: String?): Int? {
    return height?.takeIf { it > 0 } ?: parseHeight(resolution)
}

internal fun parseHeight(resolution: String?): Int? {
    return resolution
        ?.substringBefore('p')
        ?.filter(Char::isDigit)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

internal fun sanitizeBitrate(value: Int?): Int? =
    value?.takeIf { it > 0 }

internal fun AudioSourceCandidate.isDirectWebmOpus(): Boolean {
    if (!isAudioOnly || url.isBlank()) return false
    val hint = sourceContainerHint.uppercase()
    return "WEBM" in hint || "WEBMA" in hint || "OPUS" in hint
}

internal fun outputBitrateFor(container: ContainerFormat, value: Int?): Int? {
    val sanitized = sanitizeBitrate(value) ?: return null
    return if (container == ContainerFormat.MP3) sanitized.coerceAtMost(320) else sanitized
}

internal fun preferredMp3Targets(ceiling: Int?): List<Int?> {
    val safeCeiling = ceiling ?: return listOf(null)
    return when {
        safeCeiling >= 320 -> listOf(320, 192, 128)
        safeCeiling >= 256 -> listOf(256, 192, 128)
        safeCeiling >= 192 -> listOf(192, 128)
        safeCeiling >= 160 -> listOf(160, 128)
        safeCeiling >= 128 -> listOf(128)
        safeCeiling >= 96 -> listOf(96)
        else -> listOf(safeCeiling)
    }
}

internal fun audioDistance(candidateBitrate: Int?, targetBitrate: Int?): Int {
    return when {
        candidateBitrate == null && targetBitrate == null -> 0
        candidateBitrate == null -> Int.MAX_VALUE / 4
        targetBitrate == null -> 0
        else -> abs(candidateBitrate - targetBitrate)
    }
}

internal fun audioTrackPriority(candidate: AudioSourceCandidate): Int {
    return when (candidate.audioTrackType?.lowercase()) {
        "original" -> 0
        null, "" -> if (candidate.audioTrackName.isNullOrBlank()) 1 else 2
        "secondary" -> 3
        "descriptive" -> 4
        "dubbed" -> 5
        else -> 2
    }
}

internal fun AudioSourceCandidate.toTransferSource(): TransferSource = TransferSource(
    url = url,
    headers = headers,
)

internal fun VideoSourceCandidate.toTransferSource(): TransferSource = TransferSource(
    url = url,
    headers = headers,
)
