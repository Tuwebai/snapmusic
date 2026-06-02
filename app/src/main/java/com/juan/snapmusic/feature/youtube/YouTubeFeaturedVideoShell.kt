package com.juan.snapmusic.feature.youtube


import android.graphics.Color
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.juan.snapmusic.core.model.SeekPreviewFrameset
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
internal fun FeaturedVideoPlayerShell(
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
    var isSeekingPreview by remember(featured.sourceUrl) { mutableStateOf(false) }
    var resumeAfterSeekPreview by remember(featured.sourceUrl, player) { mutableStateOf(false) }

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

    LaunchedEffect(showOverlayControls, featured.sourceUrl, isSeekingPreview) {
        if (showOverlayControls && !isSeekingPreview) {
            delay(2400)
            if (!isSeekingPreview) {
                showOverlayControls = false
            }
        }
    }

    val pauseForSeekPreview: () -> Unit = {
        player?.let { currentPlayer ->
            isSeekingPreview = true
            showOverlayControls = true
            resumeAfterSeekPreview = currentPlayer.isPlaying || currentPlayer.playWhenReady
            if (resumeAfterSeekPreview) {
                currentPlayer.pause()
                currentPlayer.playWhenReady = false
            }
        }
    }
    val finishSeekPreview: () -> Unit = {
        player?.let { currentPlayer ->
            if (resumeAfterSeekPreview) {
                currentPlayer.playWhenReady = true
                currentPlayer.play()
            }
        }
        resumeAfterSeekPreview = false
        isSeekingPreview = false
        showOverlayControls = true
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
                .videoDoubleTapSeek(
                    onTap = { showOverlayControls = !showOverlayControls },
                    onSeekBack = { player?.seekByClamped(-DOUBLE_TAP_SEEK_MS) },
                    onSeekForward = { player?.seekByClamped(DOUBLE_TAP_SEEK_MS) },
                )
        ) {
            FeaturedVideoOverlayHost(
                overlayState = overlayState,
                seekPreviewFramesets = featured.resolvedMedia?.seekPreviewFramesets.orEmpty(),
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
                onSeekPreviewStart = pauseForSeekPreview,
                onSeekPreviewFinished = finishSeekPreview,
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
        onSeekPreviewStart = pauseForSeekPreview,
        onSeekPreviewFinished = finishSeekPreview,
    )
}

@Composable
internal fun FeaturedVideoOverlayHost(
    overlayState: PlaybackOverlayState,
    seekPreviewFramesets: List<SeekPreviewFrameset> = emptyList(),
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekPreviewStart: () -> Unit = {},
    onSeekPreviewFinished: () -> Unit = {},
    onToggleResize: () -> Unit,
) {
    VideoFullscreenOverlay(
        playbackState = overlayState,
        canGoPrevious = true,
        canGoNext = true,
        seekPreviewFramesets = seekPreviewFramesets,
        onBack = onBack,
        onPlayPause = onPlayPause,
        onPrevious = onPrevious,
        onNext = onNext,
        onMore = onMore,
        onSeekTo = onSeekTo,
        onSeekPreviewStart = onSeekPreviewStart,
        onSeekPreviewFinished = onSeekPreviewFinished,
        onToggleResize = onToggleResize,
    )
}

@Composable
internal fun FeaturedVideoFullscreenShell(
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
    onSeekPreviewStart: () -> Unit = {},
    onSeekPreviewFinished: () -> Unit = {},
) {
    LandscapeFullscreenVideoDialog(
        visible = visible,
        player = player,
        overlayState = overlayState,
        canGoPrevious = true,
        canGoNext = true,
        seekPreviewFramesets = featured.resolvedMedia?.seekPreviewFramesets.orEmpty(),
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
        onSeekPreviewStart = onSeekPreviewStart,
        onSeekPreviewFinished = onSeekPreviewFinished,
    )
}

@Composable
internal fun FeaturedVideoMetadataPanel(
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

private fun featuredMeta(featured: YouTubeFeaturedVideo): String {
    return listOfNotNull(
        featured.author.takeIf { it.isNotBlank() },
        featured.publishedText?.takeIf { it.isNotBlank() },
        formatDuration(featured.durationSeconds).takeIf { featured.durationSeconds > 0 },
    ).joinToString(" · ")
}
