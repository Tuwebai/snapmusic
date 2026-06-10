package com.juan.snapmusic.feature.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.juan.snapmusic.core.designsystem.AccentRed
import kotlinx.coroutines.delay

private const val MiniPlayerProgressTickMs = 500L

@Composable
internal fun YouTubeMiniPlayerProgressBar(
    player: Player?,
    sourceUrl: String,
    modifier: Modifier = Modifier,
) {
    var fraction by remember(player, sourceUrl) {
        mutableStateOf(player.miniPlayerProgressFraction(sourceUrl))
    }
    LaunchedEffect(player, sourceUrl) {
        while (true) {
            fraction = player.miniPlayerProgressFraction(sourceUrl)
            delay(MiniPlayerProgressTickMs)
        }
    }
    Box(
        modifier = modifier
            .height(2.dp)
            .fillMaxWidth(),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .background(AccentRed),
            )
        }
    }
}

private fun Player?.miniPlayerProgressFraction(sourceUrl: String): Float {
    val currentPlayer = this ?: return 0f
    if (currentPlayer.currentMediaItem?.mediaId != sourceUrl) return 0f
    val durationMs = currentPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: return 0f
    val positionMs = currentPlayer.currentPosition.coerceIn(0L, durationMs)
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}
