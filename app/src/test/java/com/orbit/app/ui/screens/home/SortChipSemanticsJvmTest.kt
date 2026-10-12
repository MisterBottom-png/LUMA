package com.orbit.app.ui.screens.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** B18: the chip that removes a label is a button named "Remove label …", not a radio button. */
@RunWith(AndroidJUnit4::class)
class SortChipSemanticsJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun removeLabelIsANamedButton() {
        var removed = 0
        composeRule.setContent {
            MaterialTheme {
                SortChip(label = "#home  ×", selected = false, enabled = true, actionDescription = "Remove label home") { removed++ }
            }
        }
        composeRule.onNodeWithContentDescription("Remove label home")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, removed)
    }
}
