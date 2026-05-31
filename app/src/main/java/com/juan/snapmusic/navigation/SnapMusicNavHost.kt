package com.juan.snapmusic.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem as MaterialNavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.juan.snapmusic.R
import com.juan.snapmusic.MainActivity
import com.juan.snapmusic.feature.home.BottomBarUiState
import com.juan.snapmusic.feature.home.NavHostPlaybackState
import com.juan.snapmusic.feature.home.HomeScreen
import com.juan.snapmusic.feature.home.PlaybackNotificationTarget
import com.juan.snapmusic.feature.home.SnapMusicViewModel
import com.juan.snapmusic.feature.preview.PreviewMiniPlayer
import com.juan.snapmusic.feature.preview.PreviewPictureInPictureSurface
import com.juan.snapmusic.feature.preview.PreviewScreen
import com.juan.snapmusic.feature.preview.isPreviewVideoMedia
import com.juan.snapmusic.feature.preview.rememberPreviewPlayer
import com.juan.snapmusic.feature.settings.SettingsScreen
import com.juan.snapmusic.feature.youtube.PictureInPicturePlayerSurface
import com.juan.snapmusic.feature.youtube.YouTubeMiniPlayer
import com.juan.snapmusic.feature.youtube.YouTubeTabContent
import com.juan.snapmusic.feature.youtube.rememberYouTubePlayer
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.media3.common.util.UnstableApi
@Composable
fun SnapMusicNavHost(
    viewModel: SnapMusicViewModel,
    notificationRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {},
    isInPictureInPictureMode: Boolean = false,
) {
    val navController = rememberNavController()
    val items = listOf(
        SnapMusicDestination.Home,
        SnapMusicDestination.Preview,
        SnapMusicDestination.Settings,
    )
    val mountYouTubePlayer by viewModel.youtubePlayerMountEnabled.collectAsStateWithLifecycle()
    val mountPreviewPlayer by viewModel.previewPlayerMountEnabled.collectAsStateWithLifecycle()
    val navHostPlaybackState by viewModel.navHostPlaybackState.collectAsStateWithLifecycle()
    val youTubePlayer = rememberManagedYouTubePlayer(viewModel, enabled = mountYouTubePlayer)
    val previewPlayer = rememberManagedPreviewPlayer(viewModel, enabled = mountPreviewPlayer)
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    fun navigateTo(route: String) {
        if (currentRoute == route) return
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NotificationRouteEffectHost(
        viewModel = viewModel,
        state = navHostPlaybackState,
        currentRoute = currentRoute,
        notificationRoute = notificationRoute,
        onNotificationRouteConsumed = onNotificationRouteConsumed,
        onNavigate = ::navigateTo,
    )

    NavHostVisibilityEffects(
        viewModel = viewModel,
        state = navHostPlaybackState,
        currentRoute = currentRoute,
        isInPictureInPictureMode = isInPictureInPictureMode,
        onNavigateHome = { navigateTo(SnapMusicDestination.Home.route) },
        onNavigatePreview = { navigateTo(SnapMusicDestination.Preview.route) },
    )

    PictureInPictureGate(
        viewModel = viewModel,
        state = navHostPlaybackState,
        isInPictureInPictureMode = isInPictureInPictureMode,
        previewPlayer = previewPlayer,
        youTubePlayer = youTubePlayer,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                SnapMusicBottomBar(
                    viewModel = viewModel,
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = ::navigateTo,
                )
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = SnapMusicDestination.Home.route,
                ) {
                    composable(SnapMusicDestination.Home.route) {
                        HomeScreen(
                            viewModel = viewModel,
                            padding = padding,
                            player = youTubePlayer,
                            onDownloadQueued = {
                                viewModel.requestOpenPreviewDownloads()
                                navigateTo(SnapMusicDestination.Preview.route)
                            },
                        )
                    }
                    composable(SnapMusicDestination.History.route) {
                        YouTubeTabContent(
                            viewModel = viewModel,
                            player = youTubePlayer,
                            isActive = true,
                            contentPadding = padding,
                            onDownloadQueued = {
                                viewModel.requestOpenPreviewDownloads()
                                navigateTo(SnapMusicDestination.Preview.route)
                            },
                        )
                    }
                    composable(SnapMusicDestination.Preview.route) { PreviewScreen(viewModel, padding, previewPlayer) }
                    composable(SnapMusicDestination.Settings.route) { SettingsScreen(viewModel, padding) }
                }

                PreviewMiniPlayerHost(
                    viewModel = viewModel,
                    player = previewPlayer,
                    currentRoute = currentRoute,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding() + 12.dp),
                    onNavigatePreview = { navigateTo(SnapMusicDestination.Preview.route) },
                )
                YouTubeMiniPlayerHost(
                    viewModel = viewModel,
                    player = youTubePlayer,
                    currentRoute = currentRoute,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = padding.calculateBottomPadding() + 12.dp),
                    onNavigateHome = { navigateTo(SnapMusicDestination.Home.route) },
                )
            }
        }
    }
}

