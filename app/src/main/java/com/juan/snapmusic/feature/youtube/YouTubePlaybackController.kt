package com.juan.snapmusic.feature.youtube

import android.content.ComponentName
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.model.YouTubePlayerSeekState
import com.juan.snapmusic.core.model.YouTubePlayerSessionState
import com.juan.snapmusic.core.platform.SnapMusicPlaybackService
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val ACTIVE_STALL_RECOVERY_MS = 2_500L
private const val ACTIVE_STALL_RECOVERY_REPEAT_MS = 4_000L

private fun YouTubeFeaturedVideo.toMediaItem(): MediaItem {
    val resolvedPlaybackUrl = playbackUrl ?: return MediaItem.EMPTY
    val builder = MediaItem.Builder()
        .setMediaId(sourceUrl)
        .setUri(resolvedPlaybackUrl.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author)
                .setArtworkUri(thumbnailUrl.takeIf { it.isNotBlank() }?.toUri())
                .build(),
        )
    adaptivePlaybackMimeType(resolvedPlaybackUrl)?.let(builder::setMimeType)
    return builder.build()
}

private fun adaptivePlaybackMimeType(url: String): String? {
    val lower = url.lowercase()
    return when {
        lower.startsWith("data:application/dash+xml") ||
            lower.contains(".mpd") ||
            lower.contains("manifest.googlevideo.com") ||
            lower.contains("/manifest/") ||
            lower.startsWith("https://manifest") -> MimeTypes.APPLICATION_MPD

        lower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
        else -> null
    }
}

private fun MediaItem.samePlaybackAs(other: MediaItem): Boolean {
    return mediaId == other.mediaId &&
        localConfiguration?.uri == other.localConfiguration?.uri
}

private fun buildYouTubeQueueMediaItems(
    featured: YouTubeFeaturedVideo,
    preloadedNextFeatured: YouTubeFeaturedVideo?,
): List<MediaItem> {
    val currentItem = featured.toMediaItem().takeIf { it != MediaItem.EMPTY } ?: return emptyList()
    val nextItem = preloadedNextFeatured
        ?.takeIf { it.sourceUrl != featured.sourceUrl && it.isReady && it.playbackUrl != null }
        ?.toMediaItem()
        ?.takeIf { it != MediaItem.EMPTY }
    return if (nextItem != null) listOf(currentItem, nextItem) else listOf(currentItem)
}

private fun MediaController.sameYouTubeQueueAs(queueItems: List<MediaItem>): Boolean {
    if (mediaItemCount != queueItems.size) return false
    return queueItems.indices.all { index ->
        getMediaItemAt(index).samePlaybackAs(queueItems[index])
    }
}

private fun MediaController.syncNextYouTubeQueueItem(queueItems: List<MediaItem>) {
    val nextItem = queueItems.getOrNull(1)
    when {
        nextItem == null && mediaItemCount > 1 -> removeMediaItems(1, mediaItemCount)
        nextItem != null && mediaItemCount > 1 && !getMediaItemAt(1).samePlaybackAs(nextItem) -> replaceMediaItem(1, nextItem)
        nextItem != null && mediaItemCount == 1 -> addMediaItem(nextItem)
    }
}

private fun androidx.media3.common.PlaybackException.isExpiredStream403(): Boolean {
    var cursor: Throwable? = this
    var has403Cause = false
    while (cursor != null && !has403Cause) {
        has403Cause = cursor is HttpDataSource.InvalidResponseCodeException && cursor.responseCode == 403
        cursor = cursor.cause
    }
    return has403Cause ||
        message?.contains("403", ignoreCase = true) == true
}

