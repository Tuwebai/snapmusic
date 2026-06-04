package com.juan.snapmusic.feature.preview

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juan.snapmusic.R
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.designsystem.WarningAmber
import com.juan.snapmusic.core.model.PreviewState
import com.juan.snapmusic.core.platform.PlaybackArtworkBadgeHelper
import com.juan.snapmusic.feature.player.LandscapeFullscreenVideoDialog
import com.juan.snapmusic.feature.player.PlaybackOverlayState
import com.juan.snapmusic.feature.player.PlayerSurface
import com.juan.snapmusic.feature.player.VideoFullscreenOverlay
import com.juan.snapmusic.feature.player.rememberPlaybackOverlayState
import com.juan.snapmusic.feature.player.rememberPlaybackSliderBindings
import com.juan.snapmusic.feature.player.VideoMiniOverlay
import com.juan.snapmusic.feature.player.DOUBLE_TAP_SEEK_MS
import com.juan.snapmusic.feature.player.seekByClamped
import com.juan.snapmusic.feature.player.videoDoubleTapSeek
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@Composable
internal fun PreviewVideoPlaybackCard(
    preview: PreviewState,
    player: Player,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onMinimize: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    var showControls by rememberSaveable(preview.fileUri) { mutableStateOf(false) }
    var isFullscreen by rememberSaveable(preview.fileUri) { mutableStateOf(true) }
    val pauseAndMinimizeVideo = {
        player.pause()
        player.playWhenReady = false
        isFullscreen = false
        onMinimize()
    }
    val overlayState = rememberPlaybackOverlayState(
        player = player,
        mediaId = preview.fileUri,
        showControls = showControls,
        playingPollIntervalMs = 900L,
        idlePollIntervalMs = 2_500L,
        trackProgress = showControls,
    )
    var hasRenderedFirstFrame by remember(preview.fileUri, player) {
        mutableStateOf(player.videoSize.width > 0)
    }
    var totalVerticalDrag by remember(preview.fileUri) { mutableStateOf(0f) }

    DisposableEffect(player, preview.fileUri) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }
        }
        hasRenderedFirstFrame = player.videoSize.width > 0
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(showControls, preview.fileUri) {
        if (showControls) {
            delay(2400)
            showControls = false
        }
    }

    val minimizeBySwipeState = rememberDraggableState { delta ->
        if (delta > 0f) {
            totalVerticalDrag += delta
        } else {
            totalVerticalDrag = (totalVerticalDrag + delta).coerceAtLeast(0f)
        }
        if (totalVerticalDrag > 120f) {
            totalVerticalDrag = 0f
            onMinimize()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PreviewPanelGradient)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.Black),
        ) {
            if (!hasRenderedFirstFrame && preview.thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = preview.thumbnailUrl,
                    contentDescription = preview.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.preview_local_music_fallback),
                    fallback = painterResource(R.drawable.preview_local_music_fallback),
                )
            }
            if (!isFullscreen) {
                key(preview.fileUri, player) {
                    PlayerSurface(
                        player = player,
                        modifier = Modifier.fillMaxSize(),
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        shutterColor = android.graphics.Color.BLACK,
                        keepScreenOn = player.playWhenReady,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .draggable(
                        state = minimizeBySwipeState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { totalVerticalDrag = 0f },
                    )
                    .videoDoubleTapSeek(
                        onTap = { showControls = !showControls },
                        onSeekBack = { player.seekByClamped(-DOUBLE_TAP_SEEK_MS) },
                        onSeekForward = { player.seekByClamped(DOUBLE_TAP_SEEK_MS) },
                    ),
            ) {
                VideoFullscreenOverlay(
                    playbackState = overlayState,
                    canGoPrevious = canGoPrevious,
                    canGoNext = canGoNext,
                    onBack = pauseAndMinimizeVideo,
                    onPlayPause = { player.togglePlayPause() },
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onMore = null,
                    onSeekTo = player::seekTo,
                    onToggleResize = {
                        showControls = false
                        isFullscreen = true
                    },
                )
            }
        }
        LandscapeFullscreenVideoDialog(
            visible = isFullscreen,
            player = player,
            overlayState = overlayState,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            thumbnailVisible = !hasRenderedFirstFrame && preview.thumbnailUrl.isNotBlank(),
            thumbnail = {
                AsyncImage(
                    model = preview.thumbnailUrl,
                    contentDescription = preview.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = painterResource(R.drawable.preview_local_music_fallback),
                    fallback = painterResource(R.drawable.preview_local_music_fallback),
                )
            },
            onDismiss = pauseAndMinimizeVideo,
            onPlayPause = { player.togglePlayPause() },
            onPrevious = onPrevious,
            onNext = onNext,
            onMore = null,
            onSeekTo = player::seekTo,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = preview.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun PreviewEmptyState() {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Todavía no hay un archivo listo.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Cuando descargues una canción o un video, vas a poder previsualizarlo acá con controles completos.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun PreviewPictureInPictureSurface(
    preview: PreviewState,
    player: Player?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (player != null && preview.fileUri != null && preview.fileUri.isPreviewVideoMedia()) {
            key(preview.fileUri, player) {
                PlayerSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    shutterColor = android.graphics.Color.BLACK,
                    keepScreenOn = player.playWhenReady,
                )
            }
        } else {
            ArtworkHeroSurface(preview = preview)
        }
    }
}
@Composable
internal fun PreviewMiniPlayer(
    preview: PreviewState,
    player: Player?,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isPlaying by remember(preview.fileUri, player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player, preview.fileUri) {
        val currentPlayer = player
        if (currentPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            isPlaying = currentPlayer.isPlaying
            currentPlayer.addListener(listener)
            onDispose { currentPlayer.removeListener(listener) }
        }
    }

    if (preview.isVideo || preview.fileUri.isPreviewVideoMedia()) {
        Surface(
            modifier = modifier.padding(horizontal = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = SurfacePrimary,
            border = BorderStroke(1.dp, BorderSubtle),
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(124.dp)
                    .clickable(onClick = onOpen),
            ) {
                PreviewPictureInPictureSurface(
                    preview = preview,
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.06f)),
                )
                VideoMiniOverlay(
                    isPlaying = isPlaying,
                    canGoPrevious = false,
                    canGoNext = false,
                    onPrevious = {},
                    onPlayPause = {
                        val currentPlayer = player ?: return@VideoMiniOverlay
                        currentPlayer.togglePlayPause()
                        isPlaying = currentPlayer.isPlaying
                    },
                    onNext = {},
                    onOpen = onOpen,
                    onClose = onDismiss,
                )
            }
        }
        return
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(22.dp),
        color = SurfacePrimary,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (preview.fileUri.isPreviewVideoMedia()) {
                PreviewPictureInPictureSurface(
                    preview = preview,
                    player = player,
                    modifier = Modifier
                        .width(148.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else {
                PreviewMiniArtwork(
                    preview = preview,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preview.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Cerrar mini reproductor", tint = TextSecondary)
            }
        }
    }
}
