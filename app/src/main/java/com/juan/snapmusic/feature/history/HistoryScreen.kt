package com.juan.snapmusic.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juan.snapmusic.core.platform.formatTimestamp
import com.juan.snapmusic.core.ui.AppHeader
import com.juan.snapmusic.feature.home.SnapMusicViewModel

@Composable
fun HistoryScreen(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
) {
    val items by viewModel.history.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            AppHeader("Historial", "Últimos resultados guardados por SnapMusic en este dispositivo.")
        }
        items(
            items = items,
            key = { item -> item.id },
            contentType = { "history_item" },
        ) { item ->
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text("${item.qualityLabel} · ${item.format.name}")
                Text(formatTimestamp(item.createdAt), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
