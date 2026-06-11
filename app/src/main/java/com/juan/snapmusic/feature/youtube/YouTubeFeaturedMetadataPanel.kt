package com.juan.snapmusic.feature.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo

@Composable
internal fun FeaturedVideoMetadataPanel(
    featured: YouTubeFeaturedVideo,
    featuredAvatarModel: ImageRequest,
    isDownloadEnabled: Boolean,
    autoplayEnabled: Boolean,
    nextUpLabel: String?,
    onDownload: () -> Unit,
    onArtistClick: (String) -> Unit,
) {
    val cinematicBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                AccentRed.copy(alpha = 0.12f),
                SurfacePrimary.copy(alpha = 0.98f),
                SurfacePrimary,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfacePrimary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(cinematicBrush),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeaturedVideoTitleRow(featured, featuredAvatarModel, onArtistClick)
            FeaturedVideoNextUpLabel(nextUpLabel, autoplayEnabled)
            FeaturedVideoDownloadRow(isDownloadEnabled, onDownload)
        }
    }
}

@Composable
private fun FeaturedVideoTitleRow(
    featured: YouTubeFeaturedVideo,
    featuredAvatarModel: ImageRequest,
    onArtistClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = featuredAvatarModel,
            contentDescription = featured.title,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
            filterQuality = FilterQuality.Low,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(featured.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            YouTubeFeaturedMetadataText(featured = featured, onArtistClick = onArtistClick)
        }
    }
}

@Composable
private fun FeaturedVideoNextUpLabel(
    nextUpLabel: String?,
    autoplayEnabled: Boolean,
) {
    nextUpLabel?.takeIf { autoplayEnabled }?.let { title ->
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sigue: $title",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeaturedVideoDownloadRow(
    isDownloadEnabled: Boolean,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onDownload,
            enabled = isDownloadEnabled,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentRed,
                contentColor = SurfacePrimary,
                disabledContainerColor = AccentRed.copy(alpha = 0.4f),
                disabledContentColor = SurfacePrimary,
            ),
            shape = RoundedCornerShape(999.dp),
        ) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Text(
                text = if (isDownloadEnabled) "Descargar" else "Preparando descarga...",
                modifier = Modifier.padding(start = 8.dp),
                fontWeight = FontWeight.SemiBold,
            )
        }
        YouTubeCastRouteButton(modifier = Modifier.size(48.dp))
    }
}
