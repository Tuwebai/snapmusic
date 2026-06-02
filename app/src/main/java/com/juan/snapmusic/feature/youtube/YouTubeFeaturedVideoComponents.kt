package com.juan.snapmusic.feature.youtube


import android.graphics.Color
import android.view.View
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
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
import kotlinx.coroutines.launch

private val WatchPlayerHeight = 304.dp

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
    YouTubeCastPlaybackEffect(featured = featured, player = player)
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
    var swipeOffsetPx by remember(featured.sourceUrl) { mutableStateOf(0f) }
    var swipeSettleJob by remember(featured.sourceUrl) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val swipeScope = rememberCoroutineScope()
    val swipeProgress = (swipeOffsetPx / 360f).coerceIn(0f, 1f)
    val qualityOptions = remember(featured.playbackUrl, featured.resolvedMedia) { featured.toWatchQualityOptions() }
    val currentQualityLabel = remember(
        featured.playbackUrl,
        featured.resolvedMedia,
        featured.selectedVideoQualityId,
        featured.actualVideoHeight,
        featured.actualPlaybackLabel,
    ) {
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

    Column(
        modifier = Modifier
            .graphicsLayer {
                translationY = swipeOffsetPx
                val scale = 1f - (swipeProgress * 0.035f)
                scaleX = scale
                scaleY = scale
                alpha = 1f - (swipeProgress * 0.18f)
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    if (delta > 0f || swipeOffsetPx > 0f) {
                        swipeOffsetPx = (swipeOffsetPx + delta).coerceIn(0f, 460f)
                    }
                },
                orientation = Orientation.Vertical,
                enabled = !showFullscreenShell,
                onDragStarted = { swipeSettleJob?.cancel() },
                onDragStopped = { velocity ->
                    swipeSettleJob?.cancel()
                    val shouldMinimize = swipeOffsetPx > 132f || velocity > 900f
                    swipeSettleJob = swipeScope.launch {
                        val target = if (shouldMinimize) 560f else 0f
                        animate(
                            initialValue = swipeOffsetPx,
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = if (shouldMinimize) 170 else 220,
                                easing = FastOutSlowInEasing,
                            ),
                        ) { value, _ ->
                            swipeOffsetPx = value
                        }
                        if (shouldMinimize) {
                            swipeOffsetPx = 0f
                            onMinimizeVideo()
                        }
                    }
                },
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
