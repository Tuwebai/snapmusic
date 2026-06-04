package com.juan.snapmusic.data.extractor

import org.json.JSONArray
import org.json.JSONObject

internal object InstagramMediaInfoParser {
    fun parse(rawJson: String): InstagramPageMedia? {
        val root = runCatching { JSONObject(rawJson) }.getOrNull() ?: return null
        return findVideoItem(root)?.let(::toPageMedia)
    }

    private fun findVideoItem(json: JSONObject): JSONObject? {
        if (json.optJSONArray("video_versions")?.length() ?: 0 > 0) return json
        json.optJSONArray("items")?.firstObjectWithVideo()?.let { return it }
        json.optJSONArray("carousel_media")?.firstObjectWithVideo()?.let { return it }
        val keys = json.keys()
        while (keys.hasNext()) {
            when (val value = json.opt(keys.next())) {
                is JSONObject -> findVideoItem(value)?.let { return it }
                is JSONArray -> value.firstObjectWithVideo()?.let { return it }
            }
        }
        return null
    }

    private fun JSONArray.firstObjectWithVideo(): JSONObject? {
        for (index in 0 until length()) {
            val child = optJSONObject(index) ?: continue
            findVideoItem(child)?.let { return it }
        }
        return null
    }

    private fun toPageMedia(item: JSONObject): InstagramPageMedia {
        val videoUrl = bestVideoUrl(item) ?: error("No se encontró un video público descargable de Instagram.")
        return InstagramPageMedia(
            videoUrl = videoUrl,
            title = captionText(item).ifBlank { "Video de Instagram" },
            author = authorName(item).ifBlank { "Instagram" },
            thumbnailUrl = bestThumbnailUrl(item),
        )
    }

    private fun bestVideoUrl(item: JSONObject): String? {
        val versions = item.optJSONArray("video_versions") ?: return null
        return versions.objects()
            .maxByOrNull { version ->
                (version.optInt("width", 0) * version.optInt("height", 0)).coerceAtLeast(version.optInt("type", 0))
            }
            ?.optString("url")
            ?.takeIf(String::isNotBlank)
    }

    private fun bestThumbnailUrl(item: JSONObject): String {
        val candidates = item.optJSONObject("image_versions2")
            ?.optJSONArray("candidates")
            ?.objects()
            .orEmpty()
        return candidates
            .maxByOrNull { candidate -> candidate.optInt("width", 0) * candidate.optInt("height", 0) }
            ?.optString("url")
            ?.takeIf(String::isNotBlank)
            ?: item.optString("thumbnail_url").takeIf(String::isNotBlank)
            ?: item.optString("display_url").takeIf(String::isNotBlank)
            ?: ""
    }

    private fun captionText(item: JSONObject): String {
        return item.optJSONObject("caption")
            ?.optString("text")
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?: ""
    }

    private fun authorName(item: JSONObject): String {
        val user = item.optJSONObject("user") ?: item.optJSONObject("owner") ?: return ""
        val username = user.optString("username").takeIf(String::isNotBlank)
        val fullName = user.optString("full_name").takeIf(String::isNotBlank)
        return listOfNotNull(username?.let { "@$it" }, fullName).joinToString(" · ")
    }

    private fun JSONArray.objects(): List<JSONObject> = buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }

}
