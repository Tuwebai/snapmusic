package com.juan.snapmusic.feature.preview

import android.app.Activity
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.juan.snapmusic.core.model.LocalMediaItem
import com.juan.snapmusic.core.platform.buildLocalMediaShareIntent
import com.juan.snapmusic.core.performance.ReportPerformanceScene
import com.juan.snapmusic.feature.home.PreviewLibraryUiState
import com.juan.snapmusic.feature.home.*
import java.text.DecimalFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
internal fun PreviewDownloadsLifecycleHost(
    viewModel: SnapMusicViewModel,
    hasPermission: Boolean,
    showDownloadsScreen: Boolean,
    onShowDownloadsScreenChange: (Boolean) -> Unit,
) {
    val downloadsShell = viewModel.previewDownloadsShellState.collectAsStateWithLifecycle().value

    LaunchedEffect(hasPermission, downloadsShell.completedCount) {
        if (hasPermission) {
            if (downloadsShell.completedCount > 0) {
                viewModel.refreshLocalPreviewLibrary(forceRefresh = true)
            } else {
                viewModel.ensureLocalPreviewLibraryLoaded()
            }
        }
    }

    LaunchedEffect(downloadsShell.openRequestId, downloadsShell.hasActiveDownloads) {
        if (downloadsShell.openRequestId > 0L && downloadsShell.hasActiveDownloads) {
            onShowDownloadsScreenChange(true)
        }
    }

    LaunchedEffect(downloadsShell.hasActiveDownloads, showDownloadsScreen) {
        if (!downloadsShell.hasActiveDownloads && showDownloadsScreen) {
            onShowDownloadsScreenChange(false)
        }
    }
}

@Composable
internal fun PreviewSceneReporterHost(
    viewModel: SnapMusicViewModel,
    showDownloadsScreen: Boolean,
) {
    val routeVisibility = viewModel.previewRouteVisibility.collectAsStateWithLifecycle().value
    val previewPerformance = viewModel.previewPerformanceState.collectAsStateWithLifecycle().value

    ReportPerformanceScene(
        screen = "preview",
        detail = when {
            routeVisibility.detailVisible && routeVisibility.isReady && previewPerformance.isVideo -> "preview-player-video"
            routeVisibility.detailVisible && routeVisibility.isReady -> "preview-player-audio"
            showDownloadsScreen -> "downloads-active"
            else -> "preview-library"
        },
    )
}
