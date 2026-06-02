package com.juan.snapmusic.data.recommendation

import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.HistoryEntry
import com.juan.snapmusic.core.model.MusicAffinitySignal
import com.juan.snapmusic.core.model.MusicClassification
import com.juan.snapmusic.core.model.MusicContentType
import com.juan.snapmusic.core.model.MusicInterestProfile
import com.juan.snapmusic.core.model.MusicSignalType
import com.juan.snapmusic.core.model.RelatedMusicRecommendation
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry
import java.text.Normalizer
import kotlin.math.ln

class MusicRecommendationEngine {
    fun classify(item: YouTubeFeedItem): MusicClassification = classify(item.title, item.author, item.description, item.durationSeconds)

    fun classifyQuery(query: String): MusicClassification = classify(query, "", null, 0L)

    fun buildUserProfile(
        signals: List<MusicAffinitySignal>,
        downloadHistory: List<HistoryEntry>,
        watchHistory: List<YouTubeWatchHistoryEntry> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
    ): MusicInterestProfile {
        val artistScores = linkedMapOf<String, Double>()
        val tagScores = linkedMapOf<String, Double>()
        val contentTypeScores = linkedMapOf<MusicContentType, Double>()
        val searchScores = linkedMapOf<String, Double>()
        val recentUrls = linkedSetOf<String>()
        val recentArtists = linkedSetOf<String>()

        signals.forEach { signal ->
            val decay = recencyWeight(nowMs - signal.timestampMs)
            val weight = signalWeight(signal.type) * decay
            signal.sourceUrl?.takeIf(String::isNotBlank)?.let(recentUrls::add)
            signal.artistKey.takeIf(String::isNotBlank)?.let {
                artistScores[it] = (artistScores[it] ?: 0.0) + weight
                recentArtists.add(it)
            }
            signal.tags.forEach { tag -> tagScores[tag] = (tagScores[tag] ?: 0.0) + weight }
            contentTypeScores[signal.contentType] = (contentTypeScores[signal.contentType] ?: 0.0) + weight
            signal.query?.takeIf(String::isNotBlank)?.let { query ->
                searchScores[normalize(query)] = (searchScores[normalize(query)] ?: 0.0) + (weight * 0.9)
            }
        }

        downloadHistory.take(120).forEach { item ->
            val classification = classify(item.title, item.author, null, 0L)
            if (!classification.isMusic) return@forEach
            val weight = recencyWeight(nowMs - item.createdAt) * 5.0
            if (classification.artistKey.isNotBlank()) {
                artistScores[classification.artistKey] = (artistScores[classification.artistKey] ?: 0.0) + weight
                recentArtists.add(classification.artistKey)
            }
            classification.tags.forEach { tag -> tagScores[tag] = (tagScores[tag] ?: 0.0) + weight }
            contentTypeScores[classification.contentType] = (contentTypeScores[classification.contentType] ?: 0.0) + weight
        }

        watchHistory.take(160).forEach { item ->
            val classification = classify(item.title, item.author, item.description, item.durationSeconds)
            if (!classification.isMusic) return@forEach
            val durationMs = (item.durationSeconds * 1_000L).coerceAtLeast(0L)
            val completionRate = if (durationMs > 0L) {
                item.lastPositionMs.coerceAtLeast(0L).toDouble() / durationMs.toDouble()
            } else {
                0.0
            }
            val engagementWeight = when {
                completionRate >= 0.7 -> 4.0
                item.lastPositionMs >= 30_000L -> 2.5
                item.lastPositionMs > 0L -> 1.2
                else -> 0.8
            }
            val weight = recencyWeight(nowMs - item.watchedAt) * engagementWeight
            item.sourceUrl.takeIf(String::isNotBlank)?.let(recentUrls::add)
            if (classification.artistKey.isNotBlank()) {
                artistScores[classification.artistKey] = (artistScores[classification.artistKey] ?: 0.0) + weight
                recentArtists.add(classification.artistKey)
            }
            classification.tags.forEach { tag -> tagScores[tag] = (tagScores[tag] ?: 0.0) + (weight * 0.8) }
            contentTypeScores[classification.contentType] = (contentTypeScores[classification.contentType] ?: 0.0) + (weight * 0.5)
        }

        return MusicInterestProfile(
            artistScores = artistScores,
            tagScores = tagScores,
            contentTypeScores = contentTypeScores,
            searchScores = searchScores,
            recentUrls = recentUrls.take(80).toSet(),
            recentArtists = recentArtists.take(24).toSet(),
        )
    }

