package com.juan.snapmusic.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.NavigationBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.AccentRed

private val LiquidBlobHeight = 36.dp
private val LiquidBlobMaxWidth = 76.dp
private val LiquidBlobTopPadding = 6.dp

@Composable
internal fun LiquidBottomNavigationFrame(
    items: List<SnapMusicDestination>,
    currentRoute: String?,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar {
        Box(modifier = Modifier.fillMaxWidth()) {
            LiquidBottomNavigationIndicator(
                itemCount = items.size,
                activeIndex = items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0),
                modifier = Modifier.matchParentSize(),
            )
            Row(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
private fun LiquidBottomNavigationIndicator(
    itemCount: Int,
    activeIndex: Int,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    val blobCenter = remember { Animatable(0f) }
    val safeCount = itemCount.coerceAtLeast(1)
    val targetCenter = if (widthPx > 0) {
        (widthPx / safeCount.toFloat()) * (activeIndex.coerceIn(0, safeCount - 1) + 0.5f)
    } else {
        0f
    }
    LaunchedEffect(targetCenter) {
        if (targetCenter > 0f && blobCenter.value == 0f) {
            blobCenter.snapTo(targetCenter)
        } else {
            blobCenter.animateTo(
                targetValue = targetCenter,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    Canvas(
        modifier = modifier.onSizeChanged { widthPx = it.width },
    ) {
        if (itemCount <= 0 || size.width <= 0f || size.height <= 0f) return@Canvas
        val itemWidth = size.width / safeCount
        val blobWidth = minOf(itemWidth * 0.62f, LiquidBlobMaxWidth.toPx())
        val blobHeight = LiquidBlobHeight.toPx()
        val top = LiquidBlobTopPadding.toPx()
        val left = (blobCenter.value - (blobWidth / 2f)).coerceIn(0f, size.width - blobWidth)
        val corner = CornerRadius(blobHeight / 2f, blobHeight / 2f)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    AccentRed.copy(alpha = 0.10f),
                    AccentRed.copy(alpha = 0.22f),
                    AccentRed.copy(alpha = 0.10f),
                ),
                startX = left,
                endX = left + blobWidth,
            ),
            topLeft = Offset(left, top),
            size = Size(blobWidth, blobHeight),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = Color.White.copy(alpha = 0.04f),
            topLeft = Offset(left + (blobWidth * 0.18f), top + 2.dp.toPx()),
            size = Size(blobWidth * 0.64f, 1.5.dp.toPx()),
            cornerRadius = CornerRadius(999f, 999f),
        )
    }
}
