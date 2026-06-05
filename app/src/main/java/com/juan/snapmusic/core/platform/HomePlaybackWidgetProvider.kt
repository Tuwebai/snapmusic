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
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juan.snapmusic.MainActivity
import com.juan.snapmusic.R

class HomePlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlaybackWidgetRenderer.update(context, appWidgetManager, appWidgetIds, PlaybackWidgetKind.FULL)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PLAY_PAUSE,
            ACTION_NEXT,
            ACTION_PREVIOUS,
            -> {
                dispatchMediaSessionAction(context.applicationContext, intent.action.orEmpty(), goAsync())
                return
            }
        }
        super.onReceive(context, intent)
        updateAll(context)
    }

    private fun dispatchMediaSessionAction(
        context: Context,
        action: String,
        pendingResult: PendingResult,
    ) {
        val token = SessionToken(context, ComponentName(context, SnapMusicPlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture.addListener(
            {
                val controller = runCatching { controllerFuture.get() }.getOrNull()
                try {
                    when (action) {
                        ACTION_PLAY_PAUSE -> {
                            if (controller?.isPlaying == true || PlaybackSessionStateStore.state.value.showPauseButton) {
                                controller?.pause()
                            } else {
                                controller?.play()
                            }
                        }

                        ACTION_NEXT -> controller?.seekToNextMediaItem()
                        ACTION_PREVIOUS -> controller?.seekToPreviousMediaItem()
                    }
                } finally {
                    controller?.release()
                    updateAll(context)
                    pendingResult.finish()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    companion object {
        private const val ACTION_PLAY_PAUSE = "com.juan.snapmusic.widget.PLAY_PAUSE"
        private const val ACTION_NEXT = "com.juan.snapmusic.widget.NEXT"
        private const val ACTION_PREVIOUS = "com.juan.snapmusic.widget.PREVIOUS"
        private const val WIDGET_PROGRESS_MAX = 1_000
        private const val WIDGET_ARTWORK_MAX_PX = 192
        private var lastArtworkHash: Int? = null
        private var lastArtworkBitmap: Bitmap? = null

        fun updateAll(context: Context, state: PlaybackSessionState = PlaybackSessionStateStore.state.value) {
            PlaybackWidgetRenderer.updateAll(context, state)
        }

        private fun buildViews(context: Context, state: PlaybackSessionState): RemoteViews {
            val title = state.title?.takeIf { it.isNotBlank() } ?: "SnapMusic"
            val subtitle = state.subtitle?.takeIf { it.isNotBlank() }
                ?: if (state.target == PlaybackSessionTarget.NONE) {
                    "Sin reproducción activa"
                } else {
                    "Reproduciendo ahora"
                }

            return RemoteViews(context.packageName, R.layout.widget_home_playback).apply {
                val positionMs = state.effectivePositionMs()
                val durationMs = state.durationMs.coerceAtLeast(0L)
                val progress = if (durationMs > 0L) {
                    ((positionMs.coerceAtMost(durationMs) * WIDGET_PROGRESS_MAX) / durationMs).toInt()
                } else {
                    0
                }
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_subtitle, subtitle)
                setTextViewText(R.id.widget_elapsed, positionMs.formatDuration())
                setTextViewText(R.id.widget_duration, durationMs.formatDuration())
                setProgressBar(R.id.widget_progress, WIDGET_PROGRESS_MAX, progress, false)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.showPauseButton) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                )
                val artworkBitmap = decodeWidgetArtwork(state.artworkData)
                if (artworkBitmap != null) {
                    setImageViewBitmap(R.id.widget_artwork, artworkBitmap)
                } else {
                    val artworkUriBitmap = decodeWidgetArtworkUri(context, state.artworkUri)
                    if (artworkUriBitmap != null) {
                        setImageViewBitmap(R.id.widget_artwork, artworkUriBitmap)
                    } else {
                        setImageViewResource(R.id.widget_artwork, R.drawable.snapmusic_brand_logo)
                    }
                }
                setOnClickPendingIntent(R.id.widget_root, MainActivity.buildOpenPlaybackPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, ACTION_PREVIOUS, 4101))
                setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE, 4102))
                setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT, 4103))
                setInt(R.id.widget_previous, "setAlpha", if (state.hasPreviousWidgetAction()) 255 else 120)
                setInt(R.id.widget_next, "setAlpha", if (state.hasNextWidgetAction()) 255 else 120)
            }
        }

        private fun PlaybackSessionState.effectivePositionMs(): Long {
            val safePosition = positionMs.coerceAtLeast(0L)
            if (!isPlaying || progressUpdatedAtMs <= 0L) return safePosition
            val elapsed = (SystemClock.elapsedRealtime() - progressUpdatedAtMs).coerceAtLeast(0L)
            return if (durationMs > 0L) {
                (safePosition + elapsed).coerceAtMost(durationMs)
            } else {
                safePosition + elapsed
            }
        }

        private fun PlaybackSessionState.hasPreviousWidgetAction(): Boolean {
            return when (target) {
                PlaybackSessionTarget.NONE -> false
                PlaybackSessionTarget.YOUTUBE -> youtubeHasPrevious
                PlaybackSessionTarget.PREVIEW -> true
            }
        }

        private fun PlaybackSessionState.hasNextWidgetAction(): Boolean {
            return when (target) {
                PlaybackSessionTarget.NONE -> false
                PlaybackSessionTarget.YOUTUBE -> youtubeHasNext
                PlaybackSessionTarget.PREVIEW -> true
            }
        }

        private fun Long.formatDuration(): String {
            val totalSeconds = (this / 1_000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }

        private fun decodeWidgetArtwork(artworkData: ByteArray?): Bitmap? {
            if (artworkData == null || artworkData.isEmpty()) return null
            val artworkHash = artworkData.contentHashCode()
            if (lastArtworkHash == artworkHash) return lastArtworkBitmap
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, bounds)
            val largestSide = maxOf(bounds.outWidth, bounds.outHeight)
            val sampleSize = generateSequence(1) { it * 2 }
                .first { sample -> largestSide / sample <= WIDGET_ARTWORK_MAX_PX }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, options).also { bitmap ->
                lastArtworkHash = artworkHash
                lastArtworkBitmap = bitmap
            }
        }

        private fun decodeWidgetArtworkUri(context: Context, artworkUri: Uri?): Bitmap? {
            if (artworkUri == null) return null
            val scheme = artworkUri.scheme?.lowercase()
            if (scheme !in setOf("content", "file", "android.resource")) return null
            val cacheKey = artworkUri.toString().hashCode()
            if (lastArtworkHash == cacheKey) return lastArtworkBitmap
            val data = runCatching {
                when (scheme) {
                    "file" -> context.contentResolver.openInputStream(artworkUri)?.use { it.readBytes() }
                    else -> context.contentResolver.openInputStream(artworkUri)?.use { it.readBytes() }
                }
            }.getOrNull() ?: return null
            return decodeWidgetArtwork(data).also { bitmap ->
                lastArtworkHash = cacheKey
                lastArtworkBitmap = bitmap
            }
        }

        private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, HomePlaybackWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