    fun buildHomeQueries(profile: MusicInterestProfile): List<String> {
        val mixFriendly = prefersMixLike(profile)
        val artistQueries = profile.artistScores.entries
            .sortedByDescending { it.value }
            .take(6)
            .flatMap { entry ->
                buildList {
                    add("${entry.key} official video")
                    if ((profile.tagScores["live"] ?: 0.0) > 2.5) add("${entry.key} en vivo")
                }
            }
        val tagQueries = profile.tagScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filterNot { !mixFriendly && it in mixLikeTags }
            .take(4)
            .map(::queryForTag)
        val searchQueries = profile.searchScores.entries
            .sortedByDescending { it.value }
            .take(6)
            .map { it.key }
        val artistTagQueries = profile.artistScores.entries
            .sortedByDescending { it.value }
            .take(3)
            .flatMap { entry ->
                topSceneTags(profile, mixFriendly)
                    .take(2)
                    .map { tag -> "${entry.key} $tag" }
            }
        val fallbackQueries = if (mixFriendly) mixFriendlyDefaultQueries else defaultMusicQueries
        return (searchQueries + artistQueries + artistTagQueries + tagQueries + fallbackQueries)
            .map(::normalizeSpaces)
            .filter(String::isNotBlank)
            .distinct()
    }

    fun hasStrongHomeProfile(profile: MusicInterestProfile): Boolean {
        val artistSignal = profile.artistScores.values.sortedDescending().take(3).sum()
        val searchSignal = profile.searchScores.values.sortedDescending().take(3).sum()
        return artistSignal >= 5.0 || searchSignal >= 4.0 || profile.recentArtists.size >= 3
    }

    fun rankHomeCandidates(
        candidates: List<YouTubeFeedItem>,
        profile: MusicInterestProfile,
        impressions: List<FeedImpression>,
        sessionSeed: Long,
        limit: Int,
    ): List<YouTubeFeedItem> {
        val recentByUrl = impressions.associateBy(FeedImpression::url)
        val scored = candidates.mapNotNull { item ->
            val classification = classify(item)
            if (!classification.isMusic) return@mapNotNull null
            val score = homeScore(item, classification, profile, recentByUrl[item.url], sessionSeed)
            RelatedMusicRecommendation(item = item, score = score, classification = classification)
        }.sortedByDescending(RelatedMusicRecommendation::score)
        return diversify(scored, limit)
    }

    fun rankRelatedCandidates(
        currentItem: YouTubeFeedItem,
        candidates: List<YouTubeFeedItem>,
        profile: MusicInterestProfile,
        impressions: List<FeedImpression>,
        limit: Int,
        primaryUrls: Set<String> = emptySet(),
    ): List<YouTubeFeedItem> {
        val currentClassification = classify(currentItem)
        val recentByUrl = impressions.associateBy(FeedImpression::url)
        val scored = candidates.mapNotNull { item ->
            if (item.url == currentItem.url) return@mapNotNull null
            val classification = classify(item)
            if (!classification.isMusic) return@mapNotNull null
            val score = relatedScore(
                currentItem = currentItem,
                currentClassification = currentClassification,
                candidate = item,
                classification = classification,
                profile = profile,
                impression = recentByUrl[item.url],
                primaryUrls = primaryUrls,
            )
            RelatedMusicRecommendation(item = item, score = score, classification = classification)
        }.sortedByDescending(RelatedMusicRecommendation::score)
        return diversify(scored, limit, strictArtistDiversity = true)
    }

