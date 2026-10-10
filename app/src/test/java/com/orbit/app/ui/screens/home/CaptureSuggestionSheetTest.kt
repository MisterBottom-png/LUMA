package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSuggestionSheetTest {
    @Test
    fun brainDumpSheetAllowsHiddenOnlyWhenClosingTheSession() {
        assertTrue(brainDumpSheetAllowsHidden(BrainDumpDismissalDecision.CloseSession))
        assertFalse(brainDumpSheetAllowsHidden(BrainDumpDismissalDecision.StepBack))
        assertFalse(brainDumpSheetAllowsHidden(BrainDumpDismissalDecision.ConfirmDiscard))
        assertFalse(brainDumpSheetAllowsHidden(BrainDumpDismissalDecision.Blocked))
    }

    @Test
    fun actionInProgressBlocksEveryDismissalPath() {
        val draft = BrainDumpDraft("Title", SuggestedItemType.Note, null, null)

        assertEquals(
            BrainDumpDismissalDecision.Blocked,
            brainDumpDismissalDecision(
                stage = BrainDumpStage.Edit,
                initialDraft = draft,
                draft = draft,
                actionInProgress = true,
            ),
        )
    }

    @Test
    fun nestedCaptureAndBrainDumpSetupsConsumeBackBeforeTheSheetIsDismissed() {
        assertTrue(hasNestedCaptureSetup(ActionSetup.Task))
        assertTrue(hasNestedCaptureSetup(ActionSetup.Reminder))
        assertFalse(hasNestedCaptureSetup(null))
        assertTrue(hasNestedBrainDumpSetup(showTaskSetup = true, showReminderSetup = false))
        assertTrue(hasNestedBrainDumpSetup(showTaskSetup = false, showReminderSetup = true))
        assertFalse(hasNestedBrainDumpSetup(showTaskSetup = false, showReminderSetup = false))
    }

    @Test
    fun brainDumpDraftKeepsAReminderTimeAndGivesATaskOnlyItsDay() {
        val parsedTime = 1_789_000_000_000L
        val reminder = BrainDumpSuggestion(
            id = "brain:1",
            rawText = "Walk the dog tomorrow at 1500",
            title = "Walk the dog",
            suggestedType = SuggestedItemType.Reminder,
            suggestedSpaceName = "Dog",
            confidence = 0.9f,
            tinyNextAction = "Get ready for the walk",
            reason = "The fragment contains a resolved time.",
            suggestedReminderAt = parsedTime,
        )
        val tomorrow = LocalDate.of(2026, 8, 20).toEpochDay()
        val task = reminder.copy(
            rawText = "Buy milk tomorrow",
            suggestedType = SuggestedItemType.Task,
            suggestedReminderAt = null,
            taskDateEpochDay = tomorrow,
        )

        assertEquals(parsedTime, initialBrainDumpDraft(reminder, emptyList()).scheduledAt)
        val taskDraft = initialBrainDumpDraft(task, emptyList())
        assertEquals(tomorrow, taskDraft.scheduledDateEpochDay)
        assertEquals(null, taskDraft.scheduledAt)
    }

    @Test
    fun taskDueLabelShowsAPickedTimeButNoTimeForADay() {
        val format = OrbitTimeFormat(uses24HourClock = true)
        val dueAt = LocalDateTime.of(2026, 8, 19, 21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertTrue(taskDueLabel(TaskDue(at = dueAt), format)?.endsWith("21:00") == true)
        val dayLabel = requireNotNull(taskDueLabel(TaskDue(dayEpochDay = LocalDate.of(2026, 8, 19).toEpochDay()), format))
        assertFalse(dayLabel.contains(":"))
        assertEquals(null, taskDueLabel(TaskDue(), format))
    }
}
