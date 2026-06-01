package com.juan.snapmusic.data.download

import android.content.Context
import com.juan.snapmusic.core.model.DownloadProgressSnapshot
import com.juan.snapmusic.core.model.DownloadStage
import com.juan.snapmusic.core.model.TransferProbe
import com.juan.snapmusic.core.model.TransferSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import kotlin.math.min

data class HttpTransferPolicy(
    val maxParallelConnections: Int,
    val maxChunkBytes: Long = 8L * 1024L * 1024L,
    val retryCount: Int = 5,
)

class TransferExpiredException(message: String) : IllegalStateException(message)

class TransferValidationException(message: String) : IllegalStateException(message)

class HttpTransferEngine(
    private val workDir: File,
    private val client: OkHttpClient,
) {
    constructor(context: Context, client: OkHttpClient) : this(
        File(context.cacheDir, "http-transfer").apply { mkdirs() },
        client,
    )

    init {
        workDir.mkdirs()
    }

    suspend fun probe(source: TransferSource): TransferProbe = withContext(Dispatchers.IO) {
        val headResponse = runCatching {
            execute(Request.Builder().url(source.url).head().applyHeaders(source).build())
        }.getOrNull()
        headResponse?.use { response ->
            if (response.isSuccessful) {
                val contentType = response.header("Content-Type")
                validateContentType(contentType)
                val length = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0L }
                val acceptsRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                if (length != null || acceptsRanges) {
                    return@withContext TransferProbe(
                        contentLength = length,
                        contentType = contentType,
                        etag = response.header("ETag"),
                        lastModified = response.header("Last-Modified"),
                        acceptsRanges = acceptsRanges,
                    )
                }
            }
        }

        execute(
            Request.Builder()
                .url(source.url)
                .applyHeaders(source)
                .addHeader("Range", "bytes=0-0")
                .get()
                .build(),
        ).use { response ->
            val contentType = response.header("Content-Type")
            validateContentType(contentType)
            val total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
            TransferProbe(
                contentLength = total ?: response.body?.contentLength()?.takeIf { it > 0L },
                contentType = contentType,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                acceptsRanges = response.code == 206 && total != null,
            )
        }
    }

    suspend fun download(
        source: TransferSource,
        requestId: String,
        policy: HttpTransferPolicy,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val probe = probe(source)
        val jobDir = File(workDir, requestId.sanitizePathSegment()).apply { mkdirs() }
        val metadataFile = File(jobDir, "metadata.properties")
        val payloadFile = File(jobDir, "payload.bin")
        val segmentsDir = File(jobDir, "segments").apply { mkdirs() }
        resetIfMetadataChanged(metadataFile, payloadFile, segmentsDir, source, probe)
        if (probe.acceptsRanges && (probe.contentLength ?: 0L) > 0L) {
            downloadSegmented(source, probe, payloadFile, segmentsDir, policy, onProgress)
        } else {
            downloadSingle(source, probe, payloadFile, policy, onProgress)
        }
    }

    private suspend fun downloadSingle(
        source: TransferSource,
        probe: TransferProbe,
        payloadFile: File,
        policy: HttpTransferPolicy,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit,
    ): File {
        val expectedLength = probe.contentLength
        val canResume = probe.acceptsRanges && expectedLength != null
        val existingLength = payloadFile.takeIf(File::exists)?.length()?.coerceAtLeast(0L) ?: 0L
        val append = canResume && existingLength in 1 until expectedLength
        if (!append && payloadFile.exists()) payloadFile.delete()
        val startingOffset = if (append) existingLength else 0L
        executeWithRetry(policy.retryCount) {
            val builder = Request.Builder().url(source.url).applyHeaders(source).get()
            if (append) builder.addHeader("Range", "bytes=$startingOffset-")
            execute(builder.build()).use { response ->
                validateDownloadResponse(response)
                if (append && response.code != 206) {
                    payloadFile.delete()
                    throw TransferValidationException("El origen rechazó la reanudación del archivo parcial.")
                }
                streamIntoFile(
                    response = response,
                    target = payloadFile,
                    append = append,
                    initialDownloaded = startingOffset,
                    totalBytes = expectedLength,
                    stage = DownloadStage.DOWNLOADING,
                    onProgress = onProgress,
                )
            }
        }
        validateFinalSize(payloadFile, expectedLength)
        return payloadFile
    }

    private suspend fun downloadSegmented(
        source: TransferSource,
        probe: TransferProbe,
        payloadFile: File,
        segmentsDir: File,
        policy: HttpTransferPolicy,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit,
    ): File = coroutineScope {
        val totalBytes = probe.contentLength
            ?: return@coroutineScope downloadSingle(source, probe, payloadFile, policy, onProgress)
        val ranges = buildRanges(totalBytes, policy.maxChunkBytes)
        val tracker = SegmentProgressTracker(ranges.size, totalBytes, onProgress)
        val semaphore = Semaphore(policy.maxParallelConnections.coerceAtLeast(1))
        ranges.mapIndexed { index, range ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val partFile = File(segmentsDir, "segment-$index.part")
                    downloadRangePart(source, range.first, range.last, partFile, tracker, index, policy)
                }
            }
        }.forEach { it.await() }

        FileOutputStream(payloadFile, false).use { output ->
            ranges.indices.forEach { index ->
                FileInputStream(File(segmentsDir, "segment-$index.part")).use { input ->
                    input.copyTo(output, DEFAULT_BUFFER_SIZE)
                }
            }
        }
        validateFinalSize(payloadFile, totalBytes)
        onProgress(DownloadProgressSnapshot(totalBytes, totalBytes, tracker.speedBytesPerSecond(), DownloadStage.DOWNLOADING))
        payloadFile
    }

    private suspend fun downloadRangePart(
        source: TransferSource,
        startInclusive: Long,
        endInclusive: Long,
        partFile: File,
        tracker: SegmentProgressTracker,
        segmentIndex: Int,
        policy: HttpTransferPolicy,
    ) {
        val expectedLength = endInclusive - startInclusive + 1
        val existingLength = partFile.takeIf(File::exists)?.length()?.coerceAtLeast(0L) ?: 0L
        if (existingLength == expectedLength) {
            tracker.update(segmentIndex, existingLength)
            return
        }
        if (existingLength > expectedLength) {
            partFile.delete()
        }
        executeWithRetry(policy.retryCount) {
            val rangeStart = startInclusive + existingLength.coerceAtLeast(0L)
            val request = Request.Builder()
                .url(source.url)
                .applyHeaders(source)
                .addHeader("Range", "bytes=$rangeStart-$endInclusive")
                .get()
                .build()
            execute(request).use { response ->
                validateDownloadResponse(response)
                if (response.code != 206) {
                    partFile.delete()
                    throw TransferValidationException("El origen dejó de soportar descargas segmentadas.")
                }
                streamIntoPart(response, partFile, existingLength > 0L, existingLength, tracker, segmentIndex)
            }
        }
        if (partFile.length() != expectedLength) {
            throw TransferValidationException("El segmento descargado quedó incompleto.")
        }
    }

    private suspend fun streamIntoFile(
        response: Response,
        target: File,
        append: Boolean,
        initialDownloaded: Long,
        totalBytes: Long?,
        stage: DownloadStage,
        onProgress: suspend (DownloadProgressSnapshot) -> Unit,
    ) {
        response.body?.byteStream()?.use { input ->
            FileOutputStream(target, append).use { output ->
                val buffer = ByteArray(256 * 1024)
                var downloaded = initialDownloaded
                val startMs = System.currentTimeMillis()
                var lastPublishedAt = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    val now = System.currentTimeMillis()
                    if (now - lastPublishedAt >= 250L) {
                        lastPublishedAt = now
                        val elapsed = (now - startMs).coerceAtLeast(1L)
                        onProgress(
                            DownloadProgressSnapshot(
                                bytesDownloaded = downloaded,
                                totalBytes = totalBytes,
                                speedBytesPerSecond = downloaded * 1000L / elapsed,
                                stage = stage,
                            ),
                        )
                    }
                }
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                onProgress(DownloadProgressSnapshot(downloaded, totalBytes, downloaded * 1000L / elapsed, stage))
            }
        } ?: throw TransferValidationException("La respuesta del stream llegó vacía.")
    }

    private suspend fun streamIntoPart(
        response: Response,
        target: File,
        append: Boolean,
        initialDownloaded: Long,
        tracker: SegmentProgressTracker,
        segmentIndex: Int,
    ) {
        response.body?.byteStream()?.use { input ->
            FileOutputStream(target, append).use { output ->
                val buffer = ByteArray(256 * 1024)
                var downloaded = initialDownloaded
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    tracker.update(segmentIndex, downloaded)
                }
            }
        } ?: throw TransferValidationException("La respuesta del stream llegó vacía.")
    }

    private suspend fun executeWithRetry(retryCount: Int, block: suspend () -> Unit) {
        var lastError: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                block()
                return
            } catch (error: Throwable) {
                lastError = error
                if (error is TransferExpiredException || error is TransferValidationException || attempt == retryCount - 1) {
                    throw error
                }
                delay(min(4_000L, 250L * (1 shl attempt)))
            }
        }
        throw lastError ?: IllegalStateException("La descarga falló sin detalle adicional.")
    }

    private fun execute(request: Request): Response {
        val response = client.newCall(request).execute()
        when (response.code) {
            403, 404, 410 -> {
                response.close()
                throw TransferExpiredException("El stream remoto expiró o ya no existe.")
            }
        }
        return response
    }

    private fun buildRanges(totalBytes: Long, maxChunkBytes: Long): List<LongRange> {
        val chunkSize = maxChunkBytes.coerceAtLeast(256L * 1024L)
        val result = mutableListOf<LongRange>()
        var cursor = 0L
        while (cursor < totalBytes) {
            val end = min(totalBytes - 1, cursor + chunkSize - 1)
            result += cursor..end
            cursor = end + 1
        }
        return result
    }

    private fun validateDownloadResponse(response: Response) {
        if (!response.isSuccessful && response.code != 206) {
            throw IllegalStateException("No se pudo descargar el stream seleccionado.")
        }
        validateContentType(response.header("Content-Type"))
    }

    private fun validateContentType(contentType: String?) {
        val normalized = contentType?.lowercase().orEmpty()
        if (normalized.contains("text/html") || normalized.contains("application/json") || normalized.startsWith("text/")) {
            throw TransferValidationException("El origen devolvió una respuesta inválida en lugar del archivo multimedia.")
        }
    }

    private fun validateFinalSize(file: File, expectedLength: Long?) {
        val size = file.length()
        if (size <= 0L) throw TransferValidationException("El stream descargado quedó vacío.")
        if (expectedLength != null && size < expectedLength) {
            throw TransferValidationException("El archivo descargado quedó incompleto.")
        }
    }

    private fun resetIfMetadataChanged(
        metadataFile: File,
        payloadFile: File,
        segmentsDir: File,
        source: TransferSource,
        probe: TransferProbe,
    ) {
        val current = Properties()
        if (metadataFile.exists()) {
            metadataFile.inputStream().use(current::load)
        }
        val changed =
            current.getProperty("url") != source.url ||
                current.getProperty("etag") != probe.etag.orEmpty() ||
                current.getProperty("lastModified") != probe.lastModified.orEmpty() ||
                current.getProperty("contentLength") != probe.contentLength?.toString().orEmpty()
        if (changed) {
            payloadFile.delete()
            segmentsDir.listFiles().orEmpty().forEach(File::delete)
        }
        val updated = Properties().apply {
            setProperty("url", source.url)
            setProperty("etag", probe.etag.orEmpty())
            setProperty("lastModified", probe.lastModified.orEmpty())
            setProperty("contentLength", probe.contentLength?.toString().orEmpty())
        }
        metadataFile.outputStream().use { updated.store(it, null) }
    }

    private fun Request.Builder.applyHeaders(source: TransferSource): Request.Builder {
        source.headers.forEach { (name, value) -> addHeader(name, value) }
        return this
    }

    private fun String.sanitizePathSegment(): String =
        replace(Regex("[^a-zA-Z0-9._-]"), "_")
}

private class SegmentProgressTracker(
    segmentCount: Int,
    private val totalBytes: Long,
    private val onProgress: suspend (DownloadProgressSnapshot) -> Unit,
) {
    private val segmentBytes = LongArray(segmentCount)
    private val startedAt = System.currentTimeMillis()
    private var lastPublishedAt = 0L

    suspend fun update(index: Int, bytes: Long) {
        val snapshot = synchronized(this) {
            segmentBytes[index] = bytes
            val now = System.currentTimeMillis()
            if (now - lastPublishedAt < 250L && bytes < totalBytes) return
            lastPublishedAt = now
            val downloaded = segmentBytes.sum()
            val elapsed = (now - startedAt).coerceAtLeast(1L)
            DownloadProgressSnapshot(
                bytesDownloaded = downloaded,
                totalBytes = totalBytes,
                speedBytesPerSecond = downloaded * 1000L / elapsed,
                stage = DownloadStage.DOWNLOADING,
            )
        }
        onProgress(snapshot)
    }

    fun speedBytesPerSecond(): Long {
        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        return segmentBytes.sum() * 1000L / elapsed
    }
}
