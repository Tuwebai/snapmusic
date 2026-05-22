package com.juan.snapmusic.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Immutable
internal data class PlaybackOverlayState(
    val showControls: Boolean = false,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
)

@Composable
internal fun rememberPlaybackOverlayState(
    player: Player?,
    showControls: Boolean,
    mediaId: String? = null,
    playingPollIntervalMs: Long = 500L,
    idlePollIntervalMs: Long = 1_200L,
): PlaybackOverlayState {
    val targetMediaId = mediaId?.takeIf { it.isNotBlank() }
    return produceState(
        initialValue = PlaybackOverlayState(showControls = showControls),
        key1 = player,
        key2 = targetMediaId,
        key3 = showControls,
    ) {
        val currentPlayer = player
        if (currentPlayer == null) {
            value = PlaybackOverlayState(showControls = showControls)
            return@produceState
        }

        fun isTargetMediaActive(): Boolean {
            return targetMediaId == null || currentPlayer.currentMediaItem?.mediaId == targetMediaId
        }

        fun snapshot(): PlaybackOverlayState {
            val targetActive = isTargetMediaActive()
            val currentPositionMs = if (targetActive) currentPlayer.currentPosition.asPlaybackPositionMs() else 0L
            val durationMs = if (targetActive) currentPlayer.duration.asPlaybackDurationMs() else 0L
            val bufferedPositionMs = if (targetActive) {
                currentPlayer.contentBufferedPosition.asPlaybackBufferedMs(
                    fallback = currentPlayer.bufferedPosition,
                )
            } else {
                0L
            }
            val isPlaying = targetActive &&
                (
                    currentPlayer.isPlaying ||
                        (currentPlayer.playWhenReady && currentPlayer.playbackState != Player.STATE_ENDED)
                    )

            return PlaybackOverlayState(
                showControls = showControls,
                isPlaying = isPlaying,
                currentPositionMs = currentPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = durationMs,
            )
        }

        fun publishSnapshot() {
            val nextValue = snapshot()
            if (nextValue != value) {
                value = nextValue
            }
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publishSnapshot()
            }
        }

        currentPlayer.addListener(listener)
        try {
            publishSnapshot()
            while (isActive) {
                publishSnapshot()
                delay(
                    if (value.isPlaying) {
                        playingPollIntervalMs
                    } else {
                        idlePollIntervalMs
                    },
                )
            }
        } finally {
            currentPlayer.removeListener(listener)
        }
    }.value
}

private fun Long.asPlaybackDurationMs(): Long {
    return takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
}

private fun Long.asPlaybackPositionMs(): Long {
    return if (this == C.TIME_UNSET) 0L else coerceAtLeast(0L)
}

private fun Long.asPlaybackBufferedMs(fallback: Long): Long {
    val candidate = takeIf { it != C.TIME_UNSET && it > 0L }
        ?: fallback.takeIf { it != C.TIME_UNSET && it > 0L }
        ?: 0L
    return candidate.coerceAtLeast(0L)
}
