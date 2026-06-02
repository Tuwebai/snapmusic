package com.juan.snapmusic.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.juan.snapmusic.core.model.AppThemeMode

private val SnapMusicColorScheme = darkColorScheme(
    primary = AccentRed,
    onPrimary = TextPrimary,
    background = BackgroundPrimary,
    onBackground = TextPrimary,
    surface = SurfacePrimary,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    outline = BorderSubtle,
)

private val SnapMusicShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun SnapMusicTheme(
    @Suppress("UNUSED_PARAMETER")
    themeMode: AppThemeMode = AppThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SnapMusicColorScheme,
        shapes = SnapMusicShapes,
        content = content,
    )
}
