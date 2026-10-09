package com.orbit.app.ui.screens.spaces

import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceNextItemTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 9)
    private val now = today.atTime(12, 0).toInstant(zone).toEpochMilli()
    private fun at(day: LocalDate, hour: Int) = day.atTime(hour, 0).toInstant(zone).toEpochMilli()

    @Test
    fun earliestUpcomingOpenItemWins_pastDoneAndCompletedAreIgnored() {
        val next = calculateSpaceNextItems(
            tasks = listOf(
                TaskEntity(id = 1, title = "Past", spaceId = 7, dueAt = at(today, 9)),
                TaskEntity(id = 2, title = "Done", spaceId = 7, dueAt = at(today, 13), status = TaskStatus.Done),
                TaskEntity(id = 3, title = "Later", spaceId = 7, dueAt = at(today.plusDays(2), 9)),
            ),
            reminders = listOf(
                ReminderEntity(id = 4, title = "Soon", spaceId = 7, dueAt = at(today, 15)),
                ReminderEntity(id = 5, title = "Handled", spaceId = 7, dueAt = at(today, 14), completedAt = now),
            ),
            now = now,
            zoneId = zone,
        )

        assertEquals("Soon", next[7L]?.title)
        assertNull(next[8L])
    }

    @Test
    fun aTaskPlannedForTodayCountsAndHasNoTime() {
        val next = calculateSpaceNextItems(
            tasks = listOf(TaskEntity(id = 1, title = "Today", spaceId = 3, scheduledDateEpochDay = today.toEpochDay())),
            reminders = emptyList(),
            now = now,
            zoneId = zone,
        )

        assertEquals("Today", next[3L]?.title)
        assertFalse(next.getValue(3L).hasTime)
    }
}
