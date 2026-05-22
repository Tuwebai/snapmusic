package com.juan.snapmusic.core.platform

import android.net.Uri

enum class PlaybackNotificationRouteTarget {
    NONE,
    PREVIEW,
    YOUTUBE,
}

object PlaybackNotificationRouteStore {
    @Volatile
    private var currentTarget: PlaybackNotificationRouteTarget = PlaybackNotificationRouteTarget.NONE

    fun currentTarget(): PlaybackNotificationRouteTarget = currentTarget

    fun update(mediaId: String?, mediaUri: Uri?) {
        currentTarget = when {
            mediaId != null && validateYouTubeUrl(mediaId).normalizedUrl != null -> PlaybackNotificationRouteTarget.YOUTUBE
            mediaUri?.scheme in setOf("content", "file") -> PlaybackNotificationRouteTarget.PREVIEW
            else -> PlaybackNotificationRouteTarget.NONE
        }
    }

    fun clear() {
        currentTarget = PlaybackNotificationRouteTarget.NONE
    }
}
