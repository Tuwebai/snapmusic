package com.juan.snapmusic.feature.player

import android.os.Handler
import android.os.Looper
import android.view.View
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
    backgroundColor: Int = android.graphics.Color.BLACK,
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
                applyPlayerSurfaceBackground(backgroundColor)
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
            } else {
                PlayerSurfaceTargetRegistry.release(player, view)
            }
            if (view.resizeMode != resizeMode) {
                view.resizeMode = resizeMode
            }
            view.setKeepContentOnPlayerReset(keepContentOnPlayerReset)
            view.setShutterBackgroundColor(shutterColor)
            view.applyPlayerSurfaceBackground(backgroundColor)
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

private fun PlayerView.applyPlayerSurfaceBackground(color: Int) {
    setBackgroundColor(color)
    findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)?.setBackgroundColor(color)
}

private object PlayerSurfaceTargetRegistry {
    private const val RELEASE_GRACE_MS = 300L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val targets = WeakHashMap<Player, WeakReference<PlayerView>>()
    private val generations = WeakHashMap<Player, Int>()

    fun attach(player: Player, target: PlayerView) {
        if (target.player === player) {
            targets[player] = WeakReference(target)
            generations[player] = (generations[player] ?: 0) + 1
            return
        }
        val previous = targets[player]?.get()?.takeIf { it !== target && it.player === player }
        if (previous != null) {
            PlayerView.switchTargetView(player, previous, target)
        } else {
            target.player = player
        }
        targets[player] = WeakReference(target)
        generations[player] = (generations[player] ?: 0) + 1
    }

    fun release(player: Player, target: PlayerView) {
        if (targets[player]?.get() !== target) return
        val releaseGeneration = (generations[player] ?: 0) + 1
        generations[player] = releaseGeneration
        mainHandler.postDelayed(
            {
                if (generations[player] != releaseGeneration || targets[player]?.get() !== target) {
                    return@postDelayed
                }
                targets.remove(player)
                generations.remove(player)
                if (target.player === player) {
                    target.player = null
                }
            },
            RELEASE_GRACE_MS,
        )
    }
}
