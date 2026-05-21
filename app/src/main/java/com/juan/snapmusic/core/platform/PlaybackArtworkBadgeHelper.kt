package com.juan.snapmusic.core.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import com.juan.snapmusic.R
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PLAYBACK_ARTWORK_MAX_SIZE_PX = 256
private const val SNAPMUSIC_BADGE_RED = 0xFFFF3131.toInt()

object PlaybackArtworkBadgeHelper {
    private val artworkCache = LruCache<String, ByteArray>(48)

    suspend fun resolve(
        context: Context,
        artworkSource: String?,
        mediaSource: String? = null,
        fallbackResId: Int? = null,
    ): ByteArray? = withContext(Dispatchers.IO) {
        val cacheKey = "v5|${artworkSource.orEmpty()}|${mediaSource.orEmpty()}|${fallbackResId ?: 0}"
        artworkCache.get(cacheKey)?.let { return@withContext it }

        val baseBitmap = loadBitmap(context, artworkSource)
            ?: loadMediaArtwork(context, mediaSource)
            ?: fallbackResId?.let { decodeResource(context, it) }
            ?: return@withContext null

        val badged = badge(
            baseBitmap,
            decodeResource(context, R.drawable.playback_badge_logo)
                .transparentizeDarkPixels()
                .tintToSnapMusicRed(),
        )
        val data = ByteArrayOutputStream().use { output ->
            badged.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        artworkCache.put(cacheKey, data)
        if (badged !== baseBitmap) badged.recycle()
        data
    }

    private fun loadBitmap(
        context: Context,
        artworkSource: String?,
    ): Bitmap? {
        if (artworkSource.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(artworkSource)
            when (uri.scheme?.lowercase()) {
                "content", "file", "android.resource" -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(
                            stream,
                            null,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                        )?.fit()
                    }
                }
                "http", "https" -> {
                    val connection = (URL(artworkSource).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        doInput = true
                    }
                    try {
                        connection.connect()
                        connection.inputStream.use { stream ->
                            BitmapFactory.decodeStream(
                                stream,
                                null,
                                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                            )?.fit()
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun loadMediaArtwork(
        context: Context,
        mediaSource: String?,
    ): Bitmap? {
        if (mediaSource.isNullOrBlank()) return null
        return runCatching {
            val uri = Uri.parse(mediaSource)
            val retriever = MediaMetadataRetriever()
            try {
                when (uri.scheme?.lowercase()) {
                    null, "", "file" -> retriever.setDataSource(mediaSource.removePrefix("file://"))
                    else -> retriever.setDataSource(context, uri)
                }
                retriever.embeddedPicture?.let { data ->
                    BitmapFactory.decodeByteArray(
                        data,
                        0,
                        data.size,
                        BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
                    )?.fit()
                }
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    private fun decodeResource(
        context: Context,
        resId: Int,
    ): Bitmap {
        return BitmapFactory.decodeResource(
            context.resources,
            resId,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        ).fit()
    }

    private fun badge(
        base: Bitmap,
        logo: Bitmap,
    ): Bitmap {
        val mutable = if (base.isMutable) base else base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutable)
        val shortestSide = minOf(mutable.width, mutable.height).coerceAtLeast(1)
        val badgeSize = (shortestSide * 0.30f).roundToInt().coerceAtLeast(18)
        val margin = (shortestSide * 0.028f).roundToInt().coerceAtLeast(3)
        val badgeLeft = mutable.width - badgeSize - margin
        val badgeTop = mutable.height - badgeSize - margin
        val scaledLogo = Bitmap.createScaledBitmap(logo, badgeSize, badgeSize, true)
        canvas.drawBitmap(scaledLogo, badgeLeft.toFloat(), badgeTop.toFloat(), null)
        if (scaledLogo !== logo) scaledLogo.recycle()
        return mutable
    }

    private fun Bitmap.fit(): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        if (safeWidth <= PLAYBACK_ARTWORK_MAX_SIZE_PX && safeHeight <= PLAYBACK_ARTWORK_MAX_SIZE_PX) return this
        val scale = minOf(
            PLAYBACK_ARTWORK_MAX_SIZE_PX.toFloat() / safeWidth.toFloat(),
            PLAYBACK_ARTWORK_MAX_SIZE_PX.toFloat() / safeHeight.toFloat(),
        )
        val targetWidth = (safeWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (safeHeight * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        if (scaled !== this) recycle()
        return scaled
    }

    private fun Bitmap.transparentizeDarkPixels(): Bitmap {
        val mutable = if (isMutable) this else copy(Bitmap.Config.ARGB_8888, true)
        val width = mutable.width
        val height = mutable.height
        val pixels = IntArray(width * height)
        mutable.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = color ushr 24 and 0xFF
            if (alpha == 0) continue
            val red = color shr 16 and 0xFF
            val green = color shr 8 and 0xFF
            val blue = color and 0xFF
            if (red < 42 && green < 42 && blue < 42) {
                pixels[index] = 0x00000000
            }
        }
        mutable.setPixels(pixels, 0, width, 0, 0, width, height)
        return mutable
    }

    private fun Bitmap.tintToSnapMusicRed(): Bitmap {
        val mutable = if (isMutable) this else copy(Bitmap.Config.ARGB_8888, true)
        val width = mutable.width
        val height = mutable.height
        val pixels = IntArray(width * height)
        mutable.getPixels(pixels, 0, width, 0, 0, width, height)
        for (index in pixels.indices) {
            val color = pixels[index]
            val alpha = color ushr 24 and 0xFF
            if (alpha == 0) continue
            pixels[index] = (alpha shl 24) or (SNAPMUSIC_BADGE_RED and 0x00FFFFFF)
        }
        mutable.setPixels(pixels, 0, width, 0, 0, width, height)
        return mutable
    }
}
