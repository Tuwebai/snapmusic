package com.juan.snapmusic.data.extractor

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class OkHttpNewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {
    fun executeRawGet(url: String): String {
        val request = okhttp3.Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} al consultar $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    override fun execute(request: Request): Response {
        val builder = okhttp3.Request.Builder().url(request.url())
        request.headers().orEmpty().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        when (request.httpMethod().uppercase()) {
            "POST" -> {
                val body = (request.dataToSend() ?: ByteArray(0))
                    .toRequestBody("application/json".toMediaTypeOrNull())
                builder.post(body)
            }
            "HEAD" -> builder.head()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val headers = response.headers.toMultimap().mapValues { (_, values) -> values.toList() }
            return Response(
                response.code,
                response.message,
                headers,
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
