package com.juan.snapmusic.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class YouTubePlayerSessionState(
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val preloadedNextFeatured: YouTubeFeaturedVideo? = null,
)

@Immutable
data class YouTubePlayerSeekState(
    val requestId: Long = 0L,
    val positionMs: Long = 0L,
)
