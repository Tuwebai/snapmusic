package com.juan.snapmusic.feature.player

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView

@UnstableApi
@Composable
internal fun PlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    resizeMode: Int,
    keepScreenOn: Boolean,
    shutterColor: Int,
    keepContentOnPlayerReset: Boolean = false,
    layoutParams: ViewGroup.LayoutParams? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                controllerAutoShow = false
                controllerHideOnTouch = true
                controllerShowTimeoutMs = 2_500
                this.resizeMode = resizeMode
                setKeepContentOnPlayerReset(keepContentOnPlayerReset)
                setShutterBackgroundColor(shutterColor)
                layoutParams?.let { this.layoutParams = it }
                this.player = player
                this.keepScreenOn = keepScreenOn
                hideController()
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = null
                view.player = player
            }
            if (view.resizeMode != resizeMode) {
                view.resizeMode = resizeMode
            }
            view.setKeepContentOnPlayerReset(keepContentOnPlayerReset)
            view.setShutterBackgroundColor(shutterColor)
            layoutParams?.let { view.layoutParams = it }
            if (view.keepScreenOn != keepScreenOn) {
                view.keepScreenOn = keepScreenOn
            }
        },
        onReset = { view ->
            view.hideController()
            view.keepScreenOn = false
            view.player = null
        },
        onRelease = { view ->
            view.hideController()
            view.keepScreenOn = false
            view.player = null
        },
    )
}
