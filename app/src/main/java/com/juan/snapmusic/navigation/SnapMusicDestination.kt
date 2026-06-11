package com.juan.snapmusic.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.ui.graphics.vector.ImageVector

enum class SnapMusicDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Descargar", Icons.Outlined.Search),
    Queue("queue", "Descargas", Icons.Outlined.Download),
    History("history", "YouTube", Icons.Outlined.SmartDisplay),
    Preview("preview", "Reproducir", Icons.Outlined.PlayCircle),
    Settings("settings", "Configuración", Icons.Outlined.Settings),
}
