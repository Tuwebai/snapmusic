package com.juan.snapmusic.core.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juan.snapmusic.MainActivity
import com.juan.snapmusic.R

class HomePlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            appWidgetManager.updateAppWidget(appWidgetId, buildViews(context, PlaybackSessionStateStore.state.value))
        }
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

        fun updateAll(context: Context, state: PlaybackSessionState = PlaybackSessionStateStore.state.value) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, HomePlaybackWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(component)
            if (widgetIds.isEmpty()) return
            val views = buildViews(appContext, state)
            widgetIds.forEach { manager.updateAppWidget(it, views) }
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
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_subtitle, subtitle)
                setImageViewResource(
                    R.id.widget_play_pause,
                    if (state.showPauseButton) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                )
                val artworkUri = state.artworkUri
                if (artworkUri?.scheme in setOf("content", "file", "android.resource")) {
                    setImageViewUri(R.id.widget_artwork, artworkUri)
                } else {
                    setImageViewResource(R.id.widget_artwork, R.drawable.snapmusic_logo)
                }
                setOnClickPendingIntent(R.id.widget_root, MainActivity.buildOpenPlaybackPendingIntent(context))
                setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, ACTION_PREVIOUS, 4101))
                setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE, 4102))
                setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT, 4103))
                setInt(R.id.widget_previous, "setAlpha", if (state.youtubeHasPrevious) 255 else 120)
                setInt(R.id.widget_next, "setAlpha", if (state.youtubeHasNext) 255 else 120)
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
