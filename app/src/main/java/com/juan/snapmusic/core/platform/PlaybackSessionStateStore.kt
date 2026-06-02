package com.juan.snapmusic.core.platform

import android.net.Uri
import android.os.SystemClock
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
    val artworkData: ByteArray? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progressUpdatedAtMs: Long = 0L,
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
        artworkData: ByteArray? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        playWhenReady: Boolean,
        isPlaying: Boolean,
        playbackState: Int,
    ) {
        val target = resolveTarget(mediaId, mediaUri)
        _state.update { current ->
            val sameRuntimeMedia = current.target == target &&
                current.mediaId == mediaId &&
                current.mediaUri == mediaUri
            val effectiveArtworkUri = artworkUri ?: current.artworkUri.takeIf { sameRuntimeMedia }
            val effectiveArtworkData = artworkData ?: current.artworkData.takeIf { sameRuntimeMedia }
            if (
                current.target == target &&
                current.mediaId == mediaId &&
                current.mediaUri == mediaUri &&
                current.title == title &&
                current.subtitle == subtitle &&
                current.artworkUri == effectiveArtworkUri &&
                current.artworkData.contentEqualsNullable(effectiveArtworkData) &&
                current.positionMs == positionMs &&
                current.durationMs == durationMs &&
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
                    artworkUri = effectiveArtworkUri,
                    artworkData = effectiveArtworkData,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    progressUpdatedAtMs = SystemClock.elapsedRealtime(),
                    playWhenReady = playWhenReady,
                    isPlaying = isPlaying,
                    playbackState = playbackState,
                )
            }
        }
    }

    fun updateArtwork(
        mediaId: String,
        artworkUri: Uri? = null,
        artworkData: ByteArray? = null,
    ) {
        if (artworkUri == null && artworkData == null) return
        _state.update { current ->
            if (current.target != PlaybackSessionTarget.PREVIEW || current.mediaId != mediaId) {
                current
            } else {
                val effectiveArtworkUri = artworkUri ?: current.artworkUri
                val effectiveArtworkData = artworkData ?: current.artworkData
                if (
                    current.artworkUri == effectiveArtworkUri &&
                    current.artworkData.contentEqualsNullable(effectiveArtworkData)
                ) {
                    current
                } else {
                    current.copy(
                        artworkUri = effectiveArtworkUri,
                        artworkData = effectiveArtworkData,
                        progressUpdatedAtMs = SystemClock.elapsedRealtime(),
                    )
                }
            }
        }
    }

    fun updateProgress(
        positionMs: Long,
        durationMs: Long,
    ) {
        _state.update { current ->
            val safePosition = positionMs.coerceAtLeast(0L)
            val safeDuration = durationMs.coerceAtLeast(0L)
            if (current.positionMs == safePosition && current.durationMs == safeDuration) {
                current
            } else {
                current.copy(
                    positionMs = safePosition,
                    durationMs = safeDuration,
                    progressUpdatedAtMs = SystemClock.elapsedRealtime(),
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

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
    return when {
        this === other -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }
}
