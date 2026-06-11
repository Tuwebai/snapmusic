package com.juan.snapmusic.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot
import kotlin.math.max

private const val RevealDurationMs = 280
private const val RevealStartProgress = 0.001f
private val DownloadButtonOriginFraction = Offset(0.86f, 1f)

@Composable
internal fun Modifier.downloadFormatMaterialReveal(revealKey: String): Modifier {
    val progress = remember(revealKey) { Animatable(RevealStartProgress) }
    LaunchedEffect(revealKey) {
        progress.snapTo(RevealStartProgress)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = RevealDurationMs, easing = FastOutSlowInEasing),
        )
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0.04f, 1f)
    }.clip(
        DownloadFormatRevealShape(
            progress = progress.value,
            originFraction = DownloadButtonOriginFraction,
        ),
    )
}

private data class DownloadFormatRevealShape(
    private val progress: Float,
    private val originFraction: Offset,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val center = Offset(
            x = size.width * originFraction.x.coerceIn(0f, 1f),
            y = size.height * originFraction.y.coerceIn(0f, 1f),
        )
        val radius = maxRevealRadius(size, center) * progress.coerceIn(RevealStartProgress, 1f)
        val path = Path().apply {
            addOval(Rect(center = center, radius = radius))
        }
        return Outline.Generic(path)
    }
}

private fun maxRevealRadius(size: Size, center: Offset): Float {
    return max(
        hypot(center.x, center.y),
        max(
            hypot(size.width - center.x, center.y),
            max(
                hypot(center.x, size.height - center.y),
                hypot(size.width - center.x, size.height - center.y),
            ),
        ),
    )
}
