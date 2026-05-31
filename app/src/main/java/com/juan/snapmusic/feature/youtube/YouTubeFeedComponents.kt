package com.juan.snapmusic.feature.youtube


import android.graphics.Color
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BackgroundSecondary
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.designsystem.WarningAmber
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.platform.formatDuration
import com.juan.snapmusic.feature.player.VideoFullscreenOverlay
import com.juan.snapmusic.feature.player.LandscapeFullscreenVideoDialog
import com.juan.snapmusic.feature.player.PlaybackOverlayState
import com.juan.snapmusic.feature.player.PlayerSurface
import com.juan.snapmusic.feature.player.rememberPlaybackOverlayState
import com.juan.snapmusic.feature.player.DOUBLE_TAP_SEEK_MS
import com.juan.snapmusic.feature.player.seekByClamped
import com.juan.snapmusic.feature.player.videoDoubleTapSeek
import androidx.compose.ui.text.style.TextOverflow
import java.text.DecimalFormat
import kotlinx.coroutines.delay

private val WatchPlayerHeight = 304.dp

@Composable
fun HeroLoadingState() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WatchPlayerHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfacePrimary),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = WarningAmber)
        }
        Text("Cargando videos reales de YouTube...", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouTubeSearchPanel(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPreset: (String) -> Unit,
) {
    val presets = listOf("Cumbia 2025", "Cuarteto en vivo", "Mix DJ", "Enganchados", "Roze Oficial")
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Explorar más", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Buscar otro video o artista") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onSearch, enabled = !isLoading) {
                        Icon(Icons.Outlined.Search, contentDescription = "Buscar")
                    }
                },
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(18.dp))
                    .size(52.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onPreset(preset) },
                    color = BackgroundSecondary,
                ) {
                    Text(
                        text = preset,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun FeaturedVideoCard(
    featured: YouTubeFeaturedVideo,
    player: Player?,
    isDownloadEnabled: Boolean,
    autoplayEnabled: Boolean,
    nextUpLabel: String?,
    onDownload: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackToFeed: () -> Unit,
    onMinimizeVideo: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onSwitchQuality: (String) -> Unit,
) {
    val context = LocalContext.current
    var showFullscreenShell by rememberSaveable(featured.sourceUrl) { mutableStateOf(false) }
    val featuredThumbnailModel = remember(featured.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(featured.thumbnailUrl)
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(720, 405)
            .build()
    }
    val featuredAvatarModel = remember(featured.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(featured.thumbnailUrl)
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(84, 84)
            .build()
    }
    var sheetMode by remember(featured.sourceUrl) { mutableStateOf<WatchSheetMode?>(null) }
    val qualityOptions = remember(featured.playbackUrl, featured.resolvedMedia) { featured.toWatchQualityOptions() }
    val currentQualityLabel = remember(featured.playbackUrl, featured.resolvedMedia) {
        featured.currentQualityLabel()
    }
    var playbackSpeed by remember(featured.sourceUrl, player) { mutableStateOf(player?.playbackParameters?.speed ?: 1f) }
    var loopEnabled by remember(featured.sourceUrl, player) { mutableStateOf(player?.repeatMode == Player.REPEAT_MODE_ONE) }
    var subtitlesAvailable by remember(featured.sourceUrl, player) {
        mutableStateOf(player?.currentTracks?.groups?.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 } == true)
    }

    DisposableEffect(player, featured.sourceUrl) {
        val currentPlayer = player
        if (currentPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    playbackSpeed = playbackParameters.speed
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    loopEnabled = repeatMode == Player.REPEAT_MODE_ONE
                }
            }
            playbackSpeed = currentPlayer.playbackParameters.speed
            loopEnabled = currentPlayer.repeatMode == Player.REPEAT_MODE_ONE
            subtitlesAvailable = currentPlayer.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            currentPlayer.addListener(listener)
            onDispose { currentPlayer.removeListener(listener) }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeaturedVideoPlayerShell(
            featured = featured,
            player = player,
            isFullscreen = showFullscreenShell,
            featuredThumbnailModel = featuredThumbnailModel,
            onPrevious = onPrevious,
            onNext = onNext,
            onMinimizeVideo = onMinimizeVideo,
            onEnterFullscreen = { showFullscreenShell = true },
            onDismissFullscreen = { showFullscreenShell = false },
            onOpenWatchSheet = { sheetMode = WatchSheetMode.MAIN },
        )

        if (!showFullscreenShell) {
            FeaturedVideoMetadataPanel(
                featured = featured,
                featuredAvatarModel = featuredAvatarModel,
                isDownloadEnabled = isDownloadEnabled,
                autoplayEnabled = autoplayEnabled,
                nextUpLabel = nextUpLabel,
                onDownload = onDownload,
            )
        }
    }

    if (sheetMode != null) {
        WatchStreamOptionsSheet(
            mode = sheetMode!!,
            qualityOptions = qualityOptions,
            currentQualityLabel = currentQualityLabel,
            selectedQualityId = featured.selectedVideoQualityId,
            playbackSpeed = playbackSpeed,
            autoplayEnabled = autoplayEnabled,
            loopEnabled = loopEnabled,
            subtitlesAvailable = subtitlesAvailable,
            onDismiss = { sheetMode = null },
            onBack = { sheetMode = WatchSheetMode.MAIN },
            onOpenQuality = { if (qualityOptions.size > 1) sheetMode = WatchSheetMode.QUALITY },
            onOpenSpeed = { sheetMode = WatchSheetMode.SPEED },
            onToggleAutoplay = {
                onToggleAutoplay()
                sheetMode = null
            },
            onToggleLoop = {
                player?.repeatMode = if (loopEnabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                sheetMode = null
            },
            onQualitySelected = { variantId ->
                onSwitchQuality(variantId)
                sheetMode = null
            },
            onSpeedSelected = { speed ->
                player?.setPlaybackParameters(PlaybackParameters(speed))
                sheetMode = null
            },
        )
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun FeaturedVideoPlayerShell(
    featured: YouTubeFeaturedVideo,
    player: Player?,
    isFullscreen: Boolean,
    featuredThumbnailModel: ImageRequest,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMinimizeVideo: () -> Unit,
    onEnterFullscreen: () -> Unit,
    onDismissFullscreen: () -> Unit,
    onOpenWatchSheet: () -> Unit,
) {
    var showOverlayControls by rememberSaveable(featured.sourceUrl) { mutableStateOf(false) }
    val overlayState = rememberPlaybackOverlayState(
        player = player,
        mediaId = featured.sourceUrl,
        showControls = showOverlayControls,
    )
    var hasRenderedFirstFrame by remember(featured.sourceUrl, player) {
        mutableStateOf(player?.videoSize?.width?.let { it > 0 } == true)
    }
    var isBuffering by remember(featured.sourceUrl, player) {
        mutableStateOf(
            player?.let { currentPlayer ->
                currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                    currentPlayer.playWhenReady &&
                    currentPlayer.playbackState == Player.STATE_BUFFERING
            } == true,
        )
    }
    var totalVerticalDrag by remember(featured.sourceUrl) { mutableStateOf(0f) }
    val minimizeBySwipeState = rememberDraggableState { delta ->
        if (delta > 0f) {
            totalVerticalDrag += delta
        } else {
            totalVerticalDrag = (totalVerticalDrag + delta).coerceAtLeast(0f)
        }
        if (totalVerticalDrag > 120f) {
            totalVerticalDrag = 0f
            onMinimizeVideo()
        }
    }

    DisposableEffect(player, featured.sourceUrl) {
        val currentPlayer = player
        if (currentPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering =
                        currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                            currentPlayer.playWhenReady &&
                            playbackState == Player.STATE_BUFFERING
                }

                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                }
            }
            hasRenderedFirstFrame =
                currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                    currentPlayer.videoSize.width > 0
            isBuffering =
                currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                    currentPlayer.playWhenReady &&
                    currentPlayer.playbackState == Player.STATE_BUFFERING
            currentPlayer.addListener(listener)
            onDispose { currentPlayer.removeListener(listener) }
        }
    }

    LaunchedEffect(showOverlayControls, featured.sourceUrl) {
        if (showOverlayControls) {
            delay(2400)
            showOverlayControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WatchPlayerHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        if (!hasRenderedFirstFrame) {
            AsyncImage(
                model = featuredThumbnailModel,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (!isFullscreen && player != null && featured.playbackUrl != null) {
            key(featured.sourceUrl, player) {
                PlayerSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (hasRenderedFirstFrame) 1f else 0f),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    keepContentOnPlayerReset = true,
                    shutterColor = Color.TRANSPARENT,
                    keepScreenOn = player.playWhenReady,
                )
            }
        }
        if (isBuffering) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.42f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp),
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
                    onTap = { showOverlayControls = !showOverlayControls },
                    onSeekBack = { player?.seekByClamped(-DOUBLE_TAP_SEEK_MS) },
                    onSeekForward = { player?.seekByClamped(DOUBLE_TAP_SEEK_MS) },
                )
        ) {
            FeaturedVideoOverlayHost(
                overlayState = overlayState,
                onBack = {
                    showOverlayControls = false
                    onMinimizeVideo()
                },
                onPlayPause = {
                    player?.let { currentPlayer ->
                        if (currentPlayer.isPlaying) {
                            currentPlayer.pause()
                            currentPlayer.playWhenReady = false
                        } else {
                            currentPlayer.playWhenReady = true
                            currentPlayer.play()
                        }
                    }
                },
                onPrevious = {
                    onPrevious()
                    showOverlayControls = false
                },
                onNext = {
                    onNext()
                    showOverlayControls = false
                },
                onMore = onOpenWatchSheet,
                onSeekTo = { seekPosition -> player?.seekTo(seekPosition) },
                onToggleResize = {
                    showOverlayControls = false
                    onEnterFullscreen()
                },
            )
        }
    }

    FeaturedVideoFullscreenShell(
        visible = isFullscreen,
        featured = featured,
        player = player,
        overlayState = overlayState,
        featuredThumbnailModel = featuredThumbnailModel,
        thumbnailVisible = !hasRenderedFirstFrame,
        isBuffering = isBuffering,
        onDismiss = onDismissFullscreen,
        onPlayPause = {
            player?.let { currentPlayer ->
                if (currentPlayer.isPlaying) {
                    currentPlayer.pause()
                    currentPlayer.playWhenReady = false
                } else {
                    currentPlayer.playWhenReady = true
                    currentPlayer.play()
                }
            }
        },
        onPrevious = onPrevious,
        onNext = onNext,
        onMore = onOpenWatchSheet,
        onSeekTo = { seekPosition -> player?.seekTo(seekPosition) },
    )
}

