package com.orbit.app.ui.screens.home

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarCaptureContextTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val selectedDate = LocalDate.of(2026, 9, 12)

    @Test
    fun matchingCalendarDateBecomesDateOnlyTaskSchedule() {
        val dueAt = selectedDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        val schedule = calendarTaskSchedule(dueAt, selectedDate.toEpochDay(), zone)

        assertNull(schedule.dueAt)
        assertEquals(selectedDate.toEpochDay(), schedule.scheduledDateEpochDay)
    }

    @Test
    fun userSelectedDifferentDateKeepsExistingTaskDueDateBehavior() {
        val otherDate = selectedDate.plusDays(2)
        val dueAt = otherDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        val schedule = calendarTaskSchedule(dueAt, selectedDate.toEpochDay(), zone)

        assertEquals(dueAt, schedule.dueAt)
        assertNull(schedule.scheduledDateEpochDay)
    }

    @Test
    fun missingOrInvalidContextLeavesDueDateUnchanged() {
        val dueAt = selectedDate.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()

        assertEquals(dueAt, calendarTaskSchedule(dueAt, null, zone).dueAt)
        assertEquals(dueAt, calendarTaskSchedule(dueAt, Long.MAX_VALUE, zone).dueAt)
    }
}
