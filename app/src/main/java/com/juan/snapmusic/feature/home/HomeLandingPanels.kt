package com.juan.snapmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.juan.snapmusic.R
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BackgroundSecondary
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.ResolvedMedia
import com.juan.snapmusic.core.platform.formatDuration

private val SearchAccent = AccentRed
private val SearchBackground = Color(0xFF0D0D10)
private val SearchFieldFill = Color(0xCC141416)
private val SearchFieldBorder = Color(0x1FFFFFFF)

@Composable
internal fun HomeSearchLanding(
    padding: PaddingValues,
    query: String,
    onSearch: () -> Unit,
    onActivateSearch: () -> Unit,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
    onSelectConvert: () -> Unit,
) {
    val overlayBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xAA0B0B0E),
                Color(0xCC0C0C10),
                SearchBackground,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchBackground)
            .padding(padding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp),
        ) {
            SearchBackdrop(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(overlayBrush),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                HomeSectionTabs(
                    selectedTab = 0,
                    onSelectSearch = onSelectSearch,
                    onSelectYouTube = onSelectYouTube,
                    onSelectConvert = onSelectConvert,
                    selectedColor = SearchAccent,
                    unselectedColor = Color.White.copy(alpha = 0.62f),
                )
                Spacer(modifier = Modifier.weight(1f))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "SnapMusic",
                        color = SearchAccent,
                        fontWeight = FontWeight.Black,
                        fontSize = 34.sp,
                        letterSpacing = (-0.8).sp,
                    )
                    Text(
                        text = "Buscá en YouTube, abrí el stream al instante y descargá desde el mismo flujo.",
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    SearchCommandBar(
                        value = query,
                        onValueChange = {},
                        onSubmit = onSearch,
                        readOnly = true,
                        onActivate = onActivateSearch,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeConvertLanding(
    padding: PaddingValues,
    state: HomeUiState,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onPaste: () -> Unit,
    onPasteAndAnalyze: () -> Unit,
    onOpenFormats: () -> Unit,
    onQuickMp3: () -> Unit,
    onQuickM4a: () -> Unit,
    onQuickMp4: () -> Unit,
    onUseClipboard: () -> Unit,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
    onSelectConvert: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchBackground)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                HomeSectionTabs(
                    selectedTab = 2,
                    onSelectSearch = onSelectSearch,
                    onSelectYouTube = onSelectYouTube,
                    onSelectConvert = onSelectConvert,
                    selectedColor = SearchAccent,
                    unselectedColor = Color.White.copy(alpha = 0.55f),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Convertir link",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Pegá una URL de YouTube, analizala y mandala a la cola con atajos directos.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        state.clipboardCandidateUrl?.let { clipboardUrl ->
            item {
                ClipboardCard(
                    url = clipboardUrl,
                    onUse = onUseClipboard,
                )
            }
        }
        item {
            ConvertUrlCard(
                url = state.url,
                error = state.errorMessage,
                isAnalyzing = state.isAnalyzing,
                onUrlChange = onUrlChange,
                onAnalyze = onAnalyze,
                onPaste = onPaste,
                onPasteAndAnalyze = onPasteAndAnalyze,
            )
        }
        state.resolvedMedia?.let { media ->
            item {
                MediaHeroCard(
                    media = media,
                    onOpenFormats = onOpenFormats,
                    onQuickMp3 = onQuickMp3,
                    onQuickM4a = onQuickM4a,
                    onQuickMp4 = onQuickMp4,
                )
            }
        }
    }
}

@Composable
internal fun HomeSectionTabs(
    selectedTab: Int,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
    onSelectConvert: () -> Unit,
    selectedColor: Color = SearchAccent,
    unselectedColor: Color = Color.White.copy(alpha = 0.62f),
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        HomeSectionTab(
            label = "Buscar",
            selected = selectedTab == 0,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            onClick = onSelectSearch,
        )
        Spacer(modifier = Modifier.width(24.dp))
        HomeSectionTab(
            label = "YouTube",
            selected = selectedTab == 1,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            onClick = onSelectYouTube,
        )
        Spacer(modifier = Modifier.width(24.dp))
        HomeSectionTab(
            label = "Convertir",
            selected = selectedTab == 2,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
            onClick = onSelectConvert,
        )
    }
}

