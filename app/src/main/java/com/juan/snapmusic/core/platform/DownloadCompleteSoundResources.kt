package com.juan.snapmusic.core.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.juan.snapmusic.R
import com.juan.snapmusic.core.model.DownloadCompleteSound

internal fun DownloadCompleteSound.notificationSoundUri(context: Context): Uri? {
    return when (this) {
        DownloadCompleteSound.NONE -> null
        DownloadCompleteSound.SYSTEM -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else -> Uri.parse("android.resource://${context.packageName}/${rawResourceId()}")
    }
}

internal fun downloadCompleteAudioAttributes(): AudioAttributes {
    return AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
}

private fun DownloadCompleteSound.rawResourceId(): Int {
    return when (this) {
        DownloadCompleteSound.SNAPMUSIC_PULSE_CONFIRM -> R.raw.download_complete_pulse_confirm
        DownloadCompleteSound.SNAPMUSIC_CRIMSON_PING -> R.raw.download_complete_crimson_ping
        DownloadCompleteSound.SNAPMUSIC_NEON_DROP -> R.raw.download_complete_neon_drop
        DownloadCompleteSound.SNAPMUSIC_SOFT_WIN -> R.raw.download_complete_soft_win
        DownloadCompleteSound.SNAPMUSIC_SNAP_CHIME -> R.raw.download_complete_snap_chime
        DownloadCompleteSound.SNAPMUSIC_GLASS_POP -> R.raw.download_complete_glass_pop
        DownloadCompleteSound.SNAPMUSIC_RED_SIGNAL -> R.raw.download_complete_red_signal
        DownloadCompleteSound.SNAPMUSIC_BASS_TAP -> R.raw.download_complete_bass_tap
        DownloadCompleteSound.SNAPMUSIC_WAVE_LOCK -> R.raw.download_complete_wave_lock
        DownloadCompleteSound.SNAPMUSIC_NIGHT_FINISH -> R.raw.download_complete_night_finish
        DownloadCompleteSound.NONE,
        DownloadCompleteSound.SYSTEM,
        -> R.raw.download_complete_pulse_confirm
    }
}
