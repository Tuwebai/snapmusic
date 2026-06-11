package com.juan.snapmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BackgroundSecondary
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.MediaKind
import com.juan.snapmusic.core.model.MediaVariant
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.platform.formatDuration
import kotlin.math.roundToInt

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun DownloadFormatSheet(
    media: ResolvedMedia,
    isPreparing: Boolean = false,
    allowedKinds: Set<MediaKind> = setOf(MediaKind.AUDIO, MediaKind.VIDEO),
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val defaultTab = remember(allowedKinds) {
        listOf(MediaKind.AUDIO, MediaKind.VIDEO).firstOrNull { it in allowedKinds } ?: MediaKind.AUDIO
    }
    var activeTab by rememberSaveable(media.sourceUrl, defaultTab.name) { mutableStateOf(defaultTab) }
    val effectiveTab = if (activeTab in allowedKinds) activeTab else defaultTab
    var selectedVariantId by rememberSaveable { mutableStateOf<String?>(null) }
    val variants = remember(media, effectiveTab) { modalVariants(media, effectiveTab) }
    val effectiveSelectedVariantId = selectedVariantId ?: variants.singleOrNull()?.id
    val selected = variants.firstOrNull { it.id == effectiveSelectedVariantId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.downloadFormatMaterialReveal(revealKey = media.sourceUrl),
        containerColor = SurfacePrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 44.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(TextSecondary.copy(alpha = 0.35f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(media)
            if (allowedKinds.size > 1) {
                TabSelector(
                    activeTab = effectiveTab,
                    onTabChange = {
                        if (it in allowedKinds) {
                            activeTab = it
                            selectedVariantId = null
                        }
                    },
                )
            }
            if (isPreparing) {
                PreparingFormatsStatus()
            } else if (errorMessage != null) {
                DownloadSheetErrorStatus(errorMessage)
            } else {
                FormatGrid(
                    media = media,
                    variants = variants,
                    selectedVariantId = effectiveSelectedVariantId,
                    onSelect = { selectedVariantId = it },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cerrar")
                }
                Button(
                    onClick = { selected?.let { onConfirm(it.id) } },
                    modifier = Modifier.weight(1f),
                    enabled = selected != null && !isPreparing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                ) {
                    if (isPreparing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = when {
                            isPreparing -> "Preparando formatos"
                            selected != null -> "Descargar ${selected.container.name} · ${variantQuality(selected)}"
                            else -> "Seleccioná un formato"
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadSheetErrorStatus(message: String) {
    Text(
        text = message,
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(18.dp))
            .padding(16.dp),
    )
}

@Composable
private fun PreparingFormatsStatus() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = "Preparando formatos reales de descarga...",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun Header(media: ResolvedMedia) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        AsyncImage(
            model = media.thumbnailUrl,
            contentDescription = media.title,
            modifier = Modifier
                .size(width = 108.dp, height = 64.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = media.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(text = media.author, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text(text = formatDuration(media.durationSeconds), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}

@Composable
private fun TabSelector(
    activeTab: MediaKind,
    onTabChange: (MediaKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundSecondary, RoundedCornerShape(16.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TabPill("Audio", Icons.Outlined.MusicNote, activeTab == MediaKind.AUDIO) { onTabChange(MediaKind.AUDIO) }
        TabPill("Video", Icons.Outlined.Videocam, activeTab == MediaKind.VIDEO) { onTabChange(MediaKind.VIDEO) }
    }
}

@Composable
private fun RowScope.TabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) SurfacePrimary else BackgroundSecondary,
            contentColor = if (selected) AccentRed else TextSecondary,
        ),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FormatGrid(
    media: ResolvedMedia,
    variants: List<MediaVariant>,
    selectedVariantId: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        maxItemsInEachRow = 2,
    ) {
        variants.forEach { variant ->
            val selected = variant.id == selectedVariantId
            Button(
                onClick = { onSelect(variant.id) },
                modifier = Modifier.widthIn(min = 148.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selected) AccentRed.copy(alpha = 0.18f) else SurfaceElevated,
                    contentColor = TextPrimary,
                    disabledContainerColor = SurfaceElevated,
                    disabledContentColor = TextPrimary,
                ),
                enabled = true,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(variant.container.name, fontWeight = FontWeight.SemiBold)
                        if (selected) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    }
                    Text(variantQuality(variant), color = if (selected) TextPrimary else TextSecondary, style = MaterialTheme.typography.bodySmall)
                    estimateSizeText(media, variant)?.let {
                        Text(it, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun modalVariants(
    media: ResolvedMedia,
    activeTab: MediaKind,
): List<MediaVariant> {
    val base = if (activeTab == MediaKind.AUDIO) media.audioVariants else media.videoVariants
    return base.sortedWith(
        compareByDescending<MediaVariant> { modalPriority(it) }
            .thenByDescending { it.bitrateKbps ?: resolutionScore(it.resolution) }
            .thenBy { it.container.name },
    )
}

private fun modalPriority(variant: MediaVariant): Int = when (variant.container.name) {
    "MP3" -> 4
    "M4A" -> 3
    "MP4" -> 2
    else -> 1
}

private fun variantQuality(variant: MediaVariant): String {
    return when (variant.kind) {
        MediaKind.AUDIO -> variant.bitrateKbps?.let { "${it}kbps" } ?: "Directo"
        MediaKind.VIDEO -> variant.resolution ?: "Directo"
    }
}

private fun estimateSizeText(
    media: ResolvedMedia,
    variant: MediaVariant,
): String? {
    val seconds = media.durationSeconds.takeIf { it > 0 } ?: return null
    val bitsPerSecond = when (variant.kind) {
        MediaKind.AUDIO -> (variant.bitrateKbps ?: 128) * 1000
        MediaKind.VIDEO -> resolutionScore(variant.resolution) * 1000
    }
    val megaBytes = (seconds * bitsPerSecond / 8.0) / 1_000_000.0
    return "~${megaBytes.roundToInt()} MB"
}

private fun resolutionScore(resolution: String?): Int = when {
    resolution == null -> 2_500
    "1080" in resolution -> 8_000
    "720" in resolution -> 5_000
    "480" in resolution -> 2_500
    "360" in resolution -> 1_300
    else -> 2_500
}
