package com.juan.snapmusic.data.extractor

import android.util.Log
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.TransferSource
import com.juan.snapmusic.core.platform.normalizeInstagramUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class InstagramStreamResolverRepository(
    private val okHttpClient: OkHttpClient,
) {
    private val socialVideoResolver by lazy { CobaltSocialVideoResolver(okHttpClient) }

    fun canResolve(url: String): Boolean = normalizeInstagramUrl(url) != null

    suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeInstagramUrl(url) ?: error("La URL de Instagram no es válida.")
        val media = resolvePublicMedia(normalizedUrl)
        val variant = MediaVariant(
            id = INSTAGRAM_VIDEO_VARIANT_ID,
            label = "Video MP4",
            kind = MediaKind.VIDEO,
            container = ContainerFormat.MP4,
            resolution = "Video",
            directUrl = media.videoUrl,
            sourceId = INSTAGRAM_VIDEO_VARIANT_ID,
            sourceContainerHint = "MP4",
        )
        ResolvedMedia(
            sourceUrl = normalizedUrl,
            title = media.title,
            author = media.author,
            durationSeconds = 0L,
            thumbnailUrl = media.thumbnailUrl,
            playbackUrl = media.videoUrl,
            audioVariants = emptyList(),
            videoVariants = listOf(variant),
        )
    }

    private fun resolvePublicMedia(normalizedUrl: String): InstagramPageMedia {
        val errors = mutableListOf<String>()
        for (apiUrl in apiCandidates(normalizedUrl)) {
            val body = runCatching { fetchApi(apiUrl, normalizedUrl) }
                .onFailure { error -> errors += error.message.orEmpty() }
                .getOrNull() ?: continue
            val media = runCatching { InstagramMediaInfoParser.parse(body) }
                .onFailure { error -> errors += error.message.orEmpty() }
                .getOrNull() ?: continue
            Log.d(LOG_TAG, "resolved api=$apiUrl hasThumbnail=${media.thumbnailUrl.isNotBlank()}")
            return media
        }
        for (candidateUrl in requestCandidates(normalizedUrl)) {
            val page = runCatching { fetchPublicPage(candidateUrl, normalizedUrl) }
                .onFailure { error -> errors += error.message.orEmpty() }
                .getOrNull() ?: continue
            val media = runCatching { InstagramHtmlExtractor.extract(page) }
                .onFailure { error -> errors += error.message.orEmpty() }
                .getOrNull() ?: continue
            Log.d(LOG_TAG, "resolved url=$normalizedUrl candidate=$candidateUrl hasThumbnail=${media.thumbnailUrl.isNotBlank()}")
            return media
        }
        runCatching { socialVideoResolver.resolve(normalizedUrl) }
            .onFailure { error -> errors += error.message.orEmpty() }
            .getOrNull()
            ?.let { media ->
                Log.d(LOG_TAG, "resolved fallback=cobalt url=$normalizedUrl")
                return media
            }
        val lastError = errors.lastOrNull()?.takeIf { it.isNotBlank() }
        error(lastError ?: "No se encontró un video público descargable de Instagram.")
    }

    suspend fun resolveDownloadPlan(url: String, selection: DownloadSelection): DownloadExecutionPlan = withContext(Dispatchers.IO) {
        require(selection.kind == MediaKind.VIDEO) {
            "Instagram solo admite descarga de video en SnapMusic."
        }
        val media = resolve(url)
        val variant = media.videoVariants.firstOrNull { it.sourceId == selection.preferredSourceId || it.id == selection.preferredSourceId }
            ?: media.videoVariants.first()
        DownloadExecutionPlan.Direct(
            selection = selection.copy(
                kind = MediaKind.VIDEO,
                targetContainer = ContainerFormat.MP4,
                targetResolution = variant.resolution,
                preferredSourceId = variant.sourceId,
                sourceContainerHint = "MP4",
            ),
            source = TransferSource(
                url = variant.directUrl,
                headers = playbackHeaders(media.sourceUrl),
            ),
            displayLabel = "${variant.container.name} · ${variant.resolution ?: "Video"}",
        )
    }

    private fun fetchPublicPage(
        url: String,
        referer: String,
    ): String {
        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders(referer))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            Log.d(LOG_TAG, "fetch candidate=$url code=${response.code}")
            if (!response.isSuccessful) {
                error("Instagram respondió ${response.code}; verificá que el video sea público.")
            }
            return response.body?.string() ?: error("Instagram no devolvió contenido.")
        }
    }

    private fun fetchApi(
        url: String,
        referer: String,
    ): String {
        val request = Request.Builder()
            .url(url)
            .headers(apiHeaders(referer))
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            Log.d(LOG_TAG, "fetch api=$url code=${response.code}")
            if (!response.isSuccessful) {
                error("Instagram respondió ${response.code}; verificá que el video sea público.")
            }
            return response.body?.string() ?: error("Instagram no devolvió contenido.")
        }
    }

    private fun apiCandidates(normalizedUrl: String): List<String> {
        val shortcode = InstagramShortcode.fromUrl(normalizedUrl) ?: return emptyList()
        val mediaId = runCatching { InstagramShortcode.toMediaId(shortcode) }.getOrNull() ?: return emptyList()
        return listOf(
            "https://i.instagram.com/api/v1/media/$mediaId/info/",
            "https://www.instagram.com/api/v1/media/$mediaId/info/",
        )
    }

    private fun requestCandidates(normalizedUrl: String): List<String> {
        val base = normalizedUrl.trimEnd('/')
        return listOf(
            "$base/",
            "$base/?__a=1&__d=dis",
            "$base/embed/",
        ).distinct()
    }

    private fun requestHeaders(url: String) = okhttp3.Headers.Builder().apply {
        playbackHeaders(url).forEach { (name, value) -> add(name, value) }
        add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
    }.build()

    private fun apiHeaders(url: String) = okhttp3.Headers.Builder().apply {
        playbackHeaders(url).forEach { (name, value) -> add(name, value) }
        add("Accept", "application/json")
        add("X-IG-App-ID", "936619743392459")
        add("X-ASBD-ID", "129477")
    }.build()

    private fun playbackHeaders(referer: String): Map<String, String> = mapOf(
        "User-Agent" to DESKTOP_USER_AGENT,
        "Referer" to referer,
        "Accept-Language" to "es-419,es;q=0.9,en;q=0.8",
    )

    private companion object {
        const val LOG_TAG = "SnapMusicInstagram"
        const val INSTAGRAM_VIDEO_VARIANT_ID = "instagram-video-mp4"
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    }
}
