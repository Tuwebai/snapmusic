package com.juan.snapmusic.core.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.util.UUID

enum class MediaKind {
    AUDIO,
    VIDEO,
}

enum class ContainerFormat {
    MP3,
    M4A,
    MP4,
}

enum class QueueStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR,
    CANCELLED,
}

@Immutable
data class MediaVariant(
    val id: String,
    val label: String,
    val kind: MediaKind,
    val container: ContainerFormat,
    val bitrateKbps: Int? = null,
    val resolution: String? = null,
    val directUrl: String,
    val secondaryUrl: String? = null,
    val requiresTranscode: Boolean = false,
    val requiresMux: Boolean = false,
    val isSyntheticOutput: Boolean = false,
    val sourceId: String? = null,
    val sourceContainerHint: String? = null,
    val sourceBitrateKbps: Int? = null,
    val sourceHeight: Int? = null,
    val allowMuxFallback: Boolean = false,
    val allowTranscodeFallback: Boolean = false,
)

@Immutable
data class ResolvedMedia(
    val sourceUrl: String,
    val title: String,
    val author: String,
    val durationSeconds: Long,
    val thumbnailUrl: String,
    val playbackUrl: String? = null,
    val adaptivePlaybackUrl: String? = null,
    val audioVariants: List<MediaVariant>,
    val videoVariants: List<MediaVariant>,
    val seekPreviewFramesets: List<SeekPreviewFrameset> = emptyList(),
)

@Immutable
data class SeekPreviewFrameset(
    val urls: List<String>,
    val frameWidth: Int,
    val frameHeight: Int,
    val totalCount: Int,
    val durationPerFrameMs: Int,
    val framesPerPageX: Int,
    val framesPerPageY: Int,
) {
    fun frameAt(positionMs: Long): SeekPreviewFrame? {
        if (
            urls.isEmpty() ||
            frameWidth <= 0 ||
            frameHeight <= 0 ||
            totalCount <= 0 ||
            durationPerFrameMs <= 0 ||
            framesPerPageX <= 0 ||
            framesPerPageY <= 0
        ) {
            return null
        }
        val framesPerPage = (framesPerPageX * framesPerPageY).coerceAtLeast(1)
        val frameIndex = (positionMs.coerceAtLeast(0L) / durationPerFrameMs.toLong())
            .toInt()
            .coerceIn(0, totalCount - 1)
        val pageIndex = (frameIndex / framesPerPage).coerceIn(0, urls.lastIndex)
        val frameInPage = frameIndex % framesPerPage
        val column = frameInPage % framesPerPageX
        val row = frameInPage / framesPerPageX
        val left = column * frameWidth
        val top = row * frameHeight
        return SeekPreviewFrame(
            imageUrl = urls[pageIndex],
            left = left,
            top = top,
            right = left + frameWidth,
            bottom = top + frameHeight,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            pageWidth = framesPerPageX * frameWidth,
            pageHeight = framesPerPageY * frameHeight,
            positionMs = frameIndex.toLong() * durationPerFrameMs.toLong(),
        )
    }
}

@Immutable
data class SeekPreviewFrame(
    val imageUrl: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val frameWidth: Int,
    val frameHeight: Int,
    val pageWidth: Int,
    val pageHeight: Int,
    val positionMs: Long,
)

@Immutable
data class ConversionRequest(
    val id: UUID = UUID.randomUUID(),
    val sourceUrl: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val selectedVariant: MediaVariant,
    val downloadSelection: DownloadSelection = selectedVariant.toDownloadSelection(),
    val destinationLabel: String,
    val destinationTreeUri: String? = null,
)

@Immutable
data class QueueEntry(
    val id: String,
    val title: String,
    val author: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val variantLabel: String,
    val container: ContainerFormat,
    val status: QueueStatus,
    val progress: Int,
    val outputUri: String?,
    val createdAt: Long,
    val errorMessage: String?,
)

