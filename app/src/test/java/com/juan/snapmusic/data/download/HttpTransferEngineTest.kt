package com.juan.snapmusic.data.download

import com.juan.snapmusic.core.model.DownloadProgressSnapshot
import com.juan.snapmusic.core.model.TransferSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class HttpTransferEngineTest {
    @Test
    fun `reconstruye el archivo con ranges 206`() = runBlocking {
        val payload = ByteArray(256 * 1024) { index -> (index % 251).toByte() }
        withServer(rangeDispatcher(payload, supportsRanges = true)) { server ->
            val engine = HttpTransferEngine(createTempDirectory("snapmusic-http-transfer").toFile(), OkHttpClient())
            val output = engine.download(
                source = TransferSource(server.url("/media").toString()),
                requestId = "range-test",
                policy = HttpTransferPolicy(maxParallelConnections = 4, maxChunkBytes = 64 * 1024L),
                onProgress = {},
            )
            assertArrayEquals(payload, output.readBytes())
        }
    }

    @Test
    fun `hace fallback a get simple si el origen no soporta ranges`() = runBlocking {
        val payload = ByteArray(96 * 1024) { index -> (index % 199).toByte() }
        withServer(rangeDispatcher(payload, supportsRanges = false)) { server ->
            val engine = HttpTransferEngine(createTempDirectory("snapmusic-http-transfer").toFile(), OkHttpClient())
            val output = engine.download(
                source = TransferSource(server.url("/media").toString()),
                requestId = "fallback-test",
                policy = HttpTransferPolicy(maxParallelConnections = 4, maxChunkBytes = 32 * 1024L),
                onProgress = {},
            )
            assertArrayEquals(payload, output.readBytes())
        }
    }

    @Test
    fun `reanuda tras un fallo transitorio de segmento`() = runBlocking {
        val payload = ByteArray(192 * 1024) { index -> (index % 173).toByte() }
        var firstChunkFailed = false
        withServer(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Type", "audio/mp4")
                }
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader == "bytes=0-0") {
                    return partialResponse(payload, 0, 0)
                }
                if (!firstChunkFailed && rangeHeader?.startsWith("bytes=0-") == true) {
                    firstChunkFailed = true
                    return MockResponse().setResponseCode(500)
                }
                return dispatchRangePayload(payload, rangeHeader, supportsRanges = true)
            }
        }) { server ->
            val progressEvents = mutableListOf<DownloadProgressSnapshot>()
            val engine = HttpTransferEngine(createTempDirectory("snapmusic-http-transfer").toFile(), OkHttpClient())
            val output = engine.download(
                source = TransferSource(server.url("/media").toString()),
                requestId = "retry-test",
                policy = HttpTransferPolicy(maxParallelConnections = 3, maxChunkBytes = 64 * 1024L),
                onProgress = { progressEvents += it },
            )
            assertArrayEquals(payload, output.readBytes())
            assertTrue(progressEvents.isNotEmpty())
        }
    }

    @Test(expected = TransferValidationException::class)
    fun `rechaza respuestas html en lugar de media`() = runBlocking {
        withServer(object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "text/html")
                    .setBody("<html>bad gateway</html>")
            }
        }) { server ->
            val engine = HttpTransferEngine(createTempDirectory("snapmusic-http-transfer").toFile(), OkHttpClient())
            engine.download(
                source = TransferSource(server.url("/media").toString()),
                requestId = "html-test",
                policy = HttpTransferPolicy(maxParallelConnections = 1),
                onProgress = {},
            )
        }
    }

    private fun rangeDispatcher(payload: ByteArray, supportsRanges: Boolean): Dispatcher {
        return object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") {
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Length", payload.size)
                        .setHeader("Content-Type", "audio/mp4")
                        .apply {
                            if (supportsRanges) {
                                setHeader("Accept-Ranges", "bytes")
                            }
                        }
                }
                return dispatchRangePayload(payload, request.getHeader("Range"), supportsRanges)
            }
        }
    }

    private fun dispatchRangePayload(
        payload: ByteArray,
        rangeHeader: String?,
        supportsRanges: Boolean,
    ): MockResponse {
        if (supportsRanges && !rangeHeader.isNullOrBlank()) {
            val range = rangeHeader.removePrefix("bytes=").split("-")
            val start = range.first().toInt()
            val end = range.getOrNull(1)?.takeIf(String::isNotBlank)?.toInt() ?: payload.lastIndex
            return partialResponse(payload, start, end)
        }
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Length", payload.size)
            .setHeader("Content-Type", "audio/mp4")
            .setBody(okio.Buffer().write(payload))
    }

    private fun partialResponse(payload: ByteArray, start: Int, end: Int): MockResponse {
        val clampedEnd = end.coerceAtMost(payload.lastIndex)
        val slice = payload.copyOfRange(start, clampedEnd + 1)
        return MockResponse()
            .setResponseCode(206)
            .setHeader("Content-Type", "audio/mp4")
            .setHeader("Content-Length", slice.size)
            .setHeader("Content-Range", "bytes $start-$clampedEnd/${payload.size}")
            .setBody(okio.Buffer().write(slice))
    }

    private suspend fun withServer(dispatcher: Dispatcher, block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.dispatcher = dispatcher
        server.start()
        try {
            block(server)
        } finally {
            server.shutdown()
        }
    }
}
