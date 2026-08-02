package com.orbit.app.ui.time

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.uses24HourClock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class OrbitTimeFormat(
    val uses24HourClock: Boolean,
    val locale: Locale = Locale.ENGLISH,
) {
    fun formatTime(epochMillis: Long): String =
        epochMillis.formatWithPattern(timePattern, locale)

    fun formatTime(localTime: LocalTime): String =
        localTime.format(DateTimeFormatter.ofPattern(timePattern, locale))

    fun formatEndOfDay(): String = if (uses24HourClock) {
        "24:00"
    } else {
        formatTime(LocalTime.MIDNIGHT)
    }

    fun formatDate(epochMillis: Long): String =
        epochMillis.formatWithPattern("EEE, MMM d", locale)

    fun formatDateWithYear(epochMillis: Long): String =
        epochMillis.formatWithPattern("MMM d, yyyy", locale)

    fun formatDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("MMM d, $timePattern", locale)

    fun formatWeekdayDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("EEE, MMM d - $timePattern", locale)

    fun formatShortDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("MMM d - $timePattern", locale)

    private val timePattern: String
        get() = if (uses24HourClock) "HH:mm" else "h:mm a"
}

@Composable
fun currentOrbitTimeFormat(mode: SettingsTimeFormatMode): OrbitTimeFormat {
    val context = LocalContext.current
    val deviceUses24HourClock = DateFormat.is24HourFormat(context)
    val locale = LocalConfiguration.current.locales[0] ?: Locale.ENGLISH
    return OrbitTimeFormat(
        uses24HourClock = mode.uses24HourClock(deviceUses24HourClock),
        locale = locale,
    )
}

private fun Long.formatWithPattern(pattern: String, locale: Locale): String = Instant.ofEpochMilli(this)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern(pattern, locale))
