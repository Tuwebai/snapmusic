package com.juan.snapmusic.core.performance

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

private const val PERFORMANCE_LOG_TAG = "SnapMusicPerf"
private const val PERFORMANCE_WINDOW_SIZE = 180
private const val PERFORMANCE_LOG_INTERVAL = 90

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
    private var currentScene = PerformanceScene(screen = "launch")

    private val statsByScene = ConcurrentHashMap<String, SceneStats>()

    fun updateScene(
        screen: String,
        detail: String = "",
    ) {
        currentScene = PerformanceScene(screen = screen, detail = detail)
    }

    fun recordFrame(frameData: FrameData) {
        val scene = currentScene.key
        val stats = statsByScene.getOrPut(scene) { SceneStats() }
        val frames = stats.frames.incrementAndGet()
        if (frameData.isJank) {
            stats.jankFrames.incrementAndGet()
        }
        val durationMs = frameData.frameDurationUiNanos / 1_000_000.0
        stats.lastDurations.addLast(durationMs)
        stats.durationCount.incrementAndGet()
        while (stats.durationCount.get() > PERFORMANCE_WINDOW_SIZE) {
            if (stats.lastDurations.pollFirst() != null) {
                stats.durationCount.decrementAndGet()
            } else {
                stats.durationCount.set(0)
                break
            }
        }
        if (frames % PERFORMANCE_LOG_INTERVAL == 0 && stats.lastDurations.isNotEmpty()) {
            val sorted = stats.lastDurations.toList().sorted()
            val p50 = percentile(sorted, 0.5)
            val p95 = percentile(sorted, 0.95)
            Log.d(
                PERFORMANCE_LOG_TAG,
                "scene=$scene frames=$frames jank=${stats.jankFrames.get()} p50=${p50.format()}ms p95=${p95.format()}ms",
            )
        }
    }

    private fun percentile(
        sorted: List<Double>,
        ratio: Double,
    ): Double {
        if (sorted.isEmpty()) return 0.0
        val index = ((sorted.lastIndex) * ratio).roundToInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun Double.format(): String = ((this * 10.0).roundToInt() / 10.0).toString()
}

private data class PerformanceScene(
    val screen: String,
    val detail: String = "",
) {
    val key: String
        get() = if (detail.isBlank()) screen else "$screen:$detail"
}

private class SceneStats {
    val frames = AtomicInteger(0)
    val jankFrames = AtomicInteger(0)
    val durationCount = AtomicInteger(0)
    val lastDurations = ConcurrentLinkedDeque<Double>()
}
