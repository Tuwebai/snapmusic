package com.juan.snapmusic.feature.youtube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import coil.compose.rememberAsyncImagePainter
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.platform.formatDuration
import java.text.DecimalFormat

private val FeedViewCountFormat = DecimalFormat("0.#")

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
    onClick: (YouTubeFeedItem) -> Unit,
    onDownload: (YouTubeFeedItem) -> Unit,
) {
    val context = LocalContext.current
    val metaLabel = remember(item.author, item.viewCount, item.publishedText, item.durationSeconds) {
        feedMeta(item)
    }
    val thumbnailModel = remember(context, item.thumbnailUrl) {
        buildYouTubeThumbnailRequest(context, item.thumbnailUrl)
    }
    val thumbnailPainter = rememberAsyncImagePainter(
        model = thumbnailModel,
        filterQuality = FilterQuality.None,
    )
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable { onClick(item) },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = thumbnailPainter,
                contentDescription = null,
                modifier = Modifier
                    .size(width = 154.dp, height = 88.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                Text(item.author, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                Text(metaLabel, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
            }
        }
        IconButton(onClick = { onDownload(item) }) {
            Icon(Icons.Outlined.Download, contentDescription = "Descargar", tint = TextSecondary)
        }
    }
}

private fun feedMeta(item: YouTubeFeedItem): String {
    val builder = StringBuilder()
    fun appendPart(value: String) {
        if (value.isBlank()) return
        if (builder.isNotEmpty()) builder.append(" · ")
        builder.append(value)
    }
    item.viewCount?.let(::formatViews)?.let(::appendPart)
    item.publishedText?.let(::appendPart)
    if (item.durationSeconds > 0) {
        appendPart(formatDuration(item.durationSeconds))
    }
    return builder.toString()
}

private fun formatViews(value: Long): String {
    if (value < 1_000) return "$value vistas"
    val base = when {
        value >= 1_000_000_000 -> value / 1_000_000_000.0 to "B"
        value >= 1_000_000 -> value / 1_000_000.0 to "M"
        else -> value / 1_000.0 to "K"
    }
    return "${FeedViewCountFormat.format(base.first)} ${base.second} vistas"
}
