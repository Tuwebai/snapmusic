package com.juan.snapmusic.feature.youtube

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val YOUTUBE_FEED_SHIMMER_COUNT = 6
private val ShimmerBase = Color(0xFF171717)
private val ShimmerHighlight = Color(0xFF2A2A2A)

@Composable
internal fun rememberYouTubeFeedShimmerProgress(): Float {
    val transition = rememberInfiniteTransition(label = "youtubeFeedShimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 980, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "youtubeFeedShimmerProgress",
    )
    return progress
}

internal fun LazyListScope.youtubeFeedShimmerItems(progress: Float) {
    items(
        count = YOUTUBE_FEED_SHIMMER_COUNT,
        key = { index -> "youtube_feed_shimmer_$index" },
        contentType = { "youtube_feed_shimmer" },
    ) {
        ShimmerFeedCard(progress = progress)
    }
}

@Composable
private fun ShimmerFeedCard(progress: Float) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp),
    ) {
        val horizontalPadding = 16.dp.toPx()
        val verticalPadding = 2.dp.toPx()
        val thumbnailWidth = 154.dp.toPx()
        val thumbnailHeight = 88.dp.toPx()
        val gap = 12.dp.toPx()
        val iconSize = 26.dp.toPx()
        val textStart = horizontalPadding + thumbnailWidth + gap
        val iconLeft = size.width - horizontalPadding - iconSize
        val textMaxWidth = (iconLeft - gap - textStart).coerceAtLeast(0f)
        val shimmerWidth = size.width * 0.42f
        val shimmerStart = (size.width + shimmerWidth) * progress - shimmerWidth
        val brush = Brush.linearGradient(
            colors = listOf(ShimmerBase, ShimmerHighlight, ShimmerBase),
            start = Offset(shimmerStart, 0f),
            end = Offset(shimmerStart + shimmerWidth, size.height),
        )
        fun rect(
            left: Float,
            top: Float,
            width: Float,
            height: Float,
            radius: Float,
        ) {
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
        rect(
            left = horizontalPadding,
            top = verticalPadding,
            width = thumbnailWidth,
            height = thumbnailHeight,
            radius = 10.dp.toPx(),
        )
        rect(textStart, 15.dp.toPx(), textMaxWidth * 0.92f, 13.dp.toPx(), 5.dp.toPx())
        rect(textStart, 36.dp.toPx(), textMaxWidth * 0.66f, 10.dp.toPx(), 5.dp.toPx())
        rect(textStart, 56.dp.toPx(), textMaxWidth * 0.48f, 10.dp.toPx(), 5.dp.toPx())
        rect(iconLeft, 33.dp.toPx(), iconSize, iconSize, 13.dp.toPx())
    }
}