@Immutable
data class HistoryEntry(
    val id: String,
    val title: String,
    val author: String,
    val sourceUrl: String,
    val thumbnailUrl: String,
    val outputUri: String,
    val format: ContainerFormat,
    val qualityLabel: String,
    val createdAt: Long,
)

@Immutable
data class DownloadStatus(
    val queueId: String,
    val status: QueueStatus,
    val progress: Int,
    val outputUri: Uri? = null,
    val message: String? = null,
)

@Immutable
data class PreviewState(
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUrl: String = "",
    val fileUri: String? = null,
    val isReady: Boolean = false,
)

@Immutable
data class LocalMediaItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val contentUri: String,
    val fileName: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val dateAdded: Long = 0L,
)

@Immutable
data class YouTubeFeedItem(
    val url: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val viewCount: Long? = null,
    val publishedText: String? = null,
    val description: String? = null,
)

enum class MusicContentType {
    TRACK,
    MIX,
    LIVE,
    LYRICS,
    SESSION,
    REMIX,
    UNKNOWN,
}

enum class MusicSignalType {
    PLAY_START,
    PLAY_30S,
    PLAY_70_PERCENT,
    PLAY_COMPLETE,
    REPLAY,
    DOWNLOAD,
    SEARCH_QUERY,
    SKIP_FAST,
    HIDE,
}

@Immutable
data class MusicClassification(
    val isMusic: Boolean = false,
    val score: Int = 0,
    val artistKey: String = "",
    val channelKey: String = "",
    val tags: List<String> = emptyList(),
    val contentType: MusicContentType = MusicContentType.UNKNOWN,
)

@Immutable
data class MusicAffinitySignal(
    val type: MusicSignalType,
    val timestampMs: Long,
    val sourceUrl: String? = null,
    val title: String = "",
    val author: String = "",
    val query: String? = null,
    val tags: List<String> = emptyList(),
    val artistKey: String = "",
    val channelKey: String = "",
    val contentType: MusicContentType = MusicContentType.UNKNOWN,
)

@Immutable
data class MusicInterestProfile(
    val artistScores: Map<String, Double> = emptyMap(),
    val tagScores: Map<String, Double> = emptyMap(),
    val contentTypeScores: Map<MusicContentType, Double> = emptyMap(),
    val searchScores: Map<String, Double> = emptyMap(),
    val recentUrls: Set<String> = emptySet(),
    val recentArtists: Set<String> = emptySet(),
)

@Immutable
data class FeedImpression(
    val url: String,
    val timestampMs: Long,
)

@Immutable
data class RelatedMusicRecommendation(
    val item: YouTubeFeedItem,
    val score: Double,
    val classification: MusicClassification,
)

@Immutable
data class MusicHomeFeedState(
    val sessionSeed: Long = 0L,
    val items: List<YouTubeFeedItem> = emptyList(),
    val nextCursor: String? = null,
)

@Immutable
data class YouTubeFeedPage(
    val items: List<YouTubeFeedItem> = emptyList(),
    val nextCursor: String? = null,
)

@Immutable
data class YouTubeFeaturedVideo(
    val sourceUrl: String = "",
    val title: String = "",
    val author: String = "",
    val thumbnailUrl: String = "",
    val playbackUrl: String? = null,
    val adaptivePlaybackUrl: String? = null,
    val selectedVideoQualityId: String = "auto",
    val availablePlaybackHeights: List<Int> = emptyList(),
    val actualVideoHeight: Int? = null,
    val actualPlaybackLabel: String? = null,
    val durationSeconds: Long = 0,
    val publishedText: String? = null,
    val description: String? = null,
    val resolvedMedia: ResolvedMedia? = null,
    val isReady: Boolean = false,
)

enum class PlaybackContinuationMode {
    STOP_AT_END,
    PLAY_NEXT,
    LOOP_FEED,
}

enum class YouTubeQueueOrigin {
    HOME_FEED,
    SEARCH_RESULTS,
    PRESET,
    RESTORED_SESSION,
}

enum class YouTubeAdvanceReason {
    USER_NEXT,
    AUTO_ENDED,
    ERROR_FALLBACK,
}

