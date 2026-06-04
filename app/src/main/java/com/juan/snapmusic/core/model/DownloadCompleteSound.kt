package com.juan.snapmusic.core.model

enum class DownloadCompleteSound(
    val preferenceKey: String,
    val label: String,
) {
    NONE("none", "Sin sonido"),
    SYSTEM("system", "Tono del sistema"),
    SNAPMUSIC_PULSE_CONFIRM("snapmusic_pulse_confirm", "SnapMusic Pulse Confirm"),
    SNAPMUSIC_CRIMSON_PING("snapmusic_crimson_ping", "SnapMusic Crimson Ping"),
    SNAPMUSIC_NEON_DROP("snapmusic_neon_drop", "SnapMusic Neon Drop"),
    SNAPMUSIC_SOFT_WIN("snapmusic_soft_win", "SnapMusic Soft Win"),
    SNAPMUSIC_SNAP_CHIME("snapmusic_snap_chime", "SnapMusic Snap Chime"),
    SNAPMUSIC_GLASS_POP("snapmusic_glass_pop", "SnapMusic Glass Pop"),
    SNAPMUSIC_RED_SIGNAL("snapmusic_red_signal", "SnapMusic Red Signal"),
    SNAPMUSIC_BASS_TAP("snapmusic_bass_tap", "SnapMusic Bass Tap"),
    SNAPMUSIC_WAVE_LOCK("snapmusic_wave_lock", "SnapMusic Wave Lock"),
    SNAPMUSIC_NIGHT_FINISH("snapmusic_night_finish", "SnapMusic Night Finish"),
    ;

    companion object {
        fun fromPreferenceKey(value: String?): DownloadCompleteSound {
            return values().firstOrNull { it.preferenceKey == value } ?: SNAPMUSIC_PULSE_CONFIRM
        }
    }
}
