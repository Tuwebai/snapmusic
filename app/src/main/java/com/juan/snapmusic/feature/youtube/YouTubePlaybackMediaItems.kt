package com.juan.snapmusic.feature.youtube

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo

internal fun YouTubeFeaturedVideo.toMediaItem(): MediaItem {
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

internal fun MediaItem.samePlaybackAs(other: MediaItem): Boolean {
    return mediaId == other.mediaId &&
        localConfiguration?.uri == other.localConfiguration?.uri
}

internal fun buildYouTubeQueueMediaItems(
    featured: YouTubeFeaturedVideo,
): List<MediaItem> {
    val currentItem = featured.toMediaItem().takeIf { it != MediaItem.EMPTY } ?: return emptyList()
    return listOf(currentItem)
}

internal fun MediaController.sameYouTubeQueueAs(queueItems: List<MediaItem>): Boolean {
    if (mediaItemCount != queueItems.size) return false
    return queueItems.indices.all { index ->
        getMediaItemAt(index).samePlaybackAs(queueItems[index])
    }
}

internal fun MediaController.syncNextYouTubeQueueItem(queueItems: List<MediaItem>) {
    val nextItem = queueItems.getOrNull(1)
    when {
        nextItem == null && mediaItemCount > 1 -> removeMediaItems(1, mediaItemCount)
        nextItem != null && mediaItemCount > 1 && !getMediaItemAt(1).samePlaybackAs(nextItem) -> replaceMediaItem(1, nextItem)
        nextItem != null && mediaItemCount == 1 -> addMediaItem(nextItem)
    }
}

internal fun stableResumePositionMs(
    controllerPositionMs: Long,
    statePositionMs: Long,
    seekPositionMs: Long,
): Long {
    return maxOf(
        controllerPositionMs.takeIf { it > 0L } ?: 0L,
        statePositionMs.takeIf { it > 0L } ?: 0L,
        seekPositionMs.takeIf { it > 0L } ?: 0L,
    )
}

internal fun PlaybackException.isExpiredStream403(): Boolean {
    var cursor: Throwable? = this
    var has403Cause = false
    while (cursor != null && !has403Cause) {
        has403Cause = cursor is HttpDataSource.InvalidResponseCodeException && cursor.responseCode == 403
        cursor = cursor.cause
    }
    return has403Cause ||
        message?.contains("403", ignoreCase = true) == true
}

internal fun YouTubeFeaturedVideo.selectedTelemetryHeight(): Int? {
    if (selectedVideoQualityId == "auto") {
        return autoMaxVideoHeight ?: resolvedMedia?.let(::resolvePreferredAutomaticHeight)
    }
    return selectedVideoQualityId.removePrefix("adaptive-").toIntOrNull()
        ?: resolvedMedia?.videoVariants
            ?.firstOrNull { it.id == selectedVideoQualityId }
            ?.resolution
            ?.substringBefore('p')
            ?.toIntOrNull()
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
