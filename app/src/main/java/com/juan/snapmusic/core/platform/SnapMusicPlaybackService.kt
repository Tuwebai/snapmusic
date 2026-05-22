package com.juan.snapmusic.core.platform

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.juan.snapmusic.MainActivity

@androidx.media3.common.util.UnstableApi
class SnapMusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        if (mediaSession != null) return

        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setForceHighestSupportedBitrate(false)
                .clearViewportSizeConstraints()
                .build()
        }
        val player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .setBufferDurationsMs(
                        8_000,
                        60_000,
                        2_000,
                        3_000,
                    )
                    .build(),
            )
            .setMediaSourceFactory(SnapMusicPlaybackMediaSourceFactory(this))
            .build()
            .apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                )
                setHandleAudioBecomingNoisy(true)
            }
        val playbackRouteListener = object : Player.Listener {
            private fun publishCurrentTarget(currentItem: MediaItem?) {
                PlaybackNotificationRouteStore.update(
                    mediaId = currentItem?.mediaId,
                    mediaUri = currentItem?.localConfiguration?.uri,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publishCurrentTarget(mediaItem)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                publishCurrentTarget(player.currentMediaItem)
            }
        }
        player.addListener(playbackRouteListener)
        PlaybackNotificationRouteStore.update(
            mediaId = player.currentMediaItem?.mediaId,
            mediaUri = player.currentMediaItem?.localConfiguration?.uri,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(MainActivity.buildOpenPlaybackPendingIntent(this))
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()

                    override fun onCustomCommand(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        customCommand: androidx.media3.session.SessionCommand,
                        args: Bundle,
                    ) = Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                },
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlaybackNotificationRouteStore.clear()
        super.onDestroy()
    }
}
