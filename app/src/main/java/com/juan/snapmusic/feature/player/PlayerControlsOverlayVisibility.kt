package com.juan.snapmusic.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

internal object PlayerControlsOverlayDefaults {
    const val AutoHideDelayMs = 3_000L
    const val FadeDurationMs = 300
}

@Composable
internal fun PlayerControlsOverlayVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(PlayerControlsOverlayDefaults.FadeDurationMs)),
        exit = fadeOut(animationSpec = tween(PlayerControlsOverlayDefaults.FadeDurationMs)),
        modifier = modifier,
    ) {
        content()
    }
}
