package com.juan.snapmusic.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

internal data class PlaybackSliderBindings(
    val sliderValue: Float,
    val displayedPositionMs: Long,
    val durationMs: Long,
    val isDragging: Boolean,
    val playedFraction: Float,
    val bufferedFraction: Float,
    val onValueChange: (Float) -> Unit,
    val onValueChangeFinished: () -> Unit,
)

@Composable
internal fun rememberPlaybackSliderBindings(
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = 0L,
    onSeekTo: (Long) -> Unit,
): PlaybackSliderBindings {
    var sliderValue by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPositionMs, durationMs, isDragging) {
        if (!isDragging) {
            sliderValue = currentPositionMs.coerceIn(0L, durationMs.takeIf { it > 0L } ?: 0L)
        }
    }

    val safeDurationMs = durationMs.takeIf { it > 0L } ?: 1L
    val displayedPositionMs = if (isDragging) sliderValue else currentPositionMs.coerceAtLeast(0L)
    val playedFraction = (displayedPositionMs.toFloat() / safeDurationMs.toFloat()).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedPositionMs.coerceAtLeast(0L).toFloat() / safeDurationMs.toFloat())
        .coerceIn(playedFraction, 1f)

    return PlaybackSliderBindings(
        sliderValue = sliderValue.toFloat(),
        displayedPositionMs = displayedPositionMs,
        durationMs = safeDurationMs,
        isDragging = isDragging,
        playedFraction = playedFraction,
        bufferedFraction = bufferedFraction,
        onValueChange = { nextValue ->
            isDragging = true
            sliderValue = nextValue.toLong()
        },
        onValueChangeFinished = {
            isDragging = false
            onSeekTo(sliderValue)
        },
    )
}
