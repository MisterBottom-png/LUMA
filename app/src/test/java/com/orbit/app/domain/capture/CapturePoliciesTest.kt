package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePoliciesTest {
    private val now = 1_800_000_000_000L

    private fun analysis(
        type: SuggestedItemType,
        status: ReminderTimeStatus = ReminderTimeStatus.Unspecified,
        at: Long? = null,
        confidence: Float = 0.8f,
    ) = CaptureAnalysis(
        rawText = "x",
        suggestedType = type,
        suggestedSpaceName = "Inbox",
        suggestedNextAction = "",
        relatedTopics = emptyList(),
        reminderPossible = type == SuggestedItemType.Reminder,
        suggestedReminderAt = at,
        reminderTimeStatus = status,
        confidence = confidence,
    )

    @Test
    fun homeAsksOnlyForClearlyTimeSensitiveThoughts() {
        assertTrue(analysis(SuggestedItemType.Reminder, ReminderTimeStatus.Resolved, now + 60_000).needsImmediateTimeQuestion(now))
        assertTrue(analysis(SuggestedItemType.Reminder, ReminderTimeStatus.NeedsClarification).needsImmediateTimeQuestion(now))
    }

    @Test
    fun everythingElseWaitsQuietlyInToSort() {
        assertFalse(analysis(SuggestedItemType.Note).needsImmediateTimeQuestion(now))
        assertFalse(analysis(SuggestedItemType.Task, ReminderTimeStatus.Resolved, now + 60_000).needsImmediateTimeQuestion(now))
        assertFalse(analysis(SuggestedItemType.Reminder, ReminderTimeStatus.Unspecified).needsImmediateTimeQuestion(now))
        assertFalse(analysis(SuggestedItemType.Reminder, ReminderTimeStatus.Resolved, now - 60_000).needsImmediateTimeQuestion(now))
        assertFalse(
            analysis(SuggestedItemType.Reminder, ReminderTimeStatus.Resolved, now + 60_000, confidence = 0.5f)
                .needsImmediateTimeQuestion(now),
        )
    }
}
