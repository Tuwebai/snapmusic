package com.juan.snapmusic.feature.youtube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.platform.formatDuration

@Composable
internal fun YouTubeArtistChip(
    author: String,
    onClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val label = remember(author) { author.trim() }
    if (label.isBlank()) return
    if (onClick == null) {
        Text(
            text = label,
            modifier = modifier,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }
    SuggestionChip(
        onClick = { onClick(label) },
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier
            .heightIn(min = 24.dp)
            .widthIn(max = 220.dp),
        shape = RoundedCornerShape(999.dp),
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = SurfaceElevated.copy(alpha = 0.42f),
            labelColor = TextPrimary,
        ),
        border = BorderStroke(1.dp, BorderSubtle.copy(alpha = 0.5f)),
    )
}

@Composable
internal fun YouTubeFeaturedMetadataText(
    featured: YouTubeFeaturedVideo,
    onArtistClick: (String) -> Unit,
) {
    Column {
        YouTubeArtistChip(author = featured.author, onClick = onArtistClick)
        featuredInfoMeta(featured).takeIf { it.isNotBlank() }?.let { meta ->
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun featuredInfoMeta(featured: YouTubeFeaturedVideo): String {
    return listOfNotNull(
        featured.publishedText?.takeIf { it.isNotBlank() },
        formatDuration(featured.durationSeconds).takeIf { featured.durationSeconds > 0 },
    ).joinToString(" · ")
}
