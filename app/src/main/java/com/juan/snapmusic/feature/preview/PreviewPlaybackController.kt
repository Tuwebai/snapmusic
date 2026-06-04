package com.juan.snapmusic.feature.preview

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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juan.snapmusic.core.model.PreviewPlaybackQueueItem
import com.juan.snapmusic.R
import com.juan.snapmusic.core.model.PreviewState
import com.juan.snapmusic.core.platform.PlaybackArtworkBadgeHelper
import com.juan.snapmusic.core.platform.PlaybackSessionStateStore
import com.juan.snapmusic.core.platform.SnapMusicPlaybackService

@androidx.media3.common.util.UnstableApi
@Composable
internal fun rememberPreviewPlayer(
    preview: PreviewState,
    playlist: List<PreviewPlaybackQueueItem>,
    resumePositionMs: Long,
    autoPlayRequestId: Long,
    onAutoPlayRequestConsumed: (Long) -> Unit,
    onPlaybackEnded: () -> Unit,
    onMediaTransition: (String, Long) -> Unit,
    onPlaybackProgress: (Long, Boolean, Boolean) -> Unit,
): Player? {
    val context = LocalContext.current
    val localArtworkSource = preview.thumbnailUrl.takeIfLocalArtworkSource()
    val currentPreviewFileUri by rememberUpdatedState(preview.fileUri)
    val currentOnPlaybackEnded by rememberUpdatedState(onPlaybackEnded)
    val currentOnMediaTransition by rememberUpdatedState(onMediaTransition)
    val currentOnPlaybackProgress by rememberUpdatedState(onPlaybackProgress)
    var artworkData by remember(preview.fileUri, preview.thumbnailUrl) { mutableStateOf<ByteArray?>(null) }
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
            MediaController.releaseFuture(future)
            controller = null
        }
    }

    LaunchedEffect(preview.fileUri, localArtworkSource) {
        artworkData = PlaybackArtworkBadgeHelper.resolve(
            context = context,
            artworkSource = localArtworkSource,
            mediaSource = preview.fileUri,
            fallbackResId = if (preview.fileUri.isPreviewVideoMedia()) null else R.drawable.preview_local_music_fallback,
        )
    }

    LaunchedEffect(preview.fileUri, artworkData) {
        val fileUri = preview.fileUri ?: return@LaunchedEffect
        val data = artworkData ?: return@LaunchedEffect
        PlaybackSessionStateStore.updateArtwork(mediaId = fileUri, artworkData = data)
    }

    DisposableEffect(controller) {
        val mediaController = controller
        if (mediaController == null) {
            onDispose { }
        } else {
            var lastEndedMediaId: String? = null
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { mediaId ->
                            currentOnMediaTransition(
                                mediaId,
                                mediaController.currentPosition.coerceAtLeast(0L),
                            )
                        }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val currentMediaId = mediaController.currentMediaItem?.mediaId
                    currentOnPlaybackProgress(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                        playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED,
                    )
                    if (
                        playbackState == Player.STATE_ENDED &&
                        currentMediaId != null &&
                        currentMediaId == currentPreviewFileUri &&
                        lastEndedMediaId != currentMediaId
                    ) {
                        lastEndedMediaId = currentMediaId
                        currentOnPlaybackEnded()
                    } else if (playbackState != Player.STATE_ENDED) {
                        lastEndedMediaId = null
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    currentOnPlaybackProgress(
                        mediaController.currentPosition.coerceAtLeast(0L),
                        mediaController.playWhenReady,
                        !isPlaying,
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
        preview.fileUri,
        playlist,
        autoPlayRequestId,
    ) {
        val mediaController = controller ?: return@LaunchedEffect
        val fileUri = preview.fileUri ?: return@LaunchedEffect
        val autoPlayToken = autoPlayRequestId.takeIf { it > 0L }
        val shouldAutoPlay = autoPlayToken != null
        val queueItems = buildPreviewQueueMediaItems(
            preview = preview,
            playlist = playlist,
        )
        val queueIndex = queueItems.indexOfFirst { it.mediaId == fileUri }.coerceAtLeast(0)
        val sameCurrentItem = mediaController.currentMediaItem?.mediaId == fileUri
        val activeMediaId = mediaController.currentMediaItem?.mediaId
        val currentItem = queueItems[queueIndex]
        if (sameCurrentItem) {
            val sameQueue = mediaController.samePreviewQueueAs(queueItems)
            val currentIndex = mediaController.currentMediaItemIndex
                .takeIf { it in queueItems.indices }
                ?: queueIndex
            if (!sameQueue) {
                val resumePositionMs = mediaController.currentPosition.coerceAtLeast(0L)
                val wasPlaying = mediaController.isPlaying || mediaController.playWhenReady
                mediaController.setMediaItems(queueItems, queueIndex, resumePositionMs)
                mediaController.prepare()
                mediaController.playWhenReady = wasPlaying
                if (wasPlaying) {
                    mediaController.play()
                } else {
                    mediaController.pause()
                }
            }
            if (shouldAutoPlay && (!mediaController.isPlaying || !mediaController.playWhenReady)) {
                mediaController.playWhenReady = true
                mediaController.play()
            }
            autoPlayToken?.let(onAutoPlayRequestConsumed)
            return@LaunchedEffect
        }

        if (!shouldAutoPlay && activeMediaId != null && activeMediaId != fileUri) {
            return@LaunchedEffect
        }

        mediaController.setMediaItems(
            queueItems,
            queueIndex,
            resumePositionMs.takeIf { it > 0L }?.coerceAtLeast(0L) ?: C.TIME_UNSET,
        )
        mediaController.prepare()
        if (shouldAutoPlay) {
            mediaController.playWhenReady = true
            mediaController.play()
        } else {
            mediaController.pause()
            mediaController.playWhenReady = false
        }
        autoPlayToken?.let(onAutoPlayRequestConsumed)
    }

    return controller
}

private fun buildPreviewQueueMediaItems(
    preview: PreviewState,
    playlist: List<PreviewPlaybackQueueItem>,
): List<MediaItem> {
    val currentFileUri = preview.fileUri ?: return emptyList()
    val basePlaylist = playlist
        .filter { it.fileUri.isNotBlank() }
        .ifEmpty {
            listOf(
                PreviewPlaybackQueueItem(
                    title = preview.title,
                    subtitle = preview.subtitle,
                    thumbnailUrl = preview.thumbnailUrl,
                    fileUri = currentFileUri,
                    isVideo = preview.isVideo || currentFileUri.isPreviewVideoMedia(),
                ),
            )
        }
    return basePlaylist.map { item ->
        val artworkUri = item.thumbnailUrl
            .takeIfLocalArtworkSource()
            ?.toUri()
            ?.takeUnless { it.scheme?.lowercase() == "file" }
        MediaItem.Builder()
            .setMediaId(item.fileUri)
            .setUri(item.fileUri.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(item.subtitle)
                    .setArtworkUri(artworkUri)
                    .build(),
            )
            .build()
    }
}

private fun MediaController.samePreviewQueueAs(queueItems: List<MediaItem>): Boolean {
    if (mediaItemCount != queueItems.size) return false
    return queueItems.indices.all { index ->
        getMediaItemAt(index).samePlaybackAs(queueItems[index])
    }
}

private fun MediaItem.samePlaybackAs(other: MediaItem): Boolean {
    return mediaId == other.mediaId &&
        localConfiguration?.uri == other.localConfiguration?.uri
}

internal fun String?.isPreviewVideoMedia(): Boolean {
    val raw = this.orEmpty()
    val decoded = runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw)
    val normalized = decoded.substringBefore('?').lowercase()
    val encoded = raw.substringBefore('?').lowercase()
    return normalized.contains("/video/") ||
        normalized.contains("video/media") ||
        normalized.endsWith(".mp4") ||
        normalized.endsWith(".mkv") ||
        normalized.endsWith(".webm") ||
        normalized.endsWith(".mov") ||
        encoded.contains("%2fvideo%2f") ||
        encoded.contains("video%2fmedia") ||
        encoded.contains(".mp4") ||
        encoded.contains(".mkv") ||
        encoded.contains(".webm") ||
        encoded.contains(".mov")
}

private fun String?.takeIfLocalArtworkSource(): String? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    return when (value.toUri().scheme?.lowercase()) {
        "content", "file", "android.resource" -> value
        else -> null
    }
}
