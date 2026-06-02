package com.juan.snapmusic.feature.settings

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.juan.snapmusic.R
import com.juan.snapmusic.core.designsystem.AccentRed
import com.juan.snapmusic.core.designsystem.BackgroundPrimary
import com.juan.snapmusic.core.designsystem.SurfaceElevated
import com.juan.snapmusic.core.designsystem.TextPrimary
import com.juan.snapmusic.core.designsystem.TextSecondary
import com.juan.snapmusic.core.model.AppThemeMode
import com.juan.snapmusic.core.model.UserPreferences
import com.juan.snapmusic.core.model.YouTubeWatchHistoryEntry

private data class TaskPreset(
    val wifi: Int,
    val mobile: Int,
)

private val taskPresets = listOf(
    TaskPreset(wifi = 4, mobile = 2),
    TaskPreset(wifi = 3, mobile = 2),
    TaskPreset(wifi = 2, mobile = 1),
    TaskPreset(wifi = 1, mobile = 1),
)

private val speedOptions = listOf(
    "Sin límites",
    "10 MB/s",
    "5 MB/s",
    "2 MB/s",
    "1 MB/s",
)

@Composable
internal fun SettingsRootScreen(
    youtubeWatchHistory: List<YouTubeWatchHistoryEntry>,
    onWatchHistory: () -> Unit,
    onDownloads: () -> Unit,
    onNotifications: () -> Unit,
    onTheme: () -> Unit,
    onShare: () -> Unit,
    onAbout: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text("Configuración", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                SettingsHistoryHero(
                    items = youtubeWatchHistory,
                    onClick = onWatchHistory,
                )
                SettingsSectionLabel("General")
                SettingsActionRow(Icons.Outlined.Download, "Configuración de descarga", onClick = onDownloads)
                SettingsActionRow(Icons.Outlined.Notifications, "Configuración de notificaciones", onClick = onNotifications)
                SettingsActionRow(Icons.Outlined.DarkMode, "Tema", onClick = onTheme)
                Spacer(modifier = Modifier.height(10.dp))
                SettingsSectionLabel("Información")
                SettingsActionRow(Icons.Outlined.Share, "Compartir SnapMusic", onClick = onShare)
                SettingsActionRow(Icons.Outlined.Info, "Acerca de", onClick = onAbout)
            }
        }
    }
}

@Composable
internal fun DownloadSettingsPane(
    prefs: UserPreferences,
    onBack: () -> Unit,
    onPickFolder: (String, String) -> Unit,
    onResetFolder: () -> Unit,
    onDownloadTaskLimitsChange: (Int, Int) -> Unit,
    onSpeedLimitChange: (String) -> Unit,
    onAllowMobileDataChange: (Boolean) -> Unit,
) {
    var showTaskDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onPickFolder(uri.toString(), "Carpeta elegida")
    }

    if (showTaskDialog) {
        SettingsSelectionDialog(
            title = "Máximas tareas de descarga",
            options = taskPresets.map { "WiFi: ${it.wifi} tareas | Datos móviles: ${it.mobile} tareas" },
            selectedIndex = taskPresets.indexOfFirst {
                it.wifi == prefs.downloadTasksWifi && it.mobile == prefs.downloadTasksMobile
            }.coerceAtLeast(0),
            onDismiss = { showTaskDialog = false },
            onSelect = { index ->
                taskPresets.getOrNull(index)?.let { onDownloadTaskLimitsChange(it.wifi, it.mobile) }
                showTaskDialog = false
            },
        )
    }

    if (showSpeedDialog) {
        SettingsSelectionDialog(
            title = "Límite de velocidad de descarga",
            options = speedOptions,
            selectedIndex = speedOptions.indexOf(prefs.downloadSpeedLimitLabel).coerceAtLeast(0),
            onDismiss = { showSpeedDialog = false },
            onSelect = { index ->
                speedOptions.getOrNull(index)?.let(onSpeedLimitChange)
                showSpeedDialog = false
            },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        item { SettingsPaneHeader("Configuración de descarga", onBack) }
        item { SettingsSectionSpacer() }
        item {
            SettingsValueRow(
                title = "Directorio de descarga",
                subtitle = prefs.defaultDestinationLabel,
                onClick = { picker.launch(null) },
            )
        }
        item {
            SettingsTextAction(
                text = "Restaurar carpeta SnapMusic",
                onClick = onResetFolder,
            )
        }
        item {
            SettingsValueRow(
                title = "Máximas tareas de descarga",
                subtitle = "WiFi: ${prefs.downloadTasksWifi} tareas | Datos móviles: ${prefs.downloadTasksMobile} tareas",
                onClick = { showTaskDialog = true },
            )
        }
        item {
            SettingsValueRow(
                title = "Límite de velocidad de descarga",
                subtitle = prefs.downloadSpeedLimitLabel,
                onClick = { showSpeedDialog = true },
            )
        }
        item {
            SettingsSwitchRow(
                title = "Descarga a través de datos móviles",
                subtitle = "Los medios se descargarán a través de datos",
                checked = prefs.allowMobileDataDownloads,
                onCheckedChange = onAllowMobileDataChange,
            )
        }
    }
}

