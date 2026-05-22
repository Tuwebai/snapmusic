package com.juan.snapmusic.data.extractor

import android.net.Uri
import com.juan.snapmusic.core.model.ContainerFormat
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.DownloadStrategy
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.TransferSource
import com.juan.snapmusic.core.model.YouTubeFeedPage
import com.juan.snapmusic.core.model.YouTubeFeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Base64
import java.util.LinkedHashMap

class NewPipeStreamResolverRepository(
    private val downloader: OkHttpNewPipeDownloader,
) : StreamResolverRepository {
    private val pageCursorStore = linkedMapOf<String, Page>()
    private var pageCursorCounter = 0L

    init {
        NewPipe.init(downloader, Localization("es", "AR"), ContentCountry("AR"))
    }

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(url)
        ResolvedMedia(
            sourceUrl = url,
            title = info.name,
            author = info.uploaderName.orEmpty(),
            durationSeconds = info.duration,
            thumbnailUrl = info.thumbnails.lastOrNull()?.url.orEmpty(),
            playbackUrl = buildPlaybackUrl(info.videoStreams),
            adaptivePlaybackUrl = info.dashMpdUrl ?: info.hlsUrl,
            audioVariants = buildAudioVariants(info.audioStreams),
            videoVariants = buildVideoVariants(
                progressiveStreams = info.videoStreams,
                videoOnlyStreams = info.videoOnlyStreams,
                audioStreams = info.audioStreams,
            ),
        )
    }

    override suspend fun resolveDownloadPlan(url: String, selection: DownloadSelection): DownloadExecutionPlan = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(url)
        when (selection.strategy) {
            DownloadStrategy.DIRECT -> {
                when (selection.kind) {
                    MediaKind.AUDIO -> {
                        val source = selectDirectAudio(info.audioStreams, selection)
                        DownloadExecutionPlan.Direct(selection, source)
                    }

                    MediaKind.VIDEO -> {
                        val source = selectDirectVideo(info.videoStreams, selection)
                        DownloadExecutionPlan.Direct(selection, source)
                    }
                }
            }

            DownloadStrategy.TRANSCODE_AUDIO -> {
                val source = selectBestAudioForTranscode(info.audioStreams)
                DownloadExecutionPlan.AudioTranscode(selection, source)
            }

            DownloadStrategy.MUX_VIDEO_AUDIO -> {
                val videoSource = selectMuxVideo(info.videoOnlyStreams, selection)
                val audioSource = selectBestAudioForTranscode(info.audioStreams)
                DownloadExecutionPlan.MuxVideoAudio(selection, videoSource, audioSource)
            }
        }
    }

    override suspend fun loadTrendingPage(limit: Int, cursor: String?): YouTubeFeedPage = withContext(Dispatchers.IO) {
        val extractor = ServiceList.YouTube.kioskList.defaultKioskExtractor
        collectFeedPage(
            limit = limit,
            cursor = cursor,
            loadInitial = {
                val info = KioskInfo.getInfo(extractor)
                FeedChunk(
                    items = info.relatedItems.filterIsInstance<StreamInfoItem>(),
                    nextPage = info.nextPage,
                )
            },
            loadMore = { page ->
                KioskInfo.getMoreItems(
                    ServiceList.YouTube,
                    extractor.url,
                    page,
                )
            },
        )
    }

    override suspend fun loadTrending(limit: Int): List<YouTubeFeedItem> = loadTrendingPage(limit = limit).items

    override suspend fun searchVideosPage(query: String, limit: Int, cursor: String?): YouTubeFeedPage = withContext(Dispatchers.IO) {
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
        collectFeedPage(
            limit = limit,
            cursor = cursor,
            loadInitial = {
                val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
                FeedChunk(
                    items = info.relatedItems.filterIsInstance<StreamInfoItem>(),
                    nextPage = info.nextPage,
                )
            },
            loadMore = { page ->
                SearchInfo.getMoreItems(
                    ServiceList.YouTube,
                    handler,
                    page,
                )
            },
        )
    }

    override suspend fun searchVideos(query: String, limit: Int): List<YouTubeFeedItem> = searchVideosPage(
        query = query,
        limit = limit,
    ).items

    override suspend fun loadRelatedVideos(url: String, limit: Int): List<YouTubeFeedItem> = withContext(Dispatchers.IO) {
        StreamInfo.getInfo(url)
            .relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toFeedItem() }
            .take(limit)
    }

    override suspend fun searchSuggestions(query: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        val normalized = query.trim()
        if (normalized.isBlank()) return@withContext emptyList()
        val endpoint =
            "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=es&gl=ar&q=${Uri.encode(normalized)}"
        val response = downloader.executeRawGet(endpoint)
        val suggestions = runCatching {
            val root = JSONArray(response)
            val items = root.optJSONArray(1) ?: JSONArray()
            buildList {
                for (index in 0 until items.length()) {
                    items.optString(index)
                        .trim()
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
        suggestions
            .distinct()
            .take(limit)
    }

    private fun buildAudioVariants(streams: List<AudioStream>): List<MediaVariant> {
        val directAudio = streams
            .filter { !it.url.isNullOrBlank() }
            .filter { stream -> stream.format == MediaFormat.M4A }
            .sortedByDescending { it.averageBitrate }
            .take(3)
            .map { stream ->
                MediaVariant(
                    id = "audio-${stream.id}",
                    label = if (stream.averageBitrate > 0) "M4A ${stream.averageBitrate}kbps" else "M4A directo",
                    kind = MediaKind.AUDIO,
                    container = ContainerFormat.M4A,
                    bitrateKbps = stream.averageBitrate.takeIf { it > 0 },
                    directUrl = stream.url.orEmpty(),
                )
            }

        val bestSource = directAudio.firstOrNull() ?: return emptyList()
        val syntheticMp3 = listOf(128, 192, 256, 320).map { kbps ->
            MediaVariant(
                id = "mp3-$kbps",
                label = "MP3 ${kbps}kbps",
                kind = MediaKind.AUDIO,
                container = ContainerFormat.MP3,
                bitrateKbps = kbps,
                directUrl = "",
                requiresTranscode = true,
            )
        }
        return directAudio + syntheticMp3
    }

    private fun buildVideoVariants(
        progressiveStreams: List<VideoStream>,
        videoOnlyStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
    ): List<MediaVariant> {
        val compatibleStreams = progressiveStreams
            .filter { !it.isVideoOnly && !it.url.isNullOrBlank() }
            .filter { stream -> stream.format == MediaFormat.MPEG_4 && stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.height }
            .distinctBy { it.resolution }
        val fallbackStreams = progressiveStreams
            .filter { !it.isVideoOnly && !it.url.isNullOrBlank() }
            .sortedByDescending { it.height }
            .distinctBy { it.resolution }
        val progressiveVariants = (compatibleStreams.ifEmpty { fallbackStreams }).map { stream ->
            MediaVariant(
                id = "video-${stream.id}",
                label = "MP4 ${stream.resolution.orEmpty()}",
                kind = MediaKind.VIDEO,
                container = ContainerFormat.MP4,
                resolution = stream.resolution,
                directUrl = stream.url.orEmpty(),
            )
        }

        val bestMuxAudio = audioStreams
            .filter { !it.url.isNullOrBlank() }
            .sortedByDescending { it.averageBitrate }
            .firstOrNull()
            ?.url

        if (bestMuxAudio.isNullOrBlank()) return progressiveVariants

        val progressiveResolutions = progressiveVariants.mapNotNull { it.resolution }.toSet()
        val muxVariants = videoOnlyStreams
            .filter { !it.url.isNullOrBlank() }
            .filter { it.height > 0 }
            .sortedByDescending { it.height }
            .distinctBy { it.resolution }
            .filterNot { it.resolution in progressiveResolutions }
            .map { stream ->
                MediaVariant(
                    id = "video-mux-${stream.id}",
                    label = "MP4 ${stream.resolution.orEmpty()}",
                    kind = MediaKind.VIDEO,
                    container = ContainerFormat.MP4,
                    resolution = stream.resolution,
                    directUrl = stream.url.orEmpty(),
                    secondaryUrl = bestMuxAudio,
                    requiresMux = true,
                )
            }

        return (progressiveVariants + muxVariants)
            .sortedByDescending { it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0 }
    }

    private fun selectDirectAudio(
        streams: List<AudioStream>,
        selection: DownloadSelection,
    ): TransferSource {
        require(selection.targetContainer == ContainerFormat.M4A) {
            "Solo podemos descargar audio directo en M4A."
        }
        val bitrate = selection.targetBitrateKbps ?: error("Falta el bitrate objetivo del audio.")
        val stream = streams
            .filter { !it.url.isNullOrBlank() }
            .filter { it.format == MediaFormat.M4A }
            .sortedByDescending { it.averageBitrate }
            .firstOrNull { it.averageBitrate == bitrate }
            ?: error("La fuente M4A ${bitrate}kbps ya no está disponible.")
        return TransferSource(stream.url.orEmpty())
    }

    private fun selectBestAudioForTranscode(
        streams: List<AudioStream>,
    ): TransferSource {
        val preferredM4a = streams
            .filter { !it.url.isNullOrBlank() }
            .filter { it.format == MediaFormat.M4A }
            .sortedByDescending { it.averageBitrate }
            .firstOrNull()
        if (preferredM4a != null) return TransferSource(preferredM4a.url.orEmpty())

        val fallback = streams
            .filter { !it.url.isNullOrBlank() }
            .sortedByDescending { it.averageBitrate }
            .firstOrNull()
            ?: error("No encontramos una pista de audio compatible para generar el archivo final.")
        return TransferSource(fallback.url.orEmpty())
    }

    private fun selectDirectVideo(
        streams: List<VideoStream>,
        selection: DownloadSelection,
    ): TransferSource {
        val resolution = selection.targetResolution ?: error("Falta la resolución objetivo del video.")
        val stream = streams
            .filter { !it.isVideoOnly && !it.url.isNullOrBlank() }
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .filter { it.format == MediaFormat.MPEG_4 }
            .sortedByDescending { it.height }
            .firstOrNull { it.resolution == resolution }
            ?: error("La variante MP4 $resolution ya no está disponible para descarga directa.")
        return TransferSource(stream.url.orEmpty())
    }

    private fun selectMuxVideo(
        streams: List<VideoStream>,
        selection: DownloadSelection,
    ): TransferSource {
        val resolution = selection.targetResolution ?: error("Falta la resolución objetivo del video.")
        val stream = streams
            .filter { !it.url.isNullOrBlank() }
            .filter { it.height > 0 }
            .filter { it.format == MediaFormat.MPEG_4 }
            .sortedByDescending { it.height }
            .firstOrNull { it.resolution == resolution }
            ?: error("La variante MP4 $resolution ya no está disponible para armar el mux final.")
        return TransferSource(stream.url.orEmpty())
    }

    private fun StreamInfoItem.toFeedItem(): YouTubeFeedItem? {
        if (streamType == StreamType.AUDIO_STREAM) return null
        if (url.isBlank()) return null
        return YouTubeFeedItem(
            url = url,
            title = name,
            author = uploaderName.orEmpty(),
            thumbnailUrl = thumbnails.lastOrNull()?.url.orEmpty(),
            durationSeconds = duration.coerceAtLeast(0),
            viewCount = viewCount.takeIf { it > 0 },
            publishedText = textualUploadDate,
            description = shortDescription,
        )
    }

    private fun buildPlaybackUrl(streams: List<VideoStream>): String? {
        val progressive = streams
            .filter { !it.isVideoOnly && !it.url.isNullOrBlank() }
            .sortedByDescending { it.height }
            .firstOrNull()
            ?.url
        if (!progressive.isNullOrBlank()) return progressive
        return streams
            .filter { !it.url.isNullOrBlank() }
            .sortedByDescending { it.height }
            .firstOrNull()
            ?.url
    }

    private fun collectFeedPage(
        limit: Int,
        cursor: String?,
        loadInitial: () -> FeedChunk,
        loadMore: (Page) -> ListExtractor.InfoItemsPage<out InfoItem>,
    ): YouTubeFeedPage {
        val uniqueItems = LinkedHashMap<String, YouTubeFeedItem>()
        val resolvedCursor = resolvePageCursor(cursor)
        if (cursor != null && resolvedCursor == null) {
            return YouTubeFeedPage(
                items = emptyList(),
                nextCursor = null,
            )
        }
        var nextPage = resolvedCursor
        if (nextPage == null) {
            val initial = loadInitial()
            appendFeedItems(uniqueItems, initial.items)
            nextPage = initial.nextPage
        }
        while (uniqueItems.size < limit && Page.isValid(nextPage)) {
            val page = loadMore(nextPage!!)
            appendFeedItems(uniqueItems, page.items.filterIsInstance<StreamInfoItem>())
            nextPage = page.nextPage
            if (!page.hasNextPage()) break
        }
        return YouTubeFeedPage(
            items = uniqueItems.values.take(limit),
            nextCursor = when {
                uniqueItems.isEmpty() -> null
                else -> rememberPageCursor(nextPage)
            },
        )
    }

    private fun appendFeedItems(
        target: LinkedHashMap<String, YouTubeFeedItem>,
        items: List<StreamInfoItem>,
    ) {
        items.forEach { item ->
            item.toFeedItem()?.let { target.putIfAbsent(it.url, it) }
        }
    }

    private fun rememberPageCursor(page: Page?): String? {
        if (!Page.isValid(page)) return null
        val safePage = page ?: return null
        return synchronized(pageCursorStore) {
            pageCursorCounter += 1
            val token = "np-page-$pageCursorCounter"
            pageCursorStore[token] = safePage
            while (pageCursorStore.size > 128) {
                val eldestKey = pageCursorStore.entries.firstOrNull()?.key ?: break
                pageCursorStore.remove(eldestKey)
            }
            token
        }
    }

    private fun encodePageCursor(page: Page?): String? {
        if (!Page.isValid(page)) return null
        val safePage = page ?: return null
        val payload = JSONObject().apply {
            safePage.url?.let { put("url", it) }
            safePage.id?.let { put("id", it) }
            put("ids", JSONArray(safePage.ids ?: emptyList<String>()))
            put(
                "cookies",
                JSONObject().apply {
                    safePage.cookies?.forEach { (key, value) -> put(key, value) }
                },
            )
            safePage.body?.takeIf { it.isNotEmpty() }?.let {
                put("body", Base64.getEncoder().encodeToString(it))
            }
        }
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun resolvePageCursor(cursor: String?): Page? {
        if (cursor.isNullOrBlank()) return null
        synchronized(pageCursorStore) {
            pageCursorStore[cursor]?.let { return it }
        }
        return decodePageCursor(cursor)
    }

    private fun decodePageCursor(cursor: String?): Page? {
        if (cursor.isNullOrBlank()) return null
        return runCatching {
            val payload = JSONObject(String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8))
            val ids = buildList {
                val rawIds = payload.optJSONArray("ids") ?: JSONArray()
                for (index in 0 until rawIds.length()) {
                    rawIds.optString(index)
                        .takeIf { it.isNotBlank() }
                        ?.let(::add)
                }
            }
            val cookies = linkedMapOf<String, String>().apply {
                payload.optJSONObject("cookies")?.keys()?.forEach { key ->
                    payload.optJSONObject("cookies")
                        ?.optString(key)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { put(key, it) }
                }
            }
            val body = payload.optString("body")
                .takeIf { it.isNotBlank() }
                ?.let(Base64.getDecoder()::decode)
            Page(
                payload.optString("url").takeIf { it.isNotBlank() },
                payload.optString("id").takeIf { it.isNotBlank() },
                ids,
                cookies,
                body,
            )
        }.getOrNull()
    }

    private data class FeedChunk(
        val items: List<StreamInfoItem>,
        val nextPage: Page?,
    )
}
