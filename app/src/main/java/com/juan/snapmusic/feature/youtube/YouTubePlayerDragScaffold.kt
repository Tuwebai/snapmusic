package com.juan.snapmusic.feature.youtube

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private enum class YouTubePlayerDragAnchor {
    Collapsed,
    Expanded,
}

private val MiniPlayerExpandDistance = 280.dp
private val WatchPlayerCollapseDistance = 360.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun YouTubeMiniPlayerExpandableDrag(
    sourceKey: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    YouTubeVerticalPanelDrag(
        sourceKey = sourceKey,
        initialAnchor = YouTubePlayerDragAnchor.Collapsed,
        actionAnchor = YouTubePlayerDragAnchor.Expanded,
        dragDistance = MiniPlayerExpandDistance,
        expandedOffsetSign = -1f,
        enabled = enabled,
        onAction = onExpand,
        modifier = modifier,
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun YouTubeWatchPlayerCollapsibleDrag(
    sourceKey: String,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    YouTubeVerticalPanelDrag(
        sourceKey = sourceKey,
        initialAnchor = YouTubePlayerDragAnchor.Expanded,
        actionAnchor = YouTubePlayerDragAnchor.Collapsed,
        dragDistance = WatchPlayerCollapseDistance,
        expandedOffsetSign = 1f,
        enabled = enabled,
        onAction = onCollapse,
        modifier = modifier,
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeVerticalPanelDrag(
    sourceKey: String,
    initialAnchor: YouTubePlayerDragAnchor,
    actionAnchor: YouTubePlayerDragAnchor,
    dragDistance: Dp,
    expandedOffsetSign: Float,
    enabled: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val state: AnchoredDraggableState<YouTubePlayerDragAnchor> = remember(sourceKey, density) {
        AnchoredDraggableState<YouTubePlayerDragAnchor>(
            initialValue = initialAnchor,
            positionalThreshold = { distance: Float -> distance * 0.35f },
            velocityThreshold = { with(density) { 720.dp.toPx() } },
            snapAnimationSpec = tween<Float>(durationMillis = 190, easing = FastOutSlowInEasing),
            decayAnimationSpec = exponentialDecay<Float>(),
            confirmValueChange = { true },
        )
    }
    BoxWithConstraints(modifier = modifier) {
        val distancePx = with(density) { dragDistance.toPx() }
        LaunchedEffect(state, distancePx) {
            val anchors = DraggableAnchors<YouTubePlayerDragAnchor> {
                YouTubePlayerDragAnchor.Expanded at if (expandedOffsetSign < 0f) -distancePx else 0f
                YouTubePlayerDragAnchor.Collapsed at if (expandedOffsetSign < 0f) 0f else distancePx
            }
            state.updateAnchors(
                anchors,
                state.targetValue,
            )
        }
        LaunchedEffect(state.currentValue) {
            if (state.currentValue == actionAnchor) {
                onAction()
            }
        }
        val rawOffset = state.offset.takeUnless { it.isNaN() } ?: 0f
        val progress = (kotlin.math.abs(rawOffset) / distancePx.coerceAtLeast(1f)).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = rawOffset
                    val scale = if (expandedOffsetSign > 0f) {
                        1f - (progress * 0.035f)
                    } else {
                        1f + (progress * 0.025f)
                    }
                    scaleX = scale
                    scaleY = scale
                    alpha = if (expandedOffsetSign > 0f) 1f - (progress * 0.18f) else 1f
                }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Vertical,
                    enabled = enabled,
                ),
        ) {
            content()
        }
    }
}
