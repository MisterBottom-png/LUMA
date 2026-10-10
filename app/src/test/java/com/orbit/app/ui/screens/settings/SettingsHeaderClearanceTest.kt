package com.orbit.app.ui.screens.settings

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsHeaderClearanceTest {
    @Test
    fun headerClearanceCoversTheStatusBarAndTheMeasuredHeader() {
        assertEquals(212.dp, settingsHeaderClearance(statusBarTop = 24.dp, headerHeight = 200.dp))
        assertEquals(112.dp, settingsHeaderClearance(statusBarTop = 24.dp, headerHeight = 96.dp))
    }
}
