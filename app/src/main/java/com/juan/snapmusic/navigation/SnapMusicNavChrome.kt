package com.juan.snapmusic.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem as MaterialNavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
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
import androidx.compose.ui.unit.Dp
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
import com.juan.snapmusic.feature.home.*
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

@Composable
internal fun SnapMusicAdaptiveScaffold(
    viewModel: SnapMusicViewModel,
    items: List<SnapMusicDestination>,
    currentRoute: String?,
    useSideNavigation: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable (PaddingValues, Dp) -> Unit,
) {
    if (useSideNavigation) {
        Row(modifier = Modifier.fillMaxSize()) {
            SnapMusicNavigationRail(
                viewModel = viewModel,
                items = items,
                currentRoute = currentRoute,
                onNavigate = onNavigate,
            )
            Scaffold(modifier = Modifier.weight(1f).fillMaxSize()) { padding ->
                content(padding, 0.dp)
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                SnapMusicBottomBar(
                    viewModel = viewModel,
                    items = items,
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                )
            },
        ) { padding ->
            content(padding, padding.calculateBottomPadding())
        }
    }
}

@Composable
internal fun SnapMusicBottomBar(
    viewModel: SnapMusicViewModel,
    items: List<SnapMusicDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val state by viewModel.bottomBarUiState.collectAsStateWithLifecycle()
    LiquidBottomNavigationFrame(
        items = items,
        currentRoute = currentRoute,
    ) {
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
private fun SnapMusicNavigationRail(
    viewModel: SnapMusicViewModel,
    items: List<SnapMusicDestination>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val state by viewModel.bottomBarUiState.collectAsStateWithLifecycle()
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            NavigationRailItem(
                selected = currentRoute == item.route,
                onClick = {
                    when (item) {
                        SnapMusicDestination.Home -> if (state.youtubeCanRestore) {
                            viewModel.restoreYouTubePlaybackShell()
                        }
                        SnapMusicDestination.Preview -> if (state.previewCanRestore) {
                            viewModel.restorePreviewPlaybackShell()
                        }
                        else -> Unit
                    }
                    onNavigate(item.route)
                },
                icon = {
                    if (item == SnapMusicDestination.Preview) {
                        PreviewNavigationIcon(item = item, activeDownloadCount = state.activeDownloadCount)
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label) },
                colors = snapMusicRailItemColors(),
            )
        }
    }
}

@Composable
private fun snapMusicRailItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = AccentRed,
    selectedTextColor = TextPrimary,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor = Color.Transparent,
)

@Composable
internal fun snapMusicBottomBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = AccentRed,
    selectedTextColor = TextPrimary,
    unselectedIconColor = TextSecondary,
    unselectedTextColor = TextSecondary,
    indicatorColor = Color.Transparent,
)

@Composable
internal fun PreviewNavigationIcon(
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
internal fun PreviewPictureInPictureHost(
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
internal fun YouTubePictureInPictureHost(
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
internal fun PreviewMiniPlayerHost(
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
internal fun YouTubeMiniPlayerHost(
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
        onTogglePlayPause = {
            if (player?.isPlaying == true) {
                player.pause()
            } else {
                player?.play()
            }
        },
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
internal fun rememberManagedYouTubePlayer(
    viewModel: SnapMusicViewModel,
    enabled: Boolean,
): Player? {
    if (!enabled) return null
    val sessionState by viewModel.youtubePlayerSessionState.collectAsStateWithLifecycle()
    val seekState by viewModel.youtubePlayerSeekState.collectAsStateWithLifecycle()
    val shouldAutoPlay by viewModel.youtubePlaybackAutoPlay.collectAsStateWithLifecycle()
    return rememberYouTubePlayer(
        sessionState = sessionState,
        seekState = seekState,
        shouldAutoPlayCurrent = shouldAutoPlay,
        onPlaybackEnded = viewModel::onYouTubePlaybackEnded,
        onPlaybackError = { rawMessage, shouldRetryExpiredStream ->
            viewModel.onYouTubePlaybackError(rawMessage, shouldRetryExpiredStream)
        },
        onPlaybackProgress = viewModel::syncYouTubePlaybackProgress,
        onMediaTransition = viewModel::syncYouTubeMediaTransition,
        onPlaybackFirstFrame = viewModel::onYouTubePlaybackFirstFrame,
        onPlaybackQualityChanged = viewModel::syncYouTubePlaybackTracks,
        onPlaybackRebuffer = viewModel::onYouTubePlaybackRebuffer,
        onPlaybackStalled = viewModel::onYouTubePlaybackStalled,
    )
}

@Composable
internal fun SnapMusicNavCounterBadge(
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
internal fun rememberManagedPreviewPlayer(
    viewModel: SnapMusicViewModel,
    enabled: Boolean,
): Player? {
    if (!enabled) return null
    val previewState by viewModel.previewPlaybackRenderState.collectAsStateWithLifecycle()
    return rememberPreviewPlayer(
        preview = previewState.preview,
        playlist = previewState.playlist,
        resumePositionMs = previewState.resumePositionMs,
        autoPlayRequestId = previewState.autoPlayRequestId,
        onAutoPlayRequestConsumed = viewModel::consumePreviewAutoplayRequest,
        onPlaybackEnded = viewModel::playNextPreviewInLibrary,
        onMediaTransition = viewModel::syncPreviewPlaybackItem,
        onPlaybackProgress = viewModel::syncPreviewPlaybackProgress,
    )
}
