package com.juan.snapmusic.feature.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.QueueEntry
import com.juan.snapmusic.core.model.QueueStatus

@Composable
internal fun PreviewDownloadsSummaryCard(
    activeDownloads: List<QueueEntry>,
    onOpenDownloads: () -> Unit,
) {
    if (activeDownloads.isEmpty()) return

    Surface(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clickable(onClick = onOpenDownloads),
        shape = RoundedCornerShape(28.dp),
        color = SurfacePrimary,
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Descargando(${activeDownloads.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Tocá para ver el progreso en detalle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AccentRed)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (activeDownloads.size > 99) "99+" else activeDownloads.size.toString(),
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            activeDownloads.take(2).forEach { item ->
                PreviewDownloadRow(
                    item = item,
                    onClick = onOpenDownloads,
                )
            }
        }
    }
}

@Composable
internal fun PreviewDownloadsDetailScreen(
    activeDownloads: List<QueueEntry>,
    onBack: () -> Unit,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .clickable(onClick = onBack)
                        .padding(10.dp),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver", tint = TextPrimary)
                }
                Text(
                    text = "Descargando${if (activeDownloads.isNotEmpty()) "(${activeDownloads.size})" else ""}",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (activeDownloads.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceElevated)
                        .clickable(onClick = onCancelAll)
                        .padding(10.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Cancelar todo", tint = TextPrimary)
                }
            }
        }

        if (activeDownloads.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                activeDownloads.forEach { item ->
                    PreviewDownloadRow(
                        item = item,
                        onCancel = { onCancel(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewDownloadRow(
    item: QueueEntry,
    onClick: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(24.dp),
        color = SurfacePrimary,
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PreviewDownloadArtwork(item)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = downloadSubtitle(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { item.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = AccentRed,
                    trackColor = SurfaceElevated,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = statusLabel(item.status),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.status == QueueStatus.RUNNING) AccentRed else TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${item.progress.coerceIn(0, 100)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                }
            }
            if (onCancel != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .clickable(onClick = onCancel)
                        .padding(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PauseCircle,
                        contentDescription = "Cancelar descarga",
                        tint = TextPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewDownloadArtwork(item: QueueEntry) {
    AsyncImage(
        model = item.thumbnailUrl,
        contentDescription = item.title,
        modifier = Modifier
            .size(width = 84.dp, height = 56.dp)
            .clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Crop,
    )
}

private fun downloadSubtitle(item: QueueEntry): String {
    return buildString {
        append(item.variantLabel)
        if (item.author.isNotBlank()) {
            append(" · ")
            append(item.author)
        }
    }
}

private fun statusLabel(status: QueueStatus): String {
    return when (status) {
        QueueStatus.RUNNING -> "Descargando"
        QueueStatus.PENDING -> "En cola"
        QueueStatus.SUCCESS -> "Completado"
        QueueStatus.ERROR -> "Error"
        QueueStatus.CANCELLED -> "Cancelado"
    }
}
