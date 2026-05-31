package com.juan.snapmusic.feature.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import kotlinx.coroutines.delay

@Composable
internal fun LandscapeFullscreenVideoDialog(
    visible: Boolean,
    player: Player?,
    overlayState: PlaybackOverlayState,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    thumbnailVisible: Boolean,
    isBuffering: Boolean = false,
    thumbnail: @Composable (() -> Unit)?,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMore: (() -> Unit)?,
    onSeekTo: (Long) -> Unit,
) {
    if (!visible || player == null) return

    val activity = LocalContext.current.findActivity()
    val mediaKey = player.currentMediaItem?.mediaId ?: "fullscreen"
    var showControls by rememberSaveable(mediaKey) { mutableStateOf(true) }
    var hasInteracted by rememberSaveable(mediaKey) { mutableStateOf(false) }

    DisposableEffect(activity, visible) {
        val initialOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = initialOrientation
        }
    }

    LaunchedEffect(visible, mediaKey) {
        if (visible) {
            hasInteracted = false
            showControls = true
        }
    }

    LaunchedEffect(showControls, hasInteracted, mediaKey) {
        if (showControls && hasInteracted) {
            delay(2400)
            showControls = false
        }
    }

    BackHandler(enabled = visible) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .videoDoubleTapSeek(
                        onTap = {
                            hasInteracted = true
                            showControls = !showControls
                        },
                        onSeekBack = {
                            hasInteracted = true
                            player.seekByClamped(-DOUBLE_TAP_SEEK_MS)
                        },
                        onSeekForward = {
                            hasInteracted = true
                            player.seekByClamped(DOUBLE_TAP_SEEK_MS)
                        },
                    ),
            ) {
                if (thumbnailVisible) {
                    thumbnail?.invoke()
                }
                PlayerSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    shutterColor = android.graphics.Color.BLACK,
                    keepScreenOn = player.playWhenReady,
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                if (isBuffering) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.42f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
                VideoFullscreenOverlay(
                    playbackState = overlayState.copy(showControls = showControls),
                    canGoPrevious = canGoPrevious,
                    canGoNext = canGoNext,
                    onBack = {
                        hasInteracted = true
                        showControls = false
                        onDismiss()
                    },
                    onPlayPause = {
                        hasInteracted = true
                        onPlayPause()
                    },
                    onPrevious = {
                        hasInteracted = true
                        onPrevious()
                    },
                    onNext = {
                        hasInteracted = true
                        onNext()
                    },
                    onMore = onMore,
                    onSeekTo = {
                        hasInteracted = true
                        onSeekTo(it)
                    },
                    onToggleResize = {
                        hasInteracted = true
                        showControls = false
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
internal fun VideoMiniOverlay(
    isPlaying: Boolean,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayGlyphButton(
                onClick = onPrevious,
                enabled = canGoPrevious,
                size = 28.dp,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "Anterior",
                        tint = if (canGoPrevious) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
            OverlayPrimaryButton(
                onClick = onPlayPause,
                size = 42.dp,
                icon = {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        tint = TextPrimary,
                        modifier = Modifier.size(21.dp),
                    )
                },
            )
            OverlayGlyphButton(
                onClick = onNext,
                enabled = canGoNext,
                size = 28.dp,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Siguiente",
                        tint = if (canGoNext) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayGlyphButton(
                onClick = onOpen,
                size = 24.dp,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Fullscreen,
                        contentDescription = "Abrir reproductor",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
            OverlayGlyphButton(
                onClick = onClose,
                size = 24.dp,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cerrar reproductor",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
    }
}

@Composable
internal fun OverlayGlyphButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 34.dp,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

@Composable
internal fun OverlayPrimaryButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 54.dp,
    icon: @Composable () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.34f),
        modifier = Modifier.size(size),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            icon()
        }
    }
}

internal fun formatOverlayMillis(value: Long): String {
    if (value <= 0L) return "00:00"
    val totalSeconds = value / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
