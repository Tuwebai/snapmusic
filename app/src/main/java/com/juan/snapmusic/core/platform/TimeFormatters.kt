package com.juan.snapmusic.core.platform

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDuration(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
