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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.media3.common.VideoSize
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
import com.juan.snapmusic.core.model.SeekPreviewFrameset
import com.juan.snapmusic.feature.player.VideoFullscreenOverlay
import com.juan.snapmusic.feature.player.LandscapeFullscreenVideoDialog
import com.juan.snapmusic.feature.player.PlaybackOverlayState
import com.juan.snapmusic.feature.player.PlayerSurface
import com.juan.snapmusic.feature.player.rememberPlaybackOverlayState
import com.juan.snapmusic.feature.player.DOUBLE_TAP_SEEK_MS
import com.juan.snapmusic.feature.player.DoubleTapSeekGestureLayer
import com.juan.snapmusic.feature.player.seekByClamped
import androidx.compose.ui.text.style.TextOverflow
import java.text.DecimalFormat
import kotlinx.coroutines.delay

@Composable
internal fun FeaturedVideoPlayerShell(
    featured: YouTubeFeaturedVideo,
    player: Player?,
    isFullscreen: Boolean,
    featuredThumbnailModel: ImageRequest,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    watchNextItems: List<YouTubeFeedItem>,
    canLoadMoreWatchNext: Boolean,
    isLoadingMoreWatchNext: Boolean,
    onSelectWatchNext: (YouTubeFeedItem) -> Unit,
    onLoadMoreWatchNext: () -> Unit,
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
    var videoAspectRatio by remember(featured.sourceUrl, player) {
        mutableStateOf(player?.videoSize?.snapMusicAspectRatio() ?: 0f)
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
                    videoAspectRatio = currentPlayer.videoSize.snapMusicAspectRatio()
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    videoAspectRatio = videoSize.snapMusicAspectRatio()
                }
            }
            hasRenderedFirstFrame =
                currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                    currentPlayer.videoSize.width > 0
            videoAspectRatio = currentPlayer.videoSize.snapMusicAspectRatio()
            isBuffering =
                currentPlayer.currentMediaItem?.mediaId == featured.sourceUrl &&
                    currentPlayer.playWhenReady &&
                    currentPlayer.playbackState == Player.STATE_BUFFERING
            currentPlayer.addListener(listener)
            onDispose { currentPlayer.removeListener(listener) }
        }
    }

    LaunchedEffect(showOverlayControls, featured.sourceUrl, isSeekingPreview, overlayState.isPlaying) {
        if (showOverlayControls && overlayState.isPlaying && !isSeekingPreview) {
            delay(com.juan.snapmusic.feature.player.PlayerControlsOverlayDefaults.AutoHideDelayMs)
            if (overlayState.isPlaying && !isSeekingPreview) {
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
    val showTheaterBackdrop = !isFullscreen &&
        shouldShowTheaterBackdrop(videoAspectRatio) &&
        featured.thumbnailUrl.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(androidx.compose.ui.graphics.Color.Black),
    ) {
        if (showTheaterBackdrop) {
            YouTubeTheaterBackdrop(
                model = featuredThumbnailModel,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!hasRenderedFirstFrame) {
            AsyncImage(
                model = featuredThumbnailModel,
                contentDescription = featured.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        if (!isFullscreen && player != null && featured.playbackUrl != null) {
            key(featured.sourceUrl, player) {
                PlayerSurface(
                    player = player,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(if (hasRenderedFirstFrame) 1f else 0f),
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    keepContentOnPlayerReset = true,
                    shutterColor = Color.TRANSPARENT,
                    backgroundColor = if (showTheaterBackdrop) Color.TRANSPARENT else Color.BLACK,
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

        DoubleTapSeekGestureLayer(
            modifier = Modifier.fillMaxSize(),
            onTap = { showOverlayControls = !showOverlayControls },
            onSeekBack = { player?.seekByClamped(-DOUBLE_TAP_SEEK_MS) },
            onSeekForward = { player?.seekByClamped(DOUBLE_TAP_SEEK_MS) },
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
        watchNextItems = watchNextItems,
        canLoadMoreWatchNext = canLoadMoreWatchNext,
        isLoadingMoreWatchNext = isLoadingMoreWatchNext,
        onSelectWatchNext = onSelectWatchNext,
        onLoadMoreWatchNext = onLoadMoreWatchNext,
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
    watchNextItems: List<YouTubeFeedItem>,
    canLoadMoreWatchNext: Boolean,
    isLoadingMoreWatchNext: Boolean,
    onSelectWatchNext: (YouTubeFeedItem) -> Unit,
    onLoadMoreWatchNext: () -> Unit,
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
                contentScale = ContentScale.Fit,
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
        extraOverlay = {
            YouTubeFullscreenOverlay(
                watchNextItems = watchNextItems,
                canLoadMore = canLoadMoreWatchNext,
                isLoadingMore = isLoadingMoreWatchNext,
                onSelectItem = onSelectWatchNext,
                onLoadMore = onLoadMoreWatchNext,
            )
        },
    )
}

