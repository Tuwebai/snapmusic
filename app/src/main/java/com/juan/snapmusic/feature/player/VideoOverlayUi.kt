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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import coil.request.ImageRequest
import coil.size.Size
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.SeekPreviewFrame
import com.juan.snapmusic.core.model.SeekPreviewFrameset
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val VIDEO_VERTICAL_GESTURE_GAIN = 1.25f

internal enum class VideoAdjustmentKind {
    BRIGHTNESS,
    VOLUME,
}

@Immutable
internal data class VideoAdjustmentFeedback(
    val kind: VideoAdjustmentKind,
    val percent: Int,
)

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun Player.seekByClamped(deltaMs: Long) {
    val durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0L }
    val targetPositionMs = (currentPosition + deltaMs).coerceAtLeast(0L)
    seekTo(durationMs?.let { targetPositionMs.coerceAtMost(it) } ?: targetPositionMs)
}

@Composable
internal fun rememberVideoGestureController(): VideoGestureController {
    val context = LocalContext.current
    return remember(context) { VideoGestureController(context) }
}

internal class VideoGestureController(
    context: Context,
) {
    private val activity = context.findActivity()
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var volumePercent: Float? = null

    fun begin(kind: VideoAdjustmentKind) {
        if (kind == VideoAdjustmentKind.VOLUME) {
            volumePercent = currentVolumePercent()
        }
    }

    fun adjust(kind: VideoAdjustmentKind, delta: Float): VideoAdjustmentFeedback {
        val percent = when (kind) {
            VideoAdjustmentKind.BRIGHTNESS -> adjustBrightness(delta)
            VideoAdjustmentKind.VOLUME -> adjustVolume(delta)
        }
        return VideoAdjustmentFeedback(kind = kind, percent = percent)
    }

    private fun adjustBrightness(delta: Float): Int {
        val window = activity?.window ?: return 0
        val current = currentBrightness(window)
        val next = (current + delta).coerceIn(0.02f, 1f)
        window.attributes = window.attributes.apply {
            screenBrightness = next
        }
        return (next * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun currentBrightness(window: Window): Float {
        val current = window.attributes.screenBrightness
        return if (current in 0f..1f) current else 0.5f
    }

    private fun adjustVolume(delta: Float): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }.coerceAtMost(max - 1)
        val range = (max - min).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(min, max)
        val nextPercent = ((volumePercent ?: currentVolumePercent()) + delta).coerceIn(0f, 1f)
        volumePercent = nextPercent
        val nextVolume = (min + nextPercent * range).roundToInt().coerceIn(min, max)
        if (nextVolume != current) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVolume, 0)
        }
        return (nextPercent * 100f).roundToInt().coerceIn(0, 100)
    }

    private fun currentVolumePercent(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }.coerceAtMost(max - 1)
        val range = (max - min).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(min, max)
        return ((current - min).toFloat() / range.toFloat()).coerceIn(0f, 1f)
    }
}

internal fun Modifier.videoBrightnessVolumeGestures(
    controller: VideoGestureController,
    onFeedback: (VideoAdjustmentFeedback) -> Unit,
): Modifier = pointerInput(controller, onFeedback) {
    var activeKind: VideoAdjustmentKind? = null
    detectVerticalDragGestures(
        onDragStart = { offset ->
            activeKind = if (offset.x < size.width / 2f) {
                VideoAdjustmentKind.BRIGHTNESS
            } else {
                VideoAdjustmentKind.VOLUME
            }
            activeKind?.let(controller::begin)
        },
        onVerticalDrag = { change, dragAmount ->
            val kind = activeKind ?: return@detectVerticalDragGestures
            change.consume()
            val delta = (-dragAmount / size.height.toFloat().coerceAtLeast(1f)) * VIDEO_VERTICAL_GESTURE_GAIN
            onFeedback(controller.adjust(kind, delta))
        },
        onDragCancel = { activeKind = null },
        onDragEnd = { activeKind = null },
    )
}

internal fun Modifier.videoDoubleTapSeek(
    onTap: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
): Modifier = pointerInput(onTap, onSeekBack, onSeekForward) {
    detectTapGestures(
        onTap = { onTap() },
        onDoubleTap = { tapOffset ->
            if (tapOffset.x < size.width / 2f) {
                onSeekBack()
            } else {
                onSeekForward()
            }
        },
    )
}

