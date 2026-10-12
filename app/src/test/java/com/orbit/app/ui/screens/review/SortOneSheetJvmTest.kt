package com.orbit.app.ui.screens.review

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.testing.JvmComposeRule
import com.orbit.app.ui.time.OrbitTimeFormat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Review §9.3 screen 8: "Save it like this?" with the suggestion, Choose something else and Not now. */
@RunWith(AndroidJUnit4::class)
class SortOneSheetJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    @Test
    fun theSuggestionIsShownAndSavedOnlyOnTap() {
        var saved = 0
        var other = 0
        var notNow = 0
        composeRule.setContent {
            MaterialTheme {
                SortOneSheet(
                    state = SortOneUiState(
                        captureId = 1,
                        step = SortOneStep.Confirm,
                        title = "Buy milk and coffee",
                        createdAt = System.currentTimeMillis(),
                        suggestedType = SuggestedItemType.Task,
                        spaceId = 2,
                    ),
                    spaces = listOf(SpaceEntity(id = 2, name = "Groceries", icon = "g", colorAccent = "#000000", sortOrder = 0)),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true, locale = Locale.UK),
                    onTitleChange = {}, onSelectSpace = {}, onSaveSuggested = { saved++ },
                    onChooseSomethingElse = { other++ }, onChooseNote = {}, onChooseTask = {}, onChooseReminder = {},
                    onRemindAt = {}, onPickTime = {}, onBackToTime = {}, onBackFromTime = {}, onKeepAsTask = {},
                    onDecideLater = {}, onMoreOptions = {}, onDismiss = { notNow++ },
                )
            }
        }
        composeRule.onNodeWithText("Save it like this?").assertExists()
        composeRule.onNodeWithText("Task in Groceries").assertExists()
        composeRule.onNodeWithText("No date").assertExists()
        assertEquals(0, saved)
        composeRule.onNodeWithText("Save as a task").performScrollTo().performClick()
        composeRule.onNodeWithText("Choose something else").performScrollTo().performClick()
        composeRule.onNodeWithText("Not now").performScrollTo().performClick()
        assertEquals(listOf(1, 1, 1), listOf(saved, other, notNow))
    }
}
