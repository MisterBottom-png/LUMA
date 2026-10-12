package com.orbit.app.domain.analyzer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
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
        assertLocal("800 tomorrow", LocalDate.of(2026, 7, 15), LocalTime.of(8, 0))
    }

    @Test
    fun pricesQuantitiesAndYearsAreNeverReadAsClockTimes() {
        listOf(
            "pay 12.50 euros tomorrow",
            "pay 1200 for rent tomorrow",
            "buy 200 eggs at the store",
            "remind me to renew the passport before 2027",
            "напомни продлить паспорт в 2027 году",
            "maksa homme 12.50 eurot",
            "купить 300 г сыра завтра",
        ).forEach { capture ->
            val result = interpretReminderTime(capture, now, zone)
            val local = result.epochMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            // None of these names a time of day: no 12:50, 12:00, 02:00, 20:27 or 03:00 may appear.
            assertNull("$capture -> $local", local)
        }
    }

    @Test
    fun aBareEarlyHourWithADayIsAskedNotReadAsTheSmallHours() {
        assertEquals(ReminderTimeStatus.NeedsClarification, interpretReminderTime("remind me tomorrow at 5", now, zone).status)
        assertEquals(ReminderTimeStatus.NeedsClarification, interpretReminderTime("tuleta homme kell 5 meelde", now, zone).status)
        // A morning hour is still read as morning, and an explicit 24-hour time is exact.
        assertLocal("remind me tomorrow at 9", LocalDate.of(2026, 7, 15), LocalTime.of(9, 0))
        assertLocal("remind me tomorrow at 17:00", LocalDate.of(2026, 7, 15), LocalTime.of(17, 0))
        assertLocal("remind me tomorrow at 5 pm", LocalDate.of(2026, 7, 15), LocalTime.of(17, 0))
    }

    @Test
    fun commonClockFormatsResolveToTheSameCanonicalLocalTime() {
        listOf("1600", "16:00", "4 PM", "today at 1600").forEach { capture ->
            assertLocal(capture, LocalDate.of(2026, 7, 14), LocalTime.of(16, 0))
        }
        assertLocal("tomorrow 0730", LocalDate.of(2026, 7, 15), LocalTime.of(7, 30))
    }

    @Test
    fun anEarlierExplicitTodayTimeAsksInsteadOfRollingOrFiringAtOnce() {
        val lateNow = Instant.parse("2026-07-14T18:00:00Z")

        val result = interpretReminderTime("1600 today", lateNow, zone)

        assertEquals(ReminderTimeStatus.NeedsClarification, result.status)
        assertNull(result.epochMillis)
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

    @Test
    fun embeddedFourDigitNumberWithoutTimeContextIsNotAssumedToBeATime() {
        val result = interpretReminderTime("Budget reference 1600 euros", now, zone)

        assertEquals(ReminderTimeStatus.Unspecified, result.status)
        assertNull(result.epochMillis)
    }

    @Test
    fun estonianRulePackResolvesCompactTimeAndPreservesDateLanguage() {
        val result = interpretReminderTime(
            rawText = "Tuleta mulle meelde homme kell 1600",
            now = now,
            zoneId = zone,
            locale = Locale.forLanguageTag("et"),
        )
        val local = Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone)

        assertEquals(ReminderTimeStatus.Resolved, result.status)
        assertEquals(LocalDate.of(2026, 7, 15), local.toLocalDate())
        assertEquals(LocalTime.of(16, 0), local.toLocalTime())
        assertEquals("16:00 homme", result.phrase)
    }

    @Test
    fun russianRulePackResolvesTwentyFourHourTimeAndPreservesDateLanguage() {
        val result = interpretReminderTime(
            rawText = "Напомни мне завтра в 16:00",
            now = now,
            zoneId = zone,
            locale = Locale.forLanguageTag("ru"),
        )
        val local = Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone)

        assertEquals(ReminderTimeStatus.Resolved, result.status)
        assertEquals(LocalDate.of(2026, 7, 15), local.toLocalDate())
        assertEquals(LocalTime.of(16, 0), local.toLocalTime())
        assertEquals("16:00 завтра", result.phrase)
    }

    @Test
    fun mixedLanguageReminderUsesSignalsWithoutTranslatingTheMatchedDatePhrase() {
        val result = interpretReminderTime(
            rawText = "Напомни tomorrow kell 1600",
            now = now,
            zoneId = zone,
            locale = Locale.forLanguageTag("et"),
        )

        assertEquals(ReminderTimeStatus.Resolved, result.status)
        assertEquals("16:00 tomorrow", result.phrase)
    }

    @Test
    fun localizedNamedDayPeriodResolvesWithoutChangingTwentyFourHourOutput() {
        val result = interpretReminderTime(
            rawText = "tomorrow at 10 in the morning",
            now = now,
            zoneId = zone,
            locale = Locale.ENGLISH,
        )
        val local = Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone)

        assertEquals(LocalTime.of(10, 0), local.toLocalTime())
        assertEquals("10:00 tomorrow", result.phrase)
    }

    private fun assertLocal(capture: String, date: LocalDate, time: LocalTime) {
        val result = interpretReminderTime(capture, now, zone)
        val local = Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone)

        assertEquals("Capture: $capture", ReminderTimeStatus.Resolved, result.status)
        assertEquals("Capture: $capture", date, local.toLocalDate())
        assertEquals("Capture: $capture", time, local.toLocalTime())
    }
}
