package com.juan.snapmusic.feature.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
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

@Composable
internal fun RenameLocalMediaDialog(
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

internal fun shareLocalMedia(
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
    launchChooserOrToast(context, shareIntent, "Compartir archivo", "No hay una app para compartir este archivo.")
}

internal fun shareLocalMediaItems(
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
        clipData = ClipData.newRawUri(items.first().title, uris.first()).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    launchChooserOrToast(context, intent, "Compartir archivos", "No hay una app para compartir estos archivos.")
}

internal fun copyLocalMediaUri(
    context: Context,
    item: LocalMediaItem,
) {
    val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(item.title, item.contentUri))
    Toast.makeText(context, "Ruta copiada", Toast.LENGTH_SHORT).show()
}

internal fun openLocalMediaLocation(
    context: Context,
    item: LocalMediaItem,
) {
    val uri = Uri.parse(item.contentUri)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, if (item.isVideo) "video/*" else "audio/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    launchChooserOrToast(context, intent, "Abrir ubicación", "No hay una app para abrir este archivo.")
}

@Composable
internal fun LocalMediaDeleteDialog(
    items: List<LocalMediaItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (items.isEmpty()) return
    val singleItem = items.singleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (singleItem != null) "Eliminar archivo" else "Eliminar archivos") },
        text = {
            Text(
                if (singleItem != null) {
                    "Se eliminará \"${singleItem.title}\" del dispositivo. Esta acción no se puede deshacer."
                } else {
                    "Se eliminarán ${items.size} archivos del dispositivo. Esta acción no se puede deshacer."
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

private fun launchChooserOrToast(
    context: Context,
    intent: Intent,
    title: String,
    errorMessage: String,
) {
    runCatching {
        context.startActivity(Intent.createChooser(intent, title))
    }.onFailure {
        Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun LocalMediaInfoDialog(
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
internal fun LocalMediaSearchDialog(
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
internal fun PreviewDownloadsSummaryVisibilityHost(
    viewModel: SnapMusicViewModel,
    onOpenDownloads: () -> Unit,
) {
    val activeDownloadCount = viewModel.previewActiveDownloadCount.collectAsStateWithLifecycle().value
    if (activeDownloadCount <= 0) return
    PreviewDownloadsSummaryHost(viewModel = viewModel, onOpenDownloads = onOpenDownloads)
}

@Composable
internal fun PreviewDownloadsSummaryHost(
    viewModel: SnapMusicViewModel,
    onOpenDownloads: () -> Unit,
) {
    val downloadsState = viewModel.previewDownloadsState.collectAsStateWithLifecycle().value
    PreviewDownloadsSummaryCard(
        activeDownloads = downloadsState.activeItems,
        onOpenDownloads = onOpenDownloads,
    )
}

internal fun hasMediaPermission(context: Context): Boolean {
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

internal data class LocalMediaFileInfo(
    val mimeType: String,
    val sizeLabel: String,
    val modifiedLabel: String,
)

internal fun readLocalMediaInfo(
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

internal fun formatFileSize(sizeBytes: Long): String {
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

internal fun previewInfoSubtitle(item: LocalMediaItem): String {
    return item.subtitle
        .removePrefix("Video local · ")
        .removePrefix("Audio local · ")
        .ifBlank { if (item.isVideo) "Video descargado" else "Audio descargado" }
}

internal fun filterLocalMediaSuggestions(
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