@Composable
private fun HomeSectionTab(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = label,
            color = if (selected) selectedColor else unselectedColor,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun SearchCommandBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    readOnly: Boolean = false,
    onActivate: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = SearchFieldFill,
        border = androidx.compose.foundation.BorderStroke(1.dp, SearchFieldBorder),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = readOnly, onClick = onActivate),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (readOnly) {
                    Text(
                        text = value.ifBlank { "Buscar para descargar" },
                        color = if (value.isBlank()) {
                            Color.White.copy(alpha = 0.42f)
                        } else {
                            Color.White
                        },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        readOnly = false,
                        cursorBrush = SolidColor(SearchAccent),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                        decorationBox = { innerTextField ->
                            if (value.isBlank()) {
                                Text(
                                    text = "Buscar para descargar",
                                    color = Color.White.copy(alpha = 0.42f),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            innerTextField()
                        },
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SearchAccent)
                    .clickable(onClick = if (readOnly) onActivate else onSubmit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Buscar",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SearchBackdrop(
    modifier: Modifier = Modifier,
) {
    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF11111A),
                Color(0xFF0D0D12),
                Color(0xFF09090D),
            ),
        )
    }
    val topGlowBrush = remember {
        Brush.radialGradient(
            colors = listOf(
                Color(0x55FF3B30),
                Color(0x22C2185B),
                Color.Transparent,
            ),
        )
    }
    Box(
        modifier = modifier
            .background(backgroundBrush),
    ) {
        SearchBackdropAccent(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 28.dp),
            containerColor = Color(0xFF1D4ED8).copy(alpha = 0.28f),
            accentColor = Color(0xFF60A5FA),
            icon = {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0xAA93C5FD),
                    modifier = Modifier.size(32.dp),
                )
            },
        )
        SearchBackdropAccent(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            containerColor = Color(0xFFDC2626).copy(alpha = 0.32f),
            accentColor = Color(0xFFF87171),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.SmartDisplay,
                    contentDescription = null,
                    tint = Color(0xAAFCA5A5),
                    modifier = Modifier.size(32.dp),
                )
            },
        )
        SearchBackdropAccent(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 30.dp, top = 24.dp),
            containerColor = Color(0xFF15803D).copy(alpha = 0.24f),
            accentColor = Color(0xFF4ADE80),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    tint = Color(0xAA86EFAC),
                    modifier = Modifier.size(30.dp),
                )
            },
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(260.dp)
                .clip(CircleShape)
                .background(topGlowBrush),
        )
    }
}

@Composable
private fun SearchBackdropAccent(
    modifier: Modifier = Modifier,
    containerColor: Color,
    accentColor: Color,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(104.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(containerColor)
            .border(1.dp, accentColor.copy(alpha = 0.16f), RoundedCornerShape(28.dp)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}

@Composable
private fun ConvertUrlCard(
    url: String,
    error: String?,
    isAnalyzing: Boolean,
    onUrlChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onPaste: () -> Unit,
    onPasteAndAnalyze: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Link de YouTube",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Outlined.Link, contentDescription = null)
                },
                placeholder = { Text("https://www.youtube.com/watch?v=...") },
                supportingText = {
                    if (error != null) {
                        Text(text = error, color = AccentRed)
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onAnalyze() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BackgroundSecondary,
                    unfocusedContainerColor = BackgroundSecondary,
                    disabledContainerColor = BackgroundSecondary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BackgroundSecondary),
                ) {
                    Text("Pegar")
                }
                Button(
                    onClick = onPasteAndAnalyze,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2E33)),
                ) {
                    Text("Pegar y analizar")
                }
            }
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
            ) {
                Text(if (isAnalyzing) "Analizando..." else "Analizar link")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaHeroCard(
    media: ResolvedMedia,
    onOpenFormats: () -> Unit,
    onQuickMp3: () -> Unit,
    onQuickM4a: () -> Unit,
    onQuickMp4: () -> Unit,
) {
    val context = LocalContext.current
    val heroImageModel = remember(media.sourceUrl, media.thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(media.thumbnailUrl)
            .memoryCacheKey("home-hero:${media.sourceUrl}:${media.thumbnailUrl}")
            .crossfade(false)
            .precision(Precision.INEXACT)
            .size(960, 540)
            .build()
    }
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = SurfaceElevated,
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = heroImageModel,
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(22.dp)),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
            )
            Text(text = media.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                text = "${media.author} · ${formatDuration(media.durationSeconds)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onOpenFormats,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
            ) {
                Text("Elegir formato")
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickPresetButton(label = "MP3 320", onClick = onQuickMp3)
                QuickPresetButton(label = "M4A", onClick = onQuickM4a)
                QuickPresetButton(label = "MP4 720p", onClick = onQuickMp4)
            }
        }
    }
}

@Composable
private fun ClipboardCard(
    url: String,
    onUse: () -> Unit,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = Color(0xFF16181D),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x26FFFFFF)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Link copiado detectado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(url, color = TextSecondary, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onUse,
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
            ) {
                Text("Usar link copiado")
            }
        }
    }
}

@Composable
private fun QuickPresetButton(
    label: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = BackgroundSecondary),
    ) {
        Text(label)
    }
}
