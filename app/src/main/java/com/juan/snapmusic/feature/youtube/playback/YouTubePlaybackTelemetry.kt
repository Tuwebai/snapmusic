package com.juan.snapmusic.feature.youtube.playback

import android.util.Log

internal object YouTubePlaybackTelemetry {
    private const val TAG = "SnapMusicYouTube"

    fun source(sourceUrl: String, mode: YouTubePlaybackSourceMode?, heights: List<String>, adaptive: Boolean) {
        Log.d(TAG, "source=$sourceUrl playback=${mode?.name.orEmpty()} heights=$heights adaptive=$adaptive")
    }

    fun rebuffer(
        sourceUrl: String,
        mode: YouTubePlaybackSourceMode,
        durationMs: Long,
        positionMs: Long,
        events: Int,
    ) {
        Log.w(TAG, "source=$sourceUrl rebuffer durationMs=$durationMs positionMs=$positionMs events=$events mode=${mode.name}")
    }

    fun stall(
        sourceUrl: String,
        mode: YouTubePlaybackSourceMode,
        durationMs: Long,
        positionMs: Long,
        events: Int,
    ) {
        Log.w(TAG, "source=$sourceUrl activeStall durationMs=$durationMs positionMs=$positionMs events=$events mode=${mode.name}")
    }

    fun recoverySkipped(sourceUrl: String, reason: String, mode: YouTubePlaybackSourceMode, durationMs: Long, positionMs: Long) {
        Log.w(TAG, "source=$sourceUrl stallRecoverySkipped reason=$reason durationMs=$durationMs positionMs=$positionMs mode=${mode.name}")
    }

    fun fallback(sourceUrl: String, from: YouTubePlaybackSourceMode, to: YouTubePlaybackSelection, cause: String?) {
        Log.w(TAG, "source=$sourceUrl fallback=${from.name}->${to.sourceMode.name} qualityHeight=${to.expectedHeight ?: 0} cause=${cause.orEmpty()}")
    }

    fun adaptiveRecovery(sourceUrl: String, from: YouTubePlaybackSourceMode, to: YouTubePlaybackSelection, durationMs: Long, positionMs: Long) {
        Log.w(TAG, "source=$sourceUrl adaptiveRecovery=${from.name}->ADAPTIVE:${to.expectedHeight ?: 0} durationMs=$durationMs positionMs=$positionMs")
    }

    fun refreshAdaptive(sourceUrl: String, cause: String?) {
        Log.w(TAG, "source=$sourceUrl refreshAdaptive cause=${cause.orEmpty()}")
    }

    fun fullscreen(sourceUrl: String, fullscreen: Boolean) {
        Log.d(TAG, "source=$sourceUrl fullscreen=$fullscreen")
    }
}
