package com.juan.snapmusic.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
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

private val SnapMusicLightColorScheme = lightColorScheme(
    primary = AccentRed,
    onPrimary = TextPrimary,
    background = androidx.compose.ui.graphics.Color(0xFFF6F2F2),
    onBackground = androidx.compose.ui.graphics.Color(0xFF121214),
    surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface = androidx.compose.ui.graphics.Color(0xFF121214),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF2EAEA),
    outline = androidx.compose.ui.graphics.Color(0x1F000000),
)

private val SnapMusicShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun SnapMusicTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) SnapMusicColorScheme else SnapMusicLightColorScheme,
        shapes = SnapMusicShapes,
        content = content,
    )
}
