package com.juan.snapmusic.feature.preview

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.juan.snapmusic.R
import com.juan.snapmusic.core.model.PreviewState
import com.juan.snapmusic.feature.player.DOUBLE_TAP_SEEK_MS
import com.juan.snapmusic.feature.player.PlaybackOverlayState
import com.juan.snapmusic.feature.player.PlayerSurface
import com.juan.snapmusic.feature.player.VideoFullscreenOverlay
import com.juan.snapmusic.feature.player.seekByClamped
import com.juan.snapmusic.feature.player.videoDoubleTapSeek

@Composable
internal fun LocalVideoFullscreenHost(
    preview: PreviewState,
    player: Player,
    overlayState: PlaybackOverlayState,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    thumbnailVisible: Boolean,
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    ApplyLocalVideoOrientation(player)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        BackHandler(onBack = onDismiss)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .videoDoubleTapSeek(
                    onTap = { onShowControlsChange(!showControls) },
                    onSeekBack = { player.seekByClamped(-DOUBLE_TAP_SEEK_MS) },
                    onSeekForward = { player.seekByClamped(DOUBLE_TAP_SEEK_MS) },
                ),
        ) {
            if (thumbnailVisible) {
                LocalVideoThumbnail(preview)
            }
            PlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                shutterColor = android.graphics.Color.TRANSPARENT,
                keepContentOnPlayerReset = true,
                keepScreenOn = player.playWhenReady,
            )
            VideoFullscreenOverlay(
                playbackState = overlayState.copy(showControls = showControls),
                canGoPrevious = canGoPrevious,
                canGoNext = canGoNext,
                fullscreenLayout = true,
                onBack = onDismiss,
                onPlayPause = {
                    onShowControlsChange(true)
                    onPlayPause()
                },
                onPrevious = {
                    onShowControlsChange(true)
                    onPrevious()
                },
                onNext = {
                    onShowControlsChange(true)
                    onNext()
                },
                onMore = null,
                onSeekTo = onSeekTo,
                onToggleResize = onDismiss,
            )
        }
    }
}

@Composable
internal fun LocalVideoThumbnail(preview: PreviewState) {
    AsyncImage(
        model = preview.thumbnailUrl,
        contentDescription = preview.title,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
        error = painterResource(R.drawable.preview_local_music_fallback),
        fallback = painterResource(R.drawable.preview_local_music_fallback),
    )
}

@Composable
private fun ApplyLocalVideoOrientation(player: Player) {
    val activity = LocalContext.current.findActivity()
    var orientation by remember(player) {
        mutableStateOf(player.videoSize.toLocalVideoOrientation())
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                orientation = videoSize.toLocalVideoOrientation()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    DisposableEffect(activity) {
        val controller = activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }
    LaunchedEffect(activity, orientation) {
        if (activity?.requestedOrientation != orientation) {
            activity?.requestedOrientation = orientation
        }
    }
}

private fun VideoSize.toLocalVideoOrientation(): Int {
    return if (width > height && width > 0 && height > 0) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    } else if (height > width && width > 0 && height > 0) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    } else {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
