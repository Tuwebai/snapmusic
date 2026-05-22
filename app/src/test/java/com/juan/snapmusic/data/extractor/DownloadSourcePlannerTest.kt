package com.juan.snapmusic.data.extractor

import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSourcePlannerTest {
    @Test
    fun `expone audio sintetico aunque no exista M4A directo`() {
        val variants = DownloadSourcePlanner.buildAudioVariants(
            listOf(
                AudioSourceCandidate(
                    id = "opus-160",
                    url = "https://cdn.test/audio",
                    bitrateKbps = 160,
                    sourceContainerHint = "WEBMA_OPUS",
                    isDirectM4a = false,
                ),
            ),
        )

        assertTrue(variants.any { it.container == ContainerFormat.MP3 && it.isSyntheticOutput })
        assertTrue(variants.any { it.container == ContainerFormat.M4A && it.isSyntheticOutput })
    }

    @Test
    fun `reutiliza la mejor M4A real aunque cambie el bitrate exacto`() {
        val plan = DownloadSourcePlanner.resolveDownloadPlan(
            selection = DownloadSelection(
                kind = MediaKind.AUDIO,
                targetContainer = ContainerFormat.M4A,
                targetBitrateKbps = 192,
                strategy = DownloadStrategy.DIRECT,
                allowTranscodeFallback = true,
            ),
            audioCandidates = listOf(
                AudioSourceCandidate(
                    id = "m4a-160",
                    url = "https://cdn.test/m4a",
                    bitrateKbps = 160,
                    sourceContainerHint = "M4A",
                    isDirectM4a = true,
                ),
            ),
            progressiveCandidates = emptyList(),
            muxCandidates = emptyList(),
        )

        assertTrue(plan is DownloadExecutionPlan.Direct)
        assertEquals("M4A 160kbps", plan.displayLabel)
        assertEquals(160, plan.selection.targetBitrateKbps)
    }

    @Test
    fun `cae a mux MP4 si el progresivo ya no existe`() {
        val plan = DownloadSourcePlanner.resolveDownloadPlan(
            selection = DownloadSelection(
                kind = MediaKind.VIDEO,
                targetContainer = ContainerFormat.MP4,
                targetResolution = "720p",
                strategy = DownloadStrategy.DIRECT,
                allowMuxFallback = true,
            ),
            audioCandidates = listOf(
                AudioSourceCandidate(
                    id = "audio-128",
                    url = "https://cdn.test/audio",
                    bitrateKbps = 128,
                    sourceContainerHint = "M4A",
                    isDirectM4a = true,
                ),
            ),
            progressiveCandidates = emptyList(),
            muxCandidates = listOf(
                VideoSourceCandidate(
                    id = "video-720",
                    url = "https://cdn.test/video",
                    resolution = "720p",
                    height = 720,
                    sourceContainerHint = "MPEG_4",
                    isProgressiveMp4 = false,
                    isMuxableMp4 = true,
                    headers = mapOf("Referer" to "https://www.youtube.com/"),
                ),
            ),
        )

        assertTrue(plan is DownloadExecutionPlan.MuxVideoAudio)
        assertEquals("MP4 720p", plan.displayLabel)
        assertEquals("https://www.youtube.com/", (plan as DownloadExecutionPlan.MuxVideoAudio).videoSource.headers["Referer"])
    }
}
