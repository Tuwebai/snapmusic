package com.juan.snapmusic.core.platform

import com.juan.snapmusic.core.model.IncomingShareItem
import com.juan.snapmusic.core.model.IncomingShareProvider
import java.net.URI

private val incomingHttpUrlRegex = Regex("""https?://\S+""")
private val instagramHosts = setOf(
    "instagram.com",
    "www.instagram.com",
    "m.instagram.com",
    "instagr.am",
    "www.instagr.am",
)

fun normalizeIncomingShareUrl(raw: String): IncomingShareItem? {
    val candidate = incomingHttpUrlRegex.find(raw)?.value ?: raw.takeIf { it.startsWith("http", ignoreCase = true) }
    val normalizedCandidate = candidate?.trimShareUrl() ?: return null
    validateYouTubeUrl(normalizedCandidate).normalizedUrl?.let { normalized ->
        return IncomingShareItem(
            url = normalized,
            provider = IncomingShareProvider.YOUTUBE,
        )
    }
    return normalizeInstagramUrl(normalizedCandidate)?.let { normalized ->
        IncomingShareItem(
            url = normalized,
            provider = IncomingShareProvider.INSTAGRAM,
        )
    }
}

fun normalizeInstagramUrl(raw: String): String? {
    val uri = runCatching { URI(raw.trimShareUrl()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    if (host !in instagramHosts) return null
    val path = uri.rawPath?.takeIf { it.isNotBlank() } ?: return null
    return URI(
        "https",
        uri.userInfo,
        "www.instagram.com",
        -1,
        path,
        null,
        null,
    ).toString()
}

private fun String.trimShareUrl(): String = trim()
    .trimEnd('.', ',', ';', ')', ']', '}', '"', '\'')
