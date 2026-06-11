package com.juan.snapmusic.feature.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.media3.common.VideoSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlin.math.abs

private const val WATCH_PLAYER_ASPECT_RATIO = 16f / 9f
private const val LETTERBOX_ASPECT_TOLERANCE = 0.035f

internal fun VideoSize.snapMusicAspectRatio(): Float {
    if (width <= 0 || height <= 0) return 0f
    val ratio = (width * pixelWidthHeightRatio) / height
    return ratio.takeIf(Float::isFinite)?.coerceAtLeast(0f) ?: 0f
}

internal fun shouldShowTheaterBackdrop(
    videoAspectRatio: Float,
    containerAspectRatio: Float = WATCH_PLAYER_ASPECT_RATIO,
): Boolean {
    if (videoAspectRatio <= 0f) return false
    return abs(videoAspectRatio - containerAspectRatio) > LETTERBOX_ASPECT_TOLERANCE
}

@Composable
internal fun YouTubeTheaterBackdrop(
    model: ImageRequest,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .blur(radius = 25.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f)),
        )
    }
}
