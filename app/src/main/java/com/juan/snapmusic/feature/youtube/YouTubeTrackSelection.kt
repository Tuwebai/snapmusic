package com.juan.snapmusic.feature.youtube

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.model.YouTubePlayerSeekState
import com.juan.snapmusic.core.model.YouTubePlayerSessionState
import com.juan.snapmusic.core.platform.PlaybackArtworkBadgeHelper
import com.juan.snapmusic.core.platform.SnapMusicPlaybackService
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal fun isAdaptivePlaybackUrl(url: String): Boolean {
    if (url.isBlank()) return false
    val lower = url.lowercase()
    return lower.contains(".mpd") ||
        lower.contains("manifest.googlevideo.com") ||
        lower.contains(".m3u8") ||
        lower.contains("/manifest/") ||
        lower.startsWith("https://manifest")
}

internal fun applyYouTubePlaybackQuality(
    mediaController: MediaController,
    featured: YouTubeFeaturedVideo,
) {
    val selectedVariant = featured.resolvedMedia?.videoVariants?.firstOrNull { it.id == featured.selectedVideoQualityId }
    val adaptivePlayback = featured.adaptivePlaybackUrl?.let(::isAdaptivePlaybackUrl) == true &&
        featured.playbackUrl == featured.adaptivePlaybackUrl
    val preferredAutomaticHeight = featured.resolvedMedia?.let(::resolvePreferredAutomaticHeight)
    val builder = mediaController.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setForceHighestSupportedBitrate(false)
        .setMinVideoSize(0, 0)
        .clearViewportSizeConstraints()
        .clearVideoSizeConstraints()

    if (adaptivePlayback && featured.selectedVideoQualityId == "auto") {
        preferredAutomaticHeight?.let { targetHeight ->
            builder.setMaxVideoSize(Int.MAX_VALUE, targetHeight)
        }
    } else if (adaptivePlayback && featured.selectedVideoQualityId != "auto") {
        val targetHeight = selectedVariant?.resolution?.substringBefore('p')?.toIntOrNull()
            ?: featured.selectedVideoQualityId.removePrefix("adaptive-").toIntOrNull()
        val override = resolveVideoTrackOverride(
            tracks = mediaController.currentTracks,
            requestedHeight = targetHeight,
        )
        if (override != null) {
            builder.setOverrideForType(override)
        }
    }

    mediaController.trackSelectionParameters = builder.build()
}

internal fun resolvePreferredAutomaticHeight(
    media: com.juan.snapmusic.core.model.ResolvedMedia,
): Int? {
    val heights = media.videoVariants
        .mapNotNull { it.resolution?.substringBefore('p')?.toIntOrNull() }
        .distinct()
        .sortedDescending()
    return heights.filter { it <= 720 }.maxOrNull()
        ?: heights.minByOrNull { kotlin.math.abs(it - 720) }
}

internal fun resolveVideoTrackOverride(
    tracks: Tracks,
    requestedHeight: Int?,
): TrackSelectionOverride? {
    if (requestedHeight == null) return null
    data class Candidate(
        val group: Tracks.Group,
        val index: Int,
        val height: Int,
        val bitrate: Int,
    )

    val candidates = tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO && group.length > 0 && group.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                val format = group.getTrackFormat(index)
                val height = format.height.takeIf { it > 0 } ?: return@mapNotNull null
                if (!group.isTrackSupported(index)) return@mapNotNull null
                Candidate(
                    group = group,
                    index = index,
                    height = height,
                    bitrate = format.bitrate.takeIf { it > 0 } ?: 0,
                )
            }
        }
        .toList()

    val selected = candidates
        .filter { it.height <= requestedHeight }
        .maxWithOrNull(compareBy<Candidate>({ it.height }, { it.bitrate }))
        ?: candidates.minWithOrNull(compareBy<Candidate>({ kotlin.math.abs(it.height - requestedHeight) }, { -it.bitrate }))
        ?: return null

    return TrackSelectionOverride(selected.group.mediaTrackGroup, listOf(selected.index))
}

