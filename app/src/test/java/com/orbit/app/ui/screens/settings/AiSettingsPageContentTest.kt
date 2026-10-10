package com.orbit.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AiSettingsPageContentTest {
    @Test
    fun eachAiPageRendersOnlyItsOwnedContent() {
        assertEquals(
            listOf(AiSettingsContent.Mode),
            aiSettingsContentFor(AiSettingsPage.Mode),
        )
        assertEquals(
            listOf(AiSettingsContent.GeminiKey, AiSettingsContent.Models),
            aiSettingsContentFor(AiSettingsPage.GeminiSetup),
        )
        assertEquals(
            listOf(AiSettingsContent.Features),
            aiSettingsContentFor(AiSettingsPage.Features),
        )
        assertEquals(
            listOf(AiSettingsContent.LocalLearning),
            aiSettingsContentFor(AiSettingsPage.LocalLearning),
        )
    }
}
