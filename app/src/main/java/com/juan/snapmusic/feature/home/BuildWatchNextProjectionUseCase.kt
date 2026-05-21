package com.juan.snapmusic.feature.home

import com.juan.snapmusic.core.model.YouTubeFeedItem

class BuildWatchNextProjectionUseCase {
    operator fun invoke(
        showPlayer: Boolean,
        featuredSourceUrl: String,
        playbackQueue: List<YouTubeFeedItem>,
        items: List<YouTubeFeedItem>,
    ): List<YouTubeFeedItem> {
        val visibleItems = if (showPlayer) playbackQueue.ifEmpty { items } else items
        if (!showPlayer || featuredSourceUrl.isBlank()) return visibleItems
        return visibleItems.filterNot { item -> item.url == featuredSourceUrl }
    }
}