@Composable
internal fun NotificationsSettingsPane(
    prefs: UserPreferences,
    onBack: () -> Unit,
    onDownloadProgressChange: (Boolean) -> Unit,
    onDownloadCompletedChange: (Boolean) -> Unit,
    onRecommendedContentChange: (Boolean) -> Unit,
    onToolUpdatesChange: (Boolean) -> Unit,
    onToolbarAccessChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        item { SettingsPaneHeader("Notificaciones", onBack) }
        item { SettingsSectionSpacer() }
        item { SettingsSubsectionLabel("Notificaciones de Descargas") }
        item {
            SettingsSwitchRow(
                title = "Progreso de Descarga",
                subtitle = "Notificarme del progreso de la descarga",
                checked = prefs.notifyDownloadProgress,
                onCheckedChange = onDownloadProgressChange,
            )
        }
        item {
            SettingsSwitchRow(
                title = "Descarga completada",
                subtitle = "Notificarme cuando se complete la descarga",
                checked = prefs.notifyDownloadCompleted,
                onCheckedChange = onDownloadCompletedChange,
            )
        }
        item { SettingsDivider() }
        item { SettingsSubsectionLabel("Notificación Push") }
        item {
            SettingsSwitchRow(
                title = "Contenido recomendado",
                subtitle = "Notificarme de videos y música que me puedan gustar",
                checked = prefs.notifyRecommendedContent,
                onCheckedChange = onRecommendedContentChange,
            )
        }
        item {
            SettingsSwitchRow(
                title = "Herramienta de notificaciones",
                subtitle = "Notificarme cuando se publiquen nuevas herramientas",
                checked = prefs.notifyToolUpdates,
                onCheckedChange = onToolUpdatesChange,
            )
        }
        item {
            SettingsSwitchRow(
                title = "Barra de Herramientas",
                subtitle = "Acceso rápido a herramientas en la barra de notificaciones",
                checked = prefs.notifyToolbarAccess,
                onCheckedChange = onToolbarAccessChange,
            )
        }
    }
}

@Composable
internal fun ThemeSettingsPane(
    prefs: UserPreferences,
    onBack: () -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary),
    ) {
        item { SettingsPaneHeader("Tema", onBack) }
        item { SettingsSectionSpacer() }
        item { SettingsSubsectionLabel("Tema de la aplicación") }
        item {
            ThemeChoiceRow(
                label = "Usar la configuración del sistema",
                selected = prefs.themeMode == AppThemeMode.SYSTEM,
                onClick = { onThemeChange(AppThemeMode.SYSTEM) },
            )
        }
        item {
            ThemeChoiceRow(
                label = "Claro",
                selected = prefs.themeMode == AppThemeMode.LIGHT,
                onClick = { onThemeChange(AppThemeMode.LIGHT) },
            )
        }
        item {
            ThemeChoiceRow(
                label = "Oscuro",
                selected = prefs.themeMode == AppThemeMode.DARK,
                onClick = { onThemeChange(AppThemeMode.DARK) },
            )
        }
    }
}

@Composable
internal fun AboutSettingsPane(
    prefs: UserPreferences,
    onBack: () -> Unit,
) {
    val versionName = rememberAppVersion()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundPrimary)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SettingsPaneHeader("Acerca de", onBack)
        Spacer(modifier = Modifier.height(44.dp))
        Box(
            modifier = Modifier
                .size(122.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF4A4A), Color(0xFFCB1F2D)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.snapmusic_logo),
                contentDescription = "Logo de SnapMusic",
                tint = Color.Unspecified,
                modifier = Modifier.size(74.dp),
            )
        }
        Spacer(modifier = Modifier.height(22.dp))
        Text("SnapMusic", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Versión $versionName", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Hecho por Juanchi López",
            color = AccentRed,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(44.dp))
        SettingsStatusRow(
            title = "Verificar actualizaciones",
            value = "Actualizado",
        )
        Spacer(modifier = Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialCircle(
                background = Color(0xFF1877F2),
                label = "f",
                onClick = {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "SnapMusic · Hecho por Juanchi López",
                                )
                            },
                            "Compartir SnapMusic",
                        ),
                    )
                },
            )
            SocialCircle(
                background = Brush.linearGradient(
                    colors = listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)),
                ),
                icon = {
                    Icon(
                        Icons.Outlined.CameraAlt,
                        contentDescription = "Instagram",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "SnapMusic · Hecho por Juanchi López",
                                )
                            },
                            "Compartir SnapMusic",
                        ),
                    )
                },
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            "Políticas y reglas  |  Créditos",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("©2026 SnapMusic", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

