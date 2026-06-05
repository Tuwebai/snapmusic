package com.juan.snapmusic.data.recommendation

import com.juan.snapmusic.core.model.MusicInterestProfile
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeFeedPage
import com.juan.snapmusic.data.extractor.StreamResolverRepository
import kotlin.math.absoluteValue
import kotlin.random.Random

internal const val HOME_FEED_LANE_LIMIT = 28
internal const val WATCH_NEXT_LANE_LIMIT = 24

internal data class FeedPagingSession(
    val id: String,
    val kind: String,
    val seedSignature: Int,
    val seenUrls: LinkedHashSet<String> = linkedSetOf(),
    val laneCursors: MutableMap<String, String?> = linkedMapOf(),
    val exhaustedLanes: MutableSet<String> = linkedSetOf(),
    var round: Int = 0,
)

internal class FeedLane(
    val id: String,
    val fetch: suspend (String?) -> YouTubeFeedPage,
)

internal fun homeFeedLanes(
    resolverRepository: StreamResolverRepository,
    engine: MusicRecommendationEngine,
    profile: MusicInterestProfile,
    sessionSeed: Long,
    round: Int,
): List<FeedLane> {
    val random = Random(sessionSeed + round * 37_911L)
    val dynamicQueries = (engine.buildHomeQueries(profile) + fallbackHomeQueries(profile, round))
        .distinct()
        .shuffled(random)
        .take(6)
    return buildList {
        add(
            FeedLane("home:trending:$round") { cursor ->
                resolverRepository.loadTrendingPage(limit = HOME_FEED_LANE_LIMIT, cursor = cursor)
            },
        )
        dynamicQueries.forEachIndexed { index, query ->
            add(
                FeedLane("home:search:$round:$index:${query.stableLaneKey()}") { cursor ->
                    resolverRepository.searchVideosPage(query = query, limit = HOME_FEED_LANE_LIMIT, cursor = cursor)
                },
            )
        }
        profile.recentUrls.take(3).forEachIndexed { index, url ->
            add(
                FeedLane("home:related:$round:$index:${url.stableLaneKey()}") {
                    YouTubeFeedPage(
                        items = resolverRepository.loadRelatedVideos(url = url, limit = HOME_FEED_LANE_LIMIT),
                        nextCursor = null,
                    )
                },
            )
        }
    }
}

internal fun watchNextFeedLanes(
    resolverRepository: StreamResolverRepository,
    currentItem: YouTubeFeedItem,
    tags: List<String>,
    profile: MusicInterestProfile,
    round: Int,
    primaryUrls: MutableSet<String>,
): List<FeedLane> {
    return buildList {
        add(
            FeedLane("watch:direct:$round:${currentItem.url.stableLaneKey()}") {
                val items = resolverRepository.loadRelatedVideos(
                    url = currentItem.url,
                    limit = WATCH_NEXT_LANE_LIMIT * 2,
                )
                primaryUrls.addAll(items.map(YouTubeFeedItem::url))
                YouTubeFeedPage(items = items, nextCursor = null)
            },
        )
        watchNextQueries(currentItem, tags, profile, round).forEachIndexed { index, query ->
            add(
                FeedLane("watch:search:$round:$index:${query.stableLaneKey()}") { cursor ->
                    resolverRepository.searchVideosPage(query = query, limit = WATCH_NEXT_LANE_LIMIT, cursor = cursor)
                },
            )
        }
        add(
            FeedLane("watch:trending:$round") { cursor ->
                resolverRepository.loadTrendingPage(limit = WATCH_NEXT_LANE_LIMIT, cursor = cursor)
            },
        )
    }
}

private fun fallbackHomeQueries(profile: MusicInterestProfile, round: Int): List<String> {
    val artistQueries = profile.recentArtists.take(4).map { "$it música" }
    return artistQueries + listOf(
        "música tendencias",
        "últimos lanzamientos música",
        "video oficial música",
        "música latina",
        "top music videos ${round + 1}",
    )
}

private fun watchNextQueries(
    currentItem: YouTubeFeedItem,
    tags: List<String>,
    profile: MusicInterestProfile,
    round: Int,
): List<String> {
    val cleanTitle = currentItem.title
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("(?i)official|video|lyrics?|audio|remix|session|en vivo|live|hd|4k"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    return buildList {
        add("${currentItem.author} $cleanTitle")
        add(currentItem.author)
        tags.take(3).forEach { add("${currentItem.author} $it") }
        profile.recentArtists.take(3).forEach { add("$it música") }
        add("música recomendada ${round + 1}")
        add("tendencias música")
    }
        .map(String::trim)
        .filter { it.length >= 3 }
        .distinct()
        .take(7)
}

internal fun String.stableLaneKey(): String = hashCode().absoluteValue.toString(36)
