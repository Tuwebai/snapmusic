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
internal fun VideoHeroSurface(
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
internal fun ArtworkHeroSurface(
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
internal fun PreviewMiniArtwork(
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
            mediaSource = preview.fileUri.takeIf { !preview.isVideo && !it.isPreviewVideoMedia() },
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
internal fun ControlsPanel(
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


internal fun formatMillis(value: Long): String {
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
