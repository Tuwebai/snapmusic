package com.juan.snapmusic.feature.youtube

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.feature.home.DownloadFormatSheet
import com.juan.snapmusic.feature.home.SnapMusicViewModel
import com.juan.snapmusic.feature.home.YouTubeSuggestionsUiState
import kotlinx.coroutines.flow.distinctUntilChanged

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

    BackHandler(enabled = playerState.showPlayer && playerState.featured.isReady) {
        viewModel.minimizeYouTubePlayer()
    }

    if (!playerState.showPlayer || playerState.featured.sourceUrl.isBlank()) return

    FeaturedVideoCard(
        featured = playerState.featured,
        player = player,
        isDownloadEnabled = playerState.featured.resolvedMedia != null,
        autoplayEnabled = playerState.autoplayEnabled,
        nextUpLabel = playerState.nextUpTitle,
        onDownload = onOpenDownloadSheet,
        onPrevious = viewModel::playPreviousYouTubeItem,
        onNext = { viewModel.playNextYouTubeItem() },
        onBackToFeed = viewModel::minimizeYouTubePlayer,
        onMinimizeVideo = viewModel::minimizeYouTubePlayer,
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

    LaunchedEffect(listState, visibleItems.size, suggestionsState.canLoadMore, suggestionsState.isLoadingMore) {
        if (!isActive || !suggestionsState.canLoadMore || suggestionsState.isLoadingMore || visibleItems.isEmpty()) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisibleIndex to listState.isScrollInProgress
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, isScrolling) ->
                if (!isScrolling && lastVisibleIndex >= visibleItems.lastIndex - 3) {
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
    val listContent: @Composable () -> Unit = {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        onClick = { onItemClick(item) },
                        onDownload = { onItemDownload(item) },
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
    if (suggestionsState.isPlayerVisible) {
        Box(modifier = modifier) {
            listContent()
        }
    } else {
        Box(modifier = modifier) {
            listContent()
        }
    }
}
