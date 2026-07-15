package com.orbit.app.ui.time

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.uses24HourClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class OrbitTimeFormat(
    val uses24HourClock: Boolean,
) {
    fun formatTime(epochMillis: Long): String =
        epochMillis.formatWithPattern(timePattern)

    fun formatDate(epochMillis: Long): String =
        epochMillis.formatWithPattern("EEE, MMM d")

    fun formatDateWithYear(epochMillis: Long): String =
        epochMillis.formatWithPattern("MMM d, yyyy")

    fun formatDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("MMM d, $timePattern")

    fun formatWeekdayDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("EEE, MMM d - $timePattern")

    fun formatShortDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("MMM d - $timePattern")

    private val timePattern: String
        get() = if (uses24HourClock) "HH:mm" else "h:mm a"
}

@Composable
fun currentOrbitTimeFormat(mode: SettingsTimeFormatMode): OrbitTimeFormat {
    val context = LocalContext.current
    val deviceUses24HourClock = DateFormat.is24HourFormat(context)
    return OrbitTimeFormat(mode.uses24HourClock(deviceUses24HourClock))
}

private fun Long.formatWithPattern(pattern: String): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(pattern))
