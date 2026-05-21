package com.juan.snapmusic.core.platform

import java.net.URI

data class UrlValidation(
    val normalizedUrl: String? = null,
    val message: String? = null,
)

private val allowedHosts = setOf(
    "youtube.com",
    "www.youtube.com",
    "m.youtube.com",
    "youtu.be",
    "music.youtube.com",
)

fun validateYouTubeUrl(raw: String): UrlValidation {
    val normalized = raw.trim()
    if (normalized.isBlank()) {
        return UrlValidation(message = "Pegá una URL de YouTube.")
    }
    val uri = runCatching { URI(normalized) }.getOrNull()
        ?: return UrlValidation(message = "La URL no es válida.")
    val host = uri.host?.lowercase()
    if (host !in allowedHosts) {
        return UrlValidation(message = "Por ahora SnapMusic v1 solo acepta links de YouTube.")
    }
    return UrlValidation(normalizedUrl = normalized)
}
