package com.juan.snapmusic.feature.youtube

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable

private const val YouTubePageTransitionMs = 240

@Composable
internal fun YouTubeHorizontalPageTransition(
    visible: Boolean,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            animationSpec = tween(YouTubePageTransitionMs, easing = FastOutSlowInEasing),
            initialOffsetX = { it },
        ) + fadeIn(animationSpec = tween(YouTubePageTransitionMs, easing = FastOutSlowInEasing)),
        exit = slideOutHorizontally(
            animationSpec = tween(YouTubePageTransitionMs, easing = FastOutSlowInEasing),
            targetOffsetX = { -it / 3 },
        ) + fadeOut(animationSpec = tween(160, easing = FastOutSlowInEasing)),
    ) {
        content()
    }
}
