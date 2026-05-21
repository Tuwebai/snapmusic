package com.juan.snapmusic.core.platform

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNameSanitizerTest {
    @Test
    fun sanitizeFileName_replaces_invalid_chars() {
        assertEquals("hola mundo", sanitizeFileName("hola:/mundo"))
    }

    @Test
    fun sanitizeFileName_returns_fallback_when_blank() {
        assertEquals("snapmusic", sanitizeFileName("   "))
    }
}
