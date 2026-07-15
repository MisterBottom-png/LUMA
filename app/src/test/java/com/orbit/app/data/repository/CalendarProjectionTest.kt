package com.orbit.app.data.repository

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import java.time.Instant
import java.time.LocalDate
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
}
