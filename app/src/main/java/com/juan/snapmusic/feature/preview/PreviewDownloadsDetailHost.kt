package com.juan.snapmusic.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juan.snapmusic.feature.home.SnapMusicViewModel
import com.juan.snapmusic.feature.home.cancelActiveDownloads
import com.juan.snapmusic.feature.home.cancelQueue

@Composable
internal fun PreviewDownloadsDetailHost(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val downloadsState = viewModel.previewDownloadsState.collectAsStateWithLifecycle().value
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 28.dp),
    ) {
        item {
            PreviewDownloadsDetailScreen(
                activeDownloads = downloadsState.activeItems,
                recentDownloads = downloadsState.recentItems,
                onBack = onBack,
                onCancel = viewModel::cancelQueue,
                onCancelAll = viewModel::cancelActiveDownloads,
            )
        }
    }
}
