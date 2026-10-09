package com.orbit.app.ui.screens.tutorial

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FirstTimeTutorialScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstPageExposesProgressAndMovesForward() {
        composeRule.setContent {
            MaterialTheme {
                FirstTimeTutorialScreen(
                    isReplay = false,
                    onFinish = {},
                    onReplayBack = {},
                )
            }
        }

        composeRule.onNodeWithText("A home for thoughts before they become plans.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Step 1 of 7").assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertIsNotEnabled()
        composeRule.onNodeWithText("Next").performClick()
        composeRule.onNodeWithText("Capture thoughts and orient yourself in time.")
            .assertIsDisplayed()
    }

    @Test
    fun horizontalSwipeMovesToTheNextPage() {
        composeRule.setContent {
            MaterialTheme {
                FirstTimeTutorialScreen(
                    isReplay = false,
                    onFinish = {},
                    onReplayBack = {},
                )
            }
        }

        composeRule.onNodeWithText("A home for thoughts before they become plans.")
            .performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("Capture thoughts and orient yourself in time.")
            .assertIsDisplayed()
    }

    @Test
    fun skipFinishesOnlyOnce() {
        var finishCount = 0
        composeRule.setContent {
            MaterialTheme {
                FirstTimeTutorialScreen(
                    isReplay = false,
                    onFinish = { finishCount += 1 },
                    onReplayBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()
        composeRule.runOnIdle {
            assertEquals(1, finishCount)
        }
    }

    @Test
    fun firstTimeEmptyInstall_canSelectSuggestedSpaceAndAddCustomOne() {
        composeRule.setContent {
            MaterialTheme {
                FirstTimeTutorialScreen(
                    isReplay = false,
                    spaceSetupState = TutorialSpaceSetupUiState(canConfigure = true),
                    onToggleSpaceTemplate = {},
                    onAddCustomSpace = {},
                    onRemoveCustomSpace = {},
                    onFinish = {},
                    onReplayBack = {},
                )
            }
        }

        repeat(4) { composeRule.onNodeWithText("Next").performClick() }

        composeRule.onNodeWithText("Choose a few Spaces").assertIsDisplayed()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithContentDescription("Add Space").assertIsDisplayed()
    }
}
