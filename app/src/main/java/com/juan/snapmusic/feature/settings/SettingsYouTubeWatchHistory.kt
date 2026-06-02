package com.juan.snapmusic.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.juan.snapmusic.core.designsystem.BackgroundPrimary
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry
import com.juan.snapmusic.core.platform.formatDuration
import com.juan.snapmusic.feature.youtube.YouTubeFeedRow
import com.juan.snapmusic.feature.youtube.buildYouTubeThumbnailRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun SettingsHistoryHero(
    items: List<YouTubeWatchHistoryEntry>,
    onClick: () -> Unit,
    onPlay: (YouTubeWatchHistoryEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Historial", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (items.isEmpty()) {
            Text(
                "Los videos que reproduzcas aparecerán acá.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = items.take(8),
                    key = YouTubeWatchHistoryEntry::sourceUrl,
                    contentType = { "settings_watch_history_preview" },
                ) { item ->
                    WatchHistoryPreviewCard(
                        item = item,
                        modifier = Modifier.width(188.dp),
                        onClick = { onPlay(item) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun YouTubeWatchHistoryPane(
    items: List<YouTubeWatchHistoryEntry>,
    onBack: () -> Unit,
    onPlay: (YouTubeWatchHistoryEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredItems = remember(items, query) {
        val normalized = query.trim()
        if (normalized.isBlank()) {
            items
        } else {
            items.filter { item ->
                item.title.contains(normalized, ignoreCase = true) ||
                    item.author.contains(normalized, ignoreCase = true)
            }
        }
    }
    val groupedItems = remember(filteredItems) {
        filteredItems.groupBy { watchHistoryDayLabel(it.watchedAt) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        item { SettingsPaneHeader("Historial", onBack) }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp)),
                placeholder = {
                    Text("Buscar en tu historial de reproducciones", color = TextSecondary)
                },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary)
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = TextPrimary,
                ),
            )
        }
        if (filteredItems.isEmpty()) {
            item {
                Text(
                    text = if (query.isBlank()) {
                        "Todavía no hay reproducciones registradas."
                    } else {
                        "No encontramos videos en este historial."
                    },
                    modifier = Modifier.padding(24.dp),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            groupedItems.forEach { (dayLabel, dayItems) ->
                item(key = "watch-history-day-$dayLabel") {
                    Text(
                        text = dayLabel,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                items(
                    items = dayItems,
                    key = YouTubeWatchHistoryEntry::sourceUrl,
                    contentType = { "youtube_watch_history_item" },
                ) { item ->
                    YouTubeFeedRow(
                        item = item.toFeedItem(),
                        onClick = { onPlay(item) },
                        onDownload = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun WatchHistoryPreviewCard(
    item: YouTubeWatchHistoryEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = remember(density) { with(density) { 188.dp.roundToPx() } }
    val heightPx = remember(density) { with(density) { 118.dp.roundToPx() } }
    val thumbnailRequest = remember(context, item.thumbnailUrl, widthPx, heightPx) {
        buildYouTubeThumbnailRequest(
            context = context,
            thumbnailUrl = item.thumbnailUrl,
            widthPx = widthPx,
            heightPx = heightPx,
        )
    }
    val thumbnailPainter = rememberAsyncImagePainter(
        model = thumbnailRequest,
        filterQuality = FilterQuality.None,
    )

    Column(
        modifier = modifier.clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Image(
                painter = thumbnailPainter,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
            val progress = watchProgressFraction(item)
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(Color.Red),
                )
            }
            if (item.durationSeconds > 0) {
                Text(
                    text = formatDuration(item.durationSeconds),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            item.title,
            color = TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            watchHistoryResumeLabel(item.lastPositionMs) ?: item.author,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun YouTubeWatchHistoryEntry.toFeedItem(): YouTubeFeedItem {
    return YouTubeFeedItem(
        url = sourceUrl,
        title = title,
        author = author,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        viewCount = viewCount,
        publishedText = watchHistoryResumeLabel(lastPositionMs) ?: publishedText,
        description = description,
    )
}

private fun watchHistoryResumeLabel(positionMs: Long): String? {
    if (positionMs <= 0L) return null
    return "Visto hasta ${formatDuration((positionMs / 1_000L).coerceAtLeast(1L))}"
}

private fun watchProgressFraction(item: YouTubeWatchHistoryEntry): Float {
    val durationMs = item.durationSeconds * 1_000L
    if (durationMs <= 0L || item.lastPositionMs <= 0L) return 0f
    return (item.lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

private fun watchHistoryDayLabel(timestampMs: Long): String {
    return SimpleDateFormat("d MMM", Locale("es", "AR")).format(Date(timestampMs))
}
