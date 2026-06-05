package com.juan.snapmusic.data.recommendation

import android.util.Log
import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.MusicInterestProfile
import com.juan.snapmusic.core.model.MusicHomeFeedState
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeFeedPage
import com.juan.snapmusic.data.extractor.StreamResolverRepository

internal class FeedPagingCoordinator(
    private val resolverRepository: StreamResolverRepository,
    private val engine: MusicRecommendationEngine,
) {
    private companion object {
        const val TAG = "SnapMusicFeedPaging"
        const val MAX_SESSION_COUNT = 24
        const val MAX_PAGE_ROUNDS = 5
    }

    private val sessions = linkedMapOf<String, FeedPagingSession>()
    private var sessionCounter = 0L

    suspend fun loadHomePage(
        sessionSeed: Long,
        cursor: String?,
        limit: Int,
        profile: MusicInterestProfile,
        impressions: List<FeedImpression>,
    ): MusicHomeFeedState {
        val signature = profile.feedSignature()
        val session = resolveSession(
            cursor = cursor,
            kind = "home",
            seedSignature = (31 * sessionSeed.hashCode()) + signature,
        )
        val items = collectPage(
            session = session,
            limit = limit,
            laneFactory = { round ->
                homeFeedLanes(
                    resolverRepository = resolverRepository,
                    engine = engine,
                    profile = profile,
                    sessionSeed = sessionSeed,
                    round = round,
                )
            },
            rank = { candidates ->
                engine.rankHomeCandidates(
                    candidates = candidates,
                    profile = profile,
                    impressions = impressions,
                    sessionSeed = sessionSeed + (session.round * 1_103_515_245L),
                    limit = candidates.size.coerceAtLeast(limit),
                )
            },
        )
        return MusicHomeFeedState(
            sessionSeed = sessionSeed,
            items = items,
            nextCursor = nextCursor(session, items),
        )
    }

    fun startHomeSession(
        sessionSeed: Long,
        profile: MusicInterestProfile,
        seededItems: List<YouTubeFeedItem>,
    ): String? {
        val session = resolveSession(
            cursor = null,
            kind = "home",
            seedSignature = (31 * sessionSeed.hashCode()) + profile.feedSignature(),
        )
        session.seenUrls.addAll(seededItems.map(YouTubeFeedItem::url))
        Log.d(TAG, "kind=home seeded=${seededItems.size} cursor=${session.id}")
        return session.id.takeIf { seededItems.isNotEmpty() }
    }

    suspend fun loadWatchNextPage(
        currentItem: YouTubeFeedItem,
        cursor: String?,
        limit: Int,
        profile: MusicInterestProfile,
        impressions: List<FeedImpression>,
    ): MusicHomeFeedState {
        val classification = engine.classify(currentItem)
        val signature = currentItem.url.hashCode() * 31 + profile.feedSignature()
        val session = resolveSession(
            cursor = cursor,
            kind = "watch:${currentItem.url}",
            seedSignature = signature,
        )
        val primaryUrls = linkedSetOf<String>()
        val items = collectPage(
            session = session,
            limit = limit,
            laneFactory = { round ->
                watchNextFeedLanes(
                    resolverRepository = resolverRepository,
                    currentItem = currentItem,
                    tags = classification.tags,
                    profile = profile,
                    round = round,
                    primaryUrls = primaryUrls,
                )
            },
            rank = { candidates ->
                engine.rankRelatedCandidates(
                    currentItem = currentItem,
                    candidates = candidates,
                    profile = profile,
                    impressions = impressions,
                    limit = candidates.size.coerceAtLeast(limit),
                    primaryUrls = primaryUrls,
                )
            },
        )
        return MusicHomeFeedState(
            sessionSeed = signature.toLong(),
            items = items,
            nextCursor = nextCursor(session, items),
        )
    }

    private suspend fun collectPage(
        session: FeedPagingSession,
        limit: Int,
        laneFactory: (Int) -> List<FeedLane>,
        rank: (List<YouTubeFeedItem>) -> List<YouTubeFeedItem>,
    ): List<YouTubeFeedItem> {
        val candidates = linkedMapOf<String, YouTubeFeedItem>()
        var guard = 0
        while (candidates.size < limit && guard < MAX_PAGE_ROUNDS) {
            val lanes = laneFactory(session.round)
            var anyLaneProgressed = false
            for (lane in lanes) {
                if (session.exhaustedLanes.contains(lane.id)) continue
                val cursor = session.laneCursors[lane.id]
                val page = runCatching { lane.fetch(cursor) }
                    .onFailure {
                        Log.w(TAG, "kind=${session.kind} lane=${lane.id} error=${it.message}")
                    }
                    .getOrDefault(YouTubeFeedPage())
                val before = candidates.size
                page.items.forEach { item ->
                    if (
                        item.url !in session.seenUrls &&
                        item.url !in candidates
                    ) {
                        candidates[item.url] = item
                    }
                }
                val added = candidates.size - before
                val duplicated = page.items.size - added
                anyLaneProgressed = anyLaneProgressed ||
                    page.items.isNotEmpty() ||
                    !page.nextCursor.isNullOrBlank()
                if (page.nextCursor.isNullOrBlank()) {
                    session.exhaustedLanes.add(lane.id)
                } else {
                    session.laneCursors[lane.id] = page.nextCursor
                }
                Log.d(
                    TAG,
                    "kind=${session.kind} lane=${lane.id} round=${session.round} " +
                        "fetched=${page.items.size} added=$added duplicated=$duplicated " +
                        "exhausted=${session.exhaustedLanes.contains(lane.id)} next=${!page.nextCursor.isNullOrBlank()}",
                )
                if (candidates.size >= limit) break
            }
            if (candidates.size >= limit) break
            if (!anyLaneProgressed || lanes.all { session.exhaustedLanes.contains(it.id) }) {
                session.round += 1
                session.laneCursors.clear()
                session.exhaustedLanes.clear()
            }
            guard += 1
        }
        val ranked = rank(candidates.values.toList())
            .filter { it.url !in session.seenUrls }
            .distinctBy(YouTubeFeedItem::url)
            .take(limit)
        session.seenUrls.addAll(ranked.map(YouTubeFeedItem::url))
        return ranked
    }

    private fun resolveSession(
        cursor: String?,
        kind: String,
        seedSignature: Int,
    ): FeedPagingSession {
        val cached = synchronized(sessions) {
            cursor?.let(sessions::get)
        }
        if (cached != null && cached.kind == kind && cached.seedSignature == seedSignature) return cached
        return synchronized(sessions) {
            sessionCounter += 1
            FeedPagingSession(
                id = "feed-page-$sessionCounter",
                kind = kind,
                seedSignature = seedSignature,
            ).also { session ->
                sessions[session.id] = session
                while (sessions.size > MAX_SESSION_COUNT) {
                    val eldestKey = sessions.entries.firstOrNull()?.key ?: break
                    sessions.remove(eldestKey)
                }
            }
        }
    }

    private fun nextCursor(session: FeedPagingSession, items: List<YouTubeFeedItem>): String? {
        if (items.isEmpty()) {
            Log.d(TAG, "kind=${session.kind} exhausted=true added=0")
            return null
        }
        Log.d(TAG, "kind=${session.kind} added=${items.size} round=${session.round} cursor=${session.id}")
        return session.id
    }

    private fun MusicInterestProfile.feedSignature(): Int {
        var result = artistScores.keys.take(16).toList().hashCode()
        result = (31 * result) + tagScores.keys.take(16).toList().hashCode()
        result = (31 * result) + contentTypeScores.keys.take(8).toList().hashCode()
        result = (31 * result) + searchScores.keys.take(12).toList().hashCode()
        result = (31 * result) + recentUrls.take(24).toList().hashCode()
        result = (31 * result) + recentArtists.take(16).toList().hashCode()
        return result
    }

}
