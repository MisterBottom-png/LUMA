package com.orbit.app.domain.analyzer

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Table-driven checks of the reminder time reader in English, Estonian and Russian.
 * "Now" is Tuesday 2026-07-14 13:00 in Tallinn.
 */
class MultilingualTimePhraseTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val now = Instant.parse("2026-07-14T10:00:00Z")

    private sealed interface Expect {
        data class ResolvesTo(val dateTime: LocalDateTime) : Expect
        data object Question : Expect
        data object Nothing : Expect
    }

    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0) =
        Expect.ResolvesTo(LocalDateTime.of(2026, month, day, hour, minute))

    private val english = listOf(
        "remind me at 8 tonight" to at(7, 14, 20),
        "call mom tonight at 9" to at(7, 14, 21),
        "dentist at 4 pm" to at(7, 14, 16),
        "remind me at 9:30 p.m." to at(7, 14, 21, 30),
        "remind me at 9am" to at(7, 15, 9),
        "in 2 hours check the oven" to at(7, 14, 15),
        "in 20 minutes" to at(7, 14, 13, 20),
        "in half an hour" to at(7, 14, 13, 30),
        "in an hour" to at(7, 14, 14),
        "pay rent in 3 days at 10:00" to at(7, 17, 10),
        "remind me on friday at 18:00" to at(7, 17, 18),
        "remind me monday at 9" to at(7, 20, 9),
        "remind me on tuesday at 10" to at(7, 21, 10),
        "12.11 at 14:00 remind me" to at(11, 12, 14),
        "remind me on 3 august at 10:30" to at(8, 3, 10, 30),
        "remind me august 3rd at 10:30" to at(8, 3, 10, 30),
        "remind me at noon tomorrow" to at(7, 15, 12),
        "tomorrow at 10 in the evening" to at(7, 15, 22),
        "day after tomorrow at 7:15" to at(7, 16, 7, 15),
        "remind me at 9" to Expect.Question,
        "remind me at 2" to Expect.Question,
        "remind me today at 11:00" to Expect.Question,
        "remind me tonight" to Expect.Question,
        "remind me tomorrow" to Expect.Question,
        "remind me tomorrow and on friday at 10:00" to Expect.Question,
        "remind me 1 hour earlier" to Expect.Question,
        "remind me on 12.11.2099 at 10:00" to Expect.Question,
        "buy milk" to Expect.Nothing,
        "version 1.2 release notes" to Expect.Nothing,
        "dinner tonight with friends" to Expect.Nothing,
        "buy sun cream" to Expect.Nothing,
    )

    private val estonian = listOf(
        "tuleta meelde täna õhtul kell 8" to at(7, 14, 20),
        "kell 8 õhtul helista emale" to at(7, 14, 20),
        "2 tunni pärast vaata ahju" to at(7, 14, 15),
        "tunni pärast" to at(7, 14, 14),
        "poole tunni pärast" to at(7, 14, 13, 30),
        "kahe päeva pärast kell 10" to at(7, 16, 10),
        "reedel kell 18" to at(7, 17, 18),
        "esmaspäeval kell 9" to at(7, 20, 9),
        "12.11 kell 14.30" to at(11, 12, 14, 30),
        "kell 12.11" to at(7, 15, 12, 11),
        "tuleta meelde homme hommikul kell 9" to at(7, 15, 9),
        "3. augustil kell 10" to at(8, 3, 10),
        "ülehomme kell 7.15" to at(7, 16, 7, 15),
        "homme keskpäeval" to at(7, 15, 12),
        "tuleta meelde kell 9" to Expect.Question,
        "tuleta meelde täna õhtul" to Expect.Question,
        "tuleta meelde täna kell 11" to Expect.Question,
        "tuleta meelde 1 tund varem" to Expect.Question,
        "osta piima" to Expect.Nothing,
    )

    private val russian = listOf(
        "напомни сегодня в 8 вечера" to at(7, 14, 20),
        "через 3 дня в 10:00" to at(7, 17, 10),
        "через 2 часа" to at(7, 14, 15),
        "через час" to at(7, 14, 14),
        "через полчаса" to at(7, 14, 13, 30),
        "через 20 минут" to at(7, 14, 13, 20),
        "в пятницу в 18:00" to at(7, 17, 18),
        "в понедельник в 9" to at(7, 20, 9),
        "12.11 в 14:00" to at(11, 12, 14),
        "в 12.11" to at(7, 15, 12, 11),
        "3 августа в 10:30" to at(8, 3, 10, 30),
        "завтра утром в 9" to at(7, 15, 9),
        "напомни в 9 утра" to at(7, 15, 9),
        "в 3 дня" to at(7, 14, 15),
        "послезавтра в 7:15" to at(7, 16, 7, 15),
        "напомни через 3 дня" to Expect.Question,
        "напомни в 9" to Expect.Question,
        "напомни сегодня вечером" to Expect.Question,
        "напомни на час раньше" to Expect.Question,
        "купить молоко" to Expect.Nothing,
    )

    @Test
    fun englishPhrases() = check(english, Locale.ENGLISH)

    @Test
    fun estonianPhrases() = check(estonian, Locale.forLanguageTag("et"))

    @Test
    fun russianPhrases() = check(russian, Locale.forLanguageTag("ru"))

    @Test
    fun relativeAdjustmentsAreReadInEveryLanguage() {
        assertEquals(Duration.ofHours(-1), interpretTimeAdjustment("1 hour earlier"))
        assertEquals(Duration.ofMinutes(30), interpretTimeAdjustment("30 minutes later"))
        assertEquals(Duration.ofHours(-1), interpretTimeAdjustment("1 tund varem", Locale.forLanguageTag("et")))
        assertEquals(Duration.ofMinutes(15), interpretTimeAdjustment("15 minutit hiljem", Locale.forLanguageTag("et")))
        assertEquals(Duration.ofHours(-1), interpretTimeAdjustment("на час раньше", Locale.forLanguageTag("ru")))
        assertEquals(Duration.ofMinutes(10), interpretTimeAdjustment("на 10 минут позже", Locale.forLanguageTag("ru")))
        assertNull(interpretTimeAdjustment("call mom"))
    }

    @Test
    fun taskDueDatesUnderstandWeekdaysAndRelativeDays() {
        val ru = interpretTaskDueDate("сдать отчёт через 3 дня", now, zone, Locale.forLanguageTag("ru"))
        assertEquals(
            java.time.LocalDate.of(2026, 7, 17),
            java.time.LocalDate.ofEpochDay(requireNotNull(ru).dateEpochDay),
        )
        val et = interpretTaskDueDate("saada arve reedel", now, zone, Locale.forLanguageTag("et"))
        assertEquals(
            java.time.LocalDate.of(2026, 7, 17),
            java.time.LocalDate.ofEpochDay(requireNotNull(et).dateEpochDay),
        )
        assertNull(interpretTaskDueDate("buy sun cream", now, zone))
    }

    @Test
    fun phrasesKeepTheLanguageOfTheDay() {
        assertEquals("20:00 сегодня", interpretReminderTime("напомни сегодня в 8 вечера", now, zone, Locale.forLanguageTag("ru")).phrase)
        assertEquals("09:00 homme", interpretReminderTime("tuleta meelde homme hommikul kell 9", now, zone, Locale.forLanguageTag("et")).phrase)
        assertEquals("15:00 today", interpretReminderTime("in 2 hours", now, zone).phrase)
    }

    private fun check(cases: List<Pair<String, Expect>>, locale: Locale) {
        val failures = cases.mapNotNull { (phrase, expected) ->
            val result = interpretReminderTime(phrase, now, zone, locale)
            val actual: Expect = when (result.status) {
                ReminderTimeStatus.Resolved -> Expect.ResolvesTo(
                    Instant.ofEpochMilli(requireNotNull(result.epochMillis)).atZone(zone).toLocalDateTime(),
                )
                ReminderTimeStatus.NeedsClarification -> Expect.Question
                ReminderTimeStatus.Unspecified -> Expect.Nothing
            }
            if (actual == expected) null else "\"$phrase\": expected $expected but was $actual"
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }
}
