package com.juan.snapmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juan.snapmusic.core.model.IncomingSharePayload
import com.juan.snapmusic.feature.home.SnapMusicViewModel
import com.juan.snapmusic.feature.home.SnapMusicViewModelFactory
import com.juan.snapmusic.core.platform.PlaybackCommand
import com.juan.snapmusic.core.platform.PlaybackCommandBus
import com.juan.snapmusic.navigation.SnapMusicNavHost
import kotlinx.coroutines.flow.collectLatest

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.media3.common.util.UnstableApi
@Composable
fun SnapMusicApp(
    graph: SnapMusicGraph,
    notificationRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {},
    incomingSharePayload: IncomingSharePayload? = null,
    onIncomingShareConsumed: () -> Unit = {},
    isInPictureInPictureMode: Boolean = false,
) {
    val viewModel: SnapMusicViewModel = viewModel(factory = SnapMusicViewModelFactory(graph))
    val activity = LocalContext.current as? MainActivity
    LaunchedEffect(activity, viewModel) {
        val currentActivity = activity ?: return@LaunchedEffect
        viewModel.appPictureInPictureConfig.collectLatest { pictureInPictureConfig ->
            currentActivity.updateYouTubePictureInPicture(
                pictureInPictureConfig.eligible,
                pictureInPictureConfig.shouldAutoPlay,
            )
        }
    }

    LaunchedEffect(incomingSharePayload) {
        if (incomingSharePayload != null) {
            viewModel.applyIncomingSharePayload(incomingSharePayload)
            onIncomingShareConsumed()
        }
    }

    LaunchedEffect(Unit) {
        PlaybackCommandBus.commands.collectLatest { command ->
            when (command) {
                PlaybackCommand.YOUTUBE_NEXT -> viewModel.playNextYouTubeItem()
                PlaybackCommand.YOUTUBE_PLAY_PAUSE -> viewModel.toggleYouTubePlayPause()
                PlaybackCommand.YOUTUBE_PREVIOUS -> viewModel.playPreviousYouTubeItem()
            }
        }
    }

    SnapMusicNavHost(
        viewModel = viewModel,
        notificationRoute = notificationRoute,
        onNotificationRouteConsumed = onNotificationRouteConsumed,
        isInPictureInPictureMode = isInPictureInPictureMode,
    )
}
