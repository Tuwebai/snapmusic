package com.juan.snapmusic.data.storage

import com.juan.snapmusic.core.model.PlaybackContinuationMode
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubePlaybackSnapshot
import com.juan.snapmusic.core.model.YouTubeQueueOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class YouTubePlaybackSnapshotCodecTest {

    @Test
    fun encodeDecode_preservesPlaybackSnapshot() {
        val snapshot = YouTubePlaybackSnapshot(
            queue = listOf(
                YouTubeFeedItem(
                    url = "https://youtube.com/watch?v=uno",
                    title = "Mix Cumbia",
                    author = "Juanchi",
                    thumbnailUrl = "https://img.youtube.com/uno.jpg",
                    durationSeconds = 180,
                    viewCount = 2_500_000,
                    publishedText = "hace 2 días",
                    description = "Una descripción con acento: López",
                ),
                YouTubeFeedItem(
                    url = "https://youtube.com/watch?v=dos",
                    title = "Cuarteto Live",
                    author = "SnapMusic",
                    thumbnailUrl = "https://img.youtube.com/dos.jpg",
                    durationSeconds = 215,
                ),
            ),
            currentQueueIndex = 1,
            query = "cuarteto 2026",
            autoplayEnabled = false,
            continuationMode = PlaybackContinuationMode.LOOP_FEED,
            lastPositionMs = 45_678L,
            origin = YouTubeQueueOrigin.SEARCH_RESULTS,
            showMiniPlayer = true,
        )

        val encoded = YouTubePlaybackSnapshotCodec.encode(snapshot)
        val decoded = YouTubePlaybackSnapshotCodec.decode(encoded)

        assertNotNull(decoded)
        assertEquals(snapshot, decoded)
    }
}
