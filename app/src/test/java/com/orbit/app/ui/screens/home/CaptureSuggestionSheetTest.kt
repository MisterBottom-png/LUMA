package com.orbit.app.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSuggestionSheetTest {
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
}
