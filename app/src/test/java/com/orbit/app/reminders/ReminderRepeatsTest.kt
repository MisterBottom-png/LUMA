package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderRepeatsTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private fun millis(dateTime: LocalDateTime) = dateTime.atZone(zone).toInstant().toEpochMilli()
    private fun local(epoch: Long) = java.time.Instant.ofEpochMilli(epoch).atZone(zone).toLocalDateTime()
    private fun reminder(due: LocalDateTime, rule: ReminderRepeat?) =
        ReminderEntity(id = 1, title = "Water plants", dueAt = millis(due), repeatRule = rule?.storageToken)

    @Test
    fun dailyWeeklyAndWeekdaysKeepTheTimeOfDay() {
        val friday = LocalDateTime.of(2026, 10, 9, 8, 30)
        val justAfter = millis(friday) + 60_000

        assertEquals(friday.plusDays(1), local(ReminderRepeats.nextOccurrence(reminder(friday, ReminderRepeat.Daily), justAfter, zone)!!))
        assertEquals(friday.plusWeeks(1), local(ReminderRepeats.nextOccurrence(reminder(friday, ReminderRepeat.Weekly), justAfter, zone)!!))
        assertEquals(
            LocalDateTime.of(2026, 10, 12, 8, 30),
            local(ReminderRepeats.nextOccurrence(reminder(friday, ReminderRepeat.Weekdays), justAfter, zone)!!),
        )
    }

    @Test
    fun monthlyFromTheLastDayUsesTheLastDayOfShorterMonths() {
        val jan31 = LocalDateTime.of(2027, 1, 31, 9, 0)
        val next = local(ReminderRepeats.nextOccurrence(reminder(jan31, ReminderRepeat.Monthly), millis(jan31) + 1, zone)!!)
        assertEquals(LocalDate.of(2027, 2, 28), next.toLocalDate())
        assertEquals(LocalTime.of(9, 0), next.toLocalTime())
    }

    @Test
    fun aLongMissedRepeatJumpsToTheFirstFutureOccurrenceNotABacklog() {
        val tenDaysAgo = LocalDateTime.of(2026, 9, 29, 7, 0)
        val now = millis(LocalDateTime.of(2026, 10, 9, 12, 0))
        assertEquals(
            LocalDateTime.of(2026, 10, 10, 7, 0),
            local(ReminderRepeats.nextOccurrence(reminder(tenDaysAgo, ReminderRepeat.Daily), now, zone)!!),
        )
    }

    private fun anchored(due: LocalDateTime, rule: ReminderRepeat) =
        reminder(due, null).copy(repeatRule = RepeatSpec.forReminder(rule, millis(due), zone).toStorage())

    @Test
    fun daylightSavingGapMovesThatDayForwardButKeepsTheChosenTimeAfterwards() {
        // 2026-03-29 03:00 does not exist in Tallinn (clocks jump to 04:00).
        val before = anchored(LocalDateTime.of(2026, 3, 28, 3, 30), ReminderRepeat.Daily)
        assertEquals("daily@03:30", before.repeatRule)
        val gapDay = ReminderRepeats.markDone(before, before.dueAt + 1, zone)
        assertEquals(LocalDateTime.of(2026, 3, 29, 4, 30), local(gapDay.dueAt))
        // Stored through the repository rule: the chosen time is kept.
        assertEquals("daily@03:30", ReminderRepeats.reanchored(before, gapDay, zone).repeatRule)
        val after = ReminderRepeats.markDone(gapDay, gapDay.dueAt + 1, zone)
        assertEquals(LocalDateTime.of(2026, 3, 30, 3, 30), local(after.dueAt))
    }

    @Test
    fun aReminderGenuinelySetFor0430StaysAt0430AcrossTheSameNight() {
        val genuine = anchored(LocalDateTime.of(2026, 3, 28, 4, 30), ReminderRepeat.Daily)
        val gapDay = ReminderRepeats.markDone(genuine, genuine.dueAt + 1, zone)
        val after = ReminderRepeats.markDone(gapDay, gapDay.dueAt + 1, zone)
        assertEquals(LocalDateTime.of(2026, 3, 29, 4, 30), local(gapDay.dueAt))
        assertEquals(LocalDateTime.of(2026, 3, 30, 4, 30), local(after.dueAt))
    }

    @Test
    fun movingARepeatingReminderToANewTimeMovesLaterOccurrencesToo() {
        val weekly = anchored(LocalDateTime.of(2026, 10, 9, 8, 30), ReminderRepeat.Weekly)
        val edited = weekly.copy(dueAt = millis(LocalDateTime.of(2026, 10, 9, 18, 0)))

        val stored = ReminderRepeats.reanchored(weekly, edited, zone)

        assertEquals("weekly@18:00", stored.repeatRule)
        assertEquals(LocalDateTime.of(2026, 10, 16, 18, 0), local(ReminderRepeats.markDone(stored, stored.dueAt + 1, zone).dueAt))
    }

    @Test
    fun storedTokensParseToleratingOldAndUnknownForms() {
        assertEquals(RepeatSpec(ReminderRepeat.Weekly, null), RepeatSpec.parse("weekly"))
        assertEquals(ReminderRepeat.Monthly, ReminderRepeat.fromStorage("monthly@07:05"))
        assertEquals(RepeatSpec(ReminderRepeat.Daily, null), RepeatSpec.parse("daily@25:99"))
        assertNull(RepeatSpec.parse("fortnightly"))
        assertNull(RepeatSpec.parse(" "))
    }

    @Test
    fun doneCompletesOneOffAndUnknownRepeats_butAdvancesKnownOnes() {
        val due = LocalDateTime.of(2026, 10, 9, 8, 0)
        val now = millis(due) + 5 * 60_000

        val oneOff = ReminderRepeats.markDone(reminder(due, null), now, zone)
        assertEquals(now, oneOff.completedAt)

        val unknown = ReminderRepeats.markDone(reminder(due, null).copy(repeatRule = "fortnightly-v2"), now, zone)
        assertEquals(now, unknown.completedAt)
        assertEquals("fortnightly-v2", unknown.repeatRule)

        val daily = ReminderRepeats.markDone(
            reminder(due, ReminderRepeat.Daily).copy(deliveredNotificationAt = millis(due), snoozedUntil = now),
            now,
            zone,
        )
        assertNull(daily.completedAt)
        assertNull(daily.deliveredNotificationAt)
        assertNull(daily.snoozedUntil)
        assertEquals(due.plusDays(1), local(daily.dueAt))
        assertEquals(now, daily.updatedAt)
    }
}
