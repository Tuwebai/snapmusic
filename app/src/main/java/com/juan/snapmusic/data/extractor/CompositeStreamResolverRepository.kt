package com.juan.snapmusic.data.extractor

import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeFeedPage

class CompositeStreamResolverRepository(
    private val youtube: StreamResolverRepository,
    private val instagram: InstagramStreamResolverRepository,
) : StreamResolverRepository {
    override suspend fun resolve(url: String): ResolvedMedia {
        return if (instagram.canResolve(url)) instagram.resolve(url) else youtube.resolve(url)
    }

    override suspend fun resolveDownloadPlan(url: String, selection: DownloadSelection): DownloadExecutionPlan {
        return if (instagram.canResolve(url)) {
            instagram.resolveDownloadPlan(url, selection)
        } else {
            youtube.resolveDownloadPlan(url, selection)
        }
    }

    override suspend fun loadTrendingPage(limit: Int, cursor: String?): YouTubeFeedPage {
        return youtube.loadTrendingPage(limit, cursor)
    }

    override suspend fun loadTrending(limit: Int): List<YouTubeFeedItem> {
        return youtube.loadTrending(limit)
    }

    override suspend fun searchVideosPage(query: String, limit: Int, cursor: String?): YouTubeFeedPage {
        return youtube.searchVideosPage(query, limit, cursor)
    }

    override suspend fun searchVideos(query: String, limit: Int): List<YouTubeFeedItem> {
        return youtube.searchVideos(query, limit)
    }

    override suspend fun loadRelatedVideos(url: String, limit: Int): List<YouTubeFeedItem> {
        return youtube.loadRelatedVideos(url, limit)
    }

    override suspend fun searchSuggestions(query: String, limit: Int): List<String> {
        return youtube.searchSuggestions(query, limit)
    }
}
