package com.juan.snapmusic.data.recommendation

import android.util.Log
import com.juan.snapmusic.core.model.MusicClassification
import com.juan.snapmusic.core.model.MusicContentType
import com.juan.snapmusic.core.model.MusicInterestProfile
import com.juan.snapmusic.core.model.YouTubeFeedItem
import java.text.Normalizer

internal enum class FeedRankingKind(val logName: String) {
    HOME("home"),
    WATCH_NEXT("watch"),
}

internal data class FeedRelevanceScore(
    val score: Double,
    val penalty: Double,
    val reason: String,
)

internal class FeedRelevancePolicy {
    fun apply(
        kind: FeedRankingKind,
        item: YouTubeFeedItem,
        classification: MusicClassification,
        profile: MusicInterestProfile,
        baseScore: Double,
        currentClassification: MusicClassification? = null,
        primaryRelated: Boolean = false,
    ): FeedRelevanceScore {
        val userLikesType = profile.contentTypeScores[classification.contentType].orZero() >= 6.0 ||
            currentClassification?.contentType == classification.contentType && isNonTrack(classification.contentType)
        val penalty = undesirableTypePenalty(classification.contentType, userLikesType) +
            longFormPenalty(item.durationSeconds, classification.contentType, userLikesType) +
            textPenalty(item, userLikesType) +
            weakWatchFallbackPenalty(kind, classification, currentClassification, primaryRelated)
        val boost = officialShortTrackBoost(item, classification, currentClassification)
        val score = baseScore + boost - penalty
        val reason = listOfNotNull(
            "penalty=${penalty.format()}".takeIf { penalty > 0.0 },
            "boost=${boost.format()}".takeIf { boost > 0.0 },
        ).ifEmpty { listOf("neutral") }.joinToString(",")
        log(kind, item, classification, score, penalty, reason)
        return FeedRelevanceScore(score = score, penalty = penalty, reason = reason)
    }

    private fun undesirableTypePenalty(type: MusicContentType, userLikesType: Boolean): Double {
        val base = when (type) {
            MusicContentType.MIX -> 18.0
            MusicContentType.REMIX -> 10.0
            MusicContentType.SESSION -> 8.0
            MusicContentType.LIVE -> 10.0
            else -> 0.0
        }
        return if (userLikesType) base * 0.25 else base
    }

    private fun longFormPenalty(seconds: Long, type: MusicContentType, userLikesType: Boolean): Double {
        val base = when {
            seconds >= 2_400L -> 24.0
            seconds >= 1_200L -> 16.0
            seconds >= 720L && type != MusicContentType.TRACK -> 8.0
            else -> 0.0
        }
        return if (userLikesType) base * 0.35 else base
    }

    private fun textPenalty(item: YouTubeFeedItem, userLikesType: Boolean): Double {
        val text = normalize("${item.title} ${item.author} ${item.description.orEmpty()}")
        val base = when {
            text.contains("playlist") || text.contains("compilado") || text.contains("compilacion") -> 14.0
            text.contains("full album") || text.contains("dj set") || text.contains("mega mix") -> 12.0
            text.contains("enganchado") || text.contains("enganchados") -> 10.0
            else -> 0.0
        }
        return if (userLikesType) base * 0.3 else base
    }

    private fun weakWatchFallbackPenalty(
        kind: FeedRankingKind,
        classification: MusicClassification,
        currentClassification: MusicClassification?,
        primaryRelated: Boolean,
    ): Double {
        if (kind != FeedRankingKind.WATCH_NEXT || primaryRelated || currentClassification == null) return 0.0
        val sameArtist = currentClassification.artistKey.isNotBlank() &&
            currentClassification.artistKey == classification.artistKey
        val sharedTags = currentClassification.tags.intersect(classification.tags.toSet()).isNotEmpty()
        return if (!sameArtist && !sharedTags) 7.5 else 0.0
    }

    private fun officialShortTrackBoost(
        item: YouTubeFeedItem,
        classification: MusicClassification,
        currentClassification: MusicClassification?,
    ): Double {
        if (classification.contentType != MusicContentType.TRACK || item.durationSeconds !in 90L..480L) return 0.0
        val text = normalize("${item.title} ${item.author}")
        val official = text.contains("official") || text.contains("oficial") || text.contains("audio")
        val sameArtist = currentClassification?.artistKey?.takeIf(String::isNotBlank) == classification.artistKey
        return when {
            official && sameArtist -> 6.0
            official -> 3.5
            else -> 1.25
        }
    }

    private fun log(
        kind: FeedRankingKind,
        item: YouTubeFeedItem,
        classification: MusicClassification,
        score: Double,
        penalty: Double,
        reason: String,
    ) {
        if (penalty <= 0.0 && reason == "neutral") return
        Log.d(
            TAG,
            "kind=${kind.logName} lane=ranking score=${score.format()} penalty=${penalty.format()} " +
                "duration=${item.durationSeconds}s type=${classification.contentType.name} reason=$reason title=${item.title.take(72)}",
        )
    }

    private fun isNonTrack(type: MusicContentType): Boolean = type != MusicContentType.TRACK && type != MusicContentType.UNKNOWN

    private fun Double?.orZero(): Double = this ?: 0.0

    private fun Double.format(): String = "%.2f".format(this)

    private fun normalize(value: String): String {
        val lowercase = value.lowercase()
        val normalized = Normalizer.normalize(lowercase, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return normalized.replace("\\s+".toRegex(), " ").trim()
    }

    private companion object {
        const val TAG = "SnapMusicFeedRanking"
    }
}
