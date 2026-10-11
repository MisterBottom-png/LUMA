package com.orbit.app.data.export

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A format 1–6 backup gives each thought the outcome the 10→11 migration would. */
class LegacyCaptureOutcomesTest {
    @Test
    fun openReminderIsReminderSet_linkedItemIsFiled_everythingElseStaysSaved() {
        val captures = listOf(
            CaptureEntity(id = 1, rawText = "remind", status = CaptureStatus.Processed, linkedItemId = 30, updatedAt = 7),
            CaptureEntity(id = 2, rawText = "note", status = CaptureStatus.Processed, linkedItemId = 20, updatedAt = 8),
            CaptureEntity(id = 3, rawText = "done reminder", status = CaptureStatus.Processed, linkedItemId = 31, updatedAt = 9),
            CaptureEntity(id = 4, rawText = "unsorted", status = CaptureStatus.Inbox),
            CaptureEntity(id = 5, rawText = "let go", status = CaptureStatus.Archived),
        )
        val reminders = listOf(
            ReminderEntity(id = 30, title = "Open", dueAt = 1, linkedCaptureId = 1),
            ReminderEntity(id = 31, title = "Completed", dueAt = 1, linkedCaptureId = 3, completedAt = 2),
            // Points at an unsorted thought: does not make it a set reminder.
            ReminderEntity(id = 32, title = "Stray", dueAt = 1, linkedCaptureId = 4),
        )

        val restored = withLegacyOutcomes(captures, reminders).associateBy { it.id }

        assertEquals(CaptureOutcome.ReminderSet, restored.getValue(1).outcome)
        assertEquals(7L, restored.getValue(1).decidedAt)
        assertEquals(CaptureOutcome.Filed, restored.getValue(2).outcome)
        assertEquals(CaptureOutcome.Filed, restored.getValue(3).outcome)
        assertEquals(CaptureOutcome.Saved, restored.getValue(4).outcome)
        assertNull(restored.getValue(4).decidedAt)
        assertEquals(CaptureOutcome.Saved, restored.getValue(5).outcome)
    }
}
