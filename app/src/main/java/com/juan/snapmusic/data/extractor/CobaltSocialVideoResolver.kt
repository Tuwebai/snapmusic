package com.juan.snapmusic.data.extractor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class CobaltSocialVideoResolver(
    private val okHttpClient: OkHttpClient,
) {
    fun resolve(url: String): InstagramPageMedia? {
        val request = Request.Builder()
            .url(COBALT_ENDPOINT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(requestBody(url))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            return when (json.optString("status")) {
                "redirect", "tunnel" -> directMedia(json)
                "picker" -> pickerMedia(json)
                else -> null
            }
        }
    }

    private fun directMedia(json: JSONObject): InstagramPageMedia? {
        val videoUrl = json.optString("url").takeIf(String::isNotBlank) ?: return null
        return InstagramPageMedia(
            videoUrl = videoUrl,
            title = json.optString("filename").takeIf(String::isNotBlank) ?: "Video de Instagram",
            author = "Instagram",
            thumbnailUrl = "",
        )
    }

    private fun pickerMedia(json: JSONObject): InstagramPageMedia? {
        val picker = json.optJSONArray("picker") ?: return null
        for (index in 0 until picker.length()) {
            val item = picker.optJSONObject(index) ?: continue
            if (item.optString("type") != "video") continue
            val videoUrl = item.optString("url").takeIf(String::isNotBlank) ?: continue
            return InstagramPageMedia(
                videoUrl = videoUrl,
                title = json.optString("filename").takeIf(String::isNotBlank) ?: "Video de Instagram",
                author = "Instagram",
                thumbnailUrl = item.optString("thumb").takeIf(String::isNotBlank) ?: "",
            )
        }
        return null
    }

    private fun requestBody(url: String) = JSONObject()
        .put("url", url)
        .put("downloadMode", "auto")
        .put("videoQuality", "720")
        .put("filenameStyle", "basic")
        .put("disableMetadata", true)
        .toString()
        .toRequestBody(JSON)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val COBALT_ENDPOINT = "https://api.cobalt.tools/"
    }
}
