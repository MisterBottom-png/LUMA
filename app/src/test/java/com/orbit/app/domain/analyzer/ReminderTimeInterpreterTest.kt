package com.orbit.app.domain.analyzer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeInterpreterTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val now = Instant.parse("2026-07-14T10:00:00Z")

    @Test
    fun compactTwentyFourHourTimesResolveOnExplicitLocalDay() {
        assertLocal("set for 1600 today", LocalDate.of(2026, 7, 14), LocalTime.of(16, 0))
        assertLocal("0830 tomorrow", LocalDate.of(2026, 7, 15), LocalTime.of(8, 30))
        assertLocal("800 today", LocalDate.of(2026, 7, 14), LocalTime.of(8, 0))
    }

    @Test
    fun commonClockFormatsResolveToTheSameCanonicalLocalTime() {
        listOf("1600", "16:00", "4 PM", "today at 1600").forEach { capture ->
            assertLocal(capture, LocalDate.of(2026, 7, 14), LocalTime.of(16, 0))
        }
        assertLocal("tomorrow 0730", LocalDate.of(2026, 7, 15), LocalTime.of(7, 30))
    }

    @Test
    fun anEarlierExplicitTodayTimeIsNotSilentlyRolledToTomorrow() {
        val lateNow = Instant.parse("2026-07-14T18:00:00Z")

        val result = interpretReminderTime("1600 today", lateNow, zone)

        assertEquals(ReminderTimeStatus.Resolved, result.status)
        assertEquals(
            LocalDate.of(2026, 7, 14),
            Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone).toLocalDate(),
        )
    }

    @Test
    fun nonUtcRoundTripKeepsTheDisplayedHour() {
        val result = interpretReminderTime("1600 today", now, zone)
        val local = Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone)

        assertEquals(LocalDate.of(2026, 7, 14), local.toLocalDate())
        assertEquals(LocalTime.of(16, 0), local.toLocalTime())
        assertEquals(Instant.parse("2026-07-14T13:00:00Z"), local.toInstant())
    }

    @Test
    fun invalidCompactTimesRequireClarification() {
        listOf("set for 2460 today", "reminder at 2965").forEach { capture ->
            val result = interpretReminderTime(capture, now, zone)

            assertEquals(ReminderTimeStatus.NeedsClarification, result.status)
            assertNull(result.epochMillis)
            assertNull(result.phrase)
        }
    }

    @Test
    fun threeDigitNumberWithoutTimeContextIsNotAssumedToBeATime() {
        val result = interpretReminderTime("Keep reference 800", now, zone)

        assertEquals(ReminderTimeStatus.Unspecified, result.status)
        assertNull(result.epochMillis)
    }

    private fun assertLocal(capture: String, date: LocalDate, time: LocalTime) {
        val result = interpretReminderTime(capture, now, zone)
        val local = Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone)

        assertEquals("Capture: $capture", ReminderTimeStatus.Resolved, result.status)
        assertEquals("Capture: $capture", date, local.toLocalDate())
        assertEquals("Capture: $capture", time, local.toLocalTime())
    }
}
