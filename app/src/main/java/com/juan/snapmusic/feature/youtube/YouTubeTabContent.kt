package com.juan.snapmusic.feature.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.imageLoader
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.feature.home.DownloadFormatSheet
import com.juan.snapmusic.feature.home.SnapMusicViewModel
import com.juan.snapmusic.feature.home.YouTubeSuggestionsUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.LinkedHashSet

private const val YOUTUBE_THUMBNAIL_PREFETCH_AHEAD = 8
private const val YOUTUBE_THUMBNAIL_PREFETCH_CACHE_SIZE = 160
private const val YOUTUBE_LOAD_MORE_THRESHOLD = 5

@OptIn(ExperimentalMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun YouTubeTabContent(
    viewModel: SnapMusicViewModel,
    player: Player?,
    isActive: Boolean,
    renderSuggestions: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onDownloadQueued: () -> Unit,
) {
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        viewModel.ensureYoutubeLoaded()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        YouTubeWatchPlayerHost(
            viewModel = viewModel,
            player = player,
            onOpenDownloadSheet = viewModel::requestYouTubeDownloadSheet,
        )

        YouTubeCommentHost(viewModel = viewModel)

        if (renderSuggestions) {
            YouTubeSuggestionsHost(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                isActive = isActive,
                onItemDownload = viewModel::prepareYouTubeDownload,
            )
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    YouTubeDownloadSheetHost(
        viewModel = viewModel,
        onDownloadQueued = onDownloadQueued,
    )
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun YouTubeWatchPlayerHost(
    viewModel: SnapMusicViewModel,
    player: Player?,
    onOpenDownloadSheet: () -> Unit,
) {
    val playerState by viewModel.youtubePlaybackPanel.collectAsStateWithLifecycle()

    BackHandler(enabled = playerState.showPlayer && playerState.featured.isReady && !playerState.isFullscreen) {
        viewModel.minimizeYouTubePlayer()
    }

    if (!playerState.showPlayer || playerState.featured.sourceUrl.isBlank()) return

    FeaturedVideoCard(
        featured = playerState.featured,
        player = player,
        isFullscreen = playerState.isFullscreen,
        isDownloadEnabled = playerState.featured.resolvedMedia != null,
        autoplayEnabled = playerState.autoplayEnabled,
        nextUpLabel = playerState.nextUpTitle,
        onDownload = onOpenDownloadSheet,
        onPrevious = viewModel::playPreviousYouTubeItem,
        onNext = { viewModel.playNextYouTubeItem() },
        onBackToFeed = viewModel::minimizeYouTubePlayer,
        onMinimizeVideo = viewModel::minimizeYouTubePlayer,
        onEnterFullscreen = viewModel::enterYouTubeFullscreen,
        onDismissFullscreen = viewModel::exitYouTubeFullscreen,
        onToggleAutoplay = viewModel::toggleYouTubeAutoplay,
        onSwitchQuality = viewModel::switchYouTubePlaybackQuality,
    )
}

@Composable
private fun YouTubeCommentHost(
    @Suppress("UNUSED_PARAMETER") viewModel: SnapMusicViewModel,
) = Unit

@Composable
private fun YouTubeSuggestionsHost(
    modifier: Modifier,
    viewModel: SnapMusicViewModel,
    isActive: Boolean,
    onItemDownload: (YouTubeFeedItem) -> Unit,
) {
    val suggestionsState by viewModel.youtubeSuggestionsScreen.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val visibleItems = suggestionsState.items
    var lastResultsAnchor by remember { mutableStateOf<String?>(null) }
    val resultsAnchor = if (suggestionsState.isPlayerVisible) {
        null
    } else {
        visibleItems.firstOrNull()?.url?.let { firstUrl -> "${suggestionsState.query}|$firstUrl" }
    }
    YouTubeThumbnailPrefetcher(
        items = visibleItems,
        listState = listState,
        isActive = isActive,
    )

    LaunchedEffect(resultsAnchor) {
        val anchor = resultsAnchor ?: return@LaunchedEffect
        if (lastResultsAnchor == null) {
            lastResultsAnchor = anchor
            return@LaunchedEffect
        }
        if (lastResultsAnchor != anchor) {
            lastResultsAnchor = anchor
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(listState, visibleItems.size, suggestionsState.canLoadMore, suggestionsState.isLoadingMore, isActive) {
        if (!isActive || !suggestionsState.canLoadMore || suggestionsState.isLoadingMore || visibleItems.isEmpty()) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex >= visibleItems.lastIndex - YOUTUBE_LOAD_MORE_THRESHOLD
        }
            .distinctUntilChanged()
            .collect { shouldLoadMore ->
                if (shouldLoadMore) {
                    viewModel.loadMoreYoutubeSuggestions()
                }
            }
    }

    YouTubeSuggestionsList(
        modifier = modifier.fillMaxSize(),
        listState = listState,
        suggestionsState = suggestionsState,
        visibleItems = visibleItems,
        onItemClick = viewModel::selectYouTubeItem,
        onItemDownload = onItemDownload,
        onRefresh = viewModel::refreshYoutubeByPull,
        onLoadMore = viewModel::loadMoreYoutubeSuggestions,
    )
}

@Composable
private fun YouTubeThumbnailPrefetcher(
    items: List<YouTubeFeedItem>,
    listState: LazyListState,
    isActive: Boolean,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val thumbnailWidthPx = androidx.compose.runtime.remember(density) { with(density) { 154.dp.roundToPx() } }
    val thumbnailHeightPx = androidx.compose.runtime.remember(density) { with(density) { 88.dp.roundToPx() } }
    val prefetchedUrls = androidx.compose.runtime.remember { LinkedHashSet<String>() }
    LaunchedEffect(items, listState, isActive, thumbnailWidthPx, thumbnailHeightPx) {
        if (!isActive || items.isEmpty()) return@LaunchedEffect
        val imageLoader = context.imageLoader
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: -1
            Triple(
                first,
                minOf(items.lastIndex, last + YOUTUBE_THUMBNAIL_PREFETCH_AHEAD),
                listState.isScrollInProgress,
            )
        }
            .distinctUntilChanged()
            .collect { (firstIndex, lastIndex, isScrolling) ->
                if (isScrolling) return@collect
                if (firstIndex > lastIndex) return@collect
                (firstIndex..lastIndex)
                    .asSequence()
                    .mapNotNull { index -> items.getOrNull(index)?.thumbnailUrl }
                    .filter(String::isNotBlank)
                    .distinct()
                    .filter { thumbnailUrl -> prefetchedUrls.add(thumbnailUrl) }
                    .forEach { thumbnailUrl ->
                        imageLoader.enqueue(
                            buildYouTubeThumbnailRequest(
                                context = context,
                                thumbnailUrl = thumbnailUrl,
                                widthPx = thumbnailWidthPx,
                                heightPx = thumbnailHeightPx,
                            ),
                        )
                    }
                while (prefetchedUrls.size > YOUTUBE_THUMBNAIL_PREFETCH_CACHE_SIZE) {
                    val iterator = prefetchedUrls.iterator()
                    if (!iterator.hasNext()) break
                    iterator.next()
                    iterator.remove()
                }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeDownloadSheetHost(
    viewModel: SnapMusicViewModel,
    onDownloadQueued: () -> Unit,
) {
    val downloadSheetState by viewModel.youtubeDownloadSheet.collectAsStateWithLifecycle()
    val media = downloadSheetState.media ?: return
    if (!downloadSheetState.visible) return

    DownloadFormatSheet(
        media = media,
        isPreparing = downloadSheetState.isPreparing,
        allowedKinds = downloadSheetState.allowedKinds,
        errorMessage = downloadSheetState.errorMessage,
        onDismiss = viewModel::dismissYouTubeDownloadSheet,
        onConfirm = { variantId ->
            viewModel.enqueueYoutubeVariant(variantId)
            onDownloadQueued()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeSuggestionsList(
    modifier: Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState,
    suggestionsState: YouTubeSuggestionsUiState,
    visibleItems: List<YouTubeFeedItem>,
    onItemClick: (YouTubeFeedItem) -> Unit,
    onItemDownload: (YouTubeFeedItem) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val density = LocalDensity.current
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    val refreshThresholdPx = remember(density) { with(density) { 86.dp.toPx() } }
    val refreshingSettledOffsetPx = remember(density) { with(density) { 18.dp.toPx() } }
    var pullOffsetPx by remember { mutableStateOf(0f) }
    val rawPullProgress = (pullOffsetPx / refreshThresholdPx).coerceIn(0f, 1f)
    val indicatorProgress by animateFloatAsState(
        targetValue = if (suggestionsState.isRefreshing) 1f else rawPullProgress,
        animationSpec = tween(durationMillis = 90, easing = FastOutSlowInEasing),
        label = "youtubePullRefreshProgress",
    )
    val indicatorTranslationY by animateFloatAsState(
        targetValue = if (suggestionsState.isRefreshing) refreshingSettledOffsetPx else pullOffsetPx * 0.45f,
        animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
        label = "youtubePullRefreshOffset",
    )
    val isAtTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val pullRefreshConnection = remember(listState, suggestionsState.isRefreshing, refreshThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta >= 0f || pullOffsetPx <= 0f) return Offset.Zero
                val consumed = delta.coerceAtLeast(-pullOffsetPx)
                pullOffsetPx += consumed
                return Offset(x = 0f, y = consumed)
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta <= 0f || !isAtTop || suggestionsState.isRefreshing) return Offset.Zero
                val consumedY = delta * 0.55f
                pullOffsetPx = (pullOffsetPx + consumedY).coerceAtMost(refreshThresholdPx * 1.35f)
                return Offset(x = 0f, y = delta)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffsetPx >= refreshThresholdPx && !suggestionsState.isRefreshing) {
                    currentOnRefresh()
                }
                if (pullOffsetPx > 0f) {
                    pullOffsetPx = 0f
                    return Velocity.Zero
                }
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(suggestionsState.isRefreshing) {
        if (suggestionsState.isRefreshing) pullOffsetPx = 0f
    }

    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullRefreshConnection),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            if (visibleItems.isEmpty() && suggestionsState.isRefreshing) {
                item(key = "youtube_feed_loading", contentType = "youtube_feed_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            suggestionsState.errorMessage?.let { message ->
                item {
                    InlineStatusCard(
                        title = "YouTube no respondió como esperábamos",
                        message = message,
                    )
                }
            }

            if (visibleItems.isNotEmpty()) {
                items(
                    items = visibleItems,
                    key = YouTubeFeedItem::url,
                    contentType = { "youtube_feed_item" },
                ) { item ->
                    YouTubeFeedRow(
                        item = item,
                        onClick = onItemClick,
                        onDownload = onItemDownload,
                    )
                }
            }

            if (visibleItems.isNotEmpty() && suggestionsState.canLoadMore) {
                item(key = "youtube_feed_load_more_trigger", contentType = "youtube_feed_load_more_trigger") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp))
                }
            }

            if (suggestionsState.isLoadingMore) {
                item(key = "youtube_feed_loading_more", contentType = "youtube_feed_loading_more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
    Box(modifier = modifier) {
        listContent()
        if (pullOffsetPx > 0f || suggestionsState.isRefreshing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        alpha = indicatorProgress
                        translationY = indicatorTranslationY
                        val scale = 0.72f + (indicatorProgress * 0.28f)
                        scaleX = scale
                        scaleY = scale
                    }
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                YouTubePullRefreshIndicator(
                    isRefreshing = suggestionsState.isRefreshing,
                    progress = indicatorProgress,
                )
            }
        }
    }
}

@Composable
private fun YouTubePullRefreshIndicator(
    isRefreshing: Boolean,
    progress: Float,
) {
    if (isRefreshing) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
    }
}
