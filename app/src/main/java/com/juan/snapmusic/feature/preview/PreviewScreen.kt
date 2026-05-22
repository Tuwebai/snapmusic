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
    val previewPerformance = viewModel.previewPerformanceState.collectAsStateWithLifecycle().value
    val downloadsShell = viewModel.previewDownloadsShellState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val hasPermission = remember(context) { hasMediaPermission(context) }
    var showDownloadsScreen by rememberSaveable { mutableStateOf(false) }
    ReportPerformanceScene(
        screen = "preview",
        detail = when {
            routeVisibility.detailVisible && previewPerformance.isReady && previewPerformance.isVideo -> "preview-player-video"
            routeVisibility.detailVisible && previewPerformance.isReady -> "preview-player-audio"
            showDownloadsScreen -> "downloads-active"
            else -> "preview-library"
        },
    )

    LaunchedEffect(hasPermission, downloadsShell.completedCount) {
        if (hasPermission) {
            if (downloadsShell.completedCount > 0) {
                viewModel.refreshLocalPreviewLibrary(forceRefresh = true)
            } else {
                viewModel.ensureLocalPreviewLibraryLoaded()
            }
        }
    }

    LaunchedEffect(downloadsShell.openRequestId) {
        if (downloadsShell.openRequestId > 0L && downloadsShell.hasActiveDownloads) {
            showDownloadsScreen = true
        }
    }

    LaunchedEffect(downloadsShell.hasActiveDownloads) {
        if (!downloadsShell.hasActiveDownloads && showDownloadsScreen) {
            showDownloadsScreen = false
        }
    }

    if (routeVisibility.detailVisible && previewPerformance.isReady) {
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
private fun PreviewDetailHost(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    player: Player?,
) {
    val detailState = viewModel.previewDetailScreen.collectAsStateWithLifecycle().value
    val libraryState = viewModel.previewLibraryScreen.collectAsStateWithLifecycle().value
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
                    isActive = item.contentUri == detailState.preview.fileUri,
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
    val activeDownloadCount = viewModel.previewActiveDownloadCount.collectAsStateWithLifecycle().value
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
        if (activeDownloadCount > 0) {
            item {
                PreviewDownloadsSummaryHost(viewModel = viewModel, onOpenDownloads = onOpenDownloads)
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

@Composable
private fun RenameLocalMediaDialog(
    item: LocalMediaItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember(item.contentUri) {
        mutableStateOf(
            item.fileName.substringBeforeLast('.', item.title).ifBlank { item.title },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Nuevo nombre") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun shareLocalMedia(
    context: Context,
    item: LocalMediaItem,
) {
    val shareIntent = buildLocalMediaShareIntent(
        context = context,
        contentUri = item.contentUri,
        title = item.title,
        fileName = item.fileName,
        isVideo = item.isVideo,
    ) ?: return
    context.startActivity(android.content.Intent.createChooser(shareIntent, "Compartir archivo"))
}

private fun shareLocalMediaItems(
    context: Context,
    items: List<LocalMediaItem>,
) {
    val uris = items.map { Uri.parse(it.contentUri) }
    if (uris.isEmpty()) return
    val mimeType = when {
        items.all(LocalMediaItem::isVideo) -> "video/*"
        items.all { !it.isVideo } -> "audio/*"
        else -> "*/*"
    }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = mimeType
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        clipData = android.content.ClipData.newRawUri(items.first().title, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(android.content.ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir archivos"))
}

private fun openLocalMediaLocation(
    context: Context,
    item: LocalMediaItem,
) {
    val uri = Uri.parse(item.contentUri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, if (item.isVideo) "video/*" else "audio/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Abrir ubicación"))
}

@Composable
private fun LocalMediaInfoDialog(
    item: LocalMediaItem,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val info = remember(item.contentUri, item.fileName) {
        readLocalMediaInfo(context, item)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información del archivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Título: ${item.title}")
                Text("Archivo: ${item.fileName.ifBlank { item.title }}")
                Text("Tipo: ${if (item.isVideo) "Video" else "Audio"}")
                Text("Mime: ${info.mimeType}")
                Text("Tamaño: ${info.sizeLabel}")
                Text("Duración: ${previewInfoSubtitle(item)}")
                Text("Modificado: ${info.modifiedLabel}")
                Text("Uri: ${item.contentUri}")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun LocalMediaSearchDialog(
    query: String,
    results: List<LocalMediaItem>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (LocalMediaItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buscar archivos locales") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Buscar") },
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(results, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(item) }
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(item.title)
                            Text(
                                previewInfoSubtitle(item),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
private fun PreviewDownloadsSummaryHost(
    viewModel: SnapMusicViewModel,
    onOpenDownloads: () -> Unit,
) {
    val downloadsState = viewModel.previewDownloadsState.collectAsStateWithLifecycle().value
    PreviewDownloadsSummaryCard(
        activeDownloads = downloadsState.activeItems,
        onOpenDownloads = onOpenDownloads,
    )
}

private fun hasMediaPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val audioGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_AUDIO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val videoGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_MEDIA_VIDEO,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        audioGranted || videoGranted
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

private data class LocalMediaFileInfo(
    val mimeType: String,
    val sizeLabel: String,
    val modifiedLabel: String,
)

private fun readLocalMediaInfo(
    context: Context,
    item: LocalMediaItem,
): LocalMediaFileInfo {
    val uri = Uri.parse(item.contentUri)
    var mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { if (item.isVideo) "video/*" else "audio/*" }
    var sizeBytes = 0L
    var modifiedSeconds = 0L
    context.contentResolver.query(
        uri,
        arrayOf(
            OpenableColumns.SIZE,
            android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
            android.provider.MediaStore.MediaColumns.MIME_TYPE,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val modifiedIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
            val mimeIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
            if (sizeIndex >= 0) sizeBytes = cursor.getLong(sizeIndex)
            if (modifiedIndex >= 0) modifiedSeconds = cursor.getLong(modifiedIndex)
            if (mimeIndex >= 0) {
                mimeType = cursor.getString(mimeIndex).orEmpty().ifBlank { mimeType }
            }
        }
    }
    return LocalMediaFileInfo(
        mimeType = mimeType,
        sizeLabel = formatFileSize(sizeBytes),
        modifiedLabel = if (modifiedSeconds > 0L) Date(modifiedSeconds * 1000L).toString() else "Desconocido",
    )
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "Desconocido"
    val units = listOf("B", "KB", "MB", "GB")
    var size = sizeBytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex += 1
    }
    return "${DecimalFormat("#,##0.#").format(size)} ${units[unitIndex]}"
}

private fun previewInfoSubtitle(item: LocalMediaItem): String {
    return item.subtitle
        .removePrefix("Video local · ")
        .removePrefix("Audio local · ")
        .ifBlank { if (item.isVideo) "Video descargado" else "Audio descargado" }
}

private fun filterLocalMediaSuggestions(
    query: String,
    items: List<LocalMediaItem>,
): List<LocalMediaItem> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return items.take(20)
    return items
        .mapNotNull { item ->
            val title = item.title.lowercase()
            val fileName = item.fileName.lowercase()
            val subtitle = item.subtitle.lowercase()
            val matches = title.contains(normalized) || fileName.contains(normalized) || subtitle.contains(normalized)
            if (!matches) return@mapNotNull null
            val priority = when {
                title.startsWith(normalized) -> 0
                fileName.startsWith(normalized) -> 1
                title.contains(normalized) -> 2
                else -> 3
            }
            priority to item
        }
        .sortedBy { it.first }
        .map { it.second }
        .take(24)
}
