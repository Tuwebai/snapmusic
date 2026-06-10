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
