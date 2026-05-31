package com.juan.snapmusic.core.performance

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.metrics.performance.FrameData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val PERFORMANCE_LOG_TAG = "SnapMusicPerf"
private const val PERFORMANCE_LOG_INTERVAL = 240

@Composable
fun ReportPerformanceScene(
    screen: String,
    detail: String = "",
) {
    SideEffect {
        PerformanceTelemetry.updateScene(screen = screen, detail = detail)
    }
}

object PerformanceTelemetry {
    @Volatile
    private var currentSceneKey = "launch"

    private val statsByScene = ConcurrentHashMap<String, SceneStats>()

    fun updateScene(
        screen: String,
        detail: String = "",
    ) {
        currentSceneKey = if (detail.isBlank()) screen else "$screen:$detail"
    }

    fun recordFrame(frameData: FrameData) {
        val scene = currentSceneKey
        val stats = statsByScene.getOrPut(scene) { SceneStats() }
        val frames = stats.frames.incrementAndGet()
        if (frameData.isJank) {
            stats.jankFrames.incrementAndGet()
        }
        if (frames % PERFORMANCE_LOG_INTERVAL == 0) {
            Log.d(
                PERFORMANCE_LOG_TAG,
                "scene=$scene frames=$frames jank=${stats.jankFrames.get()} last=${frameData.frameDurationUiNanos / 1_000_000}ms",
            )
        }
    }
}

private class SceneStats {
    val frames = AtomicInteger(0)
    val jankFrames = AtomicInteger(0)
}