    private fun classify(title: String, author: String, description: String?, durationSeconds: Long): MusicClassification {
        val text = normalize("$title $author ${description.orEmpty()}")
        val positiveHits = positiveTokens.count(text::contains)
        val negativeHits = negativeTokens.count(text::contains)
        val tags = buildSet {
            genreTokens.forEach { (token, tag) -> if (text.contains(token)) add(tag) }
            contentTokens.forEach { (token, type) -> if (text.contains(token)) add(type.name.lowercase()) }
        }.toList()
        val contentType = contentTokens.entries.firstOrNull { text.contains(it.key) }?.value ?: MusicContentType.TRACK
        val durationScore = when {
            durationSeconds in 30..8_000 -> 2
            durationSeconds == 0L -> 1
            else -> 0
        }
        val score = (positiveHits * 3) + durationScore - (negativeHits * 5) + if (author.contains("topic", true)) 2 else 0
        return MusicClassification(
            isMusic = score >= 3 && negativeHits == 0,
            score = score,
            artistKey = normalizeArtist(author),
            channelKey = normalize(author),
            tags = tags,
            contentType = contentType,
        )
    }

    private fun homeScore(
        item: YouTubeFeedItem,
        classification: MusicClassification,
        profile: MusicInterestProfile,
        impression: FeedImpression?,
        sessionSeed: Long,
    ): Double {
        val strongProfile = hasStrongHomeProfile(profile)
        val mixFriendly = prefersMixLike(profile)
        val artist = profile.artistScores[classification.artistKey] ?: 0.0
        val tags = classification.tags.sumOf { profile.tagScores[it] ?: 0.0 }
        val contentType = profile.contentTypeScores[classification.contentType] ?: 0.0
        val searchIntent = queryIntentScore(item, profile)
        val recentArtistBoost = if (classification.artistKey in profile.recentArtists) 2.5 else 0.0
        val popularity = item.viewCount?.let { ln((it.coerceAtLeast(1L)).toDouble()) / if (strongProfile) 5.0 else 3.5 } ?: 0.0
        val recentPenalty = if (impression != null && System.currentTimeMillis() - impression.timestampMs < 86_400_000L) 8.0 else 0.0
        val mixPenalty = if (!mixFriendly && isMixLike(classification)) 13.5 else 0.0
        val genrePenalty = if (strongProfile && tags <= 0.0 && searchIntent <= 0.15 && recentArtistBoost == 0.0) 4.0 else 0.0
        return (classification.score * 1.1) +
            (artist * if (strongProfile) 1.7 else 0.9) +
            (tags * if (strongProfile) 1.0 else 0.45) +
            (contentType * if (strongProfile) 0.5 else 0.25) +
            (searchIntent * if (strongProfile) 8.0 else 3.5) +
            recentArtistBoost +
            popularity -
            mixPenalty -
            genrePenalty -
            recentPenalty +
            seededNoise(item.url, sessionSeed)
    }

    private fun queryIntentScore(item: YouTubeFeedItem, profile: MusicInterestProfile): Double {
        val itemTokens = titleTokens("${item.title} ${item.author}")
        if (itemTokens.isEmpty()) return 0.0
        return profile.searchScores.entries
            .sortedByDescending { it.value }
            .take(6)
            .maxOfOrNull { (query, weight) ->
                val queryTokens = titleTokens(query)
                if (queryTokens.isEmpty()) {
                    0.0
                } else {
                    (itemTokens.intersect(queryTokens).size.toDouble() / queryTokens.size) * weight.coerceAtLeast(1.0)
                }
            } ?: 0.0
    }

    private fun topSceneTags(profile: MusicInterestProfile, mixFriendly: Boolean): List<String> = profile.tagScores.entries
        .sortedByDescending { it.value }
        .map { it.key }
        .filterNot { !mixFriendly && it in mixLikeTags }
        .ifEmpty { defaultSceneTags }

