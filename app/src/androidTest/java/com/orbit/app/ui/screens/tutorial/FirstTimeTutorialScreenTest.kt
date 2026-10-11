package com.orbit.app.ui.screens.tutorial

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
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

        composeRule.onNodeWithText("Write it down. Tallele takes it from here.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Step 1 of 4").assertExists()
        composeRule.onNodeWithText("Back").assertDoesNotExist()
        composeRule.onNodeWithText("Continue").performClick()
        composeRule.onNodeWithText("Tallele files what’s clear and asks only when it must.").assertIsDisplayed()
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

        composeRule.onNodeWithText("Write it down. Tallele takes it from here.")
            .performTouchInput { swipeLeft() }

        composeRule.onNodeWithText("Tallele files what’s clear and asks only when it must.").assertIsDisplayed()
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

        repeat(2) { composeRule.onNodeWithText("Continue").performClick() }

        composeRule.onNodeWithText("Pick your Spaces").assertIsDisplayed()
        composeRule.onNodeWithText("Household").performClick()
        composeRule.onNodeWithContentDescription("Add Space").assertIsDisplayed()
    }
}
