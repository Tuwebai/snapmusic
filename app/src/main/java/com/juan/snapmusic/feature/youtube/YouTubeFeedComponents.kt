package com.juan.snapmusic.feature.youtube


import android.graphics.Color
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BackgroundSecondary
import com.juan.snapmusic.core.designsystem.BorderSubtle
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.SurfacePrimary
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.designsystem.WarningAmber
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo
import com.juan.snapmusic.core.model.YouTubeFeedItem
import com.juan.snapmusic.core.platform.formatDuration
import com.juan.snapmusic.feature.player.VideoFullscreenOverlay
import com.juan.snapmusic.feature.player.LandscapeFullscreenVideoDialog
import com.juan.snapmusic.feature.player.PlaybackOverlayState
import com.juan.snapmusic.feature.player.PlayerSurface
import com.juan.snapmusic.feature.player.rememberPlaybackOverlayState
import com.juan.snapmusic.feature.player.DOUBLE_TAP_SEEK_MS
import com.juan.snapmusic.feature.player.seekByClamped
import com.juan.snapmusic.feature.player.videoDoubleTapSeek
import androidx.compose.ui.text.style.TextOverflow
import java.text.DecimalFormat
import kotlinx.coroutines.delay

private val WatchPlayerHeight = 304.dp

@Composable
fun HeroLoadingState() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WatchPlayerHeight)
                .clip(RoundedCornerShape(28.dp))
                .background(SurfacePrimary),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = WarningAmber)
        }
        Text("Cargando videos reales de YouTube...", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouTubeSearchPanel(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onPreset: (String) -> Unit,
) {
    val presets = listOf("Cumbia 2025", "Cuarteto en vivo", "Mix DJ", "Enganchados", "Roze Oficial")
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Explorar más", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Buscar otro video o artista") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = onSearch, enabled = !isLoading) {
                        Icon(Icons.Outlined.Search, contentDescription = "Buscar")
                    }
                },
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .background(SurfaceElevated, RoundedCornerShape(18.dp))
                    .size(52.dp),
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { preset ->
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onPreset(preset) },
                    color = BackgroundSecondary,
                ) {
                    Text(
                        text = preset,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
fun SuggestionsHeader() {
    Text(
        text = "Seguí mirando",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        color = TextPrimary,
    )
}

internal data class WatchQualityOption(
    val id: String,
    val label: String,
    val targetHeight: Int? = null,
    val mode: PlaybackQualityMode = PlaybackQualityMode.PROGRESSIVE_WITH_AUDIO,
)

internal enum class PlaybackQualityMode {
    AUTO,
    ADAPTIVE_EXACT,
    PROGRESSIVE_WITH_AUDIO,
}

internal enum class WatchSheetMode {
    MAIN,
    QUALITY,
    SPEED,
}

internal fun YouTubeFeaturedVideo.toWatchQualityOptions(): List<WatchQualityOption> {
    val resolved = resolvedMedia ?: return emptyList()
    val adaptivePlayback = resolved.adaptivePlaybackUrl?.let { watchHasAdaptivePlaybackUrl(it) } == true
    val preferredHeights = listOf(1080, 720, 480, 360, 240, 144)
    val automaticPreferredHeights = listOf(720, 1080, 480, 360, 240, 144)
    val variantsByHeight = resolved.videoVariants
        .mapNotNull { variant ->
            val height = variant.resolution?.substringBefore('p')?.toIntOrNull() ?: return@mapNotNull null
            height to variant
        }
        .groupBy({ it.first }, { it.second })
    val adaptiveHeights = if (adaptivePlayback) {
        availablePlaybackHeights.ifEmpty { variantsByHeight.keys.toList() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
    } else {
        emptyList()
    }
    return buildList {
        val automaticHeight = automaticPreferredHeights.firstOrNull { it in adaptiveHeights || it in variantsByHeight }
            ?: adaptiveHeights.maxOrNull()
            ?: variantsByHeight.keys.maxOrNull()
        if (!resolved.playbackUrl.isNullOrBlank() || automaticHeight != null) {
            add(
                WatchQualityOption(
                    id = "auto",
                    label = automaticHeight?.let { "Automático · ${it}P" } ?: "Automático",
                    targetHeight = automaticHeight,
                    mode = PlaybackQualityMode.AUTO,
                ),
            )
        }
        if (adaptivePlayback && adaptiveHeights.isNotEmpty()) {
            adaptiveHeights.forEach { target ->
                add(
                    WatchQualityOption(
                        id = "adaptive-$target",
                        label = watchQualityLabel(target),
                        targetHeight = target,
                        mode = PlaybackQualityMode.ADAPTIVE_EXACT,
                    ),
                )
            }
        } else {
            preferredHeights.forEach { target ->
                variantsByHeight[target]?.firstOrNull()?.let { variant ->
                    add(
                        WatchQualityOption(
                            id = variant.id,
                            label = watchQualityLabel(target),
                            targetHeight = target,
                        ),
                    )
                }
            }
        }
        if (size <= 1) {
            variantsByHeight.keys
                .sortedDescending()
                .forEach { height ->
                    variantsByHeight[height]?.firstOrNull()?.let { variant ->
                        if (none { it.id == variant.id }) {
                            add(
                                WatchQualityOption(
                                    id = variant.id,
                                    label = watchQualityLabel(height),
                                    targetHeight = height,
                                ),
                            )
                        }
                    }
                }
        }
    }.distinctBy { it.id }
}

internal fun YouTubeFeaturedVideo.currentQualityLabel(): String {
    val options = toWatchQualityOptions()
    return when {
        actualPlaybackLabel != null -> actualPlaybackLabel
        selectedVideoQualityId == "auto" -> options.firstOrNull { it.id == "auto" }?.label ?: "Automático"
        else -> "Aplicando calidad..."
    }
}

internal fun watchQualityLabel(height: Int): String = when {
    height >= 1080 -> "Muy alto · ${height}P HD"
    height >= 720 -> "Alta · ${height}P HD"
    height >= 480 -> "Media · ${height}P"
    else -> "Baja · ${height}P"
}

internal fun watchHasAdaptivePlaybackUrl(url: String): Boolean {
    return url.isNotBlank()
}

@Composable
private fun OverlayControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        color = SurfaceElevated.copy(alpha = 0.92f),
    ) {
        Box(
            modifier = Modifier.padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WatchStreamOptionsSheet(
    mode: WatchSheetMode,
    qualityOptions: List<WatchQualityOption>,
    currentQualityLabel: String,
    selectedQualityId: String,
    playbackSpeed: Float,
    autoplayEnabled: Boolean,
    loopEnabled: Boolean,
    subtitlesAvailable: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenSpeed: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onToggleLoop: () -> Unit,
    onQualitySelected: (String) -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfacePrimary,
        contentColor = TextPrimary,
    ) {
        when (mode) {
            WatchSheetMode.MAIN -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatchSheetRow(
                        title = "Calidad",
                        value = currentQualityLabel,
                        enabled = qualityOptions.isNotEmpty(),
                        onClick = onOpenQuality,
                    )
                    WatchSheetRow(
                        title = "Subtítulos",
                        value = if (subtitlesAvailable) "Disponibles en este stream" else "No disponibles",
                        enabled = false,
                        onClick = {},
                    )
                    WatchSheetRow(
                        title = "Velocidad de playback",
                        value = "${DecimalFormat("0.##").format(playbackSpeed)}X",
                        onClick = onOpenSpeed,
                    )
                    WatchSheetRow(
                        title = "Autoreproducción",
                        value = if (autoplayEnabled) "Encendido" else "Apagado",
                        onClick = onToggleAutoplay,
                    )
                    WatchSheetRow(
                        title = "Video en repetición",
                        value = if (loopEnabled) "Encendido" else "Apagar",
                        onClick = onToggleLoop,
                    )
                }
            }
            WatchSheetMode.QUALITY -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatchSheetHeader(title = "Calidad", onBack = onBack)
                    qualityOptions.forEach { option ->
                        WatchSheetRow(
                            title = option.label,
                            value = if (option.id == selectedQualityId) "Elegida" else "",
                            onClick = { onQualitySelected(option.id) },
                        )
                    }
                }
            }
            WatchSheetMode.SPEED -> {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    WatchSheetHeader(title = "Velocidad de playback", onBack = onBack)
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                        WatchSheetRow(
                            title = "${DecimalFormat("0.##").format(option)}X",
                            value = if (kotlin.math.abs(playbackSpeed - option) < 0.01f) "Activa" else "",
                            onClick = { onSpeedSelected(option) },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
internal fun WatchSheetHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Outlined.KeyboardArrowLeft, contentDescription = "Volver", tint = TextPrimary)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
    }
    HorizontalDivider(color = BorderSubtle)
}

@Composable
internal fun WatchSheetRow(
    title: String,
    value: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfacePrimary,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
