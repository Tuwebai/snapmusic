package com.juan.snapmusic.feature.youtube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.platform.formatDuration
import java.text.DecimalFormat

@Composable
fun CommentPreviewCard(
    text: String,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Comentarios", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 2)
        }
    }
}

@Composable
fun InlineStatusCard(
    title: String,
    message: String,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 16.dp),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.25f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
fun YouTubeFeedRow(
    item: YouTubeFeedItem,
    onClick: () -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    val metaLabel = remember(item.author, item.viewCount, item.publishedText, item.durationSeconds) {
        feedMeta(item)
    }
    val thumbnailModel = remember(item.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(item.thumbnailUrl)
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(154, 88)
            .build()
    }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = thumbnailModel,
                contentDescription = item.title,
                modifier = Modifier
                    .size(width = 154.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(item.author, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                Text(metaLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
        }
        IconButton(onClick = onDownload) {
            Icon(Icons.Outlined.Download, contentDescription = "Descargar", tint = TextSecondary)
        }
    }
}

private fun feedMeta(item: YouTubeFeedItem): String {
    return listOfNotNull(
        item.viewCount?.let(::formatViews),
        item.publishedText?.takeIf { it.isNotBlank() },
        formatDuration(item.durationSeconds).takeIf { item.durationSeconds > 0 },
    ).joinToString(" · ")
}

private fun formatViews(value: Long): String {
    if (value < 1_000) return "$value vistas"
    val base = when {
        value >= 1_000_000_000 -> value / 1_000_000_000.0 to "B"
        value >= 1_000_000 -> value / 1_000_000.0 to "M"
        else -> value / 1_000.0 to "K"
    }
    return "${DecimalFormat("0.#").format(base.first)} ${base.second} vistas"
}
