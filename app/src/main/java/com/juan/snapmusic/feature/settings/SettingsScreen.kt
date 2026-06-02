package com.juan.snapmusic.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.juan.snapmusic.core.performance.ReportPerformanceScene
import com.juan.snapmusic.feature.home.SnapMusicViewModel

private enum class SettingsPane(val depth: Int) {
    ROOT(0),
    WATCH_HISTORY(1),
    DOWNLOADS(1),
    NOTIFICATIONS(1),
    THEME(1),
    ABOUT(1),
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SettingsScreen(
    viewModel: SnapMusicViewModel,
    padding: PaddingValues,
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val youtubeWatchHistory by viewModel.youtubeWatchHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pane by remember { mutableStateOf(SettingsPane.ROOT) }
    ReportPerformanceScene(screen = "settings", detail = pane.name.lowercase())

    BackHandler(enabled = pane != SettingsPane.ROOT) {
        pane = SettingsPane.ROOT
    }

    AnimatedContent(
        targetState = pane,
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        transitionSpec = {
            settingsZoomTransition(initialState.depth <= targetState.depth)
        },
        label = "settings-pane",
    ) { currentPane ->
        when (currentPane) {
            SettingsPane.ROOT -> SettingsRootScreen(
                youtubeWatchHistory = youtubeWatchHistory,
                onWatchHistory = { pane = SettingsPane.WATCH_HISTORY },
                onPlayWatchHistory = viewModel::playYouTubeWatchHistoryItem,
                onDownloads = { pane = SettingsPane.DOWNLOADS },
                onNotifications = { pane = SettingsPane.NOTIFICATIONS },
                onTheme = { pane = SettingsPane.THEME },
                onShare = {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Estoy usando SnapMusic para bajar y reproducir mis descargas locales. Hecho por Juanchi López.",
                                )
                            },
                            "Compartir SnapMusic",
                        ),
                    )
                },
                onAbout = { pane = SettingsPane.ABOUT },
            )

            SettingsPane.WATCH_HISTORY -> YouTubeWatchHistoryPane(
                items = youtubeWatchHistory,
                onBack = { pane = SettingsPane.ROOT },
                onPlay = viewModel::playYouTubeWatchHistoryItem,
            )

            SettingsPane.DOWNLOADS -> DownloadSettingsPane(
                prefs = prefs,
                onBack = { pane = SettingsPane.ROOT },
                onPickFolder = viewModel::savePickedFolder,
                onResetFolder = viewModel::resetToDefaultFolder,
                onDownloadTaskLimitsChange = viewModel::updateDownloadTaskLimits,
                onSpeedLimitChange = viewModel::updateDownloadSpeedLimitLabel,
                onAllowMobileDataChange = viewModel::updateAllowMobileDataDownloads,
            )

            SettingsPane.NOTIFICATIONS -> NotificationsSettingsPane(
                prefs = prefs,
                onBack = { pane = SettingsPane.ROOT },
                onDownloadProgressChange = viewModel::updateNotifyDownloadProgress,
                onDownloadCompletedChange = viewModel::updateNotifyDownloadCompleted,
                onRecommendedContentChange = viewModel::updateNotifyRecommendedContent,
                onToolUpdatesChange = viewModel::updateNotifyToolUpdates,
                onToolbarAccessChange = viewModel::updateNotifyToolbarAccess,
            )

            SettingsPane.THEME -> ThemeSettingsPane(
                prefs = prefs,
                onBack = { pane = SettingsPane.ROOT },
                onThemeChange = viewModel::updateThemeMode,
            )

            SettingsPane.ABOUT -> AboutSettingsPane(
                prefs = prefs,
                onBack = { pane = SettingsPane.ROOT },
            )
        }
    }
}

private fun settingsZoomTransition(isForward: Boolean): ContentTransform {
    return if (isForward) {
        (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith
            (fadeOut() + scaleOut(targetScale = 1.04f))
    } else {
        (fadeIn() + scaleIn(initialScale = 1.04f)) togetherWith
            (fadeOut() + scaleOut(targetScale = 0.94f))
    }
}