    private fun relatedScore(
        currentItem: YouTubeFeedItem,
        currentClassification: MusicClassification,
        candidate: YouTubeFeedItem,
        classification: MusicClassification,
        profile: MusicInterestProfile,
        impression: FeedImpression?,
        primaryUrls: Set<String>,
    ): Double {
        val sharedTags = currentClassification.tags.intersect(classification.tags.toSet()).size.toDouble()
        val sameArtist = if (currentClassification.artistKey.isNotBlank() && currentClassification.artistKey == classification.artistKey) 1.0 else 0.0
        val sameChannel = if (currentClassification.channelKey.isNotBlank() && currentClassification.channelKey == classification.channelKey) 1.0 else 0.0
        val sameType = if (currentClassification.contentType == classification.contentType) 1.0 else 0.0
        val titleOverlap = titleOverlapRatio(currentItem.title, candidate.title)
        val profileAffinity = (profile.artistScores[classification.artistKey] ?: 0.0) * 0.25 +
            classification.tags.sumOf { profile.tagScores[it] ?: 0.0 } * 0.15
        val popularity = candidate.viewCount?.let { ln((it.coerceAtLeast(1L)).toDouble()) / 4.0 } ?: 0.0
        val directRelatedBoost = if (candidate.url in primaryUrls) 7.5 else 0.0
        val mixPenalty = if (!isMixLike(currentClassification) && isMixLike(classification)) 12.0 else 0.0
        val weakMatchPenalty = if (
            candidate.url !in primaryUrls &&
            sameArtist == 0.0 &&
            sameChannel == 0.0 &&
            sharedTags == 0.0 &&
            titleOverlap < 0.2
        ) {
            8.0
        } else {
            0.0
        }
        val recentPenalty = if (impression != null && System.currentTimeMillis() - impression.timestampMs < 86_400_000L) 2.0 else 0.0
        val mixBoost = if (isMixLike(currentClassification) && isMixLike(classification)) 3.0 else 0.0
        return directRelatedBoost +
            (sharedTags * 5.0) +
            (sameArtist * 4.6) +
            (sameChannel * 2.4) +
            (sameType * 2.5) +
            (titleOverlap * 4.2) +
            (profileAffinity * 0.75) +
            popularity +
            mixBoost -
            mixPenalty -
            weakMatchPenalty -
            recentPenalty
    }

    private fun titleOverlapRatio(currentTitle: String, candidateTitle: String): Double {
        val currentTokens = titleTokens(currentTitle)
        val candidateTokens = titleTokens(candidateTitle)
        if (currentTokens.isEmpty() || candidateTokens.isEmpty()) return 0.0
        val shared = currentTokens.intersect(candidateTokens).size
        return shared.toDouble() / minOf(currentTokens.size, candidateTokens.size).coerceAtLeast(1)
    }

    private fun titleTokens(value: String): Set<String> = normalize(value)
        .split(' ')
        .asSequence()
        .map(String::trim)
        .filter { token -> token.length >= 3 && token !in titleStopWords }
        .toSet()

    private fun diversify(
        candidates: List<RelatedMusicRecommendation>,
        limit: Int,
        strictArtistDiversity: Boolean = false,
    ): List<YouTubeFeedItem> {
        val selected = mutableListOf<RelatedMusicRecommendation>()
        val selectedUrls = mutableSetOf<String>()

        candidates.forEach { recommendation ->
            if (selected.size >= limit) return@forEach
            val artist = recommendation.classification.artistKey
            val channel = recommendation.classification.channelKey
            val recentWindow = selected.takeLast(if (strictArtistDiversity) 8 else 12)
            val artistHits = recentWindow.count { it.classification.artistKey == artist }
            val channelHits = recentWindow.count { it.classification.channelKey == channel }
            val artistLimit = if (strictArtistDiversity && selected.size < 8) 1 else 2
            val channelLimit = if (strictArtistDiversity && selected.size < 8) 2 else 3
            if (artist.isNotBlank() && artistHits >= artistLimit) return@forEach
            if (channel.isNotBlank() && channelHits >= channelLimit) return@forEach
            selected += recommendation
            selectedUrls += recommendation.item.url
        }

        if (selected.size < limit) {
            candidates.forEach { recommendation ->
                if (selected.size >= limit) return@forEach
                if (!selectedUrls.add(recommendation.item.url)) return@forEach
                selected += recommendation
            }
        }

        return selected.map(RelatedMusicRecommendation::item)
    }

    private fun signalWeight(type: MusicSignalType): Double = when (type) {
        MusicSignalType.PLAY_START -> 1.0
        MusicSignalType.PLAY_30S -> 2.0
        MusicSignalType.PLAY_70_PERCENT -> 3.0
        MusicSignalType.PLAY_COMPLETE -> 4.0
        MusicSignalType.REPLAY -> 2.0
        MusicSignalType.DOWNLOAD -> 5.0
        MusicSignalType.SEARCH_QUERY -> 3.0
        MusicSignalType.SKIP_FAST -> -3.0
        MusicSignalType.HIDE -> -6.0
    }

