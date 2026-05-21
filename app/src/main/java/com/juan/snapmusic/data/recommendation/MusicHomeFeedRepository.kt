package com.juan.snapmusic.data.recommendation

import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.MusicAffinitySignal
import com.juan.snapmusic.core.model.MusicHomeFeedState
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
    suspend fun loadMusicHomeFeed(
        sessionSeed: Long,
        cursor: String? = null,
        limit: Int = 48,
    ): MusicHomeFeedState = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        val impressions = recentImpressions()
        val strongProfile = engine.hasStrongHomeProfile(profile)
        val offset = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val targetCount = (offset + limit + 240).coerceAtLeast(limit + 240)
        val queryCount = if (strongProfile) {
            ((targetCount / 12) + 6).coerceIn(10, 48)
        } else {
            ((targetCount / 10) + 8).coerceIn(14, 64)
        }
        val queryVideoLimit = if (strongProfile) {
            ((targetCount / queryCount) + 10).coerceIn(18, 42)
        } else {
            ((targetCount / queryCount) + 12).coerceIn(22, 56)
        }
        val candidates = coroutineScope {
            val trending = async {
                val trendingLimit = if (strongProfile) {
                    (targetCount / 3).coerceIn(32, 160)
                } else {
                    targetCount.coerceIn(96, 320)
                }
                runCatching { resolverRepository.loadTrending(limit = trendingLimit) }.getOrDefault(emptyList())
            }
            val homeQueries = engine.buildHomeQueries(profile)
            val fixedHeadCount = if (strongProfile) (queryCount / 2).coerceAtLeast(4) else (queryCount / 3).coerceAtLeast(3)
            val searches = buildList<String> {
                addAll(homeQueries.take(fixedHeadCount))
                addAll(
                    homeQueries
                        .drop(fixedHeadCount)
                        .shuffled(kotlin.random.Random(sessionSeed))
                        .take((queryCount - size).coerceAtLeast(0)),
                )
            }
                .take(queryCount)
                .map { query ->
                    async {
                        runCatching { resolverRepository.searchVideos(query = query, limit = queryVideoLimit) }.getOrDefault(emptyList())
                    }
                }
            (searches.flatMap { it.await() } + trending.await())
                .distinctBy(YouTubeFeedItem::url)
        }
        val ranked = engine.rankHomeCandidates(candidates, profile, impressions, sessionSeed, limit = targetCount)
        val pageItems = ranked.drop(offset).take(limit)
        rememberImpressions(pageItems)
        MusicHomeFeedState(
            sessionSeed = sessionSeed,
            items = pageItems,
            nextCursor = (offset + pageItems.size).takeIf { pageItems.isNotEmpty() }?.toString(),
        )
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
    ): List<YouTubeFeedItem> = withContext(Dispatchers.IO) {
        val profile = buildProfile()
        val impressions = recentImpressions()
        val classification = engine.classify(currentItem)
        val relatedLimit = (limit * 4).coerceIn(48, 360)
        val directRelated = runCatching {
            resolverRepository.loadRelatedVideos(currentItem.url, limit = relatedLimit)
        }.getOrDefault(emptyList())
            .distinctBy(YouTubeFeedItem::url)
        val directRelatedUrls = directRelated.mapTo(linkedSetOf()) { it.url }
        val shouldAugment = true
        val queryLimit = when {
            directRelated.size >= limit -> (limit + 16).coerceIn(24, 48)
            else -> (limit + 24).coerceIn(32, 64)
        }
        val styleQueries = if (!shouldAugment) {
            emptyList()
        } else {
            buildList {
                add(currentItem.title)
                add("${currentItem.author} ${currentItem.title}".trim())
                classification.tags
                    .filterNot { it == "mix" || it == "enganchado" || it == "remix" }
                    .take(3)
                    .forEach { tag -> add("${currentItem.author} $tag".trim()) }
                if (classification.tags.none { it == "mix" || it == "enganchado" || it == "remix" }) {
                    add(currentItem.author)
                }
            }
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
        }
        val candidates = coroutineScope {
            val searchBuckets = styleQueries.map { query ->
                async {
                    runCatching { resolverRepository.searchVideos(query, limit = queryLimit) }.getOrDefault(emptyList())
                }
            }
            (directRelated + searchBuckets.flatMap { it.await() })
                .distinctBy(YouTubeFeedItem::url)
        }
        engine.rankRelatedCandidates(currentItem, candidates, profile, impressions, limit, primaryUrls = directRelatedUrls)
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
