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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
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
private const val STARTUP_REBUFFER_GRACE_POSITION_MS = 1_000L

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
    onPlaybackFirstFrame: (String) -> Unit,
    onPlaybackQualityChanged: (List<Int>, Int?) -> Unit,
    onPlaybackRebuffer: (Long, Long) -> Unit,
    onPlaybackStalled: (Long, Long) -> Unit,
): Player? {
    val context = LocalContext.current
    val featured = sessionState.featured
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
    var lastKnownPlaybackPositionMs by remember { mutableStateOf(0L) }
    var handledSeekRequestId by remember(featured.sourceUrl) { mutableStateOf(seekState.requestId) }

    LaunchedEffect(featured.sourceUrl) {
        lastKnownPlaybackPositionMs = sessionState.currentPositionMs.coerceAtLeast(0L)
    }

    DisposableEffect(controller) {
        val mediaController = controller
        if (mediaController == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                private var playbackStartedAtMs = 0L
                private var bufferStartedAtMs = 0L
                private var firstFrameReported = false
                private var rebufferCount = 0
                private var totalRebufferDurationMs = 0L
                private var actualVideoHeight: Int? = null
                private var lastQualitySignature = ""

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
                    rebufferCount = 0
                    totalRebufferDurationMs = 0L
                    actualVideoHeight = null
                    lastQualitySignature = ""
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
                    lastKnownPlaybackPositionMs = maxOf(
                        lastKnownPlaybackPositionMs,
                        mediaController.currentPosition.coerceAtLeast(0L),
                    )
                    if (playbackStartedAtMs == 0L && playbackState == Player.STATE_BUFFERING) {
                        playbackStartedAtMs = now
                    }
                    if (mediaController.playWhenReady && playbackState == Player.STATE_BUFFERING && bufferStartedAtMs == 0L) {
                        bufferStartedAtMs = now
                    } else if (playbackState == Player.STATE_READY && bufferStartedAtMs != 0L) {
                        val durationMs = now - bufferStartedAtMs
                        val positionMs = mediaController.currentPosition.coerceAtLeast(0L)
                        val isPlaybackRebuffer = firstFrameReported && positionMs >= STARTUP_REBUFFER_GRACE_POSITION_MS
                        if (isPlaybackRebuffer) {
                            rebufferCount += 1
                            totalRebufferDurationMs += durationMs
                        }
                        Log.d(
                            "SnapMusicPlayback",
                            "event=${if (isPlaybackRebuffer) "rebuffer" else "startupBuffer"} media=${mediaController.currentMediaItem?.mediaId.orEmpty()} " +
                                "count=$rebufferCount durationMs=$durationMs totalDurationMs=$totalRebufferDurationMs " +
                                "positionMs=$positionMs firstFrame=$firstFrameReported " +
                                "selectedQuality=${featured.selectedVideoQualityId} " +
                                "selectedHeight=${featured.selectedTelemetryHeight() ?: -1} " +
                                "actualHeight=${actualVideoHeight ?: -1}",
                        )
                        if (isPlaybackRebuffer) {
                            onPlaybackRebuffer(positionMs, durationMs)
                        }
                        bufferStartedAtMs = 0L
                    }
                    onPlaybackProgress(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                        false,
                    )
                    if (playbackState == Player.STATE_ENDED) {
                        onPlaybackEnded()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    syncTransitionIfNeeded()
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    lastKnownPlaybackPositionMs = maxOf(
                        lastKnownPlaybackPositionMs,
                        mediaController.currentPosition.coerceAtLeast(0L),
                    )
                    onPlaybackProgress(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                        false,
                    )
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                    val targetPositionMs = newPosition.positionMs.coerceAtLeast(0L)
                    lastKnownPlaybackPositionMs = targetPositionMs
                    onPlaybackProgress(
                        targetPositionMs,
                        mediaController.playWhenReady,
                        true,
                    )
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    val errorPositionMs = stableResumePositionMs(
                        controllerPositionMs = mediaController.currentPosition.coerceAtLeast(0L),
                        statePositionMs = maxOf(lastKnownPlaybackPositionMs, sessionState.currentPositionMs),
                        seekPositionMs = seekState.positionMs,
                    )
                    lastKnownPlaybackPositionMs = errorPositionMs
                    onPlaybackProgress(
                        errorPositionMs,
                        mediaController.playWhenReady,
                        true,
                    )
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
                        "event=firstFrame media=${mediaController.currentMediaItem?.mediaId.orEmpty()} " +
                            "firstFrameMs=${firstFrameMs ?: -1} selectedQuality=${featured.selectedVideoQualityId} " +
                            "selectedHeight=${featured.selectedTelemetryHeight() ?: -1} " +
                            "actualHeight=${actualVideoHeight ?: -1}",
                    )
                    onPlaybackFirstFrame(mediaController.currentMediaItem?.mediaId.orEmpty())
                }

                override fun onTracksChanged(tracks: Tracks) {
                    if (mediaController.currentMediaItem?.mediaId != currentFeaturedSourceUrl) return
                    val availableHeights = resolveAvailableVideoHeights(tracks)
                    val height = resolveActualVideoHeight(tracks)
                    actualVideoHeight = height
                    val signature = "${featured.selectedVideoQualityId}:${featured.selectedTelemetryHeight()}:$height:$availableHeights"
                    if (signature != lastQualitySignature) {
                        lastQualitySignature = signature
                        Log.d(
                            "SnapMusicPlayback",
                            "event=quality media=${mediaController.currentMediaItem?.mediaId.orEmpty()} " +
                                "selectedQuality=${featured.selectedVideoQualityId} " +
                                "selectedHeight=${featured.selectedTelemetryHeight() ?: -1} " +
                                "actualHeight=${height ?: -1} availableHeights=$availableHeights",
                        )
                    }
                    onPlaybackQualityChanged(
                        availableHeights,
                        height,
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
        )
        if (queueItems.isEmpty()) return@LaunchedEffect
        val sameQueue = mediaController.sameYouTubeQueueAs(queueItems)
        val sameCurrent = mediaController.mediaItemCount > 0 &&
            mediaController.getMediaItemAt(0).samePlaybackAs(queueItems[0])
        if (sameCurrent) {
            if (
                seekState.requestId > 0L &&
                seekState.requestId != handledSeekRequestId &&
                abs(mediaController.currentPosition - seekState.positionMs) > 1_200L
            ) {
                mediaController.seekTo(seekState.positionMs.coerceAtLeast(0L))
            }
            if (seekState.requestId > 0L) {
                handledSeekRequestId = seekState.requestId
            }
            mediaController.syncNextYouTubeQueueItem(queueItems)
        } else if (!sameQueue) {
            val resumePositionMs =
                if (mediaController.currentMediaItem?.mediaId == featured.sourceUrl) {
                    stableResumePositionMs(
                        controllerPositionMs = mediaController.currentPosition.coerceAtLeast(0L),
                        statePositionMs = maxOf(lastKnownPlaybackPositionMs, sessionState.currentPositionMs),
                        seekPositionMs = seekState.positionMs,
                    )
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
        if (seekState.requestId == handledSeekRequestId) return@LaunchedEffect
        if (mediaController.currentMediaItem?.mediaId != featured.sourceUrl) return@LaunchedEffect
        val targetPositionMs = seekState.positionMs.coerceAtLeast(0L)
        if (abs(mediaController.currentPosition - targetPositionMs) > 1_200L) {
            mediaController.seekTo(targetPositionMs)
        }
        handledSeekRequestId = seekState.requestId
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
                lastKnownPlaybackPositionMs = maxOf(lastKnownPlaybackPositionMs, currentPosition)
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
                        false,
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

