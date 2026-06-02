package com.juan.snapmusic.data.extractor

import android.net.Uri
import com.juan.snapmusic.core.model.DownloadExecutionPlan
import com.juan.snapmusic.core.model.DownloadSelection
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.model.SeekPreviewFrameset
import com.juan.snapmusic.core.model.YouTubeFeedPage
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.platform.YouTubePlaybackHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
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
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.Base64
import java.util.LinkedHashMap
import kotlin.math.abs

class NewPipeStreamResolverRepository(
    private val downloader: OkHttpNewPipeDownloader,
) : StreamResolverRepository {
    private companion object {
        private const val FEED_THUMBNAIL_TARGET_WIDTH = 320
        private const val FEED_THUMBNAIL_TARGET_HEIGHT = 180

        @Volatile
        private var newPipeInitialized = false

        private fun ensureInitialized(downloader: OkHttpNewPipeDownloader) {
            if (!newPipeInitialized) {
                synchronized(this) {
                    if (!newPipeInitialized) {
                        NewPipe.init(downloader, Localization("es", "AR"), ContentCountry("AR"))
                        newPipeInitialized = true
                    }
                }
            }
        }
    }

    private val pageCursorStore = linkedMapOf<String, Page>()
    private var pageCursorCounter = 0L
    private val transferHeaders = YouTubePlaybackHeaders.DEFAULT

    override suspend fun resolve(url: String): ResolvedMedia = withContext(Dispatchers.IO) {
        ensureInitialized(downloader)
        val info = StreamInfo.getInfo(url)
        val audioCandidates = info.audioStreams.map(::toAudioCandidate) +
            info.videoStreams.mapNotNull(::toProgressiveAudioCandidate)
        val progressiveCandidates = info.videoStreams.map(::toVideoCandidate)
        val muxCandidates = info.videoOnlyStreams.map(::toVideoCandidate)
        ResolvedMedia(
            sourceUrl = url,
            title = info.name,
            author = info.uploaderName.orEmpty(),
            durationSeconds = info.duration,
            thumbnailUrl = selectFeedThumbnailUrl(info.thumbnails),
            playbackUrl = buildPlaybackUrl(info.videoStreams),
            adaptivePlaybackUrl = info.dashMpdUrl ?: info.hlsUrl,
            audioVariants = DownloadSourcePlanner.buildAudioVariants(audioCandidates),
            videoVariants = DownloadSourcePlanner.buildVideoVariants(
                progressiveCandidates = progressiveCandidates,
                muxCandidates = muxCandidates,
                audioCandidates = audioCandidates,
            ),
            seekPreviewFramesets = info.previewFrames.mapNotNull(::toSeekPreviewFrameset),
        )
    }

    override suspend fun resolveDownloadPlan(url: String, selection: DownloadSelection): DownloadExecutionPlan = withContext(Dispatchers.IO) {
        ensureInitialized(downloader)
        val info = StreamInfo.getInfo(url)
        DownloadSourcePlanner.resolveDownloadPlan(
            selection = selection,
            audioCandidates = info.audioStreams.map(::toAudioCandidate) +
                info.videoStreams.mapNotNull(::toProgressiveAudioCandidate),
            progressiveCandidates = info.videoStreams.map(::toVideoCandidate),
            muxCandidates = info.videoOnlyStreams.map(::toVideoCandidate),
        )
    }

    override suspend fun loadTrendingPage(limit: Int, cursor: String?): YouTubeFeedPage = withContext(Dispatchers.IO) {
        ensureInitialized(downloader)
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
        ensureInitialized(downloader)
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
        ensureInitialized(downloader)
        StreamInfo.getInfo(url)
            .relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toFeedItem() }
            .take(limit)
    }

    override suspend fun searchSuggestions(query: String, limit: Int): List<String> = withContext(Dispatchers.IO) {
        ensureInitialized(downloader)
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

    private fun toAudioCandidate(stream: AudioStream): AudioSourceCandidate = AudioSourceCandidate(
        id = stream.id.toString(),
        url = stream.url.orEmpty(),
        bitrateKbps = stream.averageBitrate.takeIf { it > 0 },
        sourceContainerHint = stream.format?.name ?: "UNKNOWN",
        isDirectM4a = stream.format == MediaFormat.M4A,
        isAudioOnly = true,
        audioTrackType = stream.audioTrackType?.name,
        audioTrackName = stream.audioTrackName,
        headers = transferHeaders,
    )

    private fun toProgressiveAudioCandidate(stream: VideoStream): AudioSourceCandidate? {
        if (stream.isVideoOnly || stream.url.isNullOrBlank()) return null
        return AudioSourceCandidate(
            id = "progressive-${stream.id}",
            url = stream.url.orEmpty(),
            bitrateKbps = null,
            sourceContainerHint = stream.format?.name ?: "UNKNOWN",
            isDirectM4a = false,
            isAudioOnly = false,
            headers = transferHeaders,
        )
    }

    private fun toVideoCandidate(stream: VideoStream): VideoSourceCandidate = VideoSourceCandidate(
        id = stream.id.toString(),
        url = stream.url.orEmpty(),
        resolution = stream.resolution,
        height = stream.height.takeIf { it > 0 },
        sourceContainerHint = stream.format?.name ?: "UNKNOWN",
        isProgressiveMp4 = !stream.isVideoOnly &&
            stream.format == MediaFormat.MPEG_4 &&
            stream.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP,
        isMuxableMp4 = stream.format == MediaFormat.MPEG_4 || stream.format == MediaFormat.WEBM,
        isPlaybackMuxable = stream.isVideoOnly &&
            (stream.format == MediaFormat.MPEG_4 || stream.format == MediaFormat.WEBM),
        headers = transferHeaders,
    )

    private fun StreamInfoItem.toFeedItem(): YouTubeFeedItem? {
        if (streamType == StreamType.AUDIO_STREAM) return null
        if (url.isBlank()) return null
        return YouTubeFeedItem(
            url = url,
            title = name,
            author = uploaderName.orEmpty(),
            thumbnailUrl = selectFeedThumbnailUrl(thumbnails),
            durationSeconds = duration.coerceAtLeast(0),
            viewCount = viewCount.takeIf { it > 0 },
            publishedText = textualUploadDate,
            description = shortDescription,
        )
    }

    private fun toSeekPreviewFrameset(frameset: Frameset): SeekPreviewFrameset? {
        val urls = frameset.urls.filter { it.isNotBlank() }
        if (urls.isEmpty()) return null
        return SeekPreviewFrameset(
            urls = urls,
            frameWidth = frameset.frameWidth,
            frameHeight = frameset.frameHeight,
            totalCount = frameset.totalCount,
            durationPerFrameMs = frameset.durationPerFrame,
            framesPerPageX = frameset.framesPerPageX,
            framesPerPageY = frameset.framesPerPageY,
        )
    }

    private fun selectFeedThumbnailUrl(thumbnails: List<Image>): String {
        return thumbnails
            .asSequence()
            .filter { thumbnail -> thumbnail.url.isNotBlank() }
            .minWithOrNull(
                compareBy<Image> { thumbnail -> thumbnail.feedThumbnailScore() }
                    .thenBy { thumbnail -> thumbnail.safeArea() },
            )
            ?.url
            .orEmpty()
    }

    private fun Image.feedThumbnailScore(): Int {
        val width = width.takeIf { it > 0 } ?: FEED_THUMBNAIL_TARGET_WIDTH
        val height = height.takeIf { it > 0 } ?: FEED_THUMBNAIL_TARGET_HEIGHT
        val targetArea = FEED_THUMBNAIL_TARGET_WIDTH * FEED_THUMBNAIL_TARGET_HEIGHT
        val areaDelta = abs((width * height) - targetArea)
        val ratioDelta = abs((width * FEED_THUMBNAIL_TARGET_HEIGHT) - (height * FEED_THUMBNAIL_TARGET_WIDTH))
        val undersizePenalty = if (width < FEED_THUMBNAIL_TARGET_WIDTH || height < FEED_THUMBNAIL_TARGET_HEIGHT) {
            targetArea
        } else {
            0
        }
        return areaDelta + (ratioDelta / 10) + undersizePenalty
    }

    private fun Image.safeArea(): Int {
        val width = width.takeIf { it > 0 } ?: FEED_THUMBNAIL_TARGET_WIDTH
        val height = height.takeIf { it > 0 } ?: FEED_THUMBNAIL_TARGET_HEIGHT
        return width * height
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
