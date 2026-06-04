package com.juan.snapmusic.data.extractor

import java.math.BigInteger
import java.net.URI

internal object InstagramShortcode {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val shortcodeRegex = Regex("""/(?:p|reel|reels|tv)/([A-Za-z0-9_-]+)""")

    fun fromUrl(url: String): String? {
        val path = runCatching { URI(url).rawPath }.getOrNull().orEmpty()
        return shortcodeRegex.find(path)?.groupValues?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    fun toMediaId(shortcode: String): String {
        var mediaId = BigInteger.ZERO
        shortcode.forEach { char ->
            val value = ALPHABET.indexOf(char)
            require(value >= 0) { "Shortcode de Instagram inválido." }
            mediaId = mediaId.multiply(BigInteger.valueOf(64L)).add(BigInteger.valueOf(value.toLong()))
        }
        return mediaId.toString()
    }
}
