package com.juan.snapmusic.feature.preview


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.R
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.LocalMediaItem

@Composable
internal fun PreviewDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated)
                .clickable(onClick = onBack)
                .padding(10.dp),
        ) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}

@Composable
internal fun PreviewLibraryHeader(
    totalItems: Int = 0,
    title: String = "Descargado",
    selectionMode: Boolean = false,
    selectedCount: Int = 0,
    onSearch: (() -> Unit)? = null,
    onShareSelected: (() -> Unit)? = null,
    onDeleteSelected: (() -> Unit)? = null,
    onCloseSelection: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (selectionMode) "$selectedCount seleccionados" else title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        if (selectionMode) {
            onShareSelected?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Outlined.Share, contentDescription = "Compartir seleccionados", tint = TextPrimary)
                }
            }
            onDeleteSelected?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Eliminar seleccionados", tint = TextPrimary)
                }
            }
            onCloseSelection?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cerrar selección", tint = TextPrimary)
                }
            }
        } else {
            onSearch?.let { action ->
                IconButton(onClick = action) {
                    Icon(Icons.Outlined.Search, contentDescription = "Buscar archivos locales", tint = TextPrimary)
                }
            }
        }
    }
}

@Composable
internal fun PreviewLibraryRow(
    item: LocalMediaItem,
    isActive: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onOpenLocation: () -> Unit,
    onViewInfo: () -> Unit,
    onUseAsNext: () -> Unit,
    onSelectMultiple: () -> Unit,
) {
    var expanded by remember(item.contentUri) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clickable(onClick = if (selectionMode) onSelectMultiple else onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 42.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (isActive) AccentRed else Color.Transparent),
            )
            PreviewLibraryArtwork(item)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) AccentRed else TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (item.isVideo) Icons.Outlined.SmartDisplay else Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = normalizedPreviewSubtitle(item),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) AccentRed else BorderSubtle),
                )
            } else {
                Box {
                    IconButton(
                        onClick = { expanded = true },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Más opciones",
                            tint = TextSecondary,
                        )
                    }
                    DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Compartir") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onShare()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Renombrar") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Edit, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Abrir ubicación") },
                        leadingIcon = {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onOpenLocation()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Ver información") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Info, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onViewInfo()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Usar como siguiente") },
                        leadingIcon = {
                            Icon(Icons.Outlined.PlaylistPlay, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onUseAsNext()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Seleccionar múltiples") },
                        leadingIcon = {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onSelectMultiple()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        leadingIcon = {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null)
                        },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                    )
                }
            }
    }
}
}

@Composable
private fun PreviewLibraryArtwork(
    item: LocalMediaItem,
) {
    val context = LocalContext.current
    val fallbackPainter = painterResource(R.drawable.preview_local_music_fallback)
    val artworkModel = remember(item.contentUri, item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl)
            .memoryCacheKey("preview-library:${item.id}:${item.thumbnailUrl}")
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(64, 64)
            .build()
    }
    if (item.thumbnailUrl.isNotBlank()) {
        AsyncImage(
            model = artworkModel,
            contentDescription = item.title,
            error = fallbackPainter,
            fallback = fallbackPainter,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(14.dp)),
            filterQuality = FilterQuality.None,
            contentScale = ContentScale.Crop,
        )
        return
    }

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceElevated),
        contentAlignment = Alignment.Center,
    ) {
        if (item.isVideo) {
            Icon(
                imageVector = Icons.Outlined.VideoLibrary,
                contentDescription = null,
                tint = AccentRed,
            )
        } else {
            androidx.compose.foundation.Image(
                painter = fallbackPainter,
                contentDescription = item.title,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun PreviewPermissionState() {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfacePrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Falta permiso multimedia", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "SnapMusic necesita permiso para leer audio y video del dispositivo y mostrarte tu biblioteca local en Reproducir.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

@Composable
internal fun PreviewLibraryEmptyState() {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfacePrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("No encontramos archivos locales", style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                "Cuando tengas canciones o videos descargados en el dispositivo, SnapMusic te los va a listar acá en Reproducir.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}

private fun normalizedPreviewSubtitle(item: LocalMediaItem): String {
    return item.subtitle
        .removePrefix("Video local · ")
        .removePrefix("Audio local · ")
        .ifBlank { if (item.isVideo) "Video descargado" else "Audio descargado" }
}
