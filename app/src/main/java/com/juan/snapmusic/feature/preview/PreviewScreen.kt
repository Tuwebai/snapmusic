package com.juan.snapmusic.feature.preview

import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.juan.snapmusic.core.model.LocalMediaItem
import com.juan.snapmusic.core.platform.buildLocalMediaShareIntent
import com.juan.snapmusic.core.performance.ReportPerformanceScene
import com.juan.snapmusic.feature.home.PreviewLibraryUiState
import com.juan.snapmusic.feature.home.SnapMusicViewModel
import java.text.DecimalFormat
import java.util.Date

@androidx.media3.common.util.UnstableApi
@Composable
fun PreviewScreen(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    player: Player?,
) {
    val routeVisibility = viewModel.previewRouteVisibility.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val hasPermission = remember(context) { hasMediaPermission(context) }
    var showDownloadsScreen by rememberSaveable { mutableStateOf(false) }

    PreviewDownloadsLifecycleHost(
        viewModel = viewModel,
        hasPermission = hasPermission,
        showDownloadsScreen = showDownloadsScreen,
        onShowDownloadsScreenChange = { showDownloadsScreen = it },
    )
    PreviewSceneReporterHost(
        viewModel = viewModel,
        showDownloadsScreen = showDownloadsScreen,
    )

    if (routeVisibility.detailVisible && routeVisibility.isReady) {
        PreviewDetailHost(
            viewModel = viewModel,
            padding = padding,
            player = player,
        )
        return
    }

    if (showDownloadsScreen) {
        BackHandler {
            showDownloadsScreen = false
        }

        PreviewDownloadsDetailHost(
            viewModel = viewModel,
            padding = padding,
            onBack = { showDownloadsScreen = false },
        )
        return
    }

    PreviewLibraryRoot(
        viewModel = viewModel,
        padding = padding,
        hasPermission = hasPermission,
        onOpenDownloads = { showDownloadsScreen = true },
    )
}

@Composable
private fun PreviewDownloadsLifecycleHost(
    viewModel: SnapMusicViewModel,
    hasPermission: Boolean,
    showDownloadsScreen: Boolean,
    onShowDownloadsScreenChange: (Boolean) -> Unit,
) {
    val downloadsShell = viewModel.previewDownloadsShellState.collectAsStateWithLifecycle().value

    LaunchedEffect(hasPermission, downloadsShell.completedCount) {
        if (hasPermission) {
            if (downloadsShell.completedCount > 0) {
                viewModel.refreshLocalPreviewLibrary(forceRefresh = true)
            } else {
                viewModel.ensureLocalPreviewLibraryLoaded()
            }
        }
    }

    LaunchedEffect(downloadsShell.openRequestId, downloadsShell.hasActiveDownloads) {
        if (downloadsShell.openRequestId > 0L && downloadsShell.hasActiveDownloads) {
            onShowDownloadsScreenChange(true)
        }
    }

    LaunchedEffect(downloadsShell.hasActiveDownloads, showDownloadsScreen) {
        if (!downloadsShell.hasActiveDownloads && showDownloadsScreen) {
            onShowDownloadsScreenChange(false)
        }
    }
}

@Composable
private fun PreviewSceneReporterHost(
    viewModel: SnapMusicViewModel,
    showDownloadsScreen: Boolean,
) {
    val routeVisibility = viewModel.previewRouteVisibility.collectAsStateWithLifecycle().value
    val previewPerformance = viewModel.previewPerformanceState.collectAsStateWithLifecycle().value

    ReportPerformanceScene(
        screen = "preview",
        detail = when {
            routeVisibility.detailVisible && routeVisibility.isReady && previewPerformance.isVideo -> "preview-player-video"
            routeVisibility.detailVisible && routeVisibility.isReady -> "preview-player-audio"
            showDownloadsScreen -> "downloads-active"
            else -> "preview-library"
        },
    )
}

@Composable
private fun PreviewDetailHost(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    player: Player?,
) {
    val libraryState = viewModel.previewLibraryScreen.collectAsStateWithLifecycle().value
    val activePreviewUri = viewModel.previewActiveFileUri.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<LocalMediaItem?>(null) }
    var infoTarget by remember { mutableStateOf<LocalMediaItem?>(null) }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedItems = remember(selectedIds, libraryState.items) {
        libraryState.items.filter { it.id in selectedIds }
    }

    BackHandler {
        viewModel.closePreviewDetail()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            PreviewPlaybackCardHost(viewModel = viewModel, player = player)
        }
        if (libraryState.items.isNotEmpty()) {
            item {
                PreviewLibraryHeader(
                    title = "Todas las canciones",
                    selectionMode = selectionMode,
                    selectedCount = selectedItems.size,
                    onShareSelected = {
                        if (selectedItems.isNotEmpty()) {
                            shareLocalMediaItems(context, selectedItems)
                        }
                    },
                    onDeleteSelected = {
                        if (selectedItems.isNotEmpty()) {
                            viewModel.deleteLocalMediaItems(selectedItems)
                            selectedIds = emptySet()
                        }
                    },
                    onCloseSelection = { selectedIds = emptySet() },
                )
            }
            items(
                items = libraryState.items,
                key = { it.id },
                contentType = { item -> if (item.isVideo) "preview_video_item" else "preview_audio_item" },
            ) { item ->
                PreviewLibraryRow(
                    item = item,
                    isActive = item.contentUri == activePreviewUri,
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onClick = {
                        selectedIds = emptySet()
                        viewModel.openPreviewFromDevice(item)
                    },
                    onShare = { shareLocalMedia(context, item) },
                    onRename = { renameTarget = item },
                    onDelete = { viewModel.deleteLocalMediaItem(item) },
                    onOpenLocation = { openLocalMediaLocation(context, item) },
                    onViewInfo = { infoTarget = item },
                    onUseAsNext = { viewModel.queuePreviewItemNext(item) },
                    onSelectMultiple = {
                        selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                    },
                )
            }
        }
    }
    renameTarget?.let { item ->
        RenameLocalMediaDialog(
            item = item,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameLocalMediaItem(item, newName)
            },
        )
    }
    infoTarget?.let { item ->
        LocalMediaInfoDialog(
            item = item,
            onDismiss = { infoTarget = null },
        )
    }
}

