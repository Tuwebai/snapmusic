package com.juan.snapmusic.data.storage

import com.juan.snapmusic.core.model.FeedImpression
import com.juan.snapmusic.core.model.MusicAffinitySignal
import com.juan.snapmusic.core.model.MusicContentType
import com.juan.snapmusic.core.model.MusicSignalType
import org.json.JSONArray
import org.json.JSONObject

internal object MusicRecommendationCodec {
    fun encodeSignals(items: List<MusicAffinitySignal>): String {
        if (items.isEmpty()) return ""
        return JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("type", item.type.name)
                        .put("timestampMs", item.timestampMs)
                        .put("sourceUrl", item.sourceUrl)
                        .put("title", item.title)
                        .put("author", item.author)
                        .put("query", item.query)
                        .put("tags", JSONArray(item.tags))
                        .put("artistKey", item.artistKey)
                        .put("channelKey", item.channelKey)
                        .put("contentType", item.contentType.name),
                )
            }
        }.toString()
    }

    fun decodeSignals(raw: String?): List<MusicAffinitySignal> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        MusicAffinitySignal(
                            type = runCatching { MusicSignalType.valueOf(item.optString("type")) }.getOrDefault(MusicSignalType.PLAY_START),
                            timestampMs = item.optLong("timestampMs"),
                            sourceUrl = item.optString("sourceUrl").ifBlank { null },
                            title = item.optString("title"),
                            author = item.optString("author"),
                            query = item.optString("query").ifBlank { null },
                            tags = item.optJSONArray("tags").toStringList(),
                            artistKey = item.optString("artistKey"),
                            channelKey = item.optString("channelKey"),
                            contentType = runCatching { MusicContentType.valueOf(item.optString("contentType")) }.getOrDefault(MusicContentType.UNKNOWN),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeImpressions(items: List<FeedImpression>): String {
        if (items.isEmpty()) return ""
        return JSONArray().apply {
            items.forEach { item ->
                put(
                    JSONObject()
                        .put("url", item.url)
                        .put("timestampMs", item.timestampMs),
                )
            }
        }.toString()
    }

    fun decodeImpressions(raw: String?): List<FeedImpression> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val url = item.optString("url").trim()
                    if (url.isBlank()) continue
                    add(FeedImpression(url = url, timestampMs = item.optLong("timestampMs")))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index)
                    .trim()
                    .takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }
}
