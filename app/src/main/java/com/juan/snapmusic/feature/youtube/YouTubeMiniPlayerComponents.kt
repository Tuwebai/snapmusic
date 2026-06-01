package com.juan.snapmusic.feature.youtube

import android.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BackgroundSecondary
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.designsystem.WarningAmber
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.platform.formatDuration
import com.juan.snapmusic.feature.player.PlayerSurface

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
    onTogglePlayPause: () -> Unit,
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            YouTubeMiniPlayerVideoShell(
                featured = featured,
                player = player,
                compact = compact,
                thumbnailModel = miniThumbnailModel,
                fillAvailableHeight = true,
                onOpen = onOpen,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = featured.title,
                            modifier = Modifier.clickable(onClick = onOpen),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = featuredMeta(featured),
                            modifier = Modifier.clickable(onClick = onOpen),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        YouTubeMiniPlaybackControls(
                            player = player,
                            onPrevious = onPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext,
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
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
}

@Composable
private fun YouTubeMiniPlaybackControls(
    player: Player?,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    DisposableEffect(player) {
        val currentPlayer = player
        if (currentPlayer == null) {
            onDispose { }
        } else {
            isPlaying = currentPlayer.isPlaying
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                    isPlaying = isPlayingNow
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    isPlaying = currentPlayer.isPlaying
                }
            }
            currentPlayer.addListener(listener)
            onDispose { currentPlayer.removeListener(listener) }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Outlined.SkipPrevious,
                contentDescription = "Video anterior",
                tint = TextPrimary,
                modifier = Modifier.size(17.dp),
            )
        }
        IconButton(
            onClick = onTogglePlayPause,
            enabled = player != null,
            modifier = Modifier.size(30.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = "Video siguiente",
                tint = TextPrimary,
                modifier = Modifier.size(17.dp),
            )
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
    fillAvailableHeight: Boolean = false,
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
            .then(
                if (fillAvailableHeight) {
                    Modifier.fillMaxHeight()
                } else {
                    Modifier.height(if (compact) 64.dp else 74.dp)
                },
            )
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

private fun featuredMeta(featured: YouTubeFeaturedVideo): String {
    return listOfNotNull(
        featured.author.takeIf { it.isNotBlank() },
        featured.publishedText?.takeIf { it.isNotBlank() },
        formatDuration(featured.durationSeconds).takeIf { featured.durationSeconds > 0 },
    ).joinToString(" · ")
}
