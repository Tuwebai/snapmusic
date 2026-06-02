package com.juan.snapmusic.core.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.juan.snapmusic.MainActivity
import com.juan.snapmusic.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

private const val CHANNEL_ID = "snapmusic_downloads"

class NotificationHelper(
    context: Context,
) {
    private companion object {
        private val artworkCache = LruCache<String, Bitmap>(24)
        private val artworkWarmups = ConcurrentHashMap.newKeySet<String>()
        private val artworkLoaderScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val notificationArtworkSizePx =
        (appContext.resources.displayMetrics.density * 56f).roundToInt().coerceAtLeast(112)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas de SnapMusic",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Progreso, resultados y acciones rápidas de tus descargas."
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildProgress(
        queueId: String,
        title: String,
        variantLabel: String,
        progress: Int,
        thumbnailUrl: String,
    ): Notification {
        val safeProgress = progress.coerceIn(0, 100)
        val preparing = safeProgress <= 0
        val openDownloadsIntent = MainActivity.buildOpenQueuePendingIntent(appContext)
        val contentView = buildDownloadRemoteView(
            headline = if (preparing) "Preparando descarga" else "Descargando",
            title = shortTitle(title),
            detail = if (preparing) "$variantLabel · En cola" else "$variantLabel · $safeProgress%",
            thumbnailUrl = thumbnailUrl,
            progress = if (preparing) null else safeProgress,
        )

        return NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_downloading)
            .setContentTitle("Descargando")
            .setContentText(shortTitle(title))
            .setCustomContentView(contentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openDownloadsIntent)
            .build()
    }

    fun showQueued(
        queueId: String,
        title: String,
        variantLabel: String,
        thumbnailUrl: String,
    ) {
        manager.notify(progressNotificationId(queueId), buildProgress(queueId, title, variantLabel, 0, thumbnailUrl))
    }

    fun showProgress(
        queueId: String,
        title: String,
        variantLabel: String,
        progress: Int,
        thumbnailUrl: String,
    ) {
        manager.notify(
            progressNotificationId(queueId),
            buildProgress(queueId, title, variantLabel, progress, thumbnailUrl),
        )
    }

    fun showSuccess(
        queueId: String,
        title: String,
        variantLabel: String,
        thumbnailUrl: String,
    ) {
        val openDownloadsIntent = MainActivity.buildOpenQueuePendingIntent(appContext)
        val contentView = buildDownloadRemoteView(
            headline = "Descarga completa",
            title = shortTitle(title),
            detail = variantLabel,
            thumbnailUrl = thumbnailUrl,
            progress = null,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snapmusic)
            .setContentTitle("Descarga completa")
            .setContentText(shortTitle(title))
            .setCustomContentView(contentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent)
            .build()
        manager.notify(completionNotificationId(queueId), notification)
    }

    fun showError(
        queueId: String,
        title: String,
        message: String,
        thumbnailUrl: String,
    ) {
        val openDownloadsIntent = MainActivity.buildOpenQueuePendingIntent(appContext)
        val contentView = buildDownloadRemoteView(
            headline = "No pude descargar",
            title = shortTitle(title),
            detail = message.take(52),
            thumbnailUrl = thumbnailUrl,
            progress = null,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_snapmusic)
            .setContentTitle("No pude terminar la descarga")
            .setContentText(shortTitle(title))
            .setCustomContentView(contentView)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent)
            .build()
        manager.notify(completionNotificationId(queueId), notification)
    }

    private fun buildDownloadRemoteView(
        headline: String,
        title: String,
        detail: String,
        thumbnailUrl: String,
        progress: Int?,
    ): RemoteViews {
        return RemoteViews(appContext.packageName, R.layout.notification_download).apply {
            setTextViewText(R.id.notification_title, headline)
            setTextViewText(R.id.notification_message, title)
            setTextViewText(R.id.notification_detail, detail)
            setImageViewBitmap(R.id.notification_artwork, artworkFor(thumbnailUrl))
            if (progress == null) {
                setViewVisibility(R.id.notification_progress, android.view.View.GONE)
            } else {
                setViewVisibility(R.id.notification_progress, android.view.View.VISIBLE)
                setProgressBar(R.id.notification_progress, 100, progress.coerceIn(0, 100), false)
            }
        }
    }

    private fun artworkFor(rawUrl: String): Bitmap {
        if (rawUrl.isBlank()) return fallbackArtwork()
        artworkCache.get(rawUrl)?.let { return it }
        warmArtworkAsync(rawUrl)
        return fallbackArtwork()
    }

    private fun warmArtworkAsync(rawUrl: String) {
        if (rawUrl.isBlank()) return
        if (!artworkWarmups.add(rawUrl)) return
        artworkLoaderScope.launch {
            try {
                val bitmap = fetchArtwork(rawUrl) ?: fallbackArtwork()
                artworkCache.put(rawUrl, bitmap)
            } finally {
                artworkWarmups.remove(rawUrl)
            }
        }
    }

    private fun fetchArtwork(rawUrl: String): Bitmap? {
        return runCatching {
            val uri = Uri.parse(rawUrl)
            when (uri.scheme?.lowercase()) {
                "content", "file", "android.resource" -> {
                    appContext.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(
                            it,
                            null,
                            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
                        )
                    }
                }
                "http", "https" -> {
                    val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 2_000
                        readTimeout = 2_000
                        doInput = true
                    }
                    try {
                        connection.connect()
                        connection.inputStream.use {
                            BitmapFactory.decodeStream(
                                it,
                                null,
                                BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
                            )
                        }
                    } finally {
                        connection.disconnect()
                    }
                }
                else -> null
            }
        }.getOrNull()?.fitForNotification()
    }

    private fun fallbackArtwork(): Bitmap {
        return BitmapFactory.decodeResource(
            appContext.resources,
            R.drawable.snapmusic_logo,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 },
        ).fitForNotification()
    }

    private fun Bitmap.fitForNotification(): Bitmap {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        if (safeWidth <= notificationArtworkSizePx && safeHeight <= notificationArtworkSizePx) return this
        val scale = minOf(
            notificationArtworkSizePx.toFloat() / safeWidth.toFloat(),
            notificationArtworkSizePx.toFloat() / safeHeight.toFloat(),
        )
        val targetWidth = (safeWidth * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (safeHeight * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun shortTitle(value: String): String {
        return value.trim().ifBlank { "Archivo" }.take(72)
    }

    private fun progressNotificationId(queueId: String): Int = queueId.hashCode()

    private fun completionNotificationId(queueId: String): Int = queueId.hashCode() xor 0x40000000
}
