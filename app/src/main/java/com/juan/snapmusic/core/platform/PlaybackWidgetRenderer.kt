package com.juan.snapmusic.core.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import com.juan.snapmusic.MainActivity
import com.juan.snapmusic.R

enum class PlaybackWidgetKind(
    val layoutRes: Int,
    val providerClass: Class<out AppWidgetProvider>,
    val requestBase: Int,
) {
    FULL(R.layout.widget_home_playback, HomePlaybackWidgetProvider::class.java, 4100),
    COMPACT(R.layout.widget_playback_compact, CompactPlaybackWidgetProvider::class.java, 4200),
    ARTWORK(R.layout.widget_playback_artwork, ArtworkPlaybackWidgetProvider::class.java, 4300),
}

object PlaybackWidgetRenderer {
    private const val ACTION_PLAY_PAUSE = "com.juan.snapmusic.widget.PLAY_PAUSE"
    private const val ACTION_NEXT = "com.juan.snapmusic.widget.NEXT"
    private const val ACTION_PREVIOUS = "com.juan.snapmusic.widget.PREVIOUS"
    private const val WIDGET_PROGRESS_MAX = 1_000
    private const val WIDGET_ARTWORK_MAX_PX = 192

    fun updateAll(context: Context, state: PlaybackSessionState = PlaybackSessionStateStore.state.value) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        PlaybackWidgetKind.values().forEach { kind ->
            val ids = manager.getAppWidgetIds(ComponentName(appContext, kind.providerClass))
            update(appContext, manager, ids, kind, state)
        }
    }

    fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        kind: PlaybackWidgetKind,
        state: PlaybackSessionState = PlaybackSessionStateStore.state.value,
    ) {
        if (appWidgetIds.isEmpty()) return
        val views = buildViews(context.applicationContext, kind, state)
        appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
    }

    private fun buildViews(context: Context, kind: PlaybackWidgetKind, state: PlaybackSessionState): RemoteViews {
        val title = state.title?.takeIf { it.isNotBlank() } ?: "SnapMusic"
        val subtitle = state.subtitle?.takeIf { it.isNotBlank() }
            ?: if (state.target == PlaybackSessionTarget.NONE) "Listo para reproducir" else "Reproduciendo ahora"
        val positionMs = state.effectivePositionMs()
        val durationMs = state.durationMs.coerceAtLeast(0L)
        val progress = if (durationMs > 0L) {
            ((positionMs.coerceAtMost(durationMs) * WIDGET_PROGRESS_MAX) / durationMs).toInt()
        } else {
            0
        }
        return RemoteViews(context.packageName, kind.layoutRes).apply {
            setTextViewText(R.id.widget_title, title)
            setTextViewText(R.id.widget_subtitle, subtitle)
            setTextViewText(R.id.widget_elapsed, positionMs.formatDuration())
            setTextViewText(R.id.widget_duration, durationMs.formatDuration())
            setProgressBar(R.id.widget_progress, WIDGET_PROGRESS_MAX, progress, false)
            setTextViewText(R.id.widget_previous, "‹")
            setTextViewText(R.id.widget_play_pause, if (state.showPauseButton) "Ⅱ" else "▶")
            setTextViewText(R.id.widget_next, "›")
            setTextColor(R.id.widget_previous, if (state.hasPreviousWidgetAction()) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
            setTextColor(R.id.widget_next, if (state.hasNextWidgetAction()) 0xFFFFFFFF.toInt() else 0x66FFFFFF)
            setArtwork(context, state)
            setOnClickPendingIntent(R.id.widget_root, MainActivity.buildOpenPlaybackPendingIntent(context))
            setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, ACTION_PREVIOUS, kind.requestBase + 1))
            setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE, kind.requestBase + 2))
            setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT, kind.requestBase + 3))
        }
    }

    private fun RemoteViews.setArtwork(context: Context, state: PlaybackSessionState) {
        val bitmap = decodeWidgetArtwork(state.artworkData) ?: decodeWidgetArtworkUri(context, state.artworkUri)
        if (bitmap != null) {
            setImageViewBitmap(R.id.widget_artwork, bitmap)
        } else {
            setImageViewResource(R.id.widget_artwork, R.drawable.snapmusic_logo)
        }
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, HomePlaybackWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun PlaybackSessionState.effectivePositionMs(): Long {
        val safePosition = positionMs.coerceAtLeast(0L)
        if (!isPlaying || progressUpdatedAtMs <= 0L) return safePosition
        return (safePosition + (SystemClock.elapsedRealtime() - progressUpdatedAtMs)).coerceAtLeast(0L)
    }

    private fun PlaybackSessionState.hasPreviousWidgetAction(): Boolean = youtubeHasPrevious || target != PlaybackSessionTarget.NONE

    private fun PlaybackSessionState.hasNextWidgetAction(): Boolean = youtubeHasNext || target != PlaybackSessionTarget.NONE

    private fun Long.formatDuration(): String {
        if (this <= 0L) return "0:00"
        val totalSeconds = this / 1_000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }

    private fun decodeWidgetArtwork(data: ByteArray?): Bitmap? {
        if (data == null || data.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = bounds.widgetSampleSize() }
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)?.fitWidgetArtwork()
    }

    private fun decodeWidgetArtworkUri(context: Context, uri: Uri?): Bitmap? {
        if (uri == null) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        val options = BitmapFactory.Options().apply { inSampleSize = bounds.widgetSampleSize() }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()?.fitWidgetArtwork()
    }

    private fun BitmapFactory.Options.widgetSampleSize(): Int {
        var sample = 1
        while ((outWidth / sample) > WIDGET_ARTWORK_MAX_PX * 2 || (outHeight / sample) > WIDGET_ARTWORK_MAX_PX * 2) {
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.fitWidgetArtwork(): Bitmap {
        val maxSide = maxOf(width, height)
        if (maxSide <= WIDGET_ARTWORK_MAX_PX) return this
        val scale = WIDGET_ARTWORK_MAX_PX.toFloat() / maxSide
        return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
    }
}