internal data class AudioTrackCandidate(
    val group: Tracks.Group,
    val index: Int,
    val priority: Int,
    val bitrate: Int,
)

internal fun maybeApplyPreferredAudioTrackSelection(
    mediaController: MediaController,
    tracks: Tracks,
) {
    val preferred = resolvePreferredAudioTrackCandidate(tracks) ?: return
    val current = resolveCurrentAudioTrackCandidate(tracks)
    if (
        current != null &&
        current.group.mediaTrackGroup == preferred.group.mediaTrackGroup &&
        current.index == preferred.index
    ) {
        return
    }
    mediaController.trackSelectionParameters = mediaController.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setOverrideForType(TrackSelectionOverride(preferred.group.mediaTrackGroup, listOf(preferred.index)))
        .build()
}

internal fun resolveCurrentAudioTrackCandidate(
    tracks: Tracks,
): AudioTrackCandidate? {
    return tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_AUDIO && group.length > 0 && group.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                if (!group.isTrackSupported(index) || !group.isTrackSelected(index)) return@mapNotNull null
                val format = group.getTrackFormat(index)
                AudioTrackCandidate(
                    group = group,
                    index = index,
                    priority = preferredAudioTrackPriority(format),
                    bitrate = format.bitrate.takeIf { it > 0 } ?: 0,
                )
            }
        }
        .firstOrNull()
}

internal fun resolvePreferredAudioTrackCandidate(
    tracks: Tracks,
): AudioTrackCandidate? {
    data class Candidate(
        val group: Tracks.Group,
        val index: Int,
        val priority: Int,
        val bitrate: Int,
    )

    val candidate = tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_AUDIO && group.length > 0 && group.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                if (!group.isTrackSupported(index)) return@mapNotNull null
                val format = group.getTrackFormat(index)
                Candidate(
                    group = group,
                    index = index,
                    priority = preferredAudioTrackPriority(format),
                    bitrate = format.bitrate.takeIf { it > 0 } ?: 0,
                )
            }
        }
        .minWithOrNull(compareBy<Candidate>({ it.priority }, { -it.bitrate }, { it.index }))
        ?: return null

    return AudioTrackCandidate(
        group = candidate.group,
        index = candidate.index,
        priority = candidate.priority,
        bitrate = candidate.bitrate,
    )
}

internal fun preferredAudioTrackPriority(
    format: androidx.media3.common.Format,
): Int {
    val label = format.label.orEmpty().lowercase()
    val roleFlags = format.roleFlags
    val isOriginal = "original" in label
    val isDubbed = "dubbed" in label || "dub" in label || "doblad" in label || "dublad" in label
    val isSecondary = "secondary" in label || "secund" in label || (roleFlags and C.ROLE_FLAG_ALTERNATE) != 0
    val isDescriptive = "description" in label || "descript" in label || (roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO) != 0
    return when {
        isOriginal -> 0
        !isDubbed && !isSecondary && !isDescriptive -> 1
        isSecondary -> 2
        isDescriptive -> 3
        isDubbed -> 4
        else -> 1
    }
}

internal fun resolveActualVideoHeight(tracks: Tracks): Int? {
    return tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                if (!group.isTrackSelected(index)) return@mapNotNull null
                group.getTrackFormat(index).height.takeIf { it > 0 }
            }
        }
        .maxOrNull()
}

internal fun resolveAvailableVideoHeights(tracks: Tracks): List<Int> {
    return tracks.groups
        .asSequence()
        .filter { group -> group.type == C.TRACK_TYPE_VIDEO && group.isSupported }
        .flatMap { group ->
            (0 until group.length).asSequence().mapNotNull { index ->
                if (!group.isTrackSupported(index)) return@mapNotNull null
                group.getTrackFormat(index).height.takeIf { it > 0 }
            }
        }
        .distinct()
        .sortedDescending()
        .toList()
}
