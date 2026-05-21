package com.juan.snapmusic.data.extractor

import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.YouTubeFeedPage
import com.juan.snapmusic.core.model.YouTubeFeedItem

interface StreamResolverRepository {
    suspend fun resolve(url: String): ResolvedMedia
    suspend fun loadTrendingPage(limit: Int = 48, cursor: String? = null): YouTubeFeedPage
    suspend fun loadTrending(limit: Int = 48): List<YouTubeFeedItem>
    suspend fun searchVideosPage(query: String, limit: Int = 36, cursor: String? = null): YouTubeFeedPage
    suspend fun searchVideos(query: String, limit: Int = 36): List<YouTubeFeedItem>
    suspend fun loadRelatedVideos(url: String, limit: Int = 24): List<YouTubeFeedItem>
    suspend fun searchSuggestions(query: String, limit: Int = 12): List<String>
}
