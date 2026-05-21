package com.juan.snapmusic.core.platform

private val invalidChars = Regex("[\\\\/:*?\"<>|]")
private val multiSpace = Regex("\\s+")

fun sanitizeFileName(raw: String): String {
    return raw
        .replace(invalidChars, " ")
        .replace(multiSpace, " ")
        .trim()
        .ifBlank { "snapmusic" }
}
