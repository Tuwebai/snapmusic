package com.juan.snapmusic.data.storage

import com.juan.snapmusic.core.model.PreviewPlaybackQueueItem
import com.juan.snapmusic.core.model.PreviewPlaybackSnapshot
import java.nio.charset.StandardCharsets
import java.util.Base64

internal object PreviewPlaybackSnapshotCodec {
    private const val FIELD_SEPARATOR = "~"
    private const val ITEM_SEPARATOR = "|"
    private const val LINE_SEPARATOR = "\n"
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(snapshot: PreviewPlaybackSnapshot): String {
        if (snapshot.queue.isEmpty()) return ""
        val header = listOf(
            snapshot.currentQueueIndex.toString(),
            snapshot.lastPositionMs.coerceAtLeast(0L).toString(),
            snapshot.showMiniPlayer.toString(),
        ).joinToString(FIELD_SEPARATOR)
        val queue = snapshot.queue.joinToString(ITEM_SEPARATOR, transform = ::encodeItem)
        return "$header$LINE_SEPARATOR$queue"
    }

    fun decode(raw: String?): PreviewPlaybackSnapshot? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.split(LINE_SEPARATOR, limit = 2)
        if (parts.size != 2) return null
        val header = parts[0].split(FIELD_SEPARATOR)
        if (header.size < 3) return null
        val queue = parts[1]
            .split(ITEM_SEPARATOR)
            .mapNotNull(::decodeItem)
        if (queue.isEmpty()) return null
        val currentQueueIndex = header[0].toIntOrNull() ?: return null
        return PreviewPlaybackSnapshot(
            queue = queue,
            currentQueueIndex = currentQueueIndex.coerceIn(0, queue.lastIndex),
            lastPositionMs = header[1].toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            showMiniPlayer = header[2].toBooleanStrictOrNull() ?: true,
        )
    }

    private fun encodeItem(item: PreviewPlaybackQueueItem): String {
        return listOf(
            item.title.encodeField(),
            item.subtitle.encodeField(),
            item.thumbnailUrl.encodeField(),
            item.fileUri.encodeField(),
        ).joinToString(FIELD_SEPARATOR)
    }

    private fun decodeItem(raw: String): PreviewPlaybackQueueItem? {
        val fields = raw.split(FIELD_SEPARATOR)
        if (fields.size < 4) return null
        val fileUri = fields[3].decodeField().trim()
        if (fileUri.isBlank()) return null
        return PreviewPlaybackQueueItem(
            title = fields[0].decodeField(),
            subtitle = fields[1].decodeField(),
            thumbnailUrl = fields[2].decodeField(),
            fileUri = fileUri,
        )
    }

    private fun String.encodeField(): String {
        return encoder.encodeToString(toByteArray(StandardCharsets.UTF_8))
    }

    private fun String.decodeField(): String {
        return String(decoder.decode(this), StandardCharsets.UTF_8)
    }
}
