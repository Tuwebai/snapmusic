package com.juan.snapmusic.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry

private const val PageTransitionDurationMs = 260
private const val PageTransitionFarSlideDivisor = 1
private const val PageTransitionNearSlideDivisor = 3

private val PageTransitionSpec = tween<IntOffset>(
    durationMillis = PageTransitionDurationMs,
    easing = FastOutSlowInEasing,
)

private val PageFadeInSpec = tween<Float>(
    durationMillis = PageTransitionDurationMs,
    easing = FastOutSlowInEasing,
)

private val PageFadeOutSpec = tween<Float>(
    durationMillis = 180,
    easing = FastOutSlowInEasing,
)

internal fun snapMusicPageEnterTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    val forward = snapMusicRouteIndex(targetState.destination.route) >= snapMusicRouteIndex(initialState.destination.route)
    slideInHorizontally(
        animationSpec = PageTransitionSpec,
        initialOffsetX = { fullWidth ->
            if (forward) {
                fullWidth / PageTransitionFarSlideDivisor
            } else {
                -fullWidth / PageTransitionNearSlideDivisor
            }
        },
    ) + fadeIn(animationSpec = PageFadeInSpec)
}

internal fun snapMusicPageExitTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    val forward = snapMusicRouteIndex(targetState.destination.route) >= snapMusicRouteIndex(initialState.destination.route)
    slideOutHorizontally(
        animationSpec = PageTransitionSpec,
        targetOffsetX = { fullWidth ->
            if (forward) {
                -fullWidth / PageTransitionNearSlideDivisor
            } else {
                fullWidth / PageTransitionFarSlideDivisor
            }
        },
    ) + fadeOut(animationSpec = PageFadeOutSpec)
}

private fun snapMusicRouteIndex(route: String?): Int {
    return SnapMusicDestination.entries.indexOfFirst { it.route == route }.takeIf { it >= 0 } ?: 0
}
