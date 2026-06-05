package com.juan.snapmusic.data.recommendation

import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.MusicAffinitySignal
import com.juan.snapmusic.core.model.MusicHomeFeedState
import com.juan.snapmusic.core.model.MusicInterestProfile
import com.juan.snapmusic.core.model.MusicSignalType
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.data.extractor.StreamResolverRepository
import com.juan.snapmusic.data.persistence.HistoryRepository
import com.juan.snapmusic.data.persistence.YouTubeWatchHistoryRepository
import com.juan.snapmusic.data.storage.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Normalizer

class MusicHomeFeedRepository(
    private val resolverRepository: StreamResolverRepository,
    private val preferencesRepository: PreferencesRepository,
    private val historyRepository: HistoryRepository,
    private val youtubeWatchHistoryRepository: YouTubeWatchHistoryRepository,
    private val engine: MusicRecommendationEngine,
) {
    private companion object {
        const val HOME_SEARCH_CONCURRENCY = 4
        const val INITIAL_HOME_SEARCH_CONCURRENCY = 3
        const val WATCH_NEXT_SEARCH_CONCURRENCY = 2
    }

    private val feedPagingCoordinator = FeedPagingCoordinator(resolverRepository, engine)

    suspend fun loadMusicHomeFeed(
        sessionSeed: Long,
        cursor: String? = null,
        limit: Int = 48,
    ): MusicHomeFeedState = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        val impressions = recentImpressions()
        feedPagingCoordinator.loadHomePage(
            sessionSeed = sessionSeed,
            cursor = cursor,
            limit = limit,
            profile = profile,
            impressions = impressions,
        ).also { state ->
            rememberImpressions(state.items)
        }
    }

    suspend fun searchMusicVideos(query: String, limit: Int = 36): List<YouTubeFeedItem> = withContext(Dispatchers.IO) {
        val baseQuery = query.trim()
        val secondaryQuery = "$baseQuery oficial video"
        val rawResults = coroutineScope {
            val primary = async { runCatching { resolverRepository.searchVideos(query = baseQuery, limit = limit * 4) }.getOrDefault(emptyList()) }
            val secondary = async { runCatching { resolverRepository.searchVideos(query = secondaryQuery, limit = limit * 2) }.getOrDefault(emptyList()) }
            (primary.await() + secondary.await()).distinctBy(YouTubeFeedItem::url)
        }
        recordSearch(query)
        rawResults
            .sortedByDescending { item -> searchRelevance(query = baseQuery, item = item) }
            .take(limit)
    }

    suspend fun loadPopularMusicQueries(limit: Int = 8): List<String> = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        engine.buildHomeQueries(profile)
            .map(::presentableQuery)
            .filter(String::isNotBlank)
            .distinct()
            .take(limit)
    }

    suspend fun recommendWatchNext(
        currentItem: YouTubeFeedItem,
        limit: Int = 18,
    ): List<YouTubeFeedItem> = recommendWatchNextPage(
        currentItem = currentItem,
        cursor = null,
        limit = limit,
    ).items

    suspend fun recommendWatchNextPage(
        currentItem: YouTubeFeedItem,
        cursor: String? = null,
        limit: Int = 18,
    ): MusicHomeFeedState = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        val impressions = recentImpressions()
        feedPagingCoordinator.loadWatchNextPage(
            currentItem = currentItem,
            cursor = cursor,
            limit = limit,
            profile = profile,
            impressions = impressions,
        )
    }

    suspend fun rankWatchNextCandidates(
        currentItem: YouTubeFeedItem,
        candidates: List<YouTubeFeedItem>,
        limit: Int,
    ): List<YouTubeFeedItem> = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) return@withContext emptyList()
        engine.rankRelatedCandidates(
            currentItem = currentItem,
            candidates = candidates
                .filter { it.url != currentItem.url }
                .distinctBy(YouTubeFeedItem::url),
            profile = buildProfile(),
            impressions = recentImpressions(),
            limit = limit,
        )
    }

    private suspend fun limitedSearch(
        queries: List<String>,
        concurrency: Int,
        block: suspend (String) -> List<YouTubeFeedItem>,
    ): List<YouTubeFeedItem> = coroutineScope {
        queries
            .chunked(concurrency.coerceAtLeast(1))
            .flatMap { batch ->
                batch.map { query -> async { block(query) } }
                    .flatMap { it.await() }
            }
    }

    suspend fun recordSearch(query: String) {
        preferencesRepository.rememberRecentSearchQuery(query)
        val classification = engine.classifyQuery(query)
        preferencesRepository.appendMusicAffinitySignal(
            MusicAffinitySignal(
                type = MusicSignalType.SEARCH_QUERY,
                timestampMs = System.currentTimeMillis(),
                query = query,
                tags = classification.tags,
                artistKey = classification.artistKey,
                channelKey = classification.channelKey,
                contentType = classification.contentType,
            ),
        )
    }

    suspend fun recordPlaybackSignal(
        type: MusicSignalType,
        item: YouTubeFeedItem,
    ) {
        val classification = engine.classify(item)
        if (!classification.isMusic && type != MusicSignalType.SKIP_FAST) return
        preferencesRepository.appendMusicAffinitySignal(
            MusicAffinitySignal(
                type = type,
                timestampMs = System.currentTimeMillis(),
                sourceUrl = item.url,
                title = item.title,
                author = item.author,
                tags = classification.tags,
                artistKey = classification.artistKey,
                channelKey = classification.channelKey,
                contentType = classification.contentType,
            ),
        )
    }

    private suspend fun buildProfile() = engine.buildUserProfile(
        signals = preferencesRepository.readMusicAffinitySignals(),
        downloadHistory = historyRepository.observeHistory().first(),
        watchHistory = youtubeWatchHistoryRepository.observeHistory().first(),
    )

    private suspend fun recentImpressions(): List<FeedImpression> {
        val cutoff = System.currentTimeMillis() - 86_400_000L
        return preferencesRepository.readMusicFeedImpressions().filter { it.timestampMs >= cutoff }
    }

    private suspend fun rememberImpressions(items: List<YouTubeFeedItem>) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        val updated = (recentImpressions() + items.map { FeedImpression(url = it.url, timestampMs = now) })
            .sortedByDescending(FeedImpression::timestampMs)
            .distinctBy(FeedImpression::url)
            .take(240)
        preferencesRepository.saveMusicFeedImpressions(updated)
    }

    private fun presentableQuery(value: String): String {
        return value.split(" ")
            .filter(String::isNotBlank)
            .joinToString(" ") { token ->
                token.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase() else char.toString() }
            }
    }

    private fun searchRelevance(
        query: String,
        item: YouTubeFeedItem,
    ): Double {
        val normalizedQuery = normalize(query)
        val normalizedTitle = normalize(item.title)
        val normalizedAuthor = normalize(item.author)
        val classification = engine.classify(item)
        val queryTokens = normalizedQuery.split(" ").filter(String::isNotBlank)
        val fullTitleMatch = when {
            normalizedTitle == normalizedQuery -> 20.0
            normalizedTitle.contains(normalizedQuery) -> 12.0
            else -> 0.0
        }
        val fullAuthorMatch = when {
            normalizedAuthor == normalizedQuery -> 8.0
            normalizedAuthor.contains(normalizedQuery) -> 4.0
            else -> 0.0
        }
        val tokenHits = queryTokens.count { token ->
            normalizedTitle.contains(token) || normalizedAuthor.contains(token)
        }.toDouble()
        val musicBoost = when {
            classification.isMusic -> 6.0 + classification.score
            fullTitleMatch > 0.0 -> 3.0
            else -> classification.score.toDouble() * 0.25
        }
        return fullTitleMatch + fullAuthorMatch + (tokenHits * 1.8) + musicBoost
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