@Composable
private fun PreviewPlaybackCardHost(
    viewModel: SnapMusicViewModel,
    player: Player?,
) {
    val detailState = viewModel.previewDetailScreen.collectAsStateWithLifecycle().value
    if (player != null) {
        PreviewPlaybackCard(
            preview = detailState.preview,
            player = player,
            canGoPrevious = detailState.canGoPrevious,
            canGoNext = detailState.canGoNext,
            onBack = viewModel::closePreviewDetail,
            onMinimize = viewModel::minimizePreviewPlayer,
            onPrevious = viewModel::playPreviousPreviewInLibrary,
            onNext = viewModel::playNextPreviewInLibrary,
        )
    } else {
        PreviewEmptyState()
    }
}

@Composable
private fun PreviewDownloadsDetailHost(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val downloadsState = viewModel.previewDownloadsState.collectAsStateWithLifecycle().value
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
    ) {
        item {
            PreviewDownloadsDetailScreen(
                activeDownloads = downloadsState.activeItems,
                onBack = onBack,
                onCancel = viewModel::cancelQueue,
                onCancelAll = viewModel::cancelActiveDownloads,
            )
        }
    }
}

@Composable
private fun PreviewLibraryRoot(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    hasPermission: Boolean,
    onOpenDownloads: () -> Unit,
) {
    val libraryState = viewModel.previewLibraryScreen.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    var renameTarget by remember { mutableStateOf<LocalMediaItem?>(null) }
    var infoTarget by remember { mutableStateOf<LocalMediaItem?>(null) }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable { mutableStateOf(setOf<String>()) }
    val selectionMode = selectedIds.isNotEmpty()
    val selectedItems = remember(selectedIds, libraryState.items) {
        libraryState.items.filter { it.id in selectedIds }
    }
    val searchResults = remember(searchQuery, libraryState.items) {
        filterLocalMediaSuggestions(searchQuery, libraryState.items)
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        if (hasPermission) {
            item {
                PreviewDownloadsSummaryVisibilityHost(viewModel = viewModel, onOpenDownloads = onOpenDownloads)
            }
        }
        when {
            !hasPermission -> {
                item { PreviewPermissionState() }
            }

            libraryState.items.isEmpty() -> {
                item { PreviewLibraryEmptyState() }
            }

            else -> {
                item {
                    PreviewLibraryHeader(
                        totalItems = libraryState.items.size,
                        selectionMode = selectionMode,
                        selectedCount = selectedItems.size,
                        onSearch = { showSearch = true },
                        onShareSelected = {
                            if (selectedItems.isNotEmpty()) {
                                shareLocalMediaItems(context, selectedItems)
                            }
                        },
                        onDeleteSelected = {
                            if (selectedItems.isNotEmpty()) {
                                viewModel.deleteLocalMediaItems(selectedItems)
                                selectedIds = emptySet()
                            }
                        },
                        onCloseSelection = { selectedIds = emptySet() },
                    )
                }
                items(
                    items = libraryState.items,
                    key = { it.id },
                    contentType = { item -> if (item.isVideo) "preview_video_item" else "preview_audio_item" },
                ) { item ->
                    PreviewLibraryRow(
                        item = item,
                        isActive = false,
                        selectionMode = selectionMode,
                        selected = item.id in selectedIds,
                        onClick = {
                            selectedIds = emptySet()
                            viewModel.openPreviewFromDevice(item)
                        },
                        onShare = { shareLocalMedia(context, item) },
                        onRename = { renameTarget = item },
                        onDelete = { viewModel.deleteLocalMediaItem(item) },
                        onOpenLocation = { openLocalMediaLocation(context, item) },
                        onViewInfo = { infoTarget = item },
                        onUseAsNext = { viewModel.queuePreviewItemNext(item) },
                        onSelectMultiple = {
                            selectedIds = if (item.id in selectedIds) selectedIds - item.id else selectedIds + item.id
                        },
                    )
                }
            }
        }
    }
    renameTarget?.let { item ->
        RenameLocalMediaDialog(
            item = item,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                viewModel.renameLocalMediaItem(item, newName)
            },
        )
    }
    infoTarget?.let { item ->
        LocalMediaInfoDialog(
            item = item,
            onDismiss = { infoTarget = null },
        )
    }
    if (showSearch) {
        LocalMediaSearchDialog(
            query = searchQuery,
            results = searchResults,
            onQueryChange = { searchQuery = it },
            onDismiss = {
                showSearch = false
                searchQuery = ""
            },
            onSelect = { item ->
                showSearch = false
                searchQuery = ""
                viewModel.openPreviewFromDevice(item)
            },
        )
    }
}

