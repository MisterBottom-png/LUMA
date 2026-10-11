package com.orbit.app.ui.screens.spaces

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A Space's "+" writes the thought over the Space: one field, Save, and nothing else to choose. */
@RunWith(AndroidJUnit4::class)
class SpaceCaptureSheetJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun savesTheTrimmedThought_andCannotSaveNothing() {
        var saved: String? = null
        composeRule.setContent {
            MaterialTheme {
                SpaceCaptureSheet(spaceName = "Garden", onSave = { saved = it }, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("Add to Garden").assertExists()
        composeRule.onNodeWithText("Save thought").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("  Plant the tulips  ")
        composeRule.onNodeWithText("Save thought").assertIsEnabled().performClick()
        assertEquals("Plant the tulips", saved)
    }

    @Test
    fun cancelClosesWithoutSaving() {
        var saved: String? = null
        var dismissed = false
        composeRule.setContent {
            MaterialTheme {
                SpaceCaptureSheet(spaceName = "Garden", onSave = { saved = it }, onDismiss = { dismissed = true })
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("Maybe later")
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(true, dismissed)
        assertNull(saved)
    }
}
