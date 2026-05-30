package com.juan.snapmusic

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.activity.compose.setContent
import androidx.media3.common.util.UnstableApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.juan.snapmusic.core.designsystem.SnapMusicTheme
import com.juan.snapmusic.core.model.IncomingShareItem
import com.juan.snapmusic.core.model.IncomingSharePayload
import com.juan.snapmusic.core.model.IncomingShareSourceAction
import com.juan.snapmusic.core.platform.PlaybackCommandReceiver
import com.juan.snapmusic.core.platform.PlaybackSessionState
import com.juan.snapmusic.core.platform.PlaybackSessionTarget
import com.juan.snapmusic.core.platform.validateYouTubeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var isYouTubePopupEligible = false
    private var playbackSessionState = PlaybackSessionState()
    private var lastPictureInPictureSignature: Int? = null
    private val isPictureInPictureModeState = mutableStateOf(false)
    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    companion object {
        const val EXTRA_ROUTE = "extra_route"
        const val ROUTE_QUEUE = "queue"
        const val ROUTE_PREVIEW = "preview"
        const val ROUTE_HOME = "home"
        const val ROUTE_PLAYBACK = "playback"
        private val YOUTUBE_URL_REGEX = Regex("""https?://\S+""")

        fun buildOpenQueuePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROUTE, ROUTE_QUEUE)
            }
            return PendingIntent.getActivity(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun buildOpenPlaybackPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_ROUTE, ROUTE_PLAYBACK)
            }
            return PendingIntent.getActivity(
                context,
                2002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    private val routeOverride = mutableStateOf<String?>(null)
    private val incomingShareOverride = mutableStateOf<IncomingSharePayload?>(null)

    @androidx.compose.material3.ExperimentalMaterial3Api
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routeOverride.value = intent?.getStringExtra(EXTRA_ROUTE)
        incomingShareOverride.value = extractIncomingSharePayload(intent)
        if (incomingShareOverride.value != null && routeOverride.value == null) {
            routeOverride.value = ROUTE_HOME
        }
        val app = application as SnapMusicApplication
        setContent {
            val themeMode by app.appGraph.launchPreferencesRepository.themeMode.collectAsStateWithLifecycle()
            SnapMusicTheme(themeMode = themeMode) {
                SnapMusicApp(
                    graph = app.appGraph,
                    notificationRoute = routeOverride.value,
                    onNotificationRouteConsumed = { routeOverride.value = null },
                    incomingSharePayload = incomingShareOverride.value,
                    onIncomingShareConsumed = { incomingShareOverride.value = null },
                    isInPictureInPictureMode = isPictureInPictureModeState.value,
                )
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            if (!app.appGraph.launchPreferencesRepository.isInitialized()) {
                app.appGraph.launchPreferencesRepository.syncFromLegacy(
                    app.appGraph.currentPreferences(),
                )
            }
        }
        window.decorView.post { requestRuntimePermissionsIfNeeded() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeOverride.value = intent.getStringExtra(EXTRA_ROUTE) ?: routeOverride.value
        incomingShareOverride.value = extractIncomingSharePayload(intent)
        if (incomingShareOverride.value != null && intent.getStringExtra(EXTRA_ROUTE) == null) {
            routeOverride.value = ROUTE_HOME
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterYouTubePictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isPictureInPictureModeState.value = isInPictureInPictureMode
    }

    fun updateYouTubePictureInPicture(
        isEligible: Boolean,
        playbackSessionState: PlaybackSessionState = PlaybackSessionState(),
    ) {
        isYouTubePopupEligible = isEligible
        this.playbackSessionState = playbackSessionState
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val signature = pictureInPictureSignature(isEligible, playbackSessionState)
            if (signature != lastPictureInPictureSignature) {
                lastPictureInPictureSignature = signature
                setPictureInPictureParams(buildPictureInPictureParams())
            }
        }
    }

    fun enterYouTubePictureInPicture(): Boolean {
        if (!isYouTubePopupEligible || Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isInPictureInPictureMode) {
            return false
        }
        return enterPictureInPictureMode(buildPictureInPictureParams())
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val missingPermissions = requiredRuntimePermissions().filterNot { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            mediaPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun pictureInPictureSignature(
        isEligible: Boolean,
        state: PlaybackSessionState,
    ): Int {
        return arrayOf(
            isEligible,
            state.target,
            state.showPauseButton,
            state.youtubeHasPrevious,
            state.youtubeHasNext,
        ).contentHashCode()
    }

    private fun requiredRuntimePermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                android.Manifest.permission.POST_NOTIFICATIONS,
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPictureInPictureParams(): PictureInPictureParams {
        val isYouTubePlayback = playbackSessionState.target == PlaybackSessionTarget.YOUTUBE
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(
                if (isYouTubePlayback) {
                    buildList {
                        if (playbackSessionState.youtubeHasPrevious) {
                            add(
                                buildPlaybackRemoteAction(
                                    action = PlaybackCommandReceiver.ACTION_YOUTUBE_PREVIOUS,
                                    title = "Anterior",
                                    iconRes = android.R.drawable.ic_media_previous,
                                    requestCode = 3011,
                                ),
                            )
                        }
                        add(
                            buildPlaybackRemoteAction(
                                action = PlaybackCommandReceiver.ACTION_YOUTUBE_PLAY_PAUSE,
                                title = if (playbackSessionState.showPauseButton) "Pausar" else "Reproducir",
                                iconRes = if (playbackSessionState.showPauseButton) {
                                    android.R.drawable.ic_media_pause
                                } else {
                                    android.R.drawable.ic_media_play
                                },
                                requestCode = 3013,
                            ),
                        )
                        if (playbackSessionState.youtubeHasNext) {
                            add(
                                buildPlaybackRemoteAction(
                                    action = PlaybackCommandReceiver.ACTION_YOUTUBE_NEXT,
                                    title = "Siguiente",
                                    iconRes = android.R.drawable.ic_media_next,
                                    requestCode = 3012,
                                ),
                            )
                        }
                    }
                } else {
                    emptyList()
                },
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isYouTubePopupEligible)
        }

        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPlaybackRemoteAction(
        action: String,
        title: String,
        iconRes: Int,
        requestCode: Int,
    ): RemoteAction {
        val intent = Intent(this, com.juan.snapmusic.core.platform.PlaybackCommandReceiver::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return RemoteAction(
            Icon.createWithResource(this, iconRes),
            title,
            title,
            pendingIntent,
        )
    }

    private fun extractIncomingSharePayload(intent: Intent?): IncomingSharePayload? {
        val action = intent?.action ?: return null
        val candidates = when (action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> buildList {
                intent.getStringExtra(Intent.EXTRA_TEXT)?.let(::add)
                intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
                    ?.map(CharSequence::toString)
                    ?.forEach(::add)
                val clipData = intent.clipData
                if (clipData != null) {
                    for (index in 0 until clipData.itemCount) {
                        clipData.getItemAt(index)?.coerceToText(this@MainActivity)?.toString()?.let(::add)
                    }
                }
            }
            Intent.ACTION_VIEW -> listOfNotNull(intent.dataString)
            else -> emptyList()
        }
        val sourceAction = when (action) {
            Intent.ACTION_SEND -> IncomingShareSourceAction.SEND
            Intent.ACTION_SEND_MULTIPLE -> IncomingShareSourceAction.SEND_MULTIPLE
            Intent.ACTION_VIEW -> IncomingShareSourceAction.VIEW
            else -> return null
        }
        val items = candidates.asSequence()
            .flatMap { candidate -> candidate.lineSequence().map(String::trim) }
            .mapNotNull { candidate ->
                val normalized = YOUTUBE_URL_REGEX.find(candidate)?.value ?: candidate.takeIf { it.startsWith("http") }
                normalized?.let { value -> validateYouTubeUrl(value).normalizedUrl }
            }
            .distinct()
            .map(::IncomingShareItem)
            .toList()
        return IncomingSharePayload(
            sourceAction = sourceAction,
            items = items,
        )
    }

}
