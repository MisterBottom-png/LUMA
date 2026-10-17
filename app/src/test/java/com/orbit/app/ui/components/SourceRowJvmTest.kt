package com.orbit.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.components.parts.PartsHost
import com.orbit.app.ui.components.parts.assertTextFullyShown
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** [SourceRow] at 200 % text in a 320 dp wide box, in both themes. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h2400dp-xxhdpi")
class SourceRowJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val longTitle =
        "A deliberately long source title that demonstrates how the row handles more text without " +
            "turning the evidence list into a small novel"

    private fun check(dark: Boolean) {
        var clicks = 0
        composeRule.setContent {
            PartsHost(fontScale = 2f, dark = dark) {
                Column {
                    SourceRow(title = longTitle, itemType = ItemDetailType.Note, onClick = { clicks++ })
                    SourceRow(
                        title = "Prepare the review notes",
                        itemType = ItemDetailType.Task,
                        dateTimeText = "Tomorrow, 09:30",
                        onClick = {},
                    )
                }
            }
        }
        composeRule.onNodeWithText(longTitle, useUnmergedTree = true).assertIsDisplayed().assertTextFullyShown()
        composeRule.onNodeWithText("Prepare the review notes", useUnmergedTree = true).assertIsDisplayed().assertTextFullyShown()
        composeRule.onNodeWithText("Task · Tomorrow, 09:30", useUnmergedTree = true).assertIsDisplayed().assertTextFullyShown()
        composeRule.onNodeWithText("Note", useUnmergedTree = true).assertIsDisplayed().assertTextFullyShown()
        composeRule.onAllNodesWithClick().forEach { it.assertHasClickAction().assertHeightIsAtLeast(48.dp) }
        composeRule.onAllNodes(hasClickAction())[0].performClick()
        assertEquals(1, clicks)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithClick() =
        onAllNodes(hasClickAction()).let { all -> (0 until all.fetchSemanticsNodes().size).map { all[it] } }

    @Test
    fun light_showsEveryWordAndKeepsA48dpTarget() = check(dark = false)

    @Test
    fun dark_showsEveryWordAndKeepsA48dpTarget() = check(dark = true)
}
