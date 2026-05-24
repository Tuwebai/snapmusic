package com.juan.snapmusic.feature.home

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.juan.snapmusic.core.performance.ReportPerformanceScene
import com.juan.snapmusic.feature.youtube.YouTubeTabContent
import kotlinx.coroutines.delay

private const val HOME_TAB_SEARCH = 0
private const val HOME_TAB_YOUTUBE = 1
private const val HOME_TAB_CONVERT = 2

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboardManager = context.getSystemService(ClipboardManager::class.java)
    val saveableStateHolder = rememberSaveableStateHolder()
    var clipboardInspectionToken by rememberSaveable { mutableStateOf(0L) }
    HomePerformanceTelemetry(viewModel = viewModel, selectedTab = requestedTab)
    HomeYouTubeVisibilityEffects(viewModel = viewModel, selectedTab = requestedTab)

    LaunchedEffect(requestedTab, clipboardInspectionToken) {
        if (requestedTab != HOME_TAB_CONVERT) return@LaunchedEffect
        delay(350L)
        viewModel.inspectClipboardCandidate(readClipboardText(clipboardManager))
    }

    DisposableEffect(lifecycleOwner, clipboardManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                clipboardInspectionToken += 1L
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        saveableStateHolder.SaveableStateProvider(requestedTab) {
            when (requestedTab) {
            HOME_TAB_SEARCH -> HomeSearchLandingRoute(
                viewModel = viewModel,
                padding = padding,
                onSelectSearch = { viewModel.selectHomeSearchTab() },
                onSelectYouTube = { viewModel.selectHomeYouTubeTab() },
                onSelectConvert = { viewModel.selectHomeConvertTab() },
            )

            HOME_TAB_YOUTUBE -> HomeYouTubeLanding(
                padding = padding,
                player = player,
                isActive = true,
                viewModel = viewModel,
                onDownloadQueued = onDownloadQueued,
                onSelectSearch = viewModel::selectHomeSearchTab,
                onSelectYouTube = viewModel::selectHomeYouTubeTab,
                onSelectConvert = viewModel::selectHomeConvertTab,
            )

            else -> HomeConvertLandingRoute(
                viewModel = viewModel,
                padding = padding,
                clipboardManager = clipboardManager,
                onDownloadQueued = onDownloadQueued,
                onSelectSearch = viewModel::selectHomeSearchTab,
                onSelectYouTube = viewModel::selectHomeYouTubeTab,
                onSelectConvert = viewModel::selectHomeConvertTab,
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
    viewModel: SnapMusicViewModel,
    selectedTab: Int,
) {
    val youtubeRouteVisibility by viewModel.youtubeRouteVisibility.collectAsStateWithLifecycle()
    ReportPerformanceScene(
        screen = "home",
        detail = when {
            youtubeRouteVisibility.showPlayer -> "youtube-watch"
            selectedTab == HOME_TAB_YOUTUBE -> "home-youtube-feed"
            selectedTab == HOME_TAB_CONVERT -> "home-convert"
            else -> "home-search"
        },
    )
}

@Composable
private fun HomeYouTubeVisibilityEffects(
    viewModel: SnapMusicViewModel,
    selectedTab: Int,
) {
    val youtubeRouteVisibility by viewModel.youtubeRouteVisibility.collectAsStateWithLifecycle()
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
    viewModel: SnapMusicViewModel,
    onDownloadQueued: () -> Unit,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
    onSelectConvert: () -> Unit,
) {
    val showTopTabs by viewModel.homeYouTubeTabsVisible.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        if (showTopTabs) {
            Box(modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)) {
                HomeSectionTabs(
                    selectedTab = HOME_TAB_YOUTUBE,
                    onSelectSearch = onSelectSearch,
                    onSelectYouTube = onSelectYouTube,
                    onSelectConvert = onSelectConvert,
                )
            }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            YouTubeTabContent(
                viewModel = viewModel,
                player = player,
                isActive = isActive,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
                onDownloadQueued = onDownloadQueued,
            )
        }
    }
}

private fun readClipboardText(clipboardManager: ClipboardManager?): String? {
    val item = clipboardManager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0) ?: return null
    return item.text?.toString() ?: item.coerceToText(null)?.toString()
}

@Composable
private fun HomeSearchLandingRoute(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
    onSelectConvert: () -> Unit,
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
            onSelectConvert = onSelectConvert,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeConvertLandingRoute(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    clipboardManager: ClipboardManager?,
    onDownloadQueued: () -> Unit,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
    onSelectConvert: () -> Unit,
) {
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    var showSheet by rememberSaveable(homeState.resolvedMedia?.sourceUrl) { mutableStateOf(false) }

    LaunchedEffect(homeState.autoOpenFormats, homeState.resolvedMedia?.sourceUrl) {
        if (homeState.autoOpenFormats && homeState.resolvedMedia != null) {
            viewModel.selectHomeConvertTab()
            showSheet = true
            viewModel.consumeAutoOpenFormats()
        }
    }

    HomeConvertLanding(
        padding = padding,
        state = homeState,
        onUrlChange = viewModel::onUrlChange,
        onAnalyze = viewModel::analyze,
        onPaste = {
            viewModel.inspectClipboardCandidate(readClipboardText(clipboardManager))
            viewModel.useClipboardCandidate(analyzeImmediately = false)
        },
        onPasteAndAnalyze = {
            viewModel.inspectClipboardCandidate(readClipboardText(clipboardManager))
            viewModel.useClipboardCandidate(analyzeImmediately = true)
        },
        onOpenFormats = { showSheet = true },
        onQuickMp3 = {
            if (viewModel.enqueueHomePresetMp3320()) onDownloadQueued()
        },
        onQuickM4a = {
            if (viewModel.enqueueHomePresetM4a()) onDownloadQueued()
        },
        onQuickMp4 = {
            if (viewModel.enqueueHomePresetMp4720()) onDownloadQueued()
        },
        onUseClipboard = { viewModel.useClipboardCandidate(analyzeImmediately = false) },
        onSelectSearch = onSelectSearch,
        onSelectYouTube = onSelectYouTube,
        onSelectConvert = onSelectConvert,
    )

    homeState.resolvedMedia?.let { media ->
        if (showSheet) {
            DownloadFormatSheet(
                media = media,
                onDismiss = { showSheet = false },
                onConfirm = { variantId ->
                    showSheet = false
                    viewModel.enqueueVariant(variantId)
                    onDownloadQueued()
                },
            )
        }
    }
}
