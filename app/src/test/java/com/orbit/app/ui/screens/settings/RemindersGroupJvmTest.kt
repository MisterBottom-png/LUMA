package com.orbit.app.ui.screens.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import com.orbit.app.testing.JvmComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.model.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RemindersGroupJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun rowsToggleTheirOwnSettingOnly_andDeliveryStatusIsShown() {
        var settings by mutableStateOf(AppSettings())
        composeRule.setContent {
            MaterialTheme {
                RemindersGroup(settings = settings, onSettingsChanged = { settings = it })
            }
        }

        composeRule.onNodeWithText("Sort right after saving").assertIsOff().performClick()
        assertTrue(settings.sortRightAfterSaving)
        // Off until the user asks for it: Home opens calm, without the keyboard.
        assertFalse(settings.focusCaptureOnOpen)

        composeRule.onNodeWithText("Open the keyboard on Home").assertIsOff().performClick()
        assertTrue(settings.focusCaptureOnOpen)
        assertTrue(settings.sortRightAfterSaving)

        composeRule.onNodeWithText("Reminder delivery").assertExists()
    }
}