@Composable
internal fun VideoAdjustmentFeedbackOverlay(
    feedback: VideoAdjustmentFeedback?,
    modifier: Modifier = Modifier,
) {
    if (feedback == null) return
    val label = when (feedback.kind) {
        VideoAdjustmentKind.BRIGHTNESS -> "Brillo"
        VideoAdjustmentKind.VOLUME -> "Volumen"
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.62f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = "${feedback.percent}%",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun SeekPreviewStrip(
    framesets: List<SeekPreviewFrameset>,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val frameset = remember(framesets) { framesets.bestSeekPreviewFrameset() } ?: return
    val frames = remember(frameset, positionMs, durationMs) {
        buildSeekPreviewFrameRow(
            frameset = frameset,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }
    if (frames.isEmpty()) return

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
private fun StoryboardFrameThumbnail(
    frame: SeekPreviewFrame,
    selected: Boolean,
) {
    val context = LocalContext.current
    val request = remember(frame.imageUrl, context) {
        ImageRequest.Builder(context)
            .data(frame.imageUrl)
            .crossfade(false)
            .size(Size.ORIGINAL)
            .memoryCacheKey("seek-preview:${frame.imageUrl}")
            .diskCacheKey("seek-preview:${frame.imageUrl}")
            .build()
    }
    val thumbnailWidth = if (selected) 104.dp else 72.dp
    val thumbnailHeight = thumbnailWidth * (frame.frameHeight.toFloat() / frame.frameWidth.toFloat())
    val pageWidth = thumbnailWidth * (frame.pageWidth.toFloat() / frame.frameWidth.toFloat())
    val pageHeight = thumbnailHeight * (frame.pageHeight.toFloat() / frame.frameHeight.toFloat())
    val offsetX = -thumbnailWidth * (frame.left.toFloat() / frame.frameWidth.toFloat())
    val offsetY = -thumbnailHeight * (frame.top.toFloat() / frame.frameHeight.toFloat())

    Box(
        modifier = Modifier
            .size(width = thumbnailWidth, height = thumbnailHeight)
            .clip(RoundedCornerShape(if (selected) 10.dp else 8.dp))
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

@Composable
internal fun VideoFullscreenOverlay(
    playbackState: PlaybackOverlayState,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    seekPreviewFramesets: List<SeekPreviewFrameset> = emptyList(),
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMore: (() -> Unit)?,
    onSeekTo: (Long) -> Unit,
    onToggleResize: () -> Unit,
) {
    if (!playbackState.showControls) return

    val sliderBindings = rememberPlaybackSliderBindings(
        currentPositionMs = playbackState.currentPositionMs,
        durationMs = playbackState.durationMs,
        bufferedPositionMs = playbackState.bufferedPositionMs,
        onSeekTo = onSeekTo,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayGlyphButton(
                onClick = onBack,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowLeft,
                        contentDescription = "Volver",
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                },
            )
            onMore?.let {
                OverlayGlyphButton(
                    onClick = it,
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Más opciones",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayGlyphButton(
                onClick = onPrevious,
                enabled = canGoPrevious,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = if (canGoPrevious) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(26.dp),
                    )
                },
            )
            OverlayPrimaryButton(
                onClick = onPlayPause,
                icon = {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pausar" else "Reproducir",
                        tint = TextPrimary,
                        modifier = Modifier.size(30.dp),
                    )
                },
            )
            OverlayGlyphButton(
                onClick = onNext,
                enabled = canGoNext,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Siguiente",
                        tint = if (canGoNext) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(26.dp),
                    )
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (sliderBindings.isDragging) {
                SeekPreviewStrip(
                    framesets = seekPreviewFramesets,
                    positionMs = sliderBindings.displayedPositionMs,
                    durationMs = playbackState.durationMs,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White.copy(alpha = 0.22f)),
                )
                if (sliderBindings.bufferedFraction > sliderBindings.playedFraction) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sliderBindings.bufferedFraction)
                            .height(3.dp)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(AccentRed.copy(alpha = 0.34f)),
                    )
                }
                if (sliderBindings.playedFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(sliderBindings.playedFraction)
                            .height(3.dp)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(AccentRed),
                    )
                }
                Slider(
                    value = sliderBindings.sliderValue,
                    onValueChange = sliderBindings.onValueChange,
                    onValueChangeFinished = sliderBindings.onValueChangeFinished,
                    valueRange = 0f..sliderBindings.durationMs.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentRed,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatOverlayMillis(sliderBindings.displayedPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatOverlayMillis(playbackState.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                    OverlayGlyphButton(
                        onClick = onToggleResize,
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Fullscreen,
                                contentDescription = "Cambiar ajuste del video",
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        size = 24.dp,
                    )
                }
            }
        }
    }
}

