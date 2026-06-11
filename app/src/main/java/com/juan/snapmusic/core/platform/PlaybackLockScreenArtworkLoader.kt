package com.juan.snapmusic.core.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.juan.snapmusic.SnapMusicApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException

private const val LOCK_SCREEN_ARTWORK_MAX_SIZE_PX = 1024
private const val LOCK_SCREEN_ARTWORK_CACHE_SIZE = 10
private val YouTubeThumbnailPattern = Regex("""/(?:vi|vi_webp)/([^/?]+)/""")

internal class PlaybackLockScreenArtworkLoader(
    context: Context,
) : BitmapLoader {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bitmapCache = LruCache<String, Bitmap>(LOCK_SCREEN_ARTWORK_CACHE_SIZE)

    override fun supportsMimeType(mimeType: String): Boolean {
        return mimeType.startsWith("image/", ignoreCase = true)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return future("data:${data.contentHashCode()}") {
            decodeArtwork(data) ?: throw IOException("No se pudo decodificar el artwork.")
        }
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return future(uri.toString()) {
            loadArtwork(uri) ?: throw IOException("No se pudo cargar el artwork.")
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap> {
        metadata.artworkData?.let { data -> return decodeBitmap(data) }
        metadata.artworkUri?.let { uri -> return loadBitmap(uri) }
        return failedFuture(IOException("Metadata sin artwork."))
    }

    fun loadArtworkDataForMetadata(uri: Uri): ByteArray? {
        val candidates = uri.youtubeHighResolutionCandidates()
        candidates.forEachIndexed { index, candidate ->
            val data = readArtworkBytes(candidate) ?: return@forEachIndexed
            val bitmap = decodeArtwork(data) ?: return@forEachIndexed
            val encoded = bitmap.toMetadataJpeg()
            val width = bitmap.width
            bitmap.recycle()
            if (encoded != null && (width >= 320 || index == candidates.lastIndex)) {
                return encoded
            }
        }
        return null
    }

    private fun future(
        cacheKey: String,
        block: () -> Bitmap,
    ): ListenableFuture<Bitmap> {
        bitmapCache.get(cacheKey)?.let { return SettableFuture.create<Bitmap>().apply { set(it) } }
        val future = SettableFuture.create<Bitmap>()
        scope.launch {
            runCatching(block)
                .onSuccess { bitmap ->
                    bitmapCache.put(cacheKey, bitmap)
                    future.set(bitmap)
                }
                .onFailure(future::setException)
        }
        return future
    }

    private fun failedFuture(error: Throwable): ListenableFuture<Bitmap> {
        return SettableFuture.create<Bitmap>().apply { setException(error) }
    }

    private fun loadArtwork(uri: Uri): Bitmap? {
        val candidates = uri.youtubeHighResolutionCandidates()
        candidates.forEachIndexed { index, candidate ->
            val data = readArtworkBytes(candidate) ?: return@forEachIndexed
            val bitmap = decodeArtwork(data) ?: return@forEachIndexed
            if (bitmap.width >= 320 || index == candidates.lastIndex) {
                return bitmap
            }
            bitmap.recycle()
        }
        return null
    }

    private fun readArtworkBytes(uri: Uri): ByteArray? {
        return when (uri.scheme?.lowercase()) {
            "content", "file", "android.resource" -> appContext.contentResolver
                .openInputStream(uri)
                ?.use { input -> input.readBytes() }

            "http", "https" -> {
                val application = appContext as? SnapMusicApplication ?: return null
                val request = Request.Builder().url(uri.toString()).build()
                application.appGraph.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.bytes()
                }
            }

            else -> null
        }
    }

    private fun decodeArtwork(data: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        var scaledWidth = width
        var scaledHeight = height
        while (
            scaledWidth / 2 >= LOCK_SCREEN_ARTWORK_MAX_SIZE_PX ||
            scaledHeight / 2 >= LOCK_SCREEN_ARTWORK_MAX_SIZE_PX
        ) {
            sample *= 2
            scaledWidth /= 2
            scaledHeight /= 2
        }
        return sample.coerceAtLeast(1)
    }
}

private fun Bitmap.toMetadataJpeg(): ByteArray? {
    return ByteArrayOutputStream().use { output ->
        if (compress(Bitmap.CompressFormat.JPEG, 92, output)) output.toByteArray() else null
    }
}

private fun Uri.youtubeHighResolutionCandidates(): List<Uri> {
    val value = toString()
    val videoId = YouTubeThumbnailPattern.find(value)?.groupValues?.getOrNull(1)
        ?: return listOf(this)
    return listOf(
        "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg",
        "https://i.ytimg.com/vi/$videoId/sddefault.jpg",
        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
        value,
    ).distinct().map(Uri::parse)
}
