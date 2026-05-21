package com.juan.snapmusic.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.designsystem.AccentRed

private val SuggestionScreenBackground = Color(0xFF0A0A0D)
private val SuggestionFieldFill = Color(0xFF202026)
private val SuggestionFieldText = Color(0xFFF5F5F7)
private val SuggestionFieldHint = Color(0xFF8E8E97)
private val SuggestionIconTint = Color(0xFF70707A)
private val SuggestionChipFill = Color(0xFF17171B)

@Composable
internal fun HomeSearchSuggestionOverlay(
    state: HomeSearchSuggestionState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onSuggestionSelected: (String) -> Unit,
    onPopularSelected: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuggestionScreenBackground)
            .imePadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White,
                    )
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    color = SuggestionFieldFill,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester),
                            singleLine = true,
                            cursorBrush = SolidColor(AccentRed),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = SuggestionFieldText,
                                fontWeight = FontWeight.Medium,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                            decorationBox = { innerTextField ->
                                if (state.query.isBlank()) {
                                    Text(
                                        text = "Buscar para descargar",
                                        color = SuggestionFieldHint,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                innerTextField()
                            },
                        )
                        if (state.query.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Limpiar",
                                tint = SuggestionFieldHint,
                                modifier = Modifier.clickable(onClick = onClear),
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 18.dp),
            ) {
                if (state.mode == DownloadSearchUiMode.POPULAR) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Text(
                                text = "Búsquedas populares",
                                color = Color.White.copy(alpha = 0.86f),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.popularQueries.isEmpty()) {
                                Text(
                                    text = "Cargando tendencias musicales…",
                                    color = SuggestionFieldHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(
                                        items = state.popularQueries,
                                        key = { it },
                                        contentType = { "popular_chip" },
                                    ) { item ->
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = SuggestionChipFill,
                                            modifier = Modifier.clickable { onPopularSelected(item) },
                                        ) {
                                            Text(
                                                text = item,
                                                color = Color.White.copy(alpha = 0.92f),
                                                style = MaterialTheme.typography.labelMedium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = state.suggestions,
                        key = { it },
                        contentType = { "search_suggestion" },
                    ) { suggestion ->
                        SearchSuggestionRow(
                            suggestion = suggestion,
                            query = state.query,
                            onClick = { onSuggestionSelected(suggestion) },
                        )
                    }
                    if (state.isLoading && state.suggestions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 16.dp),
                            ) {
                                Text(
                                    text = "Buscando sugerencias…",
                                    color = SuggestionFieldHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    suggestion: String,
    query: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = SuggestionIconTint,
        )
        Text(
            text = rememberSuggestionLabel(suggestion = suggestion, query = query),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun rememberSuggestionLabel(
    suggestion: String,
    query: String,
) = remember(suggestion, query) {
    val normalizedQuery = query.trim()
    val lowerSuggestion = suggestion.lowercase()
    val lowerQuery = normalizedQuery.lowercase()
    val start = lowerSuggestion.indexOf(lowerQuery)
    buildAnnotatedString {
        if (start < 0 || lowerQuery.isBlank()) {
            append(suggestion)
        } else {
            append(suggestion.substring(0, start))
            pushStyle(SpanStyle(color = AccentRed, fontWeight = FontWeight.SemiBold))
            append(suggestion.substring(start, start + normalizedQuery.length))
            pop()
            append(suggestion.substring(start + normalizedQuery.length))
        }
    }
}
