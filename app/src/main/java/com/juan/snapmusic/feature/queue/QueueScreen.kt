package com.juan.snapmusic.feature.queue

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Downloading
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownloadOff
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.core.model.QueueStatus
import com.juan.snapmusic.core.platform.buildOpenFileIntent
import com.juan.snapmusic.core.platform.buildOpenFolderIntent
import com.juan.snapmusic.core.platform.buildShareIntent
import com.juan.snapmusic.core.platform.formatTimestamp
import com.juan.snapmusic.feature.home.SnapMusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    onOpenPreview: (QueueEntry) -> Unit,
) {
    val context = LocalContext.current
    val items by viewModel.queue.collectAsStateWithLifecycle()
    val feedback by viewModel.queueFeedback.collectAsStateWithLifecycle()
    val activeItems = remember(items) {
        items.filter { it.status == QueueStatus.RUNNING || it.status == QueueStatus.PENDING || it.status == QueueStatus.PAUSED }
    }
    val archivedItems = remember(items) {
        items.filterNot { it.status == QueueStatus.RUNNING || it.status == QueueStatus.PENDING || it.status == QueueStatus.PAUSED }
    }
    var modalItem by remember { mutableStateOf<QueueEntry?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf(QueueHubTab.QUEUE) }

    LaunchedEffect(feedback) {
        feedback?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeQueueFeedback()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        QueueHeader(totalCount = items.size, activeCount = activeItems.size)
        QueueHubTabs(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            queueCount = activeItems.size,
            historyCount = archivedItems.size,
        )
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                QueueHubTab.QUEUE -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        if (items.isEmpty()) {
                            item { EmptyQueueState() }
                            return@LazyColumn
                        }

                        if (activeItems.isNotEmpty()) {
                            item {
                                SectionTitle("Descargas activas", "${activeItems.size} en curso")
                            }
                            items(
                                items = activeItems,
                                key = { it.id },
                                contentType = { "active_queue_item" },
                            ) { item ->
                                ActiveQueueCard(
                                    item = item,
                                    onPause = { viewModel.pauseQueue(item.id) },
                                    onResume = { viewModel.resumeQueue(item.id) },
                                    onCancel = { viewModel.cancelQueue(item.id) },
                                    onRemove = { viewModel.removeQueueItem(item.id) },
                                )
                            }
                        }
                        if (activeItems.isEmpty() && archivedItems.isNotEmpty()) {
                            item {
                                EmptyQueueTabState(
                                    title = "No hay descargas activas",
                                    subtitle = "Pasate a Historial para ver lo último que ya salió de la cola.",
                                )
                            }
                        }
                    }
                }

                QueueHubTab.HISTORY -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 28.dp),
                    ) {
                        if (archivedItems.isEmpty()) {
                            item {
                                EmptyQueueTabState(
                                    title = "Todavía no hay historial",
                                    subtitle = "Cuando termine una descarga o haya actividad, va a aparecer acá.",
                                )
                            }
                            return@LazyColumn
                        }
                        item {
                            SectionTitle("Historial", "Las canciones y videos más recientes quedan arriba.")
                        }
                        items(
                            items = archivedItems,
                            key = { it.id },
                            contentType = { "archived_queue_item" },
                        ) { item ->
                            ArchivedQueueCard(
                                item = item,
                                onOpenPreview = { onOpenPreview(item) },
                                onOpenActions = { modalItem = item },
                            )
                        }
                    }
                }
            }
        }
    }

    modalItem?.let { item ->
        ArchivedActionsSheet(
            item = item,
            onDismiss = { modalItem = null },
            onOpenFile = if (item.status == QueueStatus.SUCCESS && item.outputUri != null) {
                {
                    modalItem = null
                    val intent = buildOpenFileIntent(
                        context = context,
                        outputUri = item.outputUri.orEmpty(),
                        title = item.title,
                        format = item.container,
                    )
                    if (intent == null) {
                        Toast.makeText(context, "No pudimos abrir ese archivo.", Toast.LENGTH_SHORT).show()
                    } else {
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "No encontramos una app para abrir esa descarga.", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            } else {
                null
            },
            onOpenFolder = if (item.status == QueueStatus.SUCCESS && item.outputUri != null) {
                {
                    modalItem = null
                    runCatching { context.startActivity(buildOpenFolderIntent(context, item.outputUri.orEmpty())) }
                        .onFailure {
                            Toast.makeText(context, "No pudimos abrir la carpeta de esa descarga.", Toast.LENGTH_SHORT).show()
                        }
                }
            } else {
                null
            },
            onShare = if (item.status == QueueStatus.SUCCESS && item.outputUri != null) {
                {
                    modalItem = null
                    val shareIntent = buildShareIntent(
                        context = context,
                        outputUri = item.outputUri.orEmpty(),
                        title = item.title,
                        format = item.container,
                    )
                    if (shareIntent == null) {
                        Toast.makeText(context, "No pudimos preparar ese archivo para compartir.", Toast.LENGTH_SHORT).show()
                    } else {
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir descarga"))
                    }
                }
            } else {
                null
            },
            onRetry = {
                modalItem = null
                selectedTab = QueueHubTab.QUEUE
                viewModel.retryQueueItem(item.id)
            },
            onDelete = if (item.status == QueueStatus.SUCCESS && item.outputUri != null) {
                {
                    modalItem = null
                    viewModel.deleteDownloadedItem(item)
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun QueueHubTabs(
    selectedTab: QueueHubTab,
    onTabSelected: (QueueHubTab) -> Unit,
    queueCount: Int,
    historyCount: Int,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .background(SurfaceElevated, RoundedCornerShape(22.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QueueHubPill(
            label = if (queueCount > 0) "Cola ($queueCount)" else "Cola",
            selected = selectedTab == QueueHubTab.QUEUE,
            onClick = { onTabSelected(QueueHubTab.QUEUE) },
        )
        QueueHubPill(
            label = if (historyCount > 0) "Historial ($historyCount)" else "Historial",
            selected = selectedTab == QueueHubTab.HISTORY,
            onClick = { onTabSelected(QueueHubTab.HISTORY) },
        )
    }
}

@Composable
private fun EmptyQueueTabState(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun RowScope.QueueHubPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) SurfacePrimary else SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun QueueHeader(
    totalCount: Int,
    activeCount: Int,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .background(AccentRed.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                    .padding(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Downloading,
                    contentDescription = null,
                    tint = AccentRed,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Descargas", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                    if (totalCount > 0) {
                        CountBadge(totalCount.toString())
                    }
                }
                Text(
                    text = if (activeCount > 0) {
                        "SnapMusic está procesando tus archivos en segundo plano."
                    } else {
                        "Acá vas a ver el progreso y el estado final de cada descarga."
                    },
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun EmptyQueueState() {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(SurfacePrimary, RoundedCornerShape(24.dp))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.FileDownloadOff,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.size(54.dp),
        )
        Text("Sin descargas todavía", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cuando confirmes un formato, SnapMusic te va a traer directo acá para que veas el progreso.",
            color = TextSecondary,
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

