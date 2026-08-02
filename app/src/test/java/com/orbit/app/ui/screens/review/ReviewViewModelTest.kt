package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.model.AppSettings
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewViewModelTest {
    @Test
    fun `date-only tasks are due today and overdue after their scheduled date`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 7, 20)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val todayTask = TaskEntity(title = "Today", scheduledDateEpochDay = today.toEpochDay())
        val oldTask = TaskEntity(title = "Old", scheduledDateEpochDay = today.minusDays(1).toEpochDay())

        assertTrue(todayTask.isDueOn(today, start, tomorrow))
        assertFalse(todayTask.isOverdueBefore(today, start))
        assertTrue(oldTask.isOverdueBefore(today, start))
    }

    @Test
    fun `weekly sources include completed activity only inside the current local week`() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 7, 22).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        val monday = LocalDate.of(2026, 7, 20).atStartOfDay(zone).toInstant().toEpochMilli()
        val old = monday - 1L
        val data = ReviewData(
            captures = emptyList(),
            notes = emptyList(),
            tasks = listOf(
                TaskEntity(id = 1, title = "Completed this week", status = TaskStatus.Done, completedAt = monday, updatedAt = monday),
                TaskEntity(id = 2, title = "Old task", updatedAt = old, createdAt = old),
            ),
            reminders = listOf(ReminderEntity(id = 3, title = "Completed reminder", dueAt = now, completedAt = now, updatedAt = now)),
            spaces = emptyList(),
            settings = AppSettings(),
            brainDumpCaptureIds = emptySet(),
        )

        assertEquals(listOf("reminder:3", "task:1"), weeklyReviewSources(data, now, zone).map { it.sourceId })
    }

    @Test
    fun onlyInboxCapturesRemainUnresolvedReviewItems() {
        assertTrue(isUnresolvedReviewCapture(CaptureEntity(rawText = "Needs a decision")))
        assertFalse(
            isUnresolvedReviewCapture(
                CaptureEntity(rawText = "Finalized source", status = CaptureStatus.Processed),
            ),
        )
        assertFalse(
            isUnresolvedReviewCapture(
                CaptureEntity(rawText = "Archived source", status = CaptureStatus.Archived),
            ),
        )
    }

    @Test
    fun unresolvedCaptureExplainsWhyReviewIsRequired() {
        val reason = CaptureEntity(rawText = "Needs context").reviewReason()

        assertEquals(ReviewReason.UnfinalizedCapture, reason)
    }
}
