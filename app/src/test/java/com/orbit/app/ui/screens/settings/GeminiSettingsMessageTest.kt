package com.orbit.app.ui.screens.settings

import com.orbit.app.R
import com.orbit.app.integrations.gemini.GeminiApiErrorKind
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiSettingsMessageTest {
    @Test
    fun `connection failures are mapped to localized presentation resources`() {
        assertEquals(
            R.string.settings_gemini_error_no_internet,
            GeminiApiErrorKind.NoInternet.settingsMessageRes(),
        )
    }
}
