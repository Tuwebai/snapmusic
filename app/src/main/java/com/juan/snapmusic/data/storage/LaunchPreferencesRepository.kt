package com.juan.snapmusic.data.storage

import android.content.Context
import androidx.compose.runtime.Immutable
import com.juan.snapmusic.core.model.AppThemeMode
import com.juan.snapmusic.core.model.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class LaunchPreferences(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val youtubeAutoplayEnabled: Boolean = true,
)

class LaunchPreferencesRepository(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()
    private val initialLaunchPreferences = load()
    private val _themeMode = MutableStateFlow(initialLaunchPreferences.themeMode)
    private val _youtubeAutoplayEnabled = MutableStateFlow(initialLaunchPreferences.youtubeAutoplayEnabled)

    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    val youtubeAutoplayEnabled: StateFlow<Boolean> = _youtubeAutoplayEnabled.asStateFlow()

    fun isInitialized(): Boolean {
        return prefs.contains(KEY_THEME_MODE) && prefs.contains(KEY_YOUTUBE_AUTOPLAY_ENABLED)
    }

    suspend fun syncFromLegacy(preferences: UserPreferences) {
        synchronized(lock) {
            if (isInitialized()) return
            write(
                themeMode = preferences.themeMode,
                youtubeAutoplayEnabled = preferences.youtubeAutoplayEnabled,
            )
        }
    }

    suspend fun setThemeMode(value: AppThemeMode) {
        synchronized(lock) {
            if (_themeMode.value == value) return
            write(themeMode = value, youtubeAutoplayEnabled = _youtubeAutoplayEnabled.value)
        }
    }

    suspend fun setYouTubeAutoplayEnabled(value: Boolean) {
        synchronized(lock) {
            if (_youtubeAutoplayEnabled.value == value) return
            write(themeMode = _themeMode.value, youtubeAutoplayEnabled = value)
        }
    }

    private fun load(): LaunchPreferences {
        return LaunchPreferences(
            themeMode = prefs.getString(KEY_THEME_MODE, null)
                ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.SYSTEM,
            youtubeAutoplayEnabled = prefs.getBoolean(KEY_YOUTUBE_AUTOPLAY_ENABLED, true),
        )
    }

    private fun write(
        themeMode: AppThemeMode = _themeMode.value,
        youtubeAutoplayEnabled: Boolean = _youtubeAutoplayEnabled.value,
    ) {
        prefs.edit()
            .putString(KEY_THEME_MODE, themeMode.name)
            .putBoolean(KEY_YOUTUBE_AUTOPLAY_ENABLED, youtubeAutoplayEnabled)
            .apply()
        _themeMode.value = themeMode
        _youtubeAutoplayEnabled.value = youtubeAutoplayEnabled
    }

    private companion object {
        const val PREFS_NAME = "snapmusic_launch_prefs"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_YOUTUBE_AUTOPLAY_ENABLED = "youtube_autoplay_enabled"
    }
}
