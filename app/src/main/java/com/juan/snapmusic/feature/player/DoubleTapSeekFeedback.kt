package com.juan.snapmusic.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val DOUBLE_TAP_FEEDBACK_SECONDS = 10
private const val DOUBLE_TAP_FEEDBACK_VISIBLE_MS = 520L
private const val DOUBLE_TAP_FEEDBACK_ANIM_MS = 180
private val DoubleTapArcColor = Color(0x40FF3131)

private enum class DoubleTapSeekDirection {
    Back,
    Forward,
}

private data class DoubleTapSeekFeedbackEvent(
    val direction: DoubleTapSeekDirection,
    val id: Long,
)

@Composable
internal fun DoubleTapSeekGestureLayer(
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    content: @Composable () -> Unit,
) {
    var eventId by remember { mutableLongStateOf(0L) }
    var feedbackEvent by remember { mutableStateOf<DoubleTapSeekFeedbackEvent?>(null) }
    Box(
        modifier = modifier.videoDoubleTapSeek(
            onTap = onTap,
            onSeekBack = {
                onSeekBack()
                feedbackEvent = DoubleTapSeekFeedbackEvent(DoubleTapSeekDirection.Back, ++eventId)
            },
            onSeekForward = {
                onSeekForward()
                feedbackEvent = DoubleTapSeekFeedbackEvent(DoubleTapSeekDirection.Forward, ++eventId)
            },
        ),
    ) {
        content()
        DoubleTapSeekFeedbackOverlay(event = feedbackEvent)
    }
}

@Composable
private fun BoxScope.DoubleTapSeekFeedbackOverlay(event: DoubleTapSeekFeedbackEvent?) {
    if (event == null) return
    val visibleState = remember(event.id) {
        MutableTransitionState(false).apply { targetState = true }
    }
    androidx.compose.runtime.LaunchedEffect(event.id) {
        delay(DOUBLE_TAP_FEEDBACK_VISIBLE_MS)
        visibleState.targetState = false
    }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = scaleIn(
            initialScale = 0.72f,
            animationSpec = tween(DOUBLE_TAP_FEEDBACK_ANIM_MS),
        ) + fadeIn(animationSpec = tween(DOUBLE_TAP_FEEDBACK_ANIM_MS)),
        exit = fadeOut(animationSpec = tween(DOUBLE_TAP_FEEDBACK_ANIM_MS)),
        modifier = Modifier
            .align(if (event.direction == DoubleTapSeekDirection.Back) Alignment.CenterStart else Alignment.CenterEnd)
            .fillMaxHeight()
            .fillMaxWidth(0.48f),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DoubleTapSeekArc(direction = event.direction)
            Text(
                text = if (event.direction == DoubleTapSeekDirection.Back) {
                    "-${DOUBLE_TAP_FEEDBACK_SECONDS}s"
                } else {
                    "+${DOUBLE_TAP_FEEDBACK_SECONDS}s"
                },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun DoubleTapSeekArc(direction: DoubleTapSeekDirection) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 7.dp.toPx()
        val arcSize = Size(size.height * 0.72f, size.height * 0.72f)
        val top = (size.height - arcSize.height) / 2f
        val left = if (direction == DoubleTapSeekDirection.Back) {
            -arcSize.width * 0.42f
        } else {
            size.width - arcSize.width * 0.58f
        }
        drawArc(
            color = DoubleTapArcColor,
            startAngle = if (direction == DoubleTapSeekDirection.Back) -64f else 116f,
            sweepAngle = 128f,
            useCenter = false,
            topLeft = Offset(left, top),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}