    private fun recencyWeight(ageMs: Long): Double = when {
        ageMs <= 14L * 86_400_000L -> 1.0
        ageMs <= 45L * 86_400_000L -> 0.55
        else -> 0.2
    }

    private fun queryForTag(tag: String): String = when (tag) {
        "cuarteto" -> "cuarteto en vivo 2026"
        "cumbia" -> "cumbia 2026 oficial"
        "enganchado" -> "enganchados cumbia cuarteto"
        "mix" -> "mix dj latino 2026"
        "session" -> "sesiones en vivo argentina"
        else -> "$tag music"
    }

    private fun isMixLike(classification: MusicClassification): Boolean = classification.tags.any { it in mixLikeTags }

    private fun prefersMixLike(profile: MusicInterestProfile): Boolean {
        val mixSignals = mixLikeTags.sumOf { profile.tagScores[it] ?: 0.0 }
        val searchedMix = profile.searchScores.keys.any { query ->
            val normalized = normalize(query)
            mixLikeTags.any { normalized.contains(it) }
        }
        return mixSignals >= 6.0 || searchedMix
    }

    private fun seededNoise(value: String, seed: Long): Double = (((value.hashCode().toLong() xor seed) and 0xFF) / 255.0) * 1.2

    private fun normalizeArtist(value: String): String = normalize(value).replace(" topic", "").replace(" vevo", "").trim()

    private fun normalizeSpaces(value: String): String = value.trim().replace("\\s+".toRegex(), " ")

    private fun normalize(value: String): String {
        val normalized = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return normalizeSpaces(normalized)
    }

    private companion object {
        val titleStopWords = setOf(
            "the",
            "and",
            "con",
            "para",
            "una",
            "uno",
            "del",
            "los",
            "las",
            "que",
            "por",
            "from",
            "feat",
            "ft",
            "video",
            "official",
            "audio",
            "live",
            "session",
            "sesion",
            "lyrics",
            "lyric",
            "mix",
            "remix",
        )
        val mixLikeTags = setOf("mix", "enganchado")
        val defaultSceneTags = listOf("cuarteto", "cumbia", "reggaeton", "trap", "pop", "rock")
        val positiveTokens = listOf("official video", "official audio", "lyrics", "lyric", "live", "session", "album", "track", "remix", "topic", "enganchado", "mix", "music", "musica")
        val negativeTokens = listOf("podcast", "entrevista", "reaction", "tutorial", "noticias", "gameplay", "vlog", "resumen", "prank", "review", "trailer", "shorts")
        val genreTokens = linkedMapOf(
            "cuarteto" to "cuarteto",
            "cumbia" to "cumbia",
            "reggaeton" to "reggaeton",
            "trap" to "trap",
            "rkt" to "rkt",
            "rock" to "rock",
            "pop" to "pop",
            "edm" to "edm",
            "house" to "house",
            "enganchado" to "enganchado",
            "mix" to "mix",
            "sesion" to "session",
            "session" to "session",
            "live" to "live",
            "remix" to "remix",
        )
        val contentTokens = linkedMapOf(
            "enganchado" to MusicContentType.MIX,
            "mix" to MusicContentType.MIX,
            "session" to MusicContentType.SESSION,
            "sesion" to MusicContentType.SESSION,
            "live" to MusicContentType.LIVE,
            "lyrics" to MusicContentType.LYRICS,
            "lyric" to MusicContentType.LYRICS,
            "remix" to MusicContentType.REMIX,
        )
        val defaultMusicQueries = listOf(
            "musica nueva argentina 2026",
            "cuarteto oficial video",
            "cumbia 2026 oficial",
            "trap argentino estreno",
            "sesiones en vivo acustico",
            "reggaeton latino 2026",
        )
        val mixFriendlyDefaultQueries = listOf(
            "enganchados cuarteto cumbia",
            "mix latino oficial 2026",
            "sesiones en vivo argentina",
            "cumbia 2026 oficial",
            "cuarteto en vivo",
        )
    }
}
