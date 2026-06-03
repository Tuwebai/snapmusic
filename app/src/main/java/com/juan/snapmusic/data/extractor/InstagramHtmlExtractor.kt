package com.juan.snapmusic.data.extractor

internal data class InstagramPageMedia(
    val videoUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
)

internal object InstagramHtmlExtractor {
    private val metaTagRegex = Regex("""<meta\s+[^>]*>""", RegexOption.IGNORE_CASE)
    private val contentRegex = Regex("""content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val videoUrlRegex = Regex(""""video_url"\s*:\s*"([^"]+)"""")
    private val playableUrlRegex = Regex(""""playable_url"\s*:\s*"([^"]+)"""")
    private val videoVersionsUrlRegex = Regex(""""video_versions"\s*:\s*\[[^\]]*?"url"\s*:\s*"([^"]+)"""")
    private val contentUrlRegex = Regex(""""contentUrl"\s*:\s*"([^"]+)"""")

    fun extract(html: String): InstagramPageMedia {
        val expanded = html.unescapeJsonLike()
        val videoUrl = metaContent(expanded, "og:video:secure_url")
            ?: metaContent(expanded, "og:video")
            ?: videoUrlRegex.find(expanded)?.groupValues?.getOrNull(1)?.decodeShareValue()
            ?: playableUrlRegex.find(expanded)?.groupValues?.getOrNull(1)?.decodeShareValue()
            ?: videoVersionsUrlRegex.find(expanded)?.groupValues?.getOrNull(1)?.decodeShareValue()
            ?: contentUrlRegex.find(expanded)?.groupValues?.getOrNull(1)?.decodeShareValue()
            ?: error("No se encontró un video público descargable de Instagram.")
        val title = metaContent(expanded, "og:title")
            ?.cleanInstagramTitle()
            ?.ifBlank { null }
            ?: "Video de Instagram"
        val thumbnailUrl = metaContent(expanded, "og:image").orEmpty()
        return InstagramPageMedia(
            videoUrl = videoUrl.decodeShareValue(),
            title = title,
            author = "Instagram",
            thumbnailUrl = thumbnailUrl.decodeShareValue(),
        )
    }

    private fun metaContent(html: String, property: String): String? {
        val expected = property.lowercase()
        return metaTagRegex.findAll(html)
            .map { it.value }
            .firstOrNull { tag ->
                val lower = tag.lowercase()
                lower.contains("""property="$expected"""") ||
                    lower.contains("""property='$expected'""") ||
                    lower.contains("""name="$expected"""") ||
                    lower.contains("""name='$expected'""")
            }
            ?.let { tag -> contentRegex.find(tag)?.groupValues?.getOrNull(1) }
            ?.decodeShareValue()
    }

    private fun String.cleanInstagramTitle(): String = substringBefore(" on Instagram")
        .substringBefore(" en Instagram")
        .trim(' ', '"', ':')

    private fun String.unescapeJsonLike(): String = replace("\\u0026", "&")
        .replace("\\u003d", "=")
        .replace("\\/", "/")
        .replace("\\\"", "\"")

    private fun String.decodeShareValue(): String = replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x2F;", "/")
        .replace("\\u0026", "&")
        .replace("\\/", "/")
        .trim()
}
