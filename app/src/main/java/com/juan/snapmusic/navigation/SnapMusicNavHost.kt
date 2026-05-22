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
    val navHostPlaybackState by viewModel.navHostPlaybackState.collectAsStateWithLifecycle()
    val bottomBarUiState by viewModel.bottomBarUiState.collectAsStateWithLifecycle()
    val youTubePlayer = rememberManagedYouTubePlayer(viewModel)
    val previewPlayer = rememberManagedPreviewPlayer(viewModel)
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

    LaunchedEffect(notificationRoute) {
        if (notificationRoute != null) {
            if (notificationRoute == SnapMusicDestination.Queue.route) {
                viewModel.requestOpenPreviewDownloads()
                if (currentRoute != SnapMusicDestination.Preview.route) {
                    navigateTo(SnapMusicDestination.Preview.route)
                }
            } else if (notificationRoute == MainActivity.ROUTE_PLAYBACK) {
                when (viewModel.resolvePlaybackNotificationTarget()) {
                    PlaybackNotificationTarget.PREVIEW -> {
                        if (navHostPlaybackState.previewCanRestore) {
                            viewModel.restorePreviewPlaybackShell()
                        } else {
                            viewModel.restorePreviewPlaybackSnapshot(showDetail = true)
                        }
                        if (currentRoute != SnapMusicDestination.Preview.route) {
                            navigateTo(SnapMusicDestination.Preview.route)
                        }
                    }
                    PlaybackNotificationTarget.YOUTUBE -> {
                        if (navHostPlaybackState.youtubeCanRestore) {
                            viewModel.restoreYouTubePlaybackShell()
                        } else {
                            viewModel.restoreYouTubePlaybackSnapshot()
                        }
                        if (currentRoute != SnapMusicDestination.Home.route) {
                            navigateTo(SnapMusicDestination.Home.route)
                        }
                    }
                    PlaybackNotificationTarget.NONE -> navigateTo(SnapMusicDestination.Home.route)
                }
            } else {
                navigateTo(notificationRoute)
            }
            onNotificationRouteConsumed()
        }
    }

    NavHostVisibilityEffects(
        viewModel = viewModel,
        currentRoute = currentRoute,
        isInPictureInPictureMode = isInPictureInPictureMode,
        playbackState = navHostPlaybackState,
        onNavigateHome = { navigateTo(SnapMusicDestination.Home.route) },
        onNavigatePreview = { navigateTo(SnapMusicDestination.Preview.route) },
    )

    if (isInPictureInPictureMode && navHostPlaybackState.previewPipEligible) {
        PreviewPictureInPictureHost(viewModel = viewModel, player = previewPlayer)
        return
    }

    if (isInPictureInPictureMode && navHostPlaybackState.youtubePipEligible) {
        YouTubePictureInPictureHost(viewModel = viewModel, player = youTubePlayer)
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
        SnapMusicBottomBar(
            viewModel = viewModel,
            items = items,
            currentRoute = currentRoute,
            state = bottomBarUiState,
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

@Composable
private fun NavHostVisibilityEffects(
    viewModel: SnapMusicViewModel,
    currentRoute: String?,
    isInPictureInPictureMode: Boolean,
    playbackState: NavHostPlaybackState,
    onNavigateHome: () -> Unit,
    onNavigatePreview: () -> Unit,
) {
    val wasInPictureInPictureMode = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(currentRoute, playbackState.youtubeShowPlayer, playbackState.youtubeReady) {
        if (
            currentRoute != null &&
            currentRoute != SnapMusicDestination.Home.route &&
            currentRoute != SnapMusicDestination.History.route &&
            playbackState.youtubeShowPlayer &&
            playbackState.youtubeReady
        ) {
            viewModel.minimizeYouTubePlayer()
        }
    }

    LaunchedEffect(currentRoute, playbackState.previewDetailVisible, playbackState.previewReady) {
        if (
            currentRoute != null &&
            currentRoute != SnapMusicDestination.Preview.route &&
            playbackState.previewDetailVisible &&
            playbackState.previewReady
        ) {
            viewModel.minimizePreviewPlayer()
        }
    }

    LaunchedEffect(
        isInPictureInPictureMode,
        playbackState.youtubePipEligible,
        playbackState.previewPipEligible,
    ) {
        if (
            wasInPictureInPictureMode.value &&
            !isInPictureInPictureMode &&
            playbackState.previewPipEligible
        ) {
            viewModel.restorePreviewPlaybackShell()
            onNavigatePreview()
        } else if (
            wasInPictureInPictureMode.value &&
            !isInPictureInPictureMode &&
            playbackState.youtubePipEligible
        ) {
            viewModel.restoreYouTubePlaybackShell()
            onNavigateHome()
        }
        wasInPictureInPictureMode.value = isInPictureInPictureMode
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SnapMusicBottomBar(
    viewModel: SnapMusicViewModel,
    items: List<SnapMusicDestination>,
    currentRoute: String?,
    state: BottomBarUiState,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        items.forEach { item ->
            when (item) {
                SnapMusicDestination.Home -> HomeNavigationItem(
                    viewModel = viewModel,
                    item = item,
                    currentRoute = currentRoute,
                    canRestore = state.youtubeCanRestore,
                    onNavigate = onNavigate,
                )
                SnapMusicDestination.Preview -> PreviewNavigationItem(
                    viewModel = viewModel,
                    item = item,
                    currentRoute = currentRoute,
                    canRestore = state.previewCanRestore,
                    activeDownloadCount = state.activeDownloadCount,
                    onNavigate = onNavigate,
                )
                else -> MaterialNavigationBarItem(
                    selected = currentRoute == item.route,
                    onClick = { onNavigate(item.route) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    colors = snapMusicBottomBarItemColors(),
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.HomeNavigationItem(
    viewModel: SnapMusicViewModel,
    item: SnapMusicDestination,
    currentRoute: String?,
    canRestore: Boolean,
    onNavigate: (String) -> Unit,
) {
    MaterialNavigationBarItem(
        selected = currentRoute == item.route,
        onClick = {
            if (canRestore) {
                viewModel.restoreYouTubePlaybackShell()
            }
            onNavigate(item.route)
        },
        icon = { Icon(item.icon, contentDescription = item.label) },
        label = { Text(item.label) },
        colors = snapMusicBottomBarItemColors(),
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.PreviewNavigationItem(
    viewModel: SnapMusicViewModel,
    item: SnapMusicDestination,
    currentRoute: String?,
    canRestore: Boolean,
    activeDownloadCount: Int,
    onNavigate: (String) -> Unit,
) {
    MaterialNavigationBarItem(
        selected = currentRoute == item.route,
        onClick = {
            if (canRestore) {
                viewModel.restorePreviewPlaybackShell()
            }
            onNavigate(item.route)
        },
        icon = { PreviewNavigationIcon(item = item, activeDownloadCount = activeDownloadCount) },
        label = { Text(item.label) },
        colors = snapMusicBottomBarItemColors(),
    )
}

@Composable
private fun snapMusicBottomBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AccentRed,
    selectedTextColor = TextPrimary,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor = Color.Transparent,
)

@Composable
private fun PreviewNavigationIcon(
    item: SnapMusicDestination,
    activeDownloadCount: Int,
) {
    if (activeDownloadCount > 0) {
        Box {
            Icon(
                painter = painterResource(id = R.drawable.ic_nav_preview_snaptube),
                contentDescription = item.label,
            )
            SnapMusicNavCounterBadge(
                countLabel = if (activeDownloadCount > 9) "9+" else activeDownloadCount.toString(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp),
            )
        }
    } else {
        Icon(
            painter = painterResource(id = R.drawable.ic_nav_preview_snaptube),
            contentDescription = item.label,
        )
    }
}

@Composable
private fun PreviewPictureInPictureHost(
    viewModel: SnapMusicViewModel,
    player: Player?,
) {
    val state by viewModel.previewPictureInPictureState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        PreviewPictureInPictureSurface(
            preview = state.preview,
            player = player,
        )
    }
}

@Composable
private fun YouTubePictureInPictureHost(
    viewModel: SnapMusicViewModel,
    player: Player?,
) {
    val state by viewModel.youtubePictureInPictureState.collectAsStateWithLifecycle()
    Box(modifier = Modifier.fillMaxSize()) {
        PictureInPicturePlayerSurface(
            featured = state.featured,
            player = player,
        )
    }
}

@Composable
private fun PreviewMiniPlayerHost(
    viewModel: SnapMusicViewModel,
    player: Player?,
    currentRoute: String?,
    modifier: Modifier,
    onNavigatePreview: () -> Unit,
) {
    val state by viewModel.previewMiniPlayerState.collectAsStateWithLifecycle()
    if (!state.visible) return
    PreviewMiniPlayer(
        preview = state.preview,
        player = player,
        modifier = modifier,
        onOpen = {
            viewModel.restorePreviewPlaybackShell()
            if (currentRoute != SnapMusicDestination.Preview.route) {
                onNavigatePreview()
            }
        },
        onDismiss = viewModel::dismissPreviewPlayer,
    )
}

@Composable
private fun YouTubeMiniPlayerHost(
    viewModel: SnapMusicViewModel,
    player: Player?,
    currentRoute: String?,
    modifier: Modifier,
    onNavigateHome: () -> Unit,
) {
    val state by viewModel.youtubeMiniPlayerState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    if (!state.visible) return
    YouTubeMiniPlayer(
        featured = state.featured,
        player = player,
        compact = state.compact,
        modifier = modifier,
        onOpen = {
            viewModel.restoreYouTubePlaybackShell()
            if (currentRoute != SnapMusicDestination.Home.route) {
                onNavigateHome()
            }
        },
        onDismiss = {
            player?.pause()
            player?.clearMediaItems()
            viewModel.dismissYouTubePlayer()
        },
        onNext = { viewModel.playNextYouTubeItem() },
        onPrevious = { viewModel.playPreviousYouTubeItem() },
        onShare = {
            context.startActivity(
                    android.content.Intent.createChooser(
                        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, state.featured.sourceUrl)
                        },
                        "Compartir video",
                    ),
            )
        },
        onDownload = {
            viewModel.requestYouTubeDownloadSheet()
            viewModel.restoreYouTubePlaybackShell()
            onNavigateHome()
        },
        onToggleCompact = viewModel::toggleYouTubeMiniPlayerMode,
    )
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun rememberManagedYouTubePlayer(
    viewModel: SnapMusicViewModel,
): Player? {
    val state by viewModel.youtubePlaybackRenderState.collectAsStateWithLifecycle()
    return rememberYouTubePlayer(
        state = state,
        onPlaybackEnded = viewModel::onYouTubePlaybackEnded,
        onPlaybackError = { rawMessage, shouldRetryExpiredStream ->
            viewModel.onYouTubePlaybackError(rawMessage, shouldRetryExpiredStream)
        },
        onPlaybackProgress = viewModel::syncYouTubePlaybackProgress,
        onMediaTransition = viewModel::syncYouTubeMediaTransition,
        onPlaybackQualityChanged = viewModel::syncYouTubePlaybackTracks,
    )
}

@Composable
private fun SnapMusicNavCounterBadge(
    countLabel: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color(0xFFE53935))
            .sizeIn(minWidth = 15.dp, minHeight = 15.dp)
            .padding(horizontal = 4.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = countLabel,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun rememberManagedPreviewPlayer(
    viewModel: SnapMusicViewModel,
): Player? {
    val previewState by viewModel.previewPlaybackRenderState.collectAsStateWithLifecycle()
    return rememberPreviewPlayer(
        preview = previewState.preview,
        playlist = previewState.playlist,
        currentPositionMs = previewState.currentPositionMs,
        autoPlayRequestId = previewState.autoPlayRequestId,
        onAutoPlayRequestConsumed = viewModel::consumePreviewAutoplayRequest,
        onPlaybackEnded = viewModel::playNextPreviewInLibrary,
        onMediaTransition = viewModel::syncPreviewPlaybackItem,
        onPlaybackProgress = viewModel::syncPreviewPlaybackProgress,
    )
}
