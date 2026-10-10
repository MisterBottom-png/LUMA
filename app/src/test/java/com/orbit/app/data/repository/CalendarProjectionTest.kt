package com.orbit.app.data.repository

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import java.time.Instant
import com.orbit.app.reminders.ReminderRepeat
import com.orbit.app.reminders.RepeatSpec
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarProjectionTest {
    @Test
    fun dateOnlyNoteKeepsOriginalIdentifierAndCivilDate() {
        val date = LocalDate.of(2026, 7, 14)
        val entry = requireNotNull(
            NoteEntity(
                id = 11,
                title = "Reference",
                body = "",
                scheduledDateEpochDay = date.toEpochDay(),
            ).toCalendarEntryOrNull(),
        )

        assertEquals(CalendarItemType.Note, entry.id.sourceType)
        assertEquals(11L, entry.id.sourceItemId)
        assertEquals(CalendarSchedule.DateOnly(date), entry.schedule)
    }

    @Test
    fun timedCompletedTaskPreservesStatusAndStart() {
        val entry = requireNotNull(
            TaskEntity(
                id = 12,
                title = "Prepare supplies",
                status = TaskStatus.Done,
                dueAt = 9_000_000,
                completedAt = 8_000_000,
            ).toCalendarEntryOrNull(),
        )

        assertEquals(CalendarItemType.Task, entry.id.sourceType)
        assertEquals(CalendarSchedule.Timed(Instant.ofEpochMilli(9_000_000)), entry.schedule)
        assertEquals(TaskStatus.Done, entry.taskStatus)
        assertEquals(8_000_000L, entry.completedAt)
    }

    @Test
    fun conflictingScheduleIsExcludedSafely() {
        val entry = NoteEntity(
            id = 13,
            title = "Conflicting",
            body = "",
            scheduledDateEpochDay = 20_000,
            scheduledAt = 9_000_000,
        ).toCalendarEntryOrNull()

        assertNull(entry)
    }

    @Test
    fun reminderProjectionKeepsTargetOffsetAndDerivedNotificationTime() {
        val entry = ReminderEntity(
            id = 14,
            title = "Check supplies",
            dueAt = 7_200_000,
            notificationOffsetMinutes = 30,
        ).toCalendarEntry()

        assertEquals(CalendarItemType.Reminder, entry.id.sourceType)
        assertEquals(7_200_000L, entry.reminderTargetAt)
        assertEquals(30L, entry.notificationOffsetMinutes)
        assertEquals(5_400_000L, entry.notificationAt)
        assertTrue(entry.notificationEnabled == true)
    }

    @Test
    fun weeklyReminderProjectsLaterOccurrencesInsideRange() {
        val zone = ZoneId.of("Europe/Tallinn")
        val first = ZonedDateTime.of(2026, 10, 5, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val reminder = ReminderEntity(
            id = 21,
            title = "Water plants",
            dueAt = first,
            repeatRule = RepeatSpec.forReminder(ReminderRepeat.Weekly, first, zone).toStorage(),
        )
        val start = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val occurrences = upcomingOccurrences(reminder, start, end, nowMillis = first - 1, zoneId = zone)

        assertEquals(
            listOf(12, 19, 26).map { day ->
                ZonedDateTime.of(2026, 10, day, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
            },
            occurrences,
        )
        val entry = reminder.copy(dueAt = occurrences.first()).toCalendarEntry(isRepeatOccurrence = true)
        assertEquals(21L, entry.id.sourceItemId)
        assertEquals(ReminderRepeat.Weekly, entry.repeat)
        assertTrue(entry.isRepeatOccurrence)
    }

    @Test
    fun overdueDailyReminderDoesNotFillPastDays() {
        val zone = ZoneId.of("Europe/Tallinn")
        val due = ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val now = ZonedDateTime.of(2026, 10, 4, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val reminder = ReminderEntity(
            id = 22,
            title = "Stretch",
            dueAt = due,
            repeatRule = RepeatSpec.forReminder(ReminderRepeat.Daily, due, zone).toStorage(),
        )
        val start = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 10, 7, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val occurrences = upcomingOccurrences(reminder, start, end, nowMillis = now, zoneId = zone)

        assertEquals(
            listOf(5, 6).map { day ->
                ZonedDateTime.of(2026, 10, day, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
            },
            occurrences,
        )
    }

    @Test
    fun oneOffAndCompletedRemindersProjectNothing() {
        val zone = ZoneId.of("Europe/Tallinn")
        val due = ZonedDateTime.of(2026, 10, 1, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        val end = due + 30L * 24 * 60 * 60 * 1000
        val oneOff = ReminderEntity(id = 23, title = "Call back", dueAt = due)
        val completed = oneOff.copy(repeatRule = "daily@08:00", completedAt = due)

        assertTrue(upcomingOccurrences(oneOff, due, end, nowMillis = due, zoneId = zone).isEmpty())
        assertTrue(upcomingOccurrences(completed, due, end, nowMillis = due, zoneId = zone).isEmpty())
    }
}