@Immutable
data class YouTubePlaybackSnapshot(
    val queue: List<YouTubeFeedItem> = emptyList(),
    val currentQueueIndex: Int = 0,
    val query: String = "",
    val autoplayEnabled: Boolean = true,
    val continuationMode: PlaybackContinuationMode = PlaybackContinuationMode.PLAY_NEXT,
    val lastPositionMs: Long = 0L,
    val origin: YouTubeQueueOrigin = YouTubeQueueOrigin.HOME_FEED,
    val showMiniPlayer: Boolean = true,
)

@Immutable
data class PreviewPlaybackSnapshot(
    val queue: List<PreviewPlaybackQueueItem> = emptyList(),
    val currentQueueIndex: Int = 0,
    val lastPositionMs: Long = 0L,
    val showMiniPlayer: Boolean = true,
)

@Immutable
data class YouTubeUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshingVideo: Boolean = false,
    val showPlayer: Boolean = false,
    val showMiniPlayer: Boolean = false,
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val items: List<YouTubeFeedItem> = emptyList(),
    val nextCursor: String? = null,
    val hasMoreSearchResults: Boolean = false,
    val watchNextItems: List<YouTubeFeedItem> = emptyList(),
    val playbackQueue: List<YouTubeFeedItem> = emptyList(),
    val currentQueueIndex: Int = -1,
    val autoplayEnabled: Boolean = true,
    val continuationMode: PlaybackContinuationMode = PlaybackContinuationMode.PLAY_NEXT,
    val nextUpItem: YouTubeFeedItem? = null,
    val canLoadMoreWatchNext: Boolean = false,
    val preloadedNextFeatured: YouTubeFeaturedVideo? = null,
    val pendingTransition: Boolean = false,
    val currentPositionMs: Long = 0L,
    val playbackSeekRequestId: Long = 0L,
    val shouldAutoPlayCurrent: Boolean = false,
    val queueOrigin: YouTubeQueueOrigin = YouTubeQueueOrigin.HOME_FEED,
    val compactMiniPlayer: Boolean = false,
    val openDownloadSheet: Boolean = false,
    val errorMessage: String? = null,
)

@Immutable
data class YouTubePlaybackRenderState(
    val featured: YouTubeFeaturedVideo = YouTubeFeaturedVideo(),
    val preloadedNextFeatured: YouTubeFeaturedVideo? = null,
    val currentPositionMs: Long = 0L,
    val shouldAutoPlayCurrent: Boolean = false,
)

@Immutable
data class PreviewPlaybackRenderState(
    val preview: PreviewState = PreviewState(),
    val autoPlayRequestId: Long = 0L,
    val playlist: List<PreviewPlaybackQueueItem> = emptyList(),
    val resumePositionMs: Long = 0L,
)

@Immutable
data class PreviewPlaybackQueueItem(
    val title: String = "",
    val subtitle: String = "",
    val thumbnailUrl: String = "",
    val fileUri: String = "",
)

@Immutable
data class DownloadBadgeState(
    val activeCount: Int = 0,
)

data class FavoriteDestination(
    val label: String,
    val treeUri: String? = null,
)

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class UserPreferences(
    val defaultAudioFormat: ContainerFormat = ContainerFormat.M4A,
    val defaultAudioQuality: String = "320",
    val defaultVideoQuality: String = "720p",
    val defaultDestinationLabel: String = "Downloads/SnapMusic",
    val customTreeUri: String? = null,
    val favoriteDestinations: List<FavoriteDestination> = emptyList(),
    val downloadTasksWifi: Int = 4,
    val downloadTasksMobile: Int = 2,
    val downloadSpeedLimitLabel: String = "Sin límites",
    val allowMobileDataDownloads: Boolean = true,
    val notifyDownloadProgress: Boolean = true,
    val notifyDownloadCompleted: Boolean = true,
    val notifyRecommendedContent: Boolean = true,
    val notifyToolUpdates: Boolean = true,
    val notifyToolbarAccess: Boolean = true,
    val youtubeAutoplayEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val previewVolume: Float = 0.9f,
)
