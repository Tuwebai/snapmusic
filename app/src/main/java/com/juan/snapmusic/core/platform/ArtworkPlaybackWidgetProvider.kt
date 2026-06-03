package com.juan.snapmusic.core.platform

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class ArtworkPlaybackWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlaybackWidgetRenderer.update(context, appWidgetManager, appWidgetIds, PlaybackWidgetKind.ARTWORK)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        PlaybackWidgetRenderer.updateAll(context)
    }
}
