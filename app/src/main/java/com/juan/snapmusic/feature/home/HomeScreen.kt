package com.juan.snapmusic.feature.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.juan.snapmusic.core.performance.ReportPerformanceScene
import com.juan.snapmusic.feature.youtube.YouTubeTabContent

private const val HOME_TAB_SEARCH = 0
private const val HOME_TAB_YOUTUBE = 1

@OptIn(ExperimentalMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun HomeScreen(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    player: Player?,
    onDownloadQueued: () -> Unit,
) {
    val requestedTab by viewModel.homeSelectedTab.collectAsStateWithLifecycle()
    val incomingShareSelection by viewModel.incomingShareSelectionState.collectAsStateWithLifecycle()
    val youtubeRouteVisibility by viewModel.youtubeRouteVisibility.collectAsStateWithLifecycle()
    val saveableStateHolder = rememberSaveableStateHolder()
    HomePerformanceTelemetry(
        selectedTab = requestedTab,
        youtubeRouteVisibility = youtubeRouteVisibility,
    )
    HomeYouTubeVisibilityEffects(
        viewModel = viewModel,
        selectedTab = requestedTab,
        youtubeRouteVisibility = youtubeRouteVisibility,
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        saveableStateHolder.SaveableStateProvider(requestedTab) {
            when (requestedTab) {
                HOME_TAB_SEARCH -> HomeSearchLandingRoute(
                    viewModel = viewModel,
                    padding = padding,
                    onSelectSearch = viewModel::selectHomeSearchTab,
                    onSelectYouTube = viewModel::selectHomeYouTubeTab,
                )

                HOME_TAB_YOUTUBE -> HomeYouTubeLanding(
                    padding = padding,
                    player = player,
                    isActive = true,
                    renderSuggestions = true,
                    viewModel = viewModel,
                    onDownloadQueued = onDownloadQueued,
                    onSelectSearch = viewModel::selectHomeSearchTab,
                    onSelectYouTube = viewModel::selectHomeYouTubeTab,
                )
            }
        }
    }

    if (incomingShareSelection.visible) {
        IncomingShareSelectionSheet(
            items = incomingShareSelection.items,
            onDismiss = viewModel::dismissIncomingShareSelection,
            onSelect = viewModel::selectIncomingShareItem,
        )
    }
}

@Composable
private fun HomePerformanceTelemetry(
    selectedTab: Int,
    youtubeRouteVisibility: YouTubeRouteVisibilityState,
) {
    ReportPerformanceScene(
        screen = "home",
        detail = when {
            youtubeRouteVisibility.showPlayer -> "youtube-watch"
            selectedTab == HOME_TAB_YOUTUBE -> "home-youtube-feed"
            else -> "home-search"
        },
    )
}

@Composable
private fun HomeYouTubeVisibilityEffects(
    viewModel: SnapMusicViewModel,
    selectedTab: Int,
    youtubeRouteVisibility: YouTubeRouteVisibilityState,
) {
    LaunchedEffect(
        selectedTab,
        youtubeRouteVisibility.showPlayer,
        youtubeRouteVisibility.isReady,
    ) {
        if (!youtubeRouteVisibility.isReady) return@LaunchedEffect
        if (selectedTab != HOME_TAB_YOUTUBE && youtubeRouteVisibility.showPlayer) {
            viewModel.minimizeYouTubePlayer()
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun HomeYouTubeLanding(
    padding: PaddingValues,
    player: Player?,
    isActive: Boolean,
    renderSuggestions: Boolean,
    viewModel: SnapMusicViewModel,
    onDownloadQueued: () -> Unit,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
) {
    val showTopTabs by viewModel.homeYouTubeTabsVisible.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        if (showTopTabs) {
            Box(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)) {
                HomeSectionTabs(
                    selectedTab = HOME_TAB_YOUTUBE,
                    onSelectSearch = onSelectSearch,
                    onSelectYouTube = onSelectYouTube,
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            YouTubeTabContent(
                viewModel = viewModel,
                player = player,
                isActive = isActive,
                renderSuggestions = renderSuggestions,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                onDownloadQueued = onDownloadQueued,
            )
        }
    }
}

@Composable
private fun HomeSearchLandingRoute(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
) {
    val homeSearch by viewModel.homeSearch.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        HomeSearchLanding(
            padding = padding,
            query = homeSearch.query,
            onSearch = viewModel::openDownloadSearchOverlay,
            onActivateSearch = viewModel::openDownloadSearchOverlay,
            onSelectSearch = onSelectSearch,
            onSelectYouTube = onSelectYouTube,
        )

        if (homeSearch.isOverlayVisible) {
            HomeSearchOverlayHost(viewModel = viewModel)
        }
    }
}

@Composable
private fun HomeSearchOverlayHost(
    viewModel: SnapMusicViewModel,
) {
    val searchSuggestions by viewModel.homeSearchSuggestions.collectAsStateWithLifecycle()
    HomeSearchSuggestionOverlay(
        state = searchSuggestions,
        onQueryChange = viewModel::onDownloadSearchQueryChange,
        onSubmit = viewModel::submitDownloadSearch,
        onSuggestionSelected = viewModel::selectDownloadSearchSuggestion,
        onPopularSelected = viewModel::selectPopularDownloadSearch,
        onBack = viewModel::closeDownloadSearchOverlay,
        onClear = viewModel::clearDownloadSearchQuery,
    )
}
