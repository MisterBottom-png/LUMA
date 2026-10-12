package com.orbit.app.ui.screens.spaces

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.time.OrbitTimeFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Space "+" sheet, step by step (review §9.2). */
@RunWith(AndroidJUnit4::class)
class SpaceCaptureSheetJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val timeFormat = OrbitTimeFormat(uses24HourClock = true, locale = Locale.UK)

    private fun sheet(
        state: SpaceCaptureUiState,
        initialDraft: String = "",
        onSave: () -> Unit = {},
        onDismiss: () -> Unit = {},
        onSaveAs: (SpaceSaveAs) -> Unit = {},
        onDecideLater: () -> Unit = {},
        onRemindAt: (Long) -> Unit = {},
    ) {
        composeRule.setContent {
            var draft by remember { mutableStateOf(initialDraft) }
            MaterialTheme {
                SpaceCaptureFlowSheet(
                    state = state,
                    spaceName = "Work",
                    draft = draft,
                    timeFormat = timeFormat,
                    onDraftChange = { draft = it },
                    onSaveAsChange = onSaveAs,
                    onSave = onSave,
                    onDismiss = onDismiss,
                    onRemindAt = onRemindAt,
                    onPickTime = {},
                    onBackToTimeQuestion = {},
                    onKeepAsTask = {},
                    onChooseNote = {},
                    onChooseTask = {},
                    onChooseReminder = {},
                    onDecideLater = onDecideLater,
                )
            }
        }
    }

    @Test
    fun theWriteStepOffersSaveAsAndCannotSaveNothing() {
        var saves = 0
        var chosen: SpaceSaveAs? = null
        sheet(SpaceCaptureUiState(spaceId = 1), onSave = { saves++ }, onSaveAs = { chosen = it })

        composeRule.onNodeWithText("Add to Work").assertExists()
        composeRule.onNodeWithText("Let Tallele decide").assertExists()
        composeRule.onNodeWithText("Save to Work").assertIsNotEnabled()
        composeRule.onNodeWithText("Reminder").performClick()
        assertEquals(SpaceSaveAs.Reminder, chosen)
        composeRule.onNode(hasSetTextAction()).performTextInput("Plant the tulips")
        composeRule.onNodeWithText("Save to Work").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }

    @Test
    fun cancelClosesWithoutSaving() {
        var saves = 0
        var dismissed = false
        sheet(SpaceCaptureUiState(spaceId = 1), initialDraft = "Maybe later", onSave = { saves++ }, onDismiss = { dismissed = true })
        composeRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
        assertEquals(0, saves)
    }

    @Test
    fun aFailedSaveSaysSo() {
        sheet(SpaceCaptureUiState(spaceId = 1, failed = true), initialDraft = "Water the roses")
        composeRule.onNodeWithText("Could not save that capture. Your text is still here.").assertExists()
    }

    @Test
    fun whileSavingNothingCanBeTappedTwiceOrClosed() {
        sheet(SpaceCaptureUiState(spaceId = 1, busy = true), initialDraft = "Water the roses")
        composeRule.onNodeWithText("Save to Work").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test
    fun aReminderWithoutATimeAsksWhen_withQuickTimesAndKeepAsTask() {
        val tomorrowNine = System.currentTimeMillis() + 24L * 60 * 60_000L
        var reminded: Long? = null
        sheet(
            SpaceCaptureUiState(
                spaceId = 1,
                step = SpaceCaptureStep.TimeQuestion,
                captureId = 9,
                thought = "Remind me to renew the parking permit",
                quickTimes = listOf(SpaceQuickTime(tomorrowNine, QuickDay.Tomorrow)),
            ),
            onRemindAt = { reminded = it },
        )
        composeRule.onNodeWithText("Saved to Work").assertExists()
        composeRule.onNodeWithText("When should this remind you?").assertExists()
        composeRule.onNodeWithText("Keep as a task, no reminder").assertExists()
        composeRule.onNodeWithText("Tomorrow").performScrollTo().performClick()
        assertEquals(tomorrowNine, reminded)
    }

    @Test
    fun anUnclearThoughtAsksWhatItShouldBe_withDecideLater() {
        var later = 0
        sheet(
            SpaceCaptureUiState(spaceId = 1, step = SpaceCaptureStep.TypeQuestion, captureId = 9, thought = "Parking permit"),
            onDecideLater = { later++ },
        )
        composeRule.onNodeWithText("What should this be?").assertExists()
        composeRule.onNodeWithText("Something to keep and read later").assertExists()
        composeRule.onNodeWithText("Decide later").performScrollTo().performClick()
        assertEquals(1, later)
    }
}
