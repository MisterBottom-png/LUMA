package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSuggestionSheetTest {
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
    fun mondayActionIsHiddenWhenIntegrationIsNotConfigured() {
        val actions = decisionActions(
            mondayConfigured = false,
            sendToMondayAvailable = true,
        )

        assertFalse(CaptureDecisionAction.SendMonday in actions)
    }

    @Test
    fun mondayActionIsHiddenWhenNoWorkingCallbackExists() {
        val actions = decisionActions(
            mondayConfigured = true,
            sendToMondayAvailable = false,
        )

        assertFalse(CaptureDecisionAction.SendMonday in actions)
    }

    @Test
    fun mondayActionRemainsAvailableForConfiguredWorkingIntegration() {
        val actions = decisionActions(
            mondayConfigured = true,
            sendToMondayAvailable = true,
        )

        assertTrue(CaptureDecisionAction.SendMonday in actions)
    }

    @Test
    fun brainDumpTaskSetupStartsWithTheParsedTime() {
        val parsedTime = 1_789_000_000_000L
        val item = BrainDumpSuggestion(
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

        assertEquals(parsedTime, brainDumpTaskInitialDueAt(item))
    }

    @Test
    fun taskDueAtLabelShowsTheParsedTime() {
        val dueAt = LocalDateTime.of(2026, 8, 19, 21, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val label = taskDueAtLabel(dueAt, OrbitTimeFormat(uses24HourClock = true))

        assertTrue(label?.endsWith("21:00") == true)
    }
}
