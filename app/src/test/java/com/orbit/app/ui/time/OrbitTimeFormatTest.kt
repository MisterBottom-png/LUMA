package com.orbit.app.ui.time

import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbitTimeFormatTest {
    @Test
    fun localTimelineLabels_followResolvedTimeFormatSetting() {
        val time = LocalTime.of(17, 33)
        val format24Hour = OrbitTimeFormat(uses24HourClock = true, locale = Locale.US)
        val format12Hour = OrbitTimeFormat(uses24HourClock = false, locale = Locale.US)

        assertEquals("17:33", format24Hour.formatTime(time))
        assertEquals("5:33 PM", format12Hour.formatTime(time))
        assertEquals("24:00", format24Hour.formatEndOfDay())
        assertEquals("12:00 AM", format12Hour.formatEndOfDay())
    }

    @Test
    fun russian_date_formatting_keeps_the_month_name_in_its_native_lowercase() {
        val epochMillis = LocalDateTime.of(2026, 7, 26, 12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val label = OrbitTimeFormat(
            uses24HourClock = true,
            locale = Locale.forLanguageTag("ru"),
        ).formatDate(epochMillis)

        assertTrue(label.contains("июл"))
    }
}
