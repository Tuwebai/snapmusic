package com.juan.snapmusic.core.platform

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val SpeedFormat = DecimalFormat(
    "0.#",
    DecimalFormatSymbols(Locale.US),
)

fun formatSpeed(bytesPerSecond: Long): String {
    val safeSpeed = bytesPerSecond.coerceAtLeast(0L).toDouble()
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = safeSpeed
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return "${SpeedFormat.format(value)} ${units[unitIndex]}"
}

fun formatDownloadEta(
    bytesDownloaded: Long,
    totalBytes: Long?,
    speedBytesPerSecond: Long,
): String? {
    val total = totalBytes?.takeIf { it > 0L } ?: return null
    val speed = speedBytesPerSecond.takeIf { it > 0L } ?: return null
    val remainingSeconds = ((total - bytesDownloaded).coerceAtLeast(0L) / speed).coerceAtLeast(1L)
    return "~${formatRemainingTime(remainingSeconds)} restantes"
}

private fun formatRemainingTime(seconds: Long): String {
    if (seconds < 60L) return "$seconds s"
    val minutes = (seconds + 59L) / 60L
    if (minutes < 60L) return "$minutes min"
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return if (remainingMinutes == 0L) "${hours} h" else "${hours} h ${remainingMinutes} min"
}
