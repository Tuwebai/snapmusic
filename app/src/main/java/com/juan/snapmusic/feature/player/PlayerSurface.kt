package com.juan.snapmusic.feature.player

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

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
    active: Boolean = true,
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
                if (active) {
                    PlayerSurfaceTargetRegistry.attach(player, this)
                }
                this.keepScreenOn = keepScreenOn
                hideController()
            }
        },
        update = { view ->
            if (active) {
                PlayerSurfaceTargetRegistry.attach(player, view)
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
            PlayerSurfaceTargetRegistry.release(player, view)
        },
        onRelease = { view ->
            view.hideController()
            view.keepScreenOn = false
            PlayerSurfaceTargetRegistry.release(player, view)
        },
    )
}

private object PlayerSurfaceTargetRegistry {
    private val targets = WeakHashMap<Player, WeakReference<PlayerView>>()

    fun attach(player: Player, target: PlayerView) {
        if (target.player === player) {
            targets[player] = WeakReference(target)
            return
        }
        val previous = targets[player]?.get()?.takeIf { it !== target && it.player === player }
        if (previous != null) {
            PlayerView.switchTargetView(player, previous, target)
        } else {
            target.player = player
        }
        targets[player] = WeakReference(target)
    }

    fun release(player: Player, target: PlayerView) {
        if (targets[player]?.get() === target) {
            targets.remove(player)
        }
        if (target.player === player) {
            target.player = null
        }
    }
}
