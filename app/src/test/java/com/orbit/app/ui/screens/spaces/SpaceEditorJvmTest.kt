package com.orbit.app.ui.screens.spaces

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.SpaceNames
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.testing.JvmComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A new Space in one field: colour and icon are folded away and follow the name. */
@RunWith(AndroidJUnit4::class)
class SpaceEditorJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private var created: Triple<String, String, String>? = null

    private fun showEditor(space: SpaceEntity? = null) {
        composeRule.setContent {
            MaterialTheme {
                SpaceEditorDialog(
                    space = space,
                    existingSpaces = listOfNotNull(space),
                    onDismiss = {},
                    onConfirm = { name, icon, accent -> created = Triple(name, icon, accent) },
                    onRestoreExisting = null,
                )
            }
        }
    }

    @Test
    fun theNameAloneCreatesTheSpace_withItsOwnColourAndIcon() {
        showEditor()

        composeRule.onNodeWithText("Color and icon").assertExists()
        composeRule.onNodeWithText("Color").assertDoesNotExist()
        composeRule.onNode(hasSetTextAction()).performTextInput("Garden")
        composeRule.onNodeWithText("Create Space").performClick()

        composeRule.runOnIdle {
            assertEquals(
                Triple(
                    "Garden",
                    iconChoices[SpaceNames.autoIconIndex("Garden", iconChoices.size)],
                    accentChoices[SpaceNames.autoColorIndex("Garden", accentChoices.size)],
                ),
                created,
            )
        }
    }

    @Test
    fun openingColourAndIcon_letsTheUserPick() {
        showEditor()

        composeRule.onNode(hasSetTextAction()).performTextInput("Garden")
        composeRule.onNodeWithText("Color and icon").performClick()
        composeRule.onNodeWithText("Color").assertExists()
        composeRule.onNodeWithText("Icon").assertExists()
    }

    @Test
    fun editingKeepsTheSpacesOwnLook() {
        val space = SpaceEntity(id = 3, name = "Garden", icon = "pets", colorAccent = "#D7798D", sortOrder = 0)
        showEditor(space)

        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle { assertEquals(Triple("Garden", "pets", "#D7798D"), created) }
    }
}