@androidx.media3.common.util.UnstableApi
@Composable
fun rememberYouTubePlayer(
    sessionState: YouTubePlayerSessionState,
    seekState: YouTubePlayerSeekState,
    shouldAutoPlayCurrent: Boolean,
    onPlaybackEnded: () -> Unit,
    onPlaybackError: (String?, Boolean) -> Unit,
    onPlaybackProgress: (Long, Boolean, Boolean) -> Unit,
    onMediaTransition: (String, Long, Boolean) -> Unit,
    onPlaybackQualityChanged: (List<Int>, Int?) -> Unit,
    onPlaybackRebuffer: (Long, Long) -> Unit,
    onPlaybackStalled: (Long, Long) -> Unit,
): Player? {
    val context = LocalContext.current
    val featured = sessionState.featured
    val preloadedNextFeatured = sessionState.preloadedNextFeatured
    val future = remember(context) {
        MediaController.Builder(
            context,
            SessionToken(context, ComponentName(context, SnapMusicPlaybackService::class.java)),
        ).buildAsync()
    }
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(future) {
        val executor = ContextCompat.getMainExecutor(context)
        future.addListener(
            {
                controller = runCatching { future.get() }.getOrNull()
            },
            executor,
        )

        onDispose {
            controller?.release()
            controller = null
        }
    }

    val currentFeaturedSourceUrl by rememberUpdatedState(featured.sourceUrl)
    val currentFeatured by rememberUpdatedState(featured)

    DisposableEffect(controller) {
        val mediaController = controller
        if (mediaController == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                private var playbackStartedAtMs = 0L
                private var bufferStartedAtMs = 0L
                private var firstFrameReported = false

                fun syncTransitionIfNeeded() {
                    val mediaId = mediaController.currentMediaItem?.mediaId ?: return
                    if (mediaId == currentFeaturedSourceUrl) return
                    onMediaTransition(
                        mediaId,
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                    )
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    playbackStartedAtMs = SystemClock.elapsedRealtime()
                    bufferStartedAtMs = 0L
                    firstFrameReported = false
                    val mediaId = mediaItem?.mediaId ?: return
                    onMediaTransition(
                        mediaId,
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                    )
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    syncTransitionIfNeeded()
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    val now = SystemClock.elapsedRealtime()
                    if (playbackStartedAtMs == 0L && playbackState == Player.STATE_BUFFERING) {
                        playbackStartedAtMs = now
                    }
                    if (mediaController.playWhenReady && playbackState == Player.STATE_BUFFERING && bufferStartedAtMs == 0L) {
                        bufferStartedAtMs = now
                    } else if (playbackState == Player.STATE_READY && bufferStartedAtMs != 0L) {
                        val durationMs = now - bufferStartedAtMs
                        val positionMs = mediaController.currentPosition.coerceAtLeast(0L)
                        Log.d(
                            "SnapMusicPlayback",
                            "rebuffer media=${mediaController.currentMediaItem?.mediaId.orEmpty()} durationMs=$durationMs positionMs=$positionMs firstFrame=$firstFrameReported",
                        )
                        if (firstFrameReported) {
                            onPlaybackRebuffer(positionMs, durationMs)
                        }
                        bufferStartedAtMs = 0L
                    }
                    onPlaybackProgress(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                        playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED,
                    )
                    if (playbackState == Player.STATE_ENDED) {
                        onPlaybackEnded()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncTransitionIfNeeded()
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    onPlaybackProgress(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                        !isPlaying,
                    )
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    Log.w(
                        "SnapMusicPlayback",
                        "error media=${mediaController.currentMediaItem?.mediaId.orEmpty()} message=${error.message.orEmpty()}",
                    )
                    onPlaybackError(error.message, error.isExpiredStream403())
                }

                override fun onRenderedFirstFrame() {
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    if (firstFrameReported) return
                    firstFrameReported = true
                    val firstFrameMs = (SystemClock.elapsedRealtime() - playbackStartedAtMs).takeIf { playbackStartedAtMs > 0L }
                    Log.d(
                        "SnapMusicPlayback",
                        "firstFrame media=${mediaController.currentMediaItem?.mediaId.orEmpty()} firstFrameMs=${firstFrameMs ?: -1}",
                    )
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    onPlaybackQualityChanged(
                        resolveAvailableVideoHeights(tracks),
                        resolveActualVideoHeight(tracks),
                    )
                }
            }
            mediaController.addListener(listener)
            onDispose {
                mediaController.removeListener(listener)
            }
        }
    }

    LaunchedEffect(
        controller,
        featured.sourceUrl,
        featured.playbackUrl,
        seekState.requestId,
        seekState.positionMs,
        preloadedNextFeatured?.sourceUrl,
        preloadedNextFeatured?.playbackUrl,
    ) {
        val mediaController = controller ?: return@LaunchedEffect
        val playbackUrl = featured.playbackUrl
        if (playbackUrl == null) {
            if (mediaController.currentMediaItem?.mediaId != featured.sourceUrl && mediaController.mediaItemCount > 0) {
                mediaController.pause()
                mediaController.playWhenReady = false
                mediaController.clearMediaItems()
            }
            return@LaunchedEffect
        }
        val queueItems = buildYouTubeQueueMediaItems(
            featured = featured.copy(playbackUrl = playbackUrl),
            preloadedNextFeatured = preloadedNextFeatured,
        )
        if (queueItems.isEmpty()) return@LaunchedEffect
        val sameQueue = mediaController.sameYouTubeQueueAs(queueItems)
        val sameCurrent = mediaController.mediaItemCount > 0 &&
            mediaController.getMediaItemAt(0).samePlaybackAs(queueItems[0])
        if (sameCurrent) {
            if (
                seekState.requestId > 0L &&
                abs(mediaController.currentPosition - seekState.positionMs) > 1_200L
            ) {
                mediaController.seekTo(seekState.positionMs.coerceAtLeast(0L))
            }
            mediaController.syncNextYouTubeQueueItem(queueItems)
        } else if (!sameQueue) {
            val resumePositionMs =
                if (mediaController.currentMediaItem?.mediaId == featured.sourceUrl) {
                    mediaController.currentPosition.coerceAtLeast(0L)
                } else {
                    seekState.positionMs.coerceAtLeast(0L)
                }
            mediaController.setMediaItems(queueItems, 0, resumePositionMs)
            mediaController.playWhenReady = shouldAutoPlayCurrent
            mediaController.prepare()
        }

        applyYouTubePlaybackQuality(
            mediaController = mediaController,
            featured = featured,
        )
        onPlaybackQualityChanged(
            resolveAvailableVideoHeights(mediaController.currentTracks),
            resolveActualVideoHeight(mediaController.currentTracks),
        )
    }

    LaunchedEffect(controller, featured.sourceUrl) {
        val mediaController = controller ?: return@LaunchedEffect
        var stallStartedAtMs = 0L
        var lastRecoveryAtMs = 0L
        while (isActive) {
            val syncingCurrentItem = mediaController.currentMediaItem?.mediaId == featured.sourceUrl
            val activelyBuffering = syncingCurrentItem &&
                mediaController.playWhenReady &&
                mediaController.playbackState == Player.STATE_BUFFERING
            val now = SystemClock.elapsedRealtime()
            if (activelyBuffering) {
                if (stallStartedAtMs == 0L) stallStartedAtMs = now
                val stalledForMs = now - stallStartedAtMs
                if (
                    stalledForMs >= ACTIVE_STALL_RECOVERY_MS &&
                    now - lastRecoveryAtMs >= ACTIVE_STALL_RECOVERY_REPEAT_MS
                ) {
                    lastRecoveryAtMs = now
                    onPlaybackStalled(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        stalledForMs,
                    )
                }
            } else {
                stallStartedAtMs = 0L
                lastRecoveryAtMs = 0L
            }
            delay(500L)
        }
    }

    LaunchedEffect(controller, featured.sourceUrl, seekState.requestId) {
        val mediaController = controller ?: return@LaunchedEffect
        if (seekState.requestId <= 0L) return@LaunchedEffect
        if (mediaController.currentMediaItem?.mediaId != featured.sourceUrl) return@LaunchedEffect
        val targetPositionMs = seekState.positionMs.coerceAtLeast(0L)
        if (abs(mediaController.currentPosition - targetPositionMs) > 1_200L) {
            mediaController.seekTo(targetPositionMs)
        }
    }

    LaunchedEffect(controller, featured.sourceUrl, shouldAutoPlayCurrent) {
        val mediaController = controller ?: return@LaunchedEffect
        if (mediaController.currentMediaItem?.mediaId != featured.sourceUrl) return@LaunchedEffect
        if (!shouldAutoPlayCurrent) {
            mediaController.pause()
            mediaController.playWhenReady = false
        } else if (!mediaController.playWhenReady) {
            mediaController.playWhenReady = true
            mediaController.play()
        }
    }

    LaunchedEffect(controller, featured.selectedVideoQualityId, featured.playbackUrl, featured.autoMaxVideoHeight) {
        val mediaController = controller ?: return@LaunchedEffect
        if (mediaController.playbackState == Player.STATE_IDLE) return@LaunchedEffect
        applyYouTubePlaybackQuality(
            mediaController = mediaController,
            featured = featured,
        )
    }

    LaunchedEffect(controller, featured.sourceUrl) {
        val mediaController = controller ?: return@LaunchedEffect
        var lastReportedPosition = -1L
        var lastReportedPlayWhenReady: Boolean? = null
        var lastReportedBuffering: Boolean? = null
        while (isActive) {
            val syncingCurrentItem = mediaController.currentMediaItem?.mediaId == featured.sourceUrl
            val activelyPlaying = syncingCurrentItem && mediaController.isPlaying
            if (syncingCurrentItem) {
                val currentPosition = mediaController.currentPosition.coerceAtLeast(0L)
                val playWhenReady = mediaController.playWhenReady
                val buffering = !activelyPlaying
                val shouldReport =
                        lastReportedPlayWhenReady != playWhenReady ||
                        lastReportedBuffering != buffering ||
                        lastReportedPosition < 0L ||
                        kotlin.math.abs(currentPosition - lastReportedPosition) >= 2_000L
                if (shouldReport) {
                    lastReportedPosition = currentPosition
                    lastReportedPlayWhenReady = playWhenReady
                    lastReportedBuffering = buffering
                    onPlaybackProgress(
                        currentPosition,
                        playWhenReady,
                        buffering,
                    )
                }
            } else {
                lastReportedPosition = -1L
                lastReportedPlayWhenReady = null
                lastReportedBuffering = null
            }
            delay(
                when {
                    activelyPlaying -> 10_000L
                    syncingCurrentItem -> 12_000L
                    else -> 20_000L
                },
            )
        }
    }

    return controller
}

