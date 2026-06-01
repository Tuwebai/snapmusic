package com.juan.snapmusic.data.extractor

import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.TransferSource
import kotlin.math.abs

internal data class AudioSourceCandidate(
    val id: String,
    val url: String,
    val bitrateKbps: Int?,
    val sourceContainerHint: String,
    val isDirectM4a: Boolean,
    val isAudioOnly: Boolean = true,
    val audioTrackType: String? = null,
    val audioTrackName: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

internal data class VideoSourceCandidate(
    val id: String,
    val url: String,
    val resolution: String?,
    val height: Int?,
    val sourceContainerHint: String,
    val isProgressiveMp4: Boolean,
    val isMuxableMp4: Boolean,
    val isPlaybackMuxable: Boolean = isMuxableMp4,
    val headers: Map<String, String> = emptyMap(),
)

internal object DownloadSourcePlanner {
    fun buildAudioVariants(
        audioCandidates: List<AudioSourceCandidate>,
    ): List<MediaVariant> {
        val compatible = audioCandidates.filter { it.url.isNotBlank() }
        if (compatible.isEmpty()) return emptyList()
        val variants = mutableListOf<MediaVariant>()
        val directM4aByBitrate = compatible
            .filter { it.isDirectM4a && it.isAudioOnly }
            .sortedWith(
                compareByDescending<AudioSourceCandidate> { sanitizeBitrate(it.bitrateKbps) ?: 0 }
                    .thenBy { audioTrackPriority(it) }
                    .thenBy { it.id },
            )
            .distinctBy { sanitizeBitrate(it.bitrateKbps) ?: -1 }

        directM4aByBitrate.forEach { candidate ->
            val bitrate = sanitizeBitrate(candidate.bitrateKbps)
            variants += MediaVariant(
                id = "audio-${candidate.id}",
                label = buildAudioLabel(ContainerFormat.M4A, bitrate, converted = false),
                kind = MediaKind.AUDIO,
                container = ContainerFormat.M4A,
                bitrateKbps = bitrate,
                directUrl = candidate.url,
                requiresTranscode = false,
                isSyntheticOutput = false,
                sourceId = candidate.id,
                sourceContainerHint = candidate.sourceContainerHint,
                sourceBitrateKbps = bitrate,
                allowTranscodeFallback = true,
            )
        }
        val ceiling = compatible.maxOfOrNull { sanitizeBitrate(it.bitrateKbps) ?: 0 }?.takeIf { it > 0 }
        preferredMp3Targets(ceiling).forEach { targetBitrate ->
            val source = pickBestAudioSource(compatible, targetBitrate, null, null) ?: return@forEach
            variants += syntheticAudioVariant(source, ContainerFormat.MP3, targetBitrate)
        }

        return variants
            .distinctBy { listOf(it.container.name, it.bitrateKbps?.toString().orEmpty(), it.label) }
            .sortedWith(
                compareByDescending<MediaVariant> { it.container == ContainerFormat.MP3 }
                    .thenByDescending { it.container == ContainerFormat.M4A }
                    .thenByDescending { it.bitrateKbps ?: 0 },
            )
    }

    fun buildVideoVariants(
        progressiveCandidates: List<VideoSourceCandidate>,
        muxCandidates: List<VideoSourceCandidate>,
        audioCandidates: List<AudioSourceCandidate>,
    ): List<MediaVariant> {
        val progressive = progressiveCandidates
            .filter { it.url.isNotBlank() && it.isProgressiveMp4 }
            .sortedByDescending { normalizeHeight(it.height, it.resolution) ?: 0 }
            .distinctBy { normalizeHeight(it.height, it.resolution) ?: -1 }
        val audioReady = audioCandidates.any { it.url.isNotBlank() }
        val bestMuxAudioUrl = pickBestAudioSource(
            candidates = audioCandidates.filter { it.url.isNotBlank() },
            targetBitrate = null,
            preferredSourceId = null,
            preferredContainerHint = null,
        )?.url
        val directHeights = progressive.mapNotNull { normalizeHeight(it.height, it.resolution) }.toSet()
        val mux = if (audioReady) {
            muxCandidates
                .filter { it.url.isNotBlank() && it.isPlaybackMuxable }
                .sortedByDescending { normalizeHeight(it.height, it.resolution) ?: 0 }
                .distinctBy { normalizeHeight(it.height, it.resolution) ?: -1 }
                .filterNot { candidate -> normalizeHeight(candidate.height, candidate.resolution) in directHeights }
        } else {
            emptyList()
        }

        return (progressive.map { candidate ->
            val height = normalizeHeight(candidate.height, candidate.resolution)
            MediaVariant(
                id = "video-${candidate.id}",
                label = buildVideoLabel(height),
                kind = MediaKind.VIDEO,
                container = ContainerFormat.MP4,
                resolution = resolutionLabel(height, candidate.resolution),
                directUrl = candidate.url,
                requiresMux = false,
                isSyntheticOutput = false,
                sourceId = candidate.id,
                sourceContainerHint = candidate.sourceContainerHint,
                sourceHeight = height,
                allowMuxFallback = true,
            )
        } + mux.map { candidate ->
            val height = normalizeHeight(candidate.height, candidate.resolution)
            MediaVariant(
                id = "video-mux-${candidate.id}",
                label = buildVideoLabel(height),
                kind = MediaKind.VIDEO,
                container = ContainerFormat.MP4,
                resolution = resolutionLabel(height, candidate.resolution),
                directUrl = candidate.url,
                secondaryUrl = bestMuxAudioUrl,
                requiresMux = true,
                isSyntheticOutput = true,
                sourceId = candidate.id,
                sourceContainerHint = candidate.sourceContainerHint,
                sourceHeight = height,
                allowMuxFallback = true,
            )
        }).sortedByDescending { it.sourceHeight ?: 0 }
    }

    fun resolveDownloadPlan(
        selection: DownloadSelection,
        audioCandidates: List<AudioSourceCandidate>,
        progressiveCandidates: List<VideoSourceCandidate>,
        muxCandidates: List<VideoSourceCandidate>,
    ): DownloadExecutionPlan {
        return when (selection.kind) {
            MediaKind.AUDIO -> resolveAudioPlan(selection, audioCandidates)
            MediaKind.VIDEO -> resolveVideoPlan(selection, audioCandidates, progressiveCandidates, muxCandidates)
        }
    }

    private fun resolveAudioPlan(
        selection: DownloadSelection,
        audioCandidates: List<AudioSourceCandidate>,
    ): DownloadExecutionPlan {
        val compatible = audioCandidates.filter { it.url.isNotBlank() }
        if (compatible.isEmpty()) {
            error("No hay una fuente de audio compatible para generar el archivo final.")
        }
        val targetBitrate = selection.targetBitrateKbps ?: selection.sourceBitrateKbps
        return when (selection.targetContainer) {
            ContainerFormat.MP3 -> {
                val source = pickBestAudioSource(
                    candidates = compatible,
                    targetBitrate = targetBitrate,
                    preferredSourceId = selection.preferredSourceId,
                    preferredContainerHint = selection.sourceContainerHint,
                ) ?: error("No hay una fuente de audio compatible para generar el MP3 final.")
                val effectiveBitrate = outputBitrateFor(ContainerFormat.MP3, source.bitrateKbps ?: targetBitrate)
                DownloadExecutionPlan.AudioTranscode(
                    selection = selection.copy(
                        targetBitrateKbps = effectiveBitrate,
                        strategy = DownloadStrategy.TRANSCODE_AUDIO,
                        preferredSourceId = source.id,
                        sourceContainerHint = source.sourceContainerHint,
                        sourceBitrateKbps = sanitizeBitrate(source.bitrateKbps),
                    ),
                    source = source.toTransferSource(),
                    displayLabel = buildAudioLabel(ContainerFormat.MP3, effectiveBitrate, converted = true),
                )
            }

            ContainerFormat.M4A -> {
                val direct = pickBestDirectM4aSource(
                    candidates = compatible,
                    targetBitrate = targetBitrate,
                    preferredSourceId = selection.preferredSourceId,
                )
                if (direct != null) {
                    val effectiveBitrate = outputBitrateFor(ContainerFormat.M4A, direct.bitrateKbps ?: targetBitrate)
                    return DownloadExecutionPlan.Direct(
                        selection = selection.copy(
                            targetBitrateKbps = effectiveBitrate,
                            strategy = DownloadStrategy.DIRECT,
                            preferredSourceId = direct.id,
                            sourceContainerHint = direct.sourceContainerHint,
                            sourceBitrateKbps = sanitizeBitrate(direct.bitrateKbps),
                        ),
                        source = direct.toTransferSource(),
                        displayLabel = buildAudioLabel(ContainerFormat.M4A, effectiveBitrate, converted = false),
                    )
                }
                if (!selection.allowTranscodeFallback && selection.strategy == DownloadStrategy.DIRECT) {
                    error("No hay una fuente M4A directa compatible para esa calidad.")
                }
                val source = pickBestAudioSource(
                    candidates = compatible,
                    targetBitrate = targetBitrate,
                    preferredSourceId = selection.preferredSourceId,
                    preferredContainerHint = selection.sourceContainerHint,
                ) ?: error("No hay una fuente de audio compatible para generar el M4A final.")
                val effectiveBitrate = outputBitrateFor(ContainerFormat.M4A, source.bitrateKbps ?: targetBitrate)
                DownloadExecutionPlan.AudioTranscode(
                    selection = selection.copy(
                        targetBitrateKbps = effectiveBitrate,
                        strategy = DownloadStrategy.TRANSCODE_AUDIO,
                        preferredSourceId = source.id,
                        sourceContainerHint = source.sourceContainerHint,
                        sourceBitrateKbps = sanitizeBitrate(source.bitrateKbps),
                    ),
                    source = source.toTransferSource(),
                    displayLabel = buildAudioLabel(ContainerFormat.M4A, effectiveBitrate, converted = true),
                )
            }

            else -> error("Solo soportamos descargas de audio finales en MP3 o M4A.")
        }
    }

    private fun resolveVideoPlan(
        selection: DownloadSelection,
        audioCandidates: List<AudioSourceCandidate>,
        progressiveCandidates: List<VideoSourceCandidate>,
        muxCandidates: List<VideoSourceCandidate>,
    ): DownloadExecutionPlan {
        require(selection.targetContainer == ContainerFormat.MP4) {
            "Solo soportamos descargas de video finales en MP4."
        }
        val targetHeight = selection.sourceHeight ?: parseHeight(selection.targetResolution)
        val direct = progressiveCandidates.filter { it.url.isNotBlank() && it.isProgressiveMp4 }
        val mux = muxCandidates.filter { it.url.isNotBlank() && it.isMuxableMp4 }
        val audioSource = pickBestAudioSource(audioCandidates.filter { it.url.isNotBlank() }, null, null, null)
        val candidate = pickBestVideoCandidate(
            directCandidates = direct,
            muxCandidates = if ((selection.allowMuxFallback || selection.strategy == DownloadStrategy.MUX_VIDEO_AUDIO) && audioSource != null) {
                mux
            } else {
                emptyList()
            },
            targetHeight = targetHeight,
            preferredSourceId = selection.preferredSourceId,
        ) ?: error("No hay una fuente MP4 compatible para generar ese video.")
        val effectiveHeight = normalizeHeight(candidate.height, candidate.resolution) ?: targetHeight
        val resolvedSelection = selection.copy(
            targetResolution = resolutionLabel(effectiveHeight, candidate.resolution),
            strategy = if (candidate.isProgressiveMp4) DownloadStrategy.DIRECT else DownloadStrategy.MUX_VIDEO_AUDIO,
            preferredSourceId = candidate.id,
            sourceContainerHint = candidate.sourceContainerHint,
            sourceHeight = effectiveHeight,
        )
        return if (candidate.isProgressiveMp4) {
            DownloadExecutionPlan.Direct(
                selection = resolvedSelection,
                source = candidate.toTransferSource(),
                displayLabel = buildVideoLabel(effectiveHeight),
            )
        } else {
            val bestAudio = audioSource ?: error("No hay una pista de audio compatible para armar el MP4 final.")
            DownloadExecutionPlan.MuxVideoAudio(
                selection = resolvedSelection,
                videoSource = candidate.toTransferSource(),
                audioSource = bestAudio.toTransferSource(),
                displayLabel = buildVideoLabel(effectiveHeight),
            )
        }
    }

    private fun syntheticAudioVariant(
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

    private fun pickBestDirectM4aSource(
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

    private fun pickBestAudioSource(
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

    private fun pickBestVideoCandidate(
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

    private fun buildAudioLabel(
        container: ContainerFormat,
        bitrateKbps: Int?,
        converted: Boolean,
    ): String {
        return if (bitrateKbps != null) "${container.name} ${bitrateKbps}kbps" else container.name
    }

    private fun buildVideoLabel(height: Int?): String {
        return if (height != null) "MP4 ${height}p" else "MP4"
    }

    private fun resolutionLabel(height: Int?, fallback: String?): String? {
        return height?.let { "${it}p" } ?: fallback
    }

    private fun normalizeHeight(height: Int?, resolution: String?): Int? {
        return height?.takeIf { it > 0 } ?: parseHeight(resolution)
    }

    private fun parseHeight(resolution: String?): Int? {
        return resolution
            ?.substringBefore('p')
            ?.filter(Char::isDigit)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    private fun sanitizeBitrate(value: Int?): Int? =
        value?.takeIf { it > 0 }

    private fun outputBitrateFor(container: ContainerFormat, value: Int?): Int? {
        val sanitized = sanitizeBitrate(value) ?: return null
        return if (container == ContainerFormat.MP3) sanitized.coerceAtMost(320) else sanitized
    }

    private fun preferredMp3Targets(ceiling: Int?): List<Int?> {
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

    private fun audioDistance(candidateBitrate: Int?, targetBitrate: Int?): Int {
        return when {
            candidateBitrate == null && targetBitrate == null -> 0
            candidateBitrate == null -> Int.MAX_VALUE / 4
            targetBitrate == null -> 0
            else -> abs(candidateBitrate - targetBitrate)
        }
    }

    private fun audioTrackPriority(candidate: AudioSourceCandidate): Int {
        return when (candidate.audioTrackType?.lowercase()) {
            "original" -> 0
            null, "" -> if (candidate.audioTrackName.isNullOrBlank()) 1 else 2
            "secondary" -> 3
            "descriptive" -> 4
            "dubbed" -> 5
            else -> 2
        }
    }

    private fun AudioSourceCandidate.toTransferSource(): TransferSource = TransferSource(
        url = url,
        headers = headers,
    )

    private fun VideoSourceCandidate.toTransferSource(): TransferSource = TransferSource(
        url = url,
        headers = headers,
    )
}
