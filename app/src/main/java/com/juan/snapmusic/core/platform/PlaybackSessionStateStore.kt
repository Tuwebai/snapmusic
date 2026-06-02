package com.juan.snapmusic.core.platform

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PlaybackSessionTarget {
    NONE,
    PREVIEW,
    YOUTUBE,
}

@Immutable
data class PlaybackSessionState(
    val target: PlaybackSessionTarget = PlaybackSessionTarget.NONE,
    val mediaId: String? = null,
    val mediaUri: Uri? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val artworkUri: Uri? = null,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE,
    val youtubeHasPrevious: Boolean = false,
    val youtubeHasNext: Boolean = false,
) {
    val showPauseButton: Boolean
        get() = playWhenReady && playbackState != Player.STATE_ENDED
}

object PlaybackSessionStateStore {
    private val _state = MutableStateFlow(PlaybackSessionState())
    val state = _state.asStateFlow()

    fun updateRuntime(
        mediaId: String?,
        mediaUri: Uri?,
        title: String? = null,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        playWhenReady: Boolean,
        isPlaying: Boolean,
        playbackState: Int,
    ) {
        val target = resolveTarget(mediaId, mediaUri)
        _state.update { current ->
            if (
                current.target == target &&
                current.mediaId == mediaId &&
                current.mediaUri == mediaUri &&
                current.title == title &&
                current.subtitle == subtitle &&
                current.artworkUri == artworkUri &&
                current.playWhenReady == playWhenReady &&
                current.isPlaying == isPlaying &&
                current.playbackState == playbackState
            ) {
                current
            } else {
                current.copy(
                    target = target,
                    mediaId = mediaId,
                    mediaUri = mediaUri,
                    title = title,
                    subtitle = subtitle,
                    artworkUri = artworkUri,
                    playWhenReady = playWhenReady,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                )
            }
        }
    }

    fun updateYouTubeTransport(
        hasPrevious: Boolean,
        hasNext: Boolean,
    ) {
        _state.update { current ->
            if (current.youtubeHasPrevious == hasPrevious && current.youtubeHasNext == hasNext) {
                current
            } else {
                current.copy(
                    youtubeHasPrevious = hasPrevious,
                    youtubeHasNext = hasNext,
                )
            }
        }
    }

    fun clear() {
        _state.value = PlaybackSessionState()
    }

    private fun resolveTarget(
        mediaId: String?,
        mediaUri: Uri?,
    ): PlaybackSessionTarget {
        return when {
            mediaId != null && validateYouTubeUrl(mediaId).normalizedUrl != null -> PlaybackSessionTarget.YOUTUBE
            mediaUri?.scheme in setOf("content", "file") -> PlaybackSessionTarget.PREVIEW
            else -> PlaybackSessionTarget.NONE
        }
    }
}
