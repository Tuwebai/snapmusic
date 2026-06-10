package com.juan.snapmusic.feature.preview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.SurfaceElevated

private val DownloadProgressHotRed = Color(0xFFFF3131)
private val DownloadProgressSoftRed = Color(0xFFFF6B6B)

@Composable
internal fun DownloadProgressIndicator(
    progress: Float,
    running: Boolean,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "downloadProgressGradient")
    val gradientOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "downloadProgressGradientOffset",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
    ) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = SurfaceElevated,
            size = size,
            cornerRadius = radius,
        )
        if (safeProgress <= 0f) return@Canvas
        val progressWidth = size.width * safeProgress
        val progressSize = Size(progressWidth, size.height)
        if (running) {
            val span = size.width * 0.82f
            val startX = ((size.width + span) * gradientOffset) - span
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        DownloadProgressHotRed,
                        DownloadProgressSoftRed,
                        DownloadProgressHotRed,
                    ),
                    start = Offset(startX, 0f),
                    end = Offset(startX + span, 0f),
                ),
                size = progressSize,
                cornerRadius = radius,
            )
        } else {
            drawRoundRect(
                color = AccentRed,
                size = progressSize,
                cornerRadius = radius,
            )
        }
    }
}
