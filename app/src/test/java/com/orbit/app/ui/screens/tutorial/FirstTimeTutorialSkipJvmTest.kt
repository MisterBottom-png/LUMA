package com.orbit.app.ui.screens.tutorial

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirstTimeTutorialSkipJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun skipSavesSpacesAlreadyChosen_thenLeavesOnce() {
        var setupRuns = 0
        var finishes = 0
        composeRule.setContent {
            MaterialTheme {
                FirstTimeTutorialScreen(
                    isReplay = false,
                    spaceSetupState = TutorialSpaceSetupUiState(
                        canConfigure = true,
                        customNames = listOf("Garden"),
                    ),
                    onFinishSpaceSetup = { done ->
                        setupRuns++
                        done()
                    },
                    onFinish = { finishes++ },
                    onReplayBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Skip").performClick()
        composeRule.waitForIdle()

        assertEquals(1, setupRuns)
        assertEquals(1, finishes)
    }
}
