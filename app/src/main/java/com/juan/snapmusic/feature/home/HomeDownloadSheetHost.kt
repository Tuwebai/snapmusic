package com.juan.snapmusic.feature.home

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeDownloadSheetHost(
    viewModel: SnapMusicViewModel,
    onDownloadQueued: () -> Unit,
) {
    val downloadSheetState by viewModel.youtubeDownloadSheet.collectAsStateWithLifecycle()
    val media = downloadSheetState.media ?: return
    if (!downloadSheetState.visible) return

    DownloadFormatSheet(
        media = media,
        isPreparing = downloadSheetState.isPreparing,
        allowedKinds = downloadSheetState.allowedKinds,
        errorMessage = downloadSheetState.errorMessage,
        onDismiss = viewModel::dismissYouTubeDownloadSheet,
        onConfirm = { variantId ->
            viewModel.enqueueYoutubeVariant(variantId)
            onDownloadQueued()
        },
    )
}
