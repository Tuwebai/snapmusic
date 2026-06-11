package com.juan.snapmusic.feature.queue

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import kotlinx.coroutines.delay

private val CompletionFlashColor = Color(0xFF25D366).copy(alpha = 0.28f)
private val CompletionProgressColor = Color(0xFF25D366)
private const val CompletionFlashDelayMs = 300L
private const val CompletionFlashDurationMs = 220L

@Immutable
internal data class DownloadCompletionAnimationState(
    val cardColor: Color,
    val progressFraction: Float,
    val barAlpha: Float,
)

@Composable
internal fun rememberDownloadCompletionAnimation(
    itemId: String,
    isComplete: Boolean,
    progressFraction: Float,
): DownloadCompletionAnimationState {
    val safeProgress = progressFraction.coerceIn(0f, 1f)
    val animatedProgress = remember(itemId) { Animatable(safeProgress) }
    var flashVisible by remember(itemId) { mutableStateOf(false) }
    var barVisible by remember(itemId) { mutableStateOf(true) }

    LaunchedEffect(itemId, isComplete, safeProgress) {
        if (isComplete) {
            val start = if (safeProgress >= 0.99f) 0.92f else safeProgress.coerceIn(0f, 0.99f)
            barVisible = true
            flashVisible = false
            animatedProgress.snapTo(start)
            animatedProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            )
            delay(CompletionFlashDelayMs)
            flashVisible = true
            delay(CompletionFlashDurationMs)
            flashVisible = false
            barVisible = false
        } else {
            flashVisible = false
            barVisible = true
            animatedProgress.animateTo(safeProgress, animationSpec = tween(durationMillis = 120))
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (flashVisible) CompletionFlashColor else SurfacePrimary,
        animationSpec = tween(durationMillis = 160),
        label = "downloadCompletionFlash",
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (barVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "downloadCompletionBarFade",
    )
    return DownloadCompletionAnimationState(
        cardColor = cardColor,
        progressFraction = animatedProgress.value.coerceIn(0f, 1f),
        barAlpha = barAlpha,
    )
}

@Composable
internal fun DownloadCompletionProgressBar(
    animation: DownloadCompletionAnimationState,
    isComplete: Boolean,
    modifier: Modifier = Modifier,
) {
    if (animation.barAlpha <= 0.01f) return
    LinearProgressIndicator(
        progress = { animation.progressFraction },
        modifier = modifier
            .graphicsLayer { alpha = animation.barAlpha }
            .clip(RoundedCornerShape(999.dp)),
        color = if (isComplete) CompletionProgressColor else AccentRed,
        trackColor = SurfaceElevated,
    )
}
