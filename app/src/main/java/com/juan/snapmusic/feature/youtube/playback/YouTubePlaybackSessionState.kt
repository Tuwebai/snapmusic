package com.juan.snapmusic.feature.youtube.playback

internal data class YouTubePlaybackSessionState(
    val sourceUrl: String = "",
    val playbackUrl: String? = null,
    val sourceMode: YouTubePlaybackSourceMode? = null,
    val selectedQualityId: String = "auto",
    val stablePositionMs: Long = 0L,
    val rebufferCount: Int = 0,
) {
    fun withProgress(positionMs: Long): YouTubePlaybackSessionState {
        return copy(stablePositionMs = maxOf(stablePositionMs, positionMs.coerceAtLeast(0L)))
    }
}