@Composable
private fun NotificationRouteEffectHost(
    viewModel: SnapMusicViewModel,
    state: NavHostPlaybackState,
    currentRoute: String?,
    notificationRoute: String?,
    onNotificationRouteConsumed: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    LaunchedEffect(notificationRoute, currentRoute, state) {
        if (notificationRoute != null) {
            if (notificationRoute == SnapMusicDestination.Queue.route) {
                viewModel.requestOpenPreviewDownloads()
                if (currentRoute != SnapMusicDestination.Preview.route) {
                    onNavigate(SnapMusicDestination.Preview.route)
                }
            } else if (notificationRoute == MainActivity.ROUTE_PLAYBACK) {
                when (viewModel.resolvePlaybackNotificationTarget()) {
                    PlaybackNotificationTarget.PREVIEW -> {
                        if (state.previewCanRestore) {
                            viewModel.restorePreviewPlaybackShell()
                        } else {
                            viewModel.restorePreviewPlaybackSnapshot(showDetail = true)
                        }
                        if (currentRoute != SnapMusicDestination.Preview.route) {
                            onNavigate(SnapMusicDestination.Preview.route)
                        }
                    }
                    PlaybackNotificationTarget.YOUTUBE -> {
                        if (state.youtubeCanRestore) {
                            viewModel.restoreYouTubePlaybackShell()
                        } else {
                            viewModel.restoreYouTubePlaybackSnapshot()
                        }
                        if (currentRoute != SnapMusicDestination.Home.route) {
                            onNavigate(SnapMusicDestination.Home.route)
                        }
                    }
                    PlaybackNotificationTarget.NONE -> onNavigate(SnapMusicDestination.Home.route)
                }
            } else {
                onNavigate(notificationRoute)
            }
            onNotificationRouteConsumed()
        }
    }
}

@Composable
private fun NavHostVisibilityEffects(
    viewModel: SnapMusicViewModel,
    state: NavHostPlaybackState,
    currentRoute: String?,
    isInPictureInPictureMode: Boolean,
    onNavigateHome: () -> Unit,
    onNavigatePreview: () -> Unit,
) {
    val wasInPictureInPictureMode = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(currentRoute, state.youtubeShowPlayer, state.youtubeReady) {
        if (
            currentRoute != null &&
            currentRoute != SnapMusicDestination.Home.route &&
            currentRoute != SnapMusicDestination.History.route &&
            state.youtubeShowPlayer &&
            state.youtubeReady
        ) {
            viewModel.minimizeYouTubePlayer()
        }
    }

    LaunchedEffect(currentRoute, state.previewDetailVisible, state.previewReady) {
        if (
            currentRoute != null &&
            currentRoute != SnapMusicDestination.Preview.route &&
            state.previewDetailVisible &&
            state.previewReady
        ) {
            viewModel.minimizePreviewPlayer()
        }
    }

    LaunchedEffect(
        isInPictureInPictureMode,
        state.youtubePipEligible,
        state.previewPipEligible,
    ) {
        if (
            wasInPictureInPictureMode.value &&
            !isInPictureInPictureMode &&
            state.previewPipEligible
        ) {
            viewModel.restorePreviewPlaybackShell()
            onNavigatePreview()
        } else if (
            wasInPictureInPictureMode.value &&
            !isInPictureInPictureMode &&
            state.youtubePipEligible
        ) {
            viewModel.restoreYouTubePlaybackShell()
            onNavigateHome()
        }
        wasInPictureInPictureMode.value = isInPictureInPictureMode
    }
}

@Composable
private fun PictureInPictureGate(
    viewModel: SnapMusicViewModel,
    state: NavHostPlaybackState,
    isInPictureInPictureMode: Boolean,
    previewPlayer: Player?,
    youTubePlayer: Player?,
    content: @Composable () -> Unit,
) {
    when {
        isInPictureInPictureMode && state.previewPipEligible -> {
            PreviewPictureInPictureHost(viewModel = viewModel, player = previewPlayer)
        }
        isInPictureInPictureMode && state.youtubePipEligible -> {
            YouTubePictureInPictureHost(viewModel = viewModel, player = youTubePlayer)
        }
        else -> content()
    }
}

