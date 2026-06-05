package com.juan.snapmusic.feature.youtube.playback

import android.os.SystemClock

internal class YouTubeFullscreenController(
    private val minimumTransitionGapMs: Long = 350L,
) {
    private var lastTransitionAtMs = 0L

    fun shouldEnter(isFullscreen: Boolean): Boolean {
        return !isFullscreen && consumeEdge()
    }

    fun shouldExit(isFullscreen: Boolean): Boolean {
        return isFullscreen && consumeEdge()
    }

    private fun consumeEdge(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTransitionAtMs < minimumTransitionGapMs) return false
        lastTransitionAtMs = now
        return true
    }
}
