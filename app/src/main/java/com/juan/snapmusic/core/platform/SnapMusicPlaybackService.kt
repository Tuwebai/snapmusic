package com.juan.snapmusic.core.platform

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.juan.snapmusic.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
class SnapMusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return ensureSession()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        PlaybackNotificationRouteStore.clear()
        PlaybackSessionStateStore.clear()
        super.onDestroy()
    }

    @Synchronized
    private fun ensureSession(): MediaSession {
        mediaSession?.let { return it }

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
                PlaybackSessionStateStore.updateRuntime(
                    mediaId = currentItem?.mediaId,
                    mediaUri = currentItem?.localConfiguration?.uri,
                    playWhenReady = player.playWhenReady,
                    isPlaying = player.isPlaying,
                    playbackState = player.playbackState,
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                publishCurrentTarget(mediaItem)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                publishCurrentTarget(player.currentMediaItem)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishCurrentTarget(player.currentMediaItem)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                publishCurrentTarget(player.currentMediaItem)
            }
        }
        player.addListener(playbackRouteListener)
        PlaybackNotificationRouteStore.update(
            mediaId = player.currentMediaItem?.mediaId,
            mediaUri = player.currentMediaItem?.localConfiguration?.uri,
        )
        PlaybackSessionStateStore.updateRuntime(
            mediaId = player.currentMediaItem?.mediaId,
            mediaUri = player.currentMediaItem?.localConfiguration?.uri,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
        )

        return MediaSession.Builder(this, player)
            .setSessionActivity(MainActivity.buildOpenPlaybackPendingIntent(this))
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult {
                        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                            .buildUpon()
                            .add(SessionCommand(PlaybackCommandReceiver.ACTION_YOUTUBE_PREVIOUS, Bundle.EMPTY))
                            .add(SessionCommand(PlaybackCommandReceiver.ACTION_YOUTUBE_PLAY_PAUSE, Bundle.EMPTY))
                            .add(SessionCommand(PlaybackCommandReceiver.ACTION_YOUTUBE_NEXT, Bundle.EMPTY))
                            .build()
                        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                            .setAvailableSessionCommands(sessionCommands)
                            .build()
                    }

                    override fun onCustomCommand(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                        customCommand: androidx.media3.session.SessionCommand,
                        args: Bundle,
                    ) = Futures.immediateFuture(
                        when (customCommand.customAction) {
                            PlaybackCommandReceiver.ACTION_YOUTUBE_PREVIOUS -> {
                                PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_PREVIOUS)
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            }

                            PlaybackCommandReceiver.ACTION_YOUTUBE_PLAY_PAUSE -> {
                                PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_PLAY_PAUSE)
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            }

                            PlaybackCommandReceiver.ACTION_YOUTUBE_NEXT -> {
                                PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_NEXT)
                                SessionResult(SessionResult.RESULT_SUCCESS)
                            }

                            else -> SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                        },
                    )
                },
            )
            .build()
            .also { session ->
                mediaSession = session
                serviceScope.launch {
                    PlaybackSessionStateStore.state.collectLatest { state ->
                        session.setCustomLayout(
                            if (state.target == PlaybackSessionTarget.YOUTUBE) {
                                buildYouTubeNotificationButtons(state)
                            } else {
                                emptyList()
                            },
                        )
                    }
                }
            }
    }

    private fun buildYouTubeNotificationButtons(state: PlaybackSessionState): List<CommandButton> {
        return buildList {
            if (state.youtubeHasPrevious) {
                add(
                    CommandButton.Builder(CommandButton.ICON_PREVIOUS)
                        .setDisplayName("Anterior")
                        .setSessionCommand(
                            SessionCommand(PlaybackCommandReceiver.ACTION_YOUTUBE_PREVIOUS, Bundle.EMPTY),
                        )
                        .build(),
                )
            }
            if (state.youtubeHasNext) {
                add(
                    CommandButton.Builder(CommandButton.ICON_NEXT)
                        .setDisplayName("Siguiente")
                        .setSessionCommand(
                            SessionCommand(PlaybackCommandReceiver.ACTION_YOUTUBE_NEXT, Bundle.EMPTY),
                        )
                        .build(),
                )
            }
        }
    }
}
