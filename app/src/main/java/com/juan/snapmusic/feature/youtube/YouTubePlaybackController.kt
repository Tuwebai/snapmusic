package com.juan.snapmusic.feature.youtube

import android.content.ComponentName
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
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juan.snapmusic.core.model.YouTubePlaybackRenderState
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.platform.PlaybackArtworkBadgeHelper
import com.juan.snapmusic.core.platform.SnapMusicPlaybackService
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private fun YouTubeFeaturedVideo.toMediaItem(
    artworkData: ByteArray? = null,
): MediaItem {
    val resolvedPlaybackUrl = playbackUrl ?: return MediaItem.EMPTY
    return MediaItem.Builder()
        .setMediaId(sourceUrl)
        .setUri(resolvedPlaybackUrl.toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(author)
                .apply {
                    if (artworkData != null) {
                        setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER.toInt())
                    } else {
                        setArtworkUri(thumbnailUrl.takeIf { it.isNotBlank() }?.toUri())
                    }
                }
                .build(),
        )
        .build()
}

private fun MediaItem.samePlaybackAs(other: MediaItem): Boolean {
    return mediaId == other.mediaId &&
        localConfiguration?.uri == other.localConfiguration?.uri
}

private fun MediaItem.sameArtworkAs(other: MediaItem): Boolean {
    val currentData = mediaMetadata.artworkData
    val otherData = other.mediaMetadata.artworkData
    return when {
        currentData != null && otherData != null -> currentData.contentEquals(otherData)
        currentData == null && otherData == null -> mediaMetadata.artworkUri == other.mediaMetadata.artworkUri
        else -> false
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
    state: YouTubePlaybackRenderState,
    onPlaybackEnded: () -> Unit,
    onPlaybackError: (String?, Boolean) -> Unit,
    onPlaybackProgress: (Long, Boolean, Boolean) -> Unit,
    onMediaTransition: (String, Long, Boolean) -> Unit,
    onPlaybackQualityChanged: (List<Int>, Int?) -> Unit,
): Player? {
    val context = LocalContext.current
    var artworkData by remember(state.featured.sourceUrl, state.featured.thumbnailUrl) { mutableStateOf<ByteArray?>(null) }
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

    LaunchedEffect(state.featured.sourceUrl, state.featured.thumbnailUrl) {
        artworkData = PlaybackArtworkBadgeHelper.resolve(
            context = context,
            artworkSource = state.featured.thumbnailUrl.takeIf { it.isNotBlank() },
        )
    }

    val currentFeaturedSourceUrl by rememberUpdatedState(state.featured.sourceUrl)
    val currentFeatured by rememberUpdatedState(state.featured)

    DisposableEffect(controller) {
        val mediaController = controller
        if (mediaController == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
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
                    onPlaybackError(error.message, error.isExpiredStream403())
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
        state.featured.sourceUrl,
        state.featured.playbackUrl,
        state.shouldAutoPlayCurrent,
    ) {
        val mediaController = controller ?: return@LaunchedEffect
        val playbackUrl = state.featured.playbackUrl ?: return@LaunchedEffect
        val currentItem = state.featured.copy(playbackUrl = playbackUrl).toMediaItem(artworkData = null)
        val sameCurrentItem =
            mediaController.mediaItemCount > 0 &&
                mediaController.getMediaItemAt(0).samePlaybackAs(currentItem)
        val sameSourceItem =
            mediaController.mediaItemCount > 0 &&
                mediaController.getMediaItemAt(0).mediaId == currentItem.mediaId

        if (!sameCurrentItem) {
            val resumePositionMs =
                if (mediaController.currentMediaItem?.mediaId == state.featured.sourceUrl) {
                    mediaController.currentPosition.coerceAtLeast(0L)
                } else {
                    state.currentPositionMs.coerceAtLeast(0L)
                }
            val shouldResumePlaying = state.shouldAutoPlayCurrent || mediaController.playWhenReady
            if (sameSourceItem) {
                mediaController.replaceMediaItem(0, currentItem)
                mediaController.seekTo(0, resumePositionMs)
                mediaController.playWhenReady = shouldResumePlaying
                if (mediaController.playbackState == Player.STATE_IDLE) {
                    mediaController.prepare()
                }
            } else {
                mediaController.setMediaItems(
                    listOf(currentItem),
                    0,
                    resumePositionMs,
                )
                mediaController.playWhenReady = shouldResumePlaying
                mediaController.prepare()
            }
        } else if (abs(mediaController.currentPosition - state.currentPositionMs) > 1_200L) {
            mediaController.seekTo(state.currentPositionMs.coerceAtLeast(0L))
        }

        applyYouTubePlaybackQuality(
            mediaController = mediaController,
            featured = state.featured,
        )
        onPlaybackQualityChanged(
            resolveAvailableVideoHeights(mediaController.currentTracks),
            resolveActualVideoHeight(mediaController.currentTracks),
        )

        if (!state.shouldAutoPlayCurrent) {
            mediaController.pause()
            mediaController.playWhenReady = false
        } else if (!mediaController.playWhenReady) {
            mediaController.playWhenReady = true
            mediaController.play()
        }
    }

    LaunchedEffect(controller, state.featured.sourceUrl, artworkData) {
        val mediaController = controller ?: return@LaunchedEffect
        val artwork = artworkData ?: return@LaunchedEffect
        if (mediaController.mediaItemCount == 0) return@LaunchedEffect
        val current = mediaController.getMediaItemAt(0)
        if (current.mediaId != state.featured.sourceUrl) return@LaunchedEffect
        val withArtwork = current.buildUpon()
            .setMediaMetadata(
                current.mediaMetadata.buildUpon()
                    .setArtworkData(artwork, MediaMetadata.PICTURE_TYPE_FRONT_COVER.toInt())
                    .build(),
            )
            .build()
        val currentUri = current.localConfiguration?.uri
        val artworkUri = withArtwork.localConfiguration?.uri
        val sameStreamUri = currentUri == artworkUri
        if (sameStreamUri && !current.sameArtworkAs(withArtwork)) {
            mediaController.replaceMediaItem(0, withArtwork)
        }
    }

    LaunchedEffect(controller, state.featured.selectedVideoQualityId, state.featured.playbackUrl) {
        val mediaController = controller ?: return@LaunchedEffect
        if (mediaController.playbackState == Player.STATE_IDLE) return@LaunchedEffect
        applyYouTubePlaybackQuality(
            mediaController = mediaController,
            featured = state.featured,
        )
    }

    LaunchedEffect(controller, state.featured.sourceUrl) {
        val mediaController = controller ?: return@LaunchedEffect
        var lastReportedPosition = -1L
        var lastReportedPlayWhenReady: Boolean? = null
        var lastReportedBuffering: Boolean? = null
        while (isActive) {
            val syncingCurrentItem = mediaController.currentMediaItem?.mediaId == state.featured.sourceUrl
            val activelyPlaying = syncingCurrentItem && mediaController.isPlaying
            if (syncingCurrentItem) {
                val currentPosition = mediaController.currentPosition.coerceAtLeast(0L)
                val playWhenReady = mediaController.playWhenReady
                val buffering = !activelyPlaying
                val shouldReport =
                    lastReportedPlayWhenReady != playWhenReady ||
                        lastReportedBuffering != buffering ||
                        lastReportedPosition < 0L ||
                        kotlin.math.abs(currentPosition - lastReportedPosition) >= 10_000L
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

private fun isAdaptivePlaybackUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return lower.contains(".mpd") ||
        lower.contains("manifest.googlevideo.com") ||
        lower.contains(".m3u8") ||
        lower.contains("/manifest/") ||
        lower.startsWith("https://manifest")
}

private fun applyYouTubePlaybackQuality(
    mediaController: MediaController,
    featured: YouTubeFeaturedVideo,
) {
    val selectedVariant = featured.resolvedMedia?.videoVariants?.firstOrNull { it.id == featured.selectedVideoQualityId }
    val adaptivePlayback = featured.adaptivePlaybackUrl?.let(::isAdaptivePlaybackUrl) == true &&
        featured.playbackUrl == featured.adaptivePlaybackUrl
    val preferredAutomaticHeight = featured.resolvedMedia?.let(::resolvePreferredAutomaticHeight)
    val builder = mediaController.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setForceHighestSupportedBitrate(false)
        .setMinVideoSize(0, 0)
        .clearViewportSizeConstraints()
        .clearVideoSizeConstraints()

    if (adaptivePlayback && featured.selectedVideoQualityId == "auto") {
        preferredAutomaticHeight?.let { targetHeight ->
            builder.setMaxVideoSize(Int.MAX_VALUE, targetHeight)
        }
    } else if (adaptivePlayback && featured.selectedVideoQualityId != "auto") {
        val targetHeight = selectedVariant?.resolution?.substringBefore('p')?.toIntOrNull()
        val override = resolveVideoTrackOverride(
            tracks = mediaController.currentTracks,
            requestedHeight = targetHeight,
        )
        if (override != null) {
            builder.setOverrideForType(override)
        }
    }

    mediaController.trackSelectionParameters = builder.build()
}

private fun resolvePreferredAutomaticHeight(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
): Int? {
    val heights = media.videoVariants
        .mapNotNull { it.resolution?.substringBefore('p')?.toIntOrNull() }
        .distinct()
        .sortedDescending()
    return when {
        720 in heights -> 720
        1080 in heights -> 1080
        480 in heights -> 480
        360 in heights -> 360
        else -> heights.firstOrNull()
    }
}

private fun resolveVideoTrackOverride(
    tracks: Tracks,
    requestedHeight: Int?,
): TrackSelectionOverride? {
    if (requestedHeight == null) return null
    data class Candidate(
        val group: Tracks.Group,
        val index: Int,
        val height: Int,
        val bitrate: Int,
    )

    val candidates = tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO && group.length > 0 && group.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                val format = group.getTrackFormat(index)
                val height = format.height.takeIf { it > 0 } ?: return@mapNotNull null
                if (!group.isTrackSupported(index)) return@mapNotNull null
                Candidate(
                    group = group,
                    index = index,
                    height = height,
                    bitrate = format.bitrate.takeIf { it > 0 } ?: 0,
                )
            }
        }
        .toList()

    val selected = candidates
        .filter { it.height <= requestedHeight }
        .maxWithOrNull(compareBy<Candidate>({ it.height }, { it.bitrate }))
        ?: candidates.minWithOrNull(compareBy<Candidate>({ kotlin.math.abs(it.height - requestedHeight) }, { -it.bitrate }))
        ?: return null

    return TrackSelectionOverride(selected.group.mediaTrackGroup, listOf(selected.index))
}

private fun resolveActualVideoHeight(tracks: Tracks): Int? {
    return tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                if (!group.isTrackSelected(index)) return@mapNotNull null
                group.getTrackFormat(index).height.takeIf { it > 0 }
            }
        }
        .maxOrNull()
}

private fun resolveAvailableVideoHeights(tracks: Tracks): List<Int> {
    return tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO && group.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                if (!group.isTrackSupported(index)) return@mapNotNull null
                group.getTrackFormat(index).height.takeIf { it > 0 }
            }
        }
        .distinct()
        .sortedDescending()
        .toList()
}
