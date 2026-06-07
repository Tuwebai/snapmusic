package com.juan.snapmusic.core.platform

import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.juan.snapmusic.R
import com.google.common.collect.ImmutableList

private const val PLAYBACK_CHANNEL_ID = "snapmusic_playback"
private const val COLORIZED_EXTRA = "android.colorized"

@UnstableApi
internal class SnapMusicMediaNotificationProvider(
    context: Context,
) : MediaNotification.Provider {
    private val delegate = DefaultMediaNotificationProvider.Builder(context)
        .setChannelId(PLAYBACK_CHANNEL_ID)
        .setChannelName(R.string.app_name)
        .build()

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val created = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            onNotificationChangedCallback,
        )
        created.notification.color = SNAPMUSIC_NOTIFICATION_RED
        created.notification.extras.putBoolean(COLORIZED_EXTRA, true)
        return MediaNotification(created.notificationId, created.notification)
    }

    override fun handleCustomCommand(
        mediaSession: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(mediaSession, action, extras)
}
