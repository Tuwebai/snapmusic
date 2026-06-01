package com.juan.snapmusic.feature.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal fun Modifier.homeTabSwipe(
    canSwipeLeft: Boolean,
    canSwipeRight: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = composed {
    val currentOnSwipeLeft by rememberUpdatedState(onSwipeLeft)
    val currentOnSwipeRight by rememberUpdatedState(onSwipeRight)
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    if (!canSwipeLeft && !canSwipeRight) {
        this
    } else {
        pointerInput(canSwipeLeft, canSwipeRight, thresholdPx) {
            var totalDrag = 0f
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    totalDrag += dragAmount
                    change.consume()
                },
                onDragCancel = { totalDrag = 0f },
                onDragEnd = {
                    when {
                        totalDrag <= -thresholdPx && canSwipeLeft -> currentOnSwipeLeft()
                        totalDrag >= thresholdPx && canSwipeRight -> currentOnSwipeRight()
                    }
                    totalDrag = 0f
                },
            )
        }
    }
}
