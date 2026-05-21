package com.juan.snapmusic.data.storage

import com.juan.snapmusic.core.model.PlaybackContinuationMode
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubePlaybackSnapshot
import com.juan.snapmusic.core.model.YouTubeQueueOrigin
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object YouTubePlaybackSnapshotCodec {
    private const val FIELD_SEPARATOR = "~"
    private const val ITEM_SEPARATOR = "|"
    private const val LINE_SEPARATOR = "\n"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(snapshot: YouTubePlaybackSnapshot): String {
        if (snapshot.queue.isEmpty()) return ""
        val header = listOf(
            snapshot.currentQueueIndex.toString(),
            snapshot.query.encodeField(),
            snapshot.autoplayEnabled.toString(),
            snapshot.continuationMode.name,
            snapshot.lastPositionMs.toString(),
            snapshot.origin.name,
            snapshot.showMiniPlayer.toString(),
        ).joinToString(FIELD_SEPARATOR)

        val queue = snapshot.queue.joinToString(ITEM_SEPARATOR, transform = ::encodeItem)

        return "$header$LINE_SEPARATOR$queue"
    }

    fun decode(raw: String?): YouTubePlaybackSnapshot? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(LINE_SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val header = parts[0].split(FIELD_SEPARATOR)
        if (header.size < 7) return null

        val queue = parts[1]
            .split(ITEM_SEPARATOR)
            .mapNotNull(::decodeItem)

        if (queue.isEmpty()) return null

        val currentIndex = header[0].toIntOrNull() ?: return null
        return YouTubePlaybackSnapshot(
            queue = queue,
            currentQueueIndex = currentIndex.coerceIn(0, queue.lastIndex),
            query = header[1].decodeField(),
            autoplayEnabled = header[2].toBooleanStrictOrNull() ?: true,
            continuationMode = runCatching { PlaybackContinuationMode.valueOf(header[3]) }.getOrDefault(PlaybackContinuationMode.PLAY_NEXT),
            lastPositionMs = header[4].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            origin = runCatching { YouTubeQueueOrigin.valueOf(header[5]) }.getOrDefault(YouTubeQueueOrigin.HOME_FEED),
            showMiniPlayer = header[6].toBooleanStrictOrNull() ?: true,
        )
    }

    fun encodeFeed(items: List<YouTubeFeedItem>): String {
        if (items.isEmpty()) return ""
        return items.joinToString(ITEM_SEPARATOR, transform = ::encodeItem)
    }

    fun decodeFeed(raw: String?): List<YouTubeFeedItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(ITEM_SEPARATOR).mapNotNull(::decodeItem)
    }

    private fun encodeItem(item: YouTubeFeedItem): String {
        return listOf(
            item.url.encodeField(),
            item.title.encodeField(),
            item.author.encodeField(),
            item.thumbnailUrl.encodeField(),
            item.durationSeconds.toString(),
            (item.viewCount ?: -1L).toString(),
            item.publishedText.orEmpty().encodeField(),
            item.description.orEmpty().encodeField(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun decodeItem(raw: String): YouTubeFeedItem? {
        val fields = raw.split(FIELD_SEPARATOR)
        if (fields.size < 8) return null
        val durationSeconds = fields[4].toLongOrNull() ?: 0L
        val viewCount = fields[5].toLongOrNull()?.takeIf { it >= 0L }
        return YouTubeFeedItem(
            url = fields[0].decodeField(),
            title = fields[1].decodeField(),
            author = fields[2].decodeField(),
            thumbnailUrl = fields[3].decodeField(),
            durationSeconds = durationSeconds,
            viewCount = viewCount,
            publishedText = fields[6].decodeField().ifBlank { null },
            description = fields[7].decodeField().ifBlank { null },
        )
    }

    private fun String.encodeField(): String {
        return encoder.encodeToString(toByteArray(StandardCharsets.UTF_8))
    }

    private fun String.decodeField(): String {
        return String(decoder.decode(this), StandardCharsets.UTF_8)
    }
}
