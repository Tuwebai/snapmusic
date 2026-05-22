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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

private val PreviewPanelGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF351114),
        Color(0xFF171214),
        Color(0xFF0A0A0B),
    ),
)
private val PreviewArtworkHaloColor = AccentRed.copy(alpha = 0.12f)
private val PreviewVideoBottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x99000000)),
)
private val PreviewArtworkBottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x66000000), Color(0xD009090A)),
)

@Immutable
internal data class PreviewPlaybackState(
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
)

@androidx.media3.common.util.UnstableApi
@Composable
internal fun PreviewPlaybackCard(
    preview: PreviewState,
    player: Player,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onMinimize: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val isVideo = remember(preview.fileUri) { preview.fileUri.isPreviewVideoMedia() }
    if (isVideo) {
        PreviewVideoPlaybackCard(
            preview = preview,
            player = player,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onBack = onBack,
            onMinimize = onMinimize,
            onPrevious = onPrevious,
            onNext = onNext,
        )
        return
    }
    val playback = rememberPlaybackOverlayState(
        player = player,
        mediaId = preview.fileUri,
        showControls = false,
        playingPollIntervalMs = 900L,
        idlePollIntervalMs = 2_500L,
    ).toPreviewPlaybackState()
    val sliderBindings = rememberPlaybackSliderBindings(
        currentPositionMs = playback.positionMs,
        durationMs = playback.durationMs,
        onSeekTo = player::seekTo,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PreviewPanelGradient)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Volver", tint = TextPrimary, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(30.dp))
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .background(PreviewArtworkHaloColor, CircleShape),
            )
            PreviewPlaybackVisual(
                preview = preview,
                player = player,
                isVideo = isVideo,
                modifier = Modifier
                    .size(188.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = preview.title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Slider(
                value = sliderBindings.sliderValue,
                onValueChange = sliderBindings.onValueChange,
                onValueChangeFinished = sliderBindings.onValueChangeFinished,
                valueRange = 0f..sliderBindings.durationMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = AccentRed,
                    activeTrackColor = AccentRed,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatMillis(sliderBindings.displayedPositionMs),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    formatMillis(playback.durationMs),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            IconButton(onClick = onPrevious, enabled = canGoPrevious) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = if (canGoPrevious) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(26.dp),
                )
            }
            Surface(
                shape = CircleShape,
                color = Color(0xFF1F1F22),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shadowElevation = 12.dp,
            ) {
                IconButton(onClick = { player.togglePlayPause() }, modifier = Modifier.size(62.dp)) {
                    Icon(
                        imageVector = if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    tint = if (canGoNext) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun PreviewVideoPlaybackCard(
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
    var isFullscreen by rememberSaveable(preview.fileUri) { mutableStateOf(false) }
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
    val tapInteractionSource = remember { MutableInteractionSource() }

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
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
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
                    .clickable(
                        interactionSource = tapInteractionSource,
                        indication = null,
                    ) { showControls = !showControls },
            ) {
                VideoFullscreenOverlay(
                    playbackState = overlayState,
                    canGoPrevious = canGoPrevious,
                    canGoNext = canGoNext,
                    onBack = onBack,
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
            onDismiss = { isFullscreen = false },
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

@androidx.media3.common.util.UnstableApi
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

@androidx.media3.common.util.UnstableApi
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

    if (preview.fileUri.isPreviewVideoMedia()) {
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

@androidx.media3.common.util.UnstableApi
@Composable
private fun VideoHeroSurface(
    preview: PreviewState,
    player: Player,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
    ) {
        key(preview.fileUri, player) {
            PlayerSurface(
                player = player,
                modifier = Modifier.fillMaxSize(),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                shutterColor = android.graphics.Color.BLACK,
                keepScreenOn = player.playWhenReady,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(92.dp)
                .background(PreviewVideoBottomScrim),
        )
    }
}

@Composable
private fun ArtworkHeroSurface(
    preview: PreviewState,
) {
    val context = LocalContext.current
    val artworkModel = remember(preview.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(preview.thumbnailUrl)
            .crossfade(false)
            .size(720, 720)
            .build()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.18f)
            .background(Color.Black),
    ) {
        if (preview.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = artworkModel,
                contentDescription = preview.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                painter = painterResource(R.drawable.preview_local_music_fallback),
                contentDescription = preview.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(132.dp)
                .background(PreviewArtworkBottomScrim),
        )
    }
}

@Composable
private fun PreviewMiniArtwork(
    preview: PreviewState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val artworkData by produceState<ByteArray?>(
        initialValue = null,
        key1 = preview.fileUri,
        key2 = preview.thumbnailUrl,
    ) {
        value = PlaybackArtworkBadgeHelper.resolve(
            context = context,
            artworkSource = preview.thumbnailUrl.takeIfLocalArtworkSource(),
            mediaSource = preview.fileUri.takeIf { !it.isPreviewVideoMedia() },
            fallbackResId = R.drawable.preview_local_music_fallback,
        )
    }
    val artworkBitmap = remember(artworkData) {
        artworkData?.let { data ->
            BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap()
        }
    }
    if (artworkBitmap != null) {
        Image(
            bitmap = artworkBitmap!!,
            contentDescription = preview.title,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.preview_local_music_fallback),
            contentDescription = preview.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

private fun String?.takeIfLocalArtworkSource(): String? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return value.takeIf {
        val normalized = it.lowercase()
        normalized.startsWith("content://") ||
            normalized.startsWith("file://") ||
            normalized.startsWith("android.resource://")
    }
}

@Composable
private fun ControlsPanel(
    preview: PreviewState,
    playback: PreviewPlaybackState,
    isVideo: Boolean,
    onSeek: (Long) -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val artworkModel = remember(preview.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(preview.thumbnailUrl)
            .crossfade(false)
            .size(100, 100)
            .build()
    }
    val sliderBindings = rememberPlaybackSliderBindings(
        currentPositionMs = playback.positionMs,
        durationMs = playback.durationMs,
        onSeekTo = onSeek,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PreviewPanelGradient)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isVideo) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = preview.title,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = WarningAmber)
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = preview.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(preview.subtitle)
                            val durationText = formatMillis(playback.durationMs)
                            if (durationText.isNotBlank()) {
                                if (isNotBlank()) append(" · ")
                                append(durationText)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Slider(
                    value = sliderBindings.sliderValue,
                    onValueChange = sliderBindings.onValueChange,
                    onValueChangeFinished = sliderBindings.onValueChangeFinished,
                    valueRange = 0f..sliderBindings.durationMs.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = WarningAmber,
                        activeTrackColor = WarningAmber,
                        inactiveTrackColor = Color.White.copy(alpha = 0.14f),
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatMillis(sliderBindings.displayedPositionMs), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(formatMillis(playback.durationMs), color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onReplay) {
                    Icon(Icons.Filled.Replay10, contentDescription = "Retroceder 10 segundos", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.size(10.dp))
                Surface(
                    shape = CircleShape,
                    color = AccentRed,
                ) {
                    IconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                            contentDescription = if (playback.isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.size(10.dp))
                IconButton(onClick = onForward) {
                    Icon(Icons.Filled.FastForward, contentDescription = "Avanzar 10 segundos", tint = TextPrimary, modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

private fun PlaybackOverlayState.toPreviewPlaybackState(): PreviewPlaybackState {
    val safeDuration = durationMs.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    return PreviewPlaybackState(
        isPlaying = isPlaying,
        durationMs = safeDuration,
        positionMs = currentPositionMs.coerceAtLeast(0L).coerceAtMost(
            safeDuration.takeIf { it > 0 } ?: currentPositionMs.coerceAtLeast(0L),
        ),
    )
}

private fun Player.togglePlayPause() {
    playWhenReady = !isPlaying
}

private fun formatMillis(value: Long): String {
    if (value <= 0L) return ""
    val totalSeconds = value / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
@androidx.media3.common.util.UnstableApi
@Composable
private fun PreviewPlaybackVisual(
    preview: PreviewState,
    player: Player,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isVideo) {
        key(preview.fileUri, player) {
            PlayerSurface(
                player = player,
                modifier = modifier.background(Color.Black),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                shutterColor = android.graphics.Color.BLACK,
                keepScreenOn = player.playWhenReady,
            )
        }
        return
    }

    if (preview.thumbnailUrl.isNotBlank()) {
        AsyncImage(
            model = preview.thumbnailUrl,
            contentDescription = preview.title,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            error = painterResource(R.drawable.preview_local_music_fallback),
            fallback = painterResource(R.drawable.preview_local_music_fallback),
        )
        return
    }

    Image(
        painter = painterResource(R.drawable.preview_local_music_fallback),
        contentDescription = preview.title,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
}
