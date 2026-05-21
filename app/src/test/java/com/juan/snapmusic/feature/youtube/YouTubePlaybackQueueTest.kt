package com.juan.snapmusic.feature.youtube

import com.juan.snapmusic.core.model.PlaybackContinuationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubePlaybackQueueTest {

    @Test
    fun nextQueueIndex_advancesNormally() {
        assertEquals(2, nextQueueIndex(4, 1, PlaybackContinuationMode.PLAY_NEXT))
    }

    @Test
    fun nextQueueIndex_loopsWhenConfigured() {
        assertEquals(0, nextQueueIndex(3, 2, PlaybackContinuationMode.LOOP_FEED))
    }

    @Test
    fun nextQueueIndex_stopsAtEndWhenNoLoop() {
        assertNull(nextQueueIndex(3, 2, PlaybackContinuationMode.PLAY_NEXT))
        assertNull(nextQueueIndex(3, 2, PlaybackContinuationMode.STOP_AT_END))
    }

    @Test
    fun previousQueueIndex_restartsCurrentWhenPastThreshold() {
        assertEquals(2, previousQueueIndex(4, 2, PREVIOUS_RESTART_THRESHOLD_MS + 1))
    }

    @Test
    fun previousQueueIndex_movesBackWhenNearStartOfSong() {
        assertEquals(1, previousQueueIndex(4, 2, 1_500L))
        assertEquals(0, previousQueueIndex(4, 0, 1_500L))
    }
}
