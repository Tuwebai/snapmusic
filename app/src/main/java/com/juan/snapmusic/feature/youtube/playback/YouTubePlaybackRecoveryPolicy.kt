package com.juan.snapmusic.feature.youtube.playback

internal data class YouTubePlaybackRecoveryDecision(
    val shouldRecover: Boolean,
    val allowSourceMutation: Boolean,
    val reason: String,
)

internal object YouTubePlaybackRecoveryPolicy {
    const val REBUFFER_WINDOW_MS = 20_000L
    const val LONG_REBUFFER_MS = 2_000L
    private const val MIN_POSITION_FOR_SOURCE_MUTATION_MS = 30_000L

    fun completedRebufferDecision(
        mode: YouTubePlaybackSourceMode,
        positionMs: Long,
        durationMs: Long,
        events: Int,
    ): YouTubePlaybackRecoveryDecision {
        if (durationMs < LONG_REBUFFER_MS && events < 2) {
            return YouTubePlaybackRecoveryDecision(false, false, "short_rebuffer")
        }
        return stallDecision(mode, positionMs, durationMs, events, "completed_rebuffer")
    }

    fun activeStallDecision(
        mode: YouTubePlaybackSourceMode,
        positionMs: Long,
        durationMs: Long,
        events: Int,
    ): YouTubePlaybackRecoveryDecision {
        return stallDecision(mode, positionMs, durationMs, events, "active_stall")
    }

    fun sanitizeProgressPosition(
        reportedPositionMs: Long,
        previousStablePositionMs: Long,
        persist: Boolean,
        playWhenReady: Boolean,
    ): Long {
        val safePosition = reportedPositionMs.coerceAtLeast(0L)
        val transientRestart = !persist &&
            playWhenReady &&
            safePosition <= 1_500L &&
            previousStablePositionMs >= 3_000L
        return if (transientRestart) previousStablePositionMs else safePosition
    }

    private fun stallDecision(
        mode: YouTubePlaybackSourceMode,
        positionMs: Long,
        durationMs: Long,
        events: Int,
        reason: String,
    ): YouTubePlaybackRecoveryDecision {
        if (mode == YouTubePlaybackSourceMode.ADAPTIVE) {
            return YouTubePlaybackRecoveryDecision(
                shouldRecover = false,
                allowSourceMutation = false,
                reason = "${reason}_adaptive_keep_abr",
            )
        }
        val sourceMutationAllowed = positionMs >= MIN_POSITION_FOR_SOURCE_MUTATION_MS &&
            (durationMs >= LONG_REBUFFER_MS || events >= 2)
        return YouTubePlaybackRecoveryDecision(
            shouldRecover = sourceMutationAllowed,
            allowSourceMutation = sourceMutationAllowed,
            reason = if (sourceMutationAllowed) reason else "${reason}_warmup_guard",
        )
    }
}
