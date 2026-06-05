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
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Frameset
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.LinkedHashMap
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.math.abs


internal fun buildGeneratedDashDataUri(
    videoStreams: List<VideoStream>,
    audioStreams: List<AudioStream>,
    durationSeconds: Long,
): String? {
    val videoManifests = selectDashVideoStreams(videoStreams).mapNotNull { stream ->
        val streamUrl = stream.url ?: return@mapNotNull null
        val itagItem = stream.itagItem ?: return@mapNotNull null
        createDashManifest(
            url = streamUrl,
            itagItem = itagItem,
            durationSeconds = durationSeconds,
            deliveryMethod = stream.deliveryMethod,
        )
    }
    if (videoManifests.isEmpty()) return null
    val audioStream = selectDashAudioStream(audioStreams) ?: return null
    val audioUrl = audioStream.url ?: return null
    val audioItag = audioStream.itagItem ?: return null
    return runCatching {
        val audioManifest = createDashManifest(
            url = audioUrl,
            itagItem = audioItag,
            durationSeconds = durationSeconds,
            deliveryMethod = audioStream.deliveryMethod,
        ) ?: return@runCatching null
        val manifest = combineDashManifests(videoManifests, audioManifest) ?: return@runCatching null
        val encoded = Base64.getEncoder()
            .encodeToString(manifest.toByteArray(StandardCharsets.UTF_8))
        "data:application/dash+xml;base64,$encoded"
    }.getOrNull()
}

internal fun createDashManifest(
    url: String,
    itagItem: ItagItem,
    durationSeconds: Long,
    deliveryMethod: DeliveryMethod?,
): String? {
    val creators = if (deliveryMethod == DeliveryMethod.DASH) {
        listOf(
            { YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(url, itagItem, durationSeconds) },
            { YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itagItem, durationSeconds) },
        )
    } else {
        listOf(
            { YoutubeProgressiveDashManifestCreator.fromProgressiveStreamingUrl(url, itagItem, durationSeconds) },
            { YoutubeOtfDashManifestCreator.fromOtfStreamingUrl(url, itagItem, durationSeconds) },
        )
    }
    return creators.firstNotNullOfOrNull { creator -> runCatching { creator() }.getOrNull() }
}

internal fun selectDashVideoStreams(streams: List<VideoStream>): List<VideoStream> {
    val candidates = streams
        .filter { it.isVideoOnly && !it.url.isNullOrBlank() && it.itagItem != null && it.height > 0 }
    val hardwareSafe = candidates.filter { it.format == MediaFormat.MPEG_4 }
    return (hardwareSafe.ifEmpty { candidates })
        .groupBy { it.height }
        .values
        .mapNotNull { group -> group.maxByOrNull { it.bitrate } }
        .sortedBy { it.height }
}

internal fun selectDashAudioStream(streams: List<AudioStream>): AudioStream? {
    val candidates = streams.filter { !it.url.isNullOrBlank() && it.itagItem != null }
    return candidates
        .filter { it.format == MediaFormat.M4A }
        .ifEmpty { candidates }
        .maxByOrNull { it.averageBitrate }
}

internal fun combineDashManifests(
    videoManifests: List<String>,
    audioManifest: String,
): String? {
    val baseDocument = parseDashDocument(videoManifests.first())
    val period = baseDocument.firstElement("Period") ?: return null
    period.removeChildren("AdaptationSet")
    val videoSet = baseDocument.importNode(
        parseDashDocument(videoManifests.first()).firstElement("AdaptationSet") ?: return null,
        true,
    ) as Element
    videoManifests.drop(1).forEach { manifest ->
        val sourceSet = parseDashDocument(manifest).firstElement("AdaptationSet") ?: return@forEach
        sourceSet.childElements("Representation").forEach { representation ->
            videoSet.appendChild(baseDocument.importNode(representation, true))
        }
    }
    period.appendChild(videoSet)
    val audioSet = baseDocument.importNode(
        parseDashDocument(audioManifest).firstElement("AdaptationSet") ?: return null,
        true,
    )
    period.appendChild(audioSet)
    return serializeDashDocument(baseDocument)
}

internal fun parseDashDocument(manifest: String): Document {
    return DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(ByteArrayInputStream(manifest.toByteArray(StandardCharsets.UTF_8)))
}

internal fun Document.firstElement(name: String): Element? {
    return getElementsByTagName(name).item(0) as? Element
}

internal fun Element.childElements(name: String): List<Element> {
    val nodes = getElementsByTagName(name)
    return (0 until nodes.length).mapNotNull { index -> nodes.item(index) as? Element }
}

internal fun Element.removeChildren(name: String) {
    val nodes = getElementsByTagName(name)
    for (index in nodes.length - 1 downTo 0) {
        removeChild(nodes.item(index))
    }
}

internal fun serializeDashDocument(document: Document): String {
    val writer = StringWriter()
    TransformerFactory.newInstance().newTransformer().apply {
        setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        setOutputProperty(OutputKeys.ENCODING, "UTF-8")
    }.transform(DOMSource(document), StreamResult(writer))
    return writer.toString()
}
