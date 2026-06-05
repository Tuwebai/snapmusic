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

internal val PreviewPanelGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF351114),
        Color(0xFF171214),
        Color(0xFF0A0A0B),
    ),
)
internal val PreviewArtworkHaloColor = AccentRed.copy(alpha = 0.12f)
internal val PreviewVideoBottomScrim = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x99000000)),
)
internal val PreviewArtworkBottomScrim = Brush.verticalGradient(
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
    val isVideo = preview.isVideo || remember(preview.fileUri) { preview.fileUri.isPreviewVideoMedia() }
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

internal fun Player.togglePlayPause() {
    playWhenReady = !isPlaying
}

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
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
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
