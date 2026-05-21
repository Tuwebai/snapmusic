package com.juan.snapmusic.core.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlValidatorTest {
    @Test
    fun validateYouTubeUrl_accepts_youtube_domain() {
        val result = validateYouTubeUrl("https://www.youtube.com/watch?v=abc123")
        assertEquals("https://www.youtube.com/watch?v=abc123", result.normalizedUrl)
        assertNull(result.message)
    }

    @Test
    fun validateYouTubeUrl_rejects_other_domains() {
        val result = validateYouTubeUrl("https://vimeo.com/123")
        assertEquals("Por ahora SnapMusic v1 solo acepta links de YouTube.", result.message)
    }
}
