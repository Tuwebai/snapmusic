package com.juan.snapmusic.feature.youtube

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.juan.snapmusic.core.model.YouTubeFeaturedVideo

@Composable
internal fun YouTubeCastRouteButton(
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MediaRouteButton(context).apply {
                contentDescription = "Transmitir"
                runCatching { CastButtonFactory.setUpMediaRouteButton(context, this) }
            }
        },
    )
}

@Composable
internal fun YouTubeCastPlaybackEffect(
    featured: YouTubeFeaturedVideo,
    player: Player?,
) {
    val context = LocalContext.current.applicationContext
    var castSession by remember(context) { mutableStateOf<CastSession?>(null) }

    DisposableEffect(context) {
        val castContext = runCatching { CastContext.getSharedInstance(context) }.getOrNull()
        val sessionManager = castContext?.sessionManager
        castSession = sessionManager?.currentCastSession
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                castSession = session
            }

            override fun onSessionStartFailed(session: CastSession, error: Int) {
                castSession = null
            }

            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionEnded(session: CastSession, error: Int) {
                castSession = null
            }

            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                castSession = session
            }

            override fun onSessionResumeFailed(session: CastSession, error: Int) {
                castSession = null
            }

            override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
        }
        sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        onDispose {
            sessionManager?.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }

    LaunchedEffect(
        castSession,
        featured.sourceUrl,
        featured.playbackUrl,
        featured.selectedVideoQualityId,
    ) {
        val session = castSession ?: return@LaunchedEffect
        val playbackUrl = featured.playbackUrl?.takeIf(::isCastablePlaybackUrl) ?: return@LaunchedEffect
        val remoteMediaClient = session.remoteMediaClient ?: return@LaunchedEffect
        if (remoteMediaClient.mediaInfo?.contentId == playbackUrl) return@LaunchedEffect
        val request = MediaLoadRequestData.Builder()
            .setMediaInfo(featured.toCastMediaInfo(playbackUrl))
            .setAutoplay(true)
            .setCurrentTime(player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
            .build()
        remoteMediaClient.load(request)
        player?.pause()
    }
}

private fun YouTubeFeaturedVideo.toCastMediaInfo(playbackUrl: String): MediaInfo {
    val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
        putString(MediaMetadata.KEY_TITLE, title.ifBlank { "SnapMusic" })
        putString(MediaMetadata.KEY_SUBTITLE, author)
        thumbnailUrl.takeIf { it.isNotBlank() }?.let { url ->
            runCatching { addImage(WebImage(Uri.parse(url))) }
        }
    }
    return MediaInfo.Builder(playbackUrl)
        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
        .setContentType(castContentType(playbackUrl))
        .setMetadata(metadata)
        .build()
}

private fun isCastablePlaybackUrl(playbackUrl: String): Boolean {
    val scheme = Uri.parse(playbackUrl).scheme?.lowercase()
    return scheme == "http" || scheme == "https"
}

private fun castContentType(playbackUrl: String): String {
    val cleanUrl = playbackUrl.substringBefore('?').lowercase()
    return when {
        cleanUrl.endsWith(".mpd") -> "application/dash+xml"
        cleanUrl.endsWith(".m3u8") -> "application/x-mpegURL"
        cleanUrl.endsWith(".webm") -> "video/webm"
        else -> "video/mp4"
    }
}
