package com.orbit.app.ui.time

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.orbit.app.domain.model.LocalizedDatePatterns
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.uses24HourClock
import java.time.Instant
import java.time.LocalDate
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

    /** One set of date patterns, in this language's own order (see LocalizedDatePatterns). */
    private val dates: LocalizedDatePatterns
        get() = LocalizedDatePatterns.forLocale(locale)

    fun formatDate(epochMillis: Long): String =
        epochMillis.formatWithPattern(dates.weekdayDate, locale)

    fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(dates.weekdayDate, locale))

    fun formatDateWithYear(epochMillis: Long): String =
        epochMillis.formatWithPattern(dates.dateWithYear, locale)

    fun formatDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("${dates.date}, $timePattern", locale)

    fun formatWeekdayDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("${dates.weekdayDate}, $timePattern", locale)

    fun formatShortDateTime(epochMillis: Long): String =
        epochMillis.formatWithPattern("${dates.date}, $timePattern", locale)

    /** "Wednesday, October 15" in this language's order. */
    fun formatLongDay(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern(dates.longWeekdayDate, locale))

    private val timePattern: String
        get() = LocalizedDatePatterns.timePattern(uses24HourClock)
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
