package com.juan.snapmusic.data.extractor

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class IgramSocialVideoResolver(
    private val okHttpClient: OkHttpClient,
) {
    fun resolve(url: String): InstagramPageMedia? {
        val request = Request.Builder()
            .url(IGRAM_ENDPOINT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(requestBody(url))
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            if (!json.optBoolean("success", false)) return null
            val data = json.optJSONArray("data") ?: return null
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                if (item.optString("type") != "video") continue
                val videoUrl = item.optString("url").takeIf(String::isNotBlank) ?: continue
                return InstagramPageMedia(
                    videoUrl = videoUrl,
                    title = item.optString("quality").takeIf(String::isNotBlank) ?: "Video de Instagram",
                    author = "Instagram",
                    thumbnailUrl = item.optString("thumbnail").takeIf(String::isNotBlank) ?: "",
                )
            }
        }
        return null
    }

    private fun requestBody(url: String) = JSONObject()
        .put("url", url)
        .toString()
        .toRequestBody(JSON)

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val IGRAM_ENDPOINT = "https://calm-snow-84ea.jldilshan0.workers.dev/"
    }
}
