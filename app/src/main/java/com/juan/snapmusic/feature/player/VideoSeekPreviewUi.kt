package com.juan.snapmusic.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.Window
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.SeekPreviewFrame
import com.juan.snapmusic.core.model.SeekPreviewFrameset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun SeekPreviewStrip(
    framesets: List<SeekPreviewFrameset>,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frameset = remember(framesets) { framesets.bestSeekPreviewFrameset() } ?: return
    val frames = remember(frameset, positionMs, durationMs) {
        buildSeekPreviewFrameRow(
            frameset = frameset,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }
    if (frames.isEmpty()) return
    val frameUrls = remember(frames) { frames.map { it.imageUrl }.distinct() }
    LaunchedEffect(context, frameUrls) {
        frameUrls.forEach { imageUrl ->
            context.imageLoader.enqueue(seekPreviewImageRequest(context, imageUrl))
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.62f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                frames.forEachIndexed { index, frame ->
                    StoryboardFrameThumbnail(
                        frame = frame,
                        selected = index == frames.size / 2,
                    )
                }
            }
            Text(
                text = formatOverlayMillis(positionMs),
                color = TextPrimary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun SeekPreviewPopup(
    framesets: List<SeekPreviewFrameset>,
    positionMs: Long,
    durationMs: Long,
    sliderFraction: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val frameset = remember(framesets) { framesets.bestSeekPreviewFrameset() } ?: return
    val frame = remember(frameset, positionMs, durationMs) {
        frameset.frameAt(positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)))
    } ?: return
    LaunchedEffect(context, frame.imageUrl) {
        context.imageLoader.enqueue(seekPreviewImageRequest(context, frame.imageUrl))
    }
    val previewWidth = 142.dp
    val previewHeight = previewWidth * (frame.frameHeight.toFloat() / frame.frameWidth.toFloat())
    BoxWithConstraints(
        modifier = modifier.height(previewHeight + 34.dp),
    ) {
        val travel = (maxWidth - previewWidth).coerceAtLeast(0.dp)
        val offsetX = travel * sliderFraction.coerceIn(0f, 1f)
        Surface(
            modifier = Modifier.offset(x = offsetX),
            shape = RoundedCornerShape(14.dp),
            color = Color.Black.copy(alpha = 0.72f),
        ) {
            Column(
                modifier = Modifier.padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                StoryboardFrameThumbnail(
                    frame = frame,
                    width = previewWidth,
                    radius = 10.dp,
                )
                Text(
                    text = formatOverlayMillis(frame.positionMs),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StoryboardFrameThumbnail(
    frame: SeekPreviewFrame,
    selected: Boolean,
) {
    StoryboardFrameThumbnail(
        frame = frame,
        width = if (selected) 104.dp else 72.dp,
        radius = if (selected) 10.dp else 8.dp,
    )
}

@Composable
private fun StoryboardFrameThumbnail(
    frame: SeekPreviewFrame,
    width: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
) {
    val context = LocalContext.current
    val request = remember(frame.imageUrl, context) { seekPreviewImageRequest(context, frame.imageUrl) }
    val thumbnailWidth = width
    val thumbnailHeight = width * (frame.frameHeight.toFloat() / frame.frameWidth.toFloat())
    val pageWidth = width * (frame.pageWidth.toFloat() / frame.frameWidth.toFloat())
    val pageHeight = thumbnailHeight * (frame.pageHeight.toFloat() / frame.frameHeight.toFloat())
    val offsetX = -width * (frame.left.toFloat() / frame.frameWidth.toFloat())
    val offsetY = -thumbnailHeight * (frame.top.toFloat() / frame.frameHeight.toFloat())

    Box(
        modifier = Modifier
            .size(width = thumbnailWidth, height = thumbnailHeight)
            .clip(RoundedCornerShape(radius))
            .background(Color.Black),
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .requiredSize(width = pageWidth, height = pageHeight)
                .offset(x = offsetX, y = offsetY),
        )
    }
}

private fun seekPreviewImageRequest(
    context: Context,
    imageUrl: String,
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .crossfade(false)
        .size(Size.ORIGINAL)
        .memoryCacheKey("seek-preview:$imageUrl")
        .diskCacheKey("seek-preview:$imageUrl")
        .build()
}

private fun List<SeekPreviewFrameset>.bestSeekPreviewFrameset(): SeekPreviewFrameset? {
    return filter { frameset ->
        frameset.urls.isNotEmpty() &&
            frameset.frameWidth > 0 &&
            frameset.frameHeight > 0 &&
            frameset.totalCount > 0 &&
            frameset.durationPerFrameMs > 0 &&
            frameset.framesPerPageX > 0 &&
            frameset.framesPerPageY > 0
    }.minWithOrNull(
        compareBy<SeekPreviewFrameset> { it.durationPerFrameMs }
            .thenByDescending { it.frameWidth * it.frameHeight },
    )
}

private fun buildSeekPreviewFrameRow(
    frameset: SeekPreviewFrameset,
    positionMs: Long,
    durationMs: Long,
): List<SeekPreviewFrame> {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val center = positionMs.coerceIn(0L, safeDuration)
    val step = frameset.durationPerFrameMs.toLong().coerceAtLeast(1_000L)
    return (-2..2)
        .mapNotNull { offset ->
            val framePosition = (center + offset * step).coerceIn(0L, safeDuration)
            frameset.frameAt(framePosition)
        }
        .distinctBy { "${it.imageUrl}:${it.left}:${it.top}:${it.right}:${it.bottom}" }
}
