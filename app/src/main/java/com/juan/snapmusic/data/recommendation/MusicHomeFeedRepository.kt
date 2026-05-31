package com.juan.snapmusic.data.recommendation

import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.MusicAffinitySignal
import com.juan.snapmusic.core.model.MusicHomeFeedState
import com.juan.snapmusic.core.model.MusicInterestProfile
import com.juan.snapmusic.core.model.MusicSignalType
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.data.extractor.StreamResolverRepository
import com.juan.snapmusic.data.persistence.HistoryRepository
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
    private val engine: MusicRecommendationEngine,
) {
    private companion object {
        const val HOME_SEARCH_CONCURRENCY = 4
        const val INITIAL_HOME_SEARCH_CONCURRENCY = 3
        const val WATCH_NEXT_SEARCH_CONCURRENCY = 2
    }

    private var homeFeedSessionCache: HomeFeedSessionCache? = null

    suspend fun loadMusicHomeFeed(
        sessionSeed: Long,
        cursor: String? = null,
        limit: Int = 48,
    ): MusicHomeFeedState = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        val impressions = recentImpressions()
        val strongProfile = engine.hasStrongHomeProfile(profile)
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val isInitialPage = offset == 0 && cursor == null
        val sessionKey = HomeFeedSessionKey(
            seed = sessionSeed,
            profileSignature = profile.homeFeedSignature(),
            strongProfile = strongProfile,
        )
        homeFeedSessionCache
            ?.takeIf { cache -> cache.key == sessionKey && cache.rankedItems.size >= offset + limit }
            ?.let { cache ->
                val pageItems = cache.rankedItems.drop(offset).take(limit)
                rememberImpressions(pageItems)
                return@withContext MusicHomeFeedState(
                    sessionSeed = sessionSeed,
                    items = pageItems,
                    nextCursor = (offset + pageItems.size)
                        .takeIf { nextOffset -> pageItems.isNotEmpty() && cache.rankedItems.size > nextOffset }
                        ?.toString(),
                )
            }
        val pageSeed = sessionSeed + (offset * 1_103_515_245L)
        val extraCandidates = if (isInitialPage) {
            if (strongProfile) 32 else 40
        } else {
            if (strongProfile) 54 else 66
        }
        val targetCount = (offset + limit + extraCandidates).coerceAtLeast(limit + extraCandidates)
        val queryCount = if (strongProfile) {
            if (isInitialPage) {
                ((targetCount / 18) + 1).coerceIn(3, 4)
            } else {
                ((targetCount / 12) + 6).coerceIn(4, 8)
            }
        } else {
            if (isInitialPage) {
                ((targetCount / 16) + 2).coerceIn(3, 5)
            } else {
                ((targetCount / 10) + 8).coerceIn(4, 8)
            }
        }
        val queryVideoLimit = if (strongProfile) {
            if (isInitialPage) {
                ((targetCount / queryCount) + 4).coerceIn(14, 20)
            } else {
                ((targetCount / queryCount) + 10).coerceIn(18, 32)
            }
        } else {
            if (isInitialPage) {
                ((targetCount / queryCount) + 6).coerceIn(16, 24)
            } else {
                ((targetCount / queryCount) + 12).coerceIn(22, 40)
            }
        }
        val homeSearchConcurrency = if (isInitialPage) INITIAL_HOME_SEARCH_CONCURRENCY else HOME_SEARCH_CONCURRENCY
        val candidates = coroutineScope {
            val trending = async {
                val trendingLimit = if (strongProfile) {
                    if (isInitialPage) {
                        (targetCount / 5).coerceIn(16, 28)
                    } else {
                        (targetCount / 4).coerceIn(24, 72)
                    }
                } else {
                    if (isInitialPage) {
                        (targetCount / 3).coerceIn(24, 40)
                    } else {
                        (targetCount / 2).coerceIn(48, 120)
                    }
                }
                runCatching { resolverRepository.loadTrending(limit = trendingLimit) }.getOrDefault(emptyList())
            }
            val homeQueries = engine.buildHomeQueries(profile)
            val fixedHeadCount = if (strongProfile) (queryCount / 2).coerceAtLeast(3) else (queryCount / 3).coerceAtLeast(2)
            val searchQueries = buildList<String> {
                addAll(homeQueries.take(fixedHeadCount))
                addAll(
                    homeQueries
                        .drop(fixedHeadCount)
                        .shuffled(kotlin.random.Random(pageSeed))
                        .take((queryCount - size).coerceAtLeast(0)),
                )
            }
                .take(queryCount)
            val searched = limitedSearch(
                queries = searchQueries,
                concurrency = homeSearchConcurrency,
            ) { query ->
                runCatching { resolverRepository.searchVideos(query = query, limit = queryVideoLimit) }.getOrDefault(emptyList())
            }
            (searched + trending.await())
                .distinctBy(YouTubeFeedItem::url)
        }
        val ranked = engine.rankHomeCandidates(candidates, profile, impressions, pageSeed, limit = targetCount)
        homeFeedSessionCache = HomeFeedSessionCache(
            key = sessionKey,
            rankedItems = ranked,
        )
        val pageItems = ranked.drop(offset).take(limit)
        rememberImpressions(pageItems)
        MusicHomeFeedState(
            sessionSeed = sessionSeed,
            items = pageItems,
            nextCursor = (offset + pageItems.size).takeIf { pageItems.isNotEmpty() }?.toString(),
        )
    }

    private fun MusicInterestProfile.homeFeedSignature(): Int {
        var result = artistScores.keys.take(16).toList().hashCode()
        result = (31 * result) + tagScores.keys.take(16).toList().hashCode()
        result = (31 * result) + contentTypeScores.keys.take(8).toList().hashCode()
        result = (31 * result) + searchScores.keys.take(12).toList().hashCode()
        result = (31 * result) + recentUrls.take(24).toList().hashCode()
        result = (31 * result) + recentArtists.take(16).toList().hashCode()
        return result
    }

    private data class HomeFeedSessionKey(
        val seed: Long,
        val profileSignature: Int,
        val strongProfile: Boolean,
    )

    private data class HomeFeedSessionCache(
        val key: HomeFeedSessionKey,
        val rankedItems: List<YouTubeFeedItem>,
    )

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
    ): List<YouTubeFeedItem> = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        val impressions = recentImpressions()
        val classification = engine.classify(currentItem)
        val relatedLimit = (limit * 2).coerceIn(24, 96)
        val directRelated = runCatching {
            resolverRepository.loadRelatedVideos(currentItem.url, limit = relatedLimit)
        }.getOrDefault(emptyList())
            .distinctBy(YouTubeFeedItem::url)
        val directRelatedUrls = directRelated.mapTo(linkedSetOf()) { it.url }
        val shouldAugment = directRelated.size < limit
        val queryLimit = when {
            directRelated.size >= limit -> (limit + 6).coerceIn(16, 24)
            else -> (limit + 10).coerceIn(18, 30)
        }
        val styleQueries = if (!shouldAugment) {
            emptyList()
        } else {
            buildList {
                add(currentItem.title)
                add("${currentItem.author} ${currentItem.title}".trim())
                classification.tags
                    .filterNot { it == "mix" || it == "enganchado" || it == "remix" }
                    .take(2)
                    .forEach { tag -> add("${currentItem.author} $tag".trim()) }
                if (classification.tags.none { it == "mix" || it == "enganchado" || it == "remix" }) {
                    add(currentItem.author)
                }
            }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .take(3)
        }
        val candidates = coroutineScope {
            val searchBuckets = limitedSearch(
                queries = styleQueries,
                concurrency = WATCH_NEXT_SEARCH_CONCURRENCY,
            ) { query ->
                runCatching { resolverRepository.searchVideos(query, limit = queryLimit) }.getOrDefault(emptyList())
            }
            (directRelated + searchBuckets)
                .distinctBy(YouTubeFeedItem::url)
        }
        engine.rankRelatedCandidates(currentItem, candidates, profile, impressions, limit, primaryUrls = directRelatedUrls)
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
