package com.juan.snapmusic.core.platform

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

@UnstableApi
class SnapMusicPlaybackMediaSourceFactory(
    context: Context,
) : MediaSource.Factory {
    private val httpDataSourceFactory = OkHttpDataSource.Factory(PlaybackHttpClientHolder.client)
        .setDefaultRequestProperties(YouTubePlaybackHeaders.DEFAULT)
    private val upstreamDataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    private val delegate = DefaultMediaSourceFactory(upstreamDataSourceFactory)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val merged = MergedPlaybackUri.parse(mediaItem.localConfiguration?.uri)
            ?: return delegate.createMediaSource(mediaItem)
        val videoItem = mediaItem.buildUpon()
            .setUri(merged.videoUrl.toUri())
            .build()
        val audioItem = MediaItem.Builder()
            .setMediaId("${mediaItem.mediaId}#audio")
            .setUri(merged.audioUrl.toUri())
            .build()
        return MergingMediaSource(
            delegate.createMediaSource(videoItem),
            delegate.createMediaSource(audioItem),
        )
    }

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes
}

object YouTubePlaybackHeaders {
    val DEFAULT: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://www.youtube.com/",
        "Origin" to "https://www.youtube.com",
        "Accept" to "*/*",
        "Accept-Language" to "es-AR,es;q=0.9,en;q=0.8",
    )
}

private object PlaybackHttpClientHolder {
    private val dispatcher = Dispatcher().apply {
        maxRequests = 24
        maxRequestsPerHost = 12
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
}

object MergedPlaybackUri {
    private const val SCHEME = "snapmusic-merged"
    private const val AUTHORITY = "youtube"
    private const val PAYLOAD = "payload"

    fun build(
        videoUrl: String,
        audioUrl: String,
    ): String {
        val raw = "$videoUrl\n$audioUrl"
        val encoded = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(raw.toByteArray(StandardCharsets.UTF_8))
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(AUTHORITY)
            .appendQueryParameter(PAYLOAD, encoded)
            .build()
            .toString()
    }

    fun parse(uri: Uri?): Parts? {
        if (uri?.scheme != SCHEME) return null
        val payload = uri.getQueryParameter(PAYLOAD).orEmpty()
        if (payload.isBlank()) return null
        val decoded = runCatching {
            String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val separator = decoded.indexOf('\n')
        if (separator <= 0 || separator >= decoded.lastIndex) return null
        val videoUrl = decoded.substring(0, separator).trim()
        val audioUrl = decoded.substring(separator + 1).trim()
        if (videoUrl.isBlank() || audioUrl.isBlank()) return null
        return Parts(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
        )
    }

    data class Parts(
        val videoUrl: String,
        val audioUrl: String,
    )
}
