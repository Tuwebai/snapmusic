package com.juan.snapmusic.core.platform

import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.juan.snapmusic.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArraySet

@androidx.media3.common.util.UnstableApi
class SnapMusicPlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private companion object {
        const val WIDGET_PROGRESS_TICK_MS = 5_000L
        const val ARTWORK_TAG = "SnapMusicArtwork"
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(SnapMusicMediaNotificationProvider(this))
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
                        30_000,
                        30_000,
                        1_500,
                        5_000,
                    )
                    .setBackBuffer(30_000, true)
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
        val artworkLoader = PlaybackLockScreenArtworkLoader(this)
        val sessionPlayer = SessionTransportPlayer(player)
        val playbackRouteListener = object : Player.Listener {
            private fun publishCurrentTarget(currentItem: MediaItem?) {
                PlaybackNotificationRouteStore.update(
                    mediaId = currentItem?.mediaId,
                    mediaUri = currentItem?.localConfiguration?.uri,
                )
                PlaybackSessionStateStore.updateRuntime(
                    mediaId = currentItem?.mediaId,
                    mediaUri = currentItem?.localConfiguration?.uri,
                    title = currentItem?.mediaMetadata?.title?.toString()
                        ?: currentItem?.mediaMetadata?.displayTitle?.toString(),
                    subtitle = currentItem?.mediaMetadata?.artist?.toString()
                        ?: currentItem?.mediaMetadata?.subtitle?.toString(),
                    artworkUri = currentItem?.mediaMetadata?.artworkUri,
                    artworkData = currentItem?.mediaMetadata?.artworkData,
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = player.knownDurationMs(),
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
            title = player.currentMediaItem?.mediaMetadata?.title?.toString()
                ?: player.currentMediaItem?.mediaMetadata?.displayTitle?.toString(),
            subtitle = player.currentMediaItem?.mediaMetadata?.artist?.toString()
                ?: player.currentMediaItem?.mediaMetadata?.subtitle?.toString(),
            artworkUri = player.currentMediaItem?.mediaMetadata?.artworkUri,
            artworkData = player.currentMediaItem?.mediaMetadata?.artworkData,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.knownDurationMs(),
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
        )

        return MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(MainActivity.buildOpenPlaybackPendingIntent(this))
            .setBitmapLoader(artworkLoader)
            .setCallback(
                object : MediaSession.Callback {
                    override fun onConnect(
                        session: MediaSession,
                        controller: MediaSession.ControllerInfo,
                    ): MediaSession.ConnectionResult {
                        val resultBuilder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        if (session.isMediaNotificationController(controller)) {
                            resultBuilder.setAvailablePlayerCommands(
                                buildNotificationPlayerCommands(PlaybackSessionStateStore.state.value),
                            )
                        }
                        return resultBuilder.build()
                    }
                },
            )
            .build()
            .also { session ->
                mediaSession = session
                serviceScope.launch {
                    var lastNotificationTransportState: Triple<PlaybackSessionTarget, Boolean, Boolean>? = null
                    var lastPublishedMetadataKey: PlaybackMetadataKey? = null
                    var lastArtworkLoadKey: String? = null
                    var artworkLoadJob: Job? = null
                    PlaybackSessionStateStore.state.collectLatest { state ->
                        HomePlaybackWidgetProvider.updateAll(this@SnapMusicPlaybackService, state)
                        val artworkLoadKey = state.artworkLoadKey()
                        if (artworkLoadKey != null && state.artworkData == null && artworkLoadKey != lastArtworkLoadKey) {
                            lastArtworkLoadKey = artworkLoadKey
                            artworkLoadJob?.cancel()
                            artworkLoadJob = serviceScope.launch(Dispatchers.IO) {
                                val uri = state.artworkUri ?: return@launch
                                val mediaId = state.mediaId ?: return@launch
                                val data = artworkLoader.loadArtworkDataForMetadata(uri)
                                if (data != null) {
                                    PlaybackSessionStateStore.updateArtwork(mediaId = mediaId, artworkData = data)
                                    Log.d(ARTWORK_TAG, "loaded mediaId=$mediaId bytes=${data.size}")
                                } else {
                                    Log.w(ARTWORK_TAG, "missing mediaId=$mediaId uri=$uri")
                                }
                            }
                        }
                        val metadataKey = state.toMetadataKey()
                        if (metadataKey != lastPublishedMetadataKey) {
                            lastPublishedMetadataKey = metadataKey
                            sessionPlayer.notifyMediaMetadataChanged()
                        }
                        val notificationController = session.mediaNotificationControllerInfo ?: return@collectLatest
                        val transportState = Triple(
                            state.target,
                            state.youtubeHasPrevious,
                            state.youtubeHasNext,
                        )
                        if (transportState == lastNotificationTransportState) return@collectLatest
                        lastNotificationTransportState = transportState
                        session.setAvailableCommands(
                            notificationController,
                            MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                            buildNotificationPlayerCommands(state),
                        )
                    }
                }
                serviceScope.launch {
                    while (isActive) {
                        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                            PlaybackSessionStateStore.updateProgress(
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = player.knownDurationMs(),
                            )
                        }
                        delay(WIDGET_PROGRESS_TICK_MS)
                    }
                }
            }
    }

    private fun Player.knownDurationMs(): Long {
        return duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    }

    private data class PlaybackMetadataKey(
        val mediaId: String?,
        val title: String?,
        val subtitle: String?,
        val artworkUri: String?,
        val artworkDataHash: Int?,
        val artworkDataSize: Int?,
    )

    private fun PlaybackSessionState.toMetadataKey(): PlaybackMetadataKey {
        return PlaybackMetadataKey(
            mediaId = mediaId,
            title = title,
            subtitle = subtitle,
            artworkUri = artworkUri?.toString(),
            artworkDataHash = artworkData?.contentHashCode(),
            artworkDataSize = artworkData?.size,
        )
    }

    private fun PlaybackSessionState.artworkLoadKey(): String? {
        val id = mediaId ?: return null
        val uri = artworkUri ?: return null
        return "$id|$uri"
    }

    private fun buildNotificationPlayerCommands(state: PlaybackSessionState): Player.Commands {
        if (state.target != PlaybackSessionTarget.YOUTUBE) {
            return MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        }
        return MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            .buildUpon()
            .remove(Player.COMMAND_SEEK_BACK)
            .remove(Player.COMMAND_SEEK_FORWARD)
            .apply {
                if (!state.youtubeHasPrevious) {
                    remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                if (!state.youtubeHasNext) {
                    remove(Player.COMMAND_SEEK_TO_NEXT)
                    remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                }
            }
            .build()
    }

    private class SessionTransportPlayer(
        player: Player,
    ) : ForwardingPlayer(player) {
        private val listeners = CopyOnWriteArraySet<Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            listeners.add(listener)
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            listeners.remove(listener)
            super.removeListener(listener)
        }

        override fun getMediaMetadata(): MediaMetadata {
            val state = PlaybackSessionStateStore.state.value
            val base = super.getMediaMetadata()
            return base.buildUpon()
                .setTitle(state.title ?: base.title)
                .setArtist(state.subtitle ?: base.artist)
                .apply {
                    state.artworkData?.let { data ->
                        setArtworkData(data, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    } ?: state.artworkUri?.let(::setArtworkUri)
                }
                .build()
        }

        fun notifyMediaMetadataChanged() {
            val metadata = mediaMetadata
            listeners.forEach { listener ->
                listener.onMediaMetadataChanged(metadata)
            }
        }

        override fun getAvailableCommands(): Player.Commands {
            val state = PlaybackSessionStateStore.state.value
            if (state.target != PlaybackSessionTarget.YOUTUBE) {
                return super.getAvailableCommands()
            }
            return super.getAvailableCommands()
                .buildUpon()
                .apply {
                    if (state.youtubeHasPrevious) {
                        add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    }
                    if (state.youtubeHasNext) {
                        add(Player.COMMAND_SEEK_TO_NEXT)
                        add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    }
                }
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return getAvailableCommands().contains(command)
        }

        override fun hasPreviousMediaItem(): Boolean {
            val state = PlaybackSessionStateStore.state.value
            return if (state.target == PlaybackSessionTarget.YOUTUBE) {
                state.youtubeHasPrevious
            } else {
                super.hasPreviousMediaItem()
            }
        }

        override fun hasNextMediaItem(): Boolean {
            val state = PlaybackSessionStateStore.state.value
            return if (state.target == PlaybackSessionTarget.YOUTUBE) {
                state.youtubeHasNext
            } else {
                super.hasNextMediaItem()
            }
        }

        override fun seekToPrevious() {
            if (!dispatchYouTubeTransport(hasPrevious = true)) {
                super.seekToPrevious()
            }
        }

        override fun seekToPreviousMediaItem() {
            if (!dispatchYouTubeTransport(hasPrevious = true)) {
                super.seekToPreviousMediaItem()
            }
        }

        override fun seekToNext() {
            if (!dispatchYouTubeTransport(hasPrevious = false)) {
                super.seekToNext()
            }
        }

        override fun seekToNextMediaItem() {
            if (!dispatchYouTubeTransport(hasPrevious = false)) {
                super.seekToNextMediaItem()
            }
        }

        private fun dispatchYouTubeTransport(hasPrevious: Boolean): Boolean {
            val state = PlaybackSessionStateStore.state.value
            if (state.target != PlaybackSessionTarget.YOUTUBE) return false
            if (hasPrevious) {
                if (!state.youtubeHasPrevious) return true
                PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_PREVIOUS)
            } else {
                if (!state.youtubeHasNext) return true
                PlaybackCommandBus.dispatch(PlaybackCommand.YOUTUBE_NEXT)
            }
            return true
        }
    }
}
