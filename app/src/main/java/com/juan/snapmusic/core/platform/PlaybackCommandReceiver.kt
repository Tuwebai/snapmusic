package com.juan.snapmusic.core.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PlaybackCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_YOUTUBE_NEXT -> PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_NEXT)
            ACTION_YOUTUBE_PREVIOUS -> PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_PREVIOUS)
            ACTION_YOUTUBE_PLAY_PAUSE -> PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_PLAY_PAUSE)
        }
    }

    companion object {
        const val ACTION_YOUTUBE_NEXT = "com.juan.snapmusic.action.YOUTUBE_NEXT"
        const val ACTION_YOUTUBE_PREVIOUS = "com.juan.snapmusic.action.YOUTUBE_PREVIOUS"
        const val ACTION_YOUTUBE_PLAY_PAUSE = "com.juan.snapmusic.action.YOUTUBE_PLAY_PAUSE"
    }
}