@Composable
private fun FeaturedVideoOverlayHost(
    overlayState: PlaybackOverlayState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleResize: () -> Unit,
) {
    VideoFullscreenOverlay(
        playbackState = overlayState,
        canGoPrevious = true,
        canGoNext = true,
        onBack = onBack,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onMore = onMore,
        onSeekTo = onSeekTo,
        onToggleResize = onToggleResize,
    )
}

@Composable
private fun FeaturedVideoFullscreenShell(
    visible: Boolean,
    featured: YouTubeFeaturedVideo,
    player: Player?,
    overlayState: PlaybackOverlayState,
    featuredThumbnailModel: ImageRequest,
    thumbnailVisible: Boolean,
    isBuffering: Boolean,
    onDismiss: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
) {
    LandscapeFullscreenVideoDialog(
        visible = visible,
        player = player,
        overlayState = overlayState,
        canGoPrevious = true,
        canGoNext = true,
        thumbnailVisible = thumbnailVisible,
        isBuffering = isBuffering,
        thumbnail = {
            AsyncImage(
                model = featuredThumbnailModel,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        },
        onDismiss = onDismiss,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onMore = onMore,
        onSeekTo = onSeekTo,
    )
}

@Composable
private fun FeaturedVideoMetadataPanel(
    featured: YouTubeFeaturedVideo,
    featuredAvatarModel: ImageRequest,
    isDownloadEnabled: Boolean,
    autoplayEnabled: Boolean,
    nextUpLabel: String?,
    onDownload: () -> Unit,
) {
    val cinematicBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                AccentRed.copy(alpha = 0.12f),
                SurfacePrimary.copy(alpha = 0.98f),
                SurfacePrimary,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfacePrimary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(cinematicBrush),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AsyncImage(
                    model = featuredAvatarModel,
                    contentDescription = featured.title,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape),
                    filterQuality = FilterQuality.Low,
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(featured.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(featuredMeta(featured), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }

            nextUpLabel?.takeIf { autoplayEnabled }?.let { title ->
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sigue: $title",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDownload,
                    enabled = isDownloadEnabled,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        contentColor = SurfacePrimary,
                        disabledContainerColor = AccentRed.copy(alpha = 0.4f),
                        disabledContentColor = SurfacePrimary,
                    ),
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(
                        text = if (isDownloadEnabled) "Descargar" else "Preparando descarga...",
                        modifier = Modifier.padding(start = 8.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun PictureInPicturePlayerSurface(
    featured: YouTubeFeaturedVideo,
    player: Player?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (player != null && featured.playbackUrl != null) {
            key(featured.sourceUrl, player) {
                PlayerSurface(
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    keepContentOnPlayerReset = true,
                    shutterColor = Color.TRANSPARENT,
                    keepScreenOn = player.playWhenReady,
                )
            }
        } else {
            CircularProgressIndicator(color = WarningAmber)
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun YouTubeMiniPlayer(
    featured: YouTubeFeaturedVideo,
    player: Player?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onToggleCompact: () -> Unit,
) {
    val context = LocalContext.current
    val miniThumbnailModel = remember(featured.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(featured.thumbnailUrl)
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(320, 180)
            .build()
    }
    Surface(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfacePrimary,
        border = BorderStroke(1.dp, BorderSubtle),
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                YouTubeMiniPlayerVideoShell(
                    featured = featured,
                    player = player,
                    compact = compact,
                    thumbnailModel = miniThumbnailModel,
                    onOpen = onOpen,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = featured.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = featuredMeta(featured),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cerrar",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = BackgroundSecondary,
                    modifier = Modifier.clickable(onClick = onShare),
                ) {
                    Text(
                        text = "Compartir",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = AccentRed,
                    modifier = Modifier.clickable(onClick = onDownload),
                ) {
                    Text(
                        text = "Descargar",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun YouTubeMiniPlayerVideoShell(
    featured: YouTubeFeaturedVideo,
    player: Player?,
    compact: Boolean,
    thumbnailModel: ImageRequest,
    onOpen: () -> Unit,
) {
    var hasRenderedFirstFrame by remember(featured.sourceUrl, player) {
        mutableStateOf(player?.videoSize?.width?.let { it > 0 } == true)
    }
    DisposableEffect(player, featured.sourceUrl) {
        val currentPlayer = player
        if (currentPlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    hasRenderedFirstFrame = true
                }
            }
            hasRenderedFirstFrame =
                currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                    currentPlayer.videoSize.width > 0
            currentPlayer.addListener(listener)
            onDispose { currentPlayer.removeListener(listener) }
        }
    }
    Box(
        modifier = Modifier
            .width(if (compact) 112.dp else 132.dp)
            .height(if (compact) 64.dp else 74.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen),
    ) {
        if (!hasRenderedFirstFrame) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        PictureInPicturePlayerSurface(
            featured = featured,
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (hasRenderedFirstFrame) 1f else 0f),
        )
    }
}

@Composable
fun SuggestionsHeader() {
    Text(
        text = "Seguí mirando",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
    )
}

private data class WatchQualityOption(
    val id: String,
    val label: String,
    val targetHeight: Int? = null,
    val mode: PlaybackQualityMode = PlaybackQualityMode.PROGRESSIVE_WITH_AUDIO,
)

private enum class PlaybackQualityMode {
    AUTO,
    ADAPTIVE_EXACT,
    PROGRESSIVE_WITH_AUDIO,
}

private enum class WatchSheetMode {
    MAIN,
    QUALITY,
    SPEED,
}

private fun YouTubeFeaturedVideo.toWatchQualityOptions(): List<WatchQualityOption> {
    val resolved = resolvedMedia ?: return emptyList()
    val adaptivePlayback = resolved.adaptivePlaybackUrl?.let { watchHasAdaptivePlaybackUrl(it) } == true
    val preferredHeights = listOf(1080, 720, 480, 360, 240, 144)
    val automaticPreferredHeights = listOf(720, 1080, 480, 360, 240, 144)
    val variantsByHeight = resolved.videoVariants
        .mapNotNull { variant ->
            val height = variant.resolution?.substringBefore('p')?.toIntOrNull() ?: return@mapNotNull null
            height to variant
        }
        .groupBy({ it.first }, { it.second })
    val adaptiveHeights = if (adaptivePlayback) {
        availablePlaybackHeights.ifEmpty { variantsByHeight.keys.toList() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
    } else {
        emptyList()
    }
    return buildList {
        val automaticHeight = automaticPreferredHeights.firstOrNull { it in adaptiveHeights || it in variantsByHeight }
            ?: adaptiveHeights.maxOrNull()
            ?: variantsByHeight.keys.maxOrNull()
        if (!resolved.playbackUrl.isNullOrBlank() || automaticHeight != null) {
            add(
                WatchQualityOption(
                    id = "auto",
                    label = automaticHeight?.let { "Automático · ${it}P" } ?: "Automático",
                    targetHeight = automaticHeight,
                    mode = PlaybackQualityMode.AUTO,
                ),
            )
        }
        if (adaptivePlayback && adaptiveHeights.isNotEmpty()) {
            adaptiveHeights.forEach { target ->
                add(
                    WatchQualityOption(
                        id = "adaptive-$target",
                        label = watchQualityLabel(target),
                        targetHeight = target,
                        mode = PlaybackQualityMode.ADAPTIVE_EXACT,
                    ),
                )
            }
        } else {
            preferredHeights.forEach { target ->
                variantsByHeight[target]?.firstOrNull()?.let { variant ->
                    add(
                        WatchQualityOption(
                            id = variant.id,
                            label = watchQualityLabel(target),
                            targetHeight = target,
                        ),
                    )
                }
            }
        }
        if (size <= 1) {
            variantsByHeight.keys
                .sortedDescending()
                .forEach { height ->
                    variantsByHeight[height]?.firstOrNull()?.let { variant ->
                        if (none { it.id == variant.id }) {
                            add(
                                WatchQualityOption(
                                    id = variant.id,
                                    label = watchQualityLabel(height),
                                    targetHeight = height,
                                ),
                            )
                        }
                    }
                }
        }
    }.distinctBy { it.id }
}

private fun YouTubeFeaturedVideo.currentQualityLabel(): String {
    val options = toWatchQualityOptions()
    return when {
        actualPlaybackLabel != null -> actualPlaybackLabel
        selectedVideoQualityId == "auto" -> options.firstOrNull { it.id == "auto" }?.label ?: "Automático"
        else -> "Aplicando calidad..."
    }
}

private fun watchQualityLabel(height: Int): String = when {
    height >= 1080 -> "Muy alto · ${height}P HD"
    height >= 720 -> "Alta · ${height}P HD"
    height >= 480 -> "Media · ${height}P"
    else -> "Baja · ${height}P"
}

private fun watchHasAdaptivePlaybackUrl(url: String): Boolean {
    return url.isNotBlank()
}

@Composable
private fun OverlayControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        color = SurfaceElevated.copy(alpha = 0.92f),
    ) {
        Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchStreamOptionsSheet(
    mode: WatchSheetMode,
    qualityOptions: List<WatchQualityOption>,
    currentQualityLabel: String,
    selectedQualityId: String,
    playbackSpeed: Float,
    autoplayEnabled: Boolean,
    loopEnabled: Boolean,
    subtitlesAvailable: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onToggleLoop: () -> Unit,
    onQualitySelected: (String) -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfacePrimary,
        contentColor = TextPrimary,
    ) {
        when (mode) {
            WatchSheetMode.MAIN -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatchSheetRow(
                        title = "Calidad",
                        value = currentQualityLabel,
                        enabled = qualityOptions.isNotEmpty(),
                        onClick = onOpenQuality,
                    )
                    WatchSheetRow(
                        title = "Subtítulos",
                        value = if (subtitlesAvailable) "Disponibles en este stream" else "No disponibles",
                        enabled = false,
                        onClick = {},
                    )
                    WatchSheetRow(
                        title = "Velocidad de playback",
                        value = "${DecimalFormat("0.##").format(playbackSpeed)}X",
                        onClick = onOpenSpeed,
                    )
                    WatchSheetRow(
                        title = "Autoreproducción",
                        value = if (autoplayEnabled) "Encendido" else "Apagado",
                        onClick = onToggleAutoplay,
                    )
                    WatchSheetRow(
                        title = "Video en repetición",
                        value = if (loopEnabled) "Encendido" else "Apagar",
                        onClick = onToggleLoop,
                    )
                }
            }
            WatchSheetMode.QUALITY -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatchSheetHeader(title = "Calidad", onBack = onBack)
                    qualityOptions.forEach { option ->
                        WatchSheetRow(
                            title = option.label,
                            value = if (option.id == selectedQualityId) "Elegida" else "",
                            onClick = { onQualitySelected(option.id) },
                        )
                    }
                }
            }
            WatchSheetMode.SPEED -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatchSheetHeader(title = "Velocidad de playback", onBack = onBack)
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                        WatchSheetRow(
                            title = "${DecimalFormat("0.##").format(option)}X",
                            value = if (kotlin.math.abs(playbackSpeed - option) < 0.01f) "Activa" else "",
                            onClick = { onSpeedSelected(option) },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun WatchSheetHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "Volver", tint = TextPrimary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
    HorizontalDivider(color = BorderSubtle)
}

@Composable
private fun WatchSheetRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfacePrimary,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun featuredMeta(featured: YouTubeFeaturedVideo): String {
    return listOfNotNull(
        featured.author.takeIf { it.isNotBlank() },
        featured.publishedText?.takeIf { it.isNotBlank() },
        formatDuration(featured.durationSeconds).takeIf { featured.durationSeconds > 0 },
    ).joinToString(" · ")
}

private fun feedMeta(item: YouTubeFeedItem): String {
    return listOfNotNull(
        item.viewCount?.let(::formatViews),
        item.publishedText?.takeIf { it.isNotBlank() },
        formatDuration(item.durationSeconds).takeIf { item.durationSeconds > 0 },
    ).joinToString(" · ")
}

private fun formatViews(value: Long): String {
    if (value < 1_000) return "$value vistas"
    val base = when {
        value >= 1_000_000_000 -> value / 1_000_000_000.0 to "B"
        value >= 1_000_000 -> value / 1_000_000.0 to "M"
        else -> value / 1_000.0 to "K"
    }
    return "${DecimalFormat("0.#").format(base.first)} ${base.second} vistas"
}
