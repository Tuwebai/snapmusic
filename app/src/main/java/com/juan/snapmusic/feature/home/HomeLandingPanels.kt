package com.juan.snapmusic.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.juan.snapmusic.core.designsystem.AccentRed

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
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .homeTabSwipe(
                canSwipeLeft = true,
                canSwipeRight = false,
                onSwipeLeft = onSelectYouTube,
                onSwipeRight = onSelectSearch,
            )
            .background(SearchBackground)
            .padding(padding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
        ) {
            SearchBackdrop(modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD40C0C10)),
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
internal fun HomeSectionTabs(
    selectedTab: Int,
    onSelectSearch: () -> Unit,
    onSelectYouTube: () -> Unit,
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
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
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
                        color = if (value.isBlank()) Color.White.copy(alpha = 0.42f) else Color.White,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 1,
                    )
                } else {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        cursorBrush = SolidColor(SearchAccent),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
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
                Icon(Icons.Outlined.Search, contentDescription = "Buscar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun SearchBackdrop(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(Color(0xFF0D0D12))) {
        SearchBackdropCard(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 26.dp),
            containerColor = Color(0xFF1D4ED8).copy(alpha = 0.28f),
            accentColor = Color(0xFF60A5FA),
            icon = {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0xAA93C5FD),
                    modifier = Modifier.size(38.dp),
                )
            },
        )
        SearchBackdropCard(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp),
            containerColor = Color(0xFFDC2626).copy(alpha = 0.32f),
            accentColor = Color(0xFFF87171),
            icon = {
                Icon(
                    imageVector = Icons.Outlined.SmartDisplay,
                    contentDescription = null,
                    tint = Color(0xAAFCA5A5),
                    modifier = Modifier.size(38.dp),
                )
            },
        )
    }
}

@Composable
private fun SearchBackdropCard(
    modifier: Modifier = Modifier,
    containerColor: Color,
    accentColor: Color,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.size(96.dp),
        color = containerColor,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.16f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            icon()
        }
    }
}
