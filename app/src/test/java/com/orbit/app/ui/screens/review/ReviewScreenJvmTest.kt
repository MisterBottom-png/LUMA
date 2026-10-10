package com.orbit.app.ui.screens.review

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import com.orbit.app.testing.JvmComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.ui.time.OrbitTimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Review rendered on the JVM (Robolectric), including large text in a narrow window. */
@RunWith(AndroidJUnit4::class)
class ReviewScreenJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private fun setReview(state: ReviewUiState, fontScale: Float = 1f, onAccept: (ToSortItem) -> Unit = {}, onChange: (ToSortItem) -> Unit = {}) {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        ReviewScreen(
                            uiState = state,
                            timeFormat = OrbitTimeFormat(uses24HourClock = true),
                            onReviewItemSelected = {},
                            onKeepTaskActive = {},
                            onConfirmCapture = {},
                            onArchive = {},
                            onCompleteTask = {},
                            onDeferTask = {},
                            onDismissCapture = {},
                            onMakeSmaller = {},
                            onUndoTaskMutation = {},
                            onTaskUndoExpired = {},
                            onCarryForwardTomorrow = {},
                            onCarryForwardToDate = { _, _ -> },
                            onKeepCarryForwardUnscheduled = {},
                            onCompleteCarryForward = {},
                            onWeeklyLookBackVisible = {},
                            onAcceptToSort = onAccept,
                            onChangeToSort = onChange,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun constrainedWidthAndLargeTextKeepReviewPaneAndHeadingAvailable() {
        setReview(ReviewUiState(), fontScale = 1.6f)
        composeRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Review")).assertExists()
        composeRule.onNode(isHeading() and hasText("Review")).assertExists()
        composeRule.onNodeWithText("All sorted").assertExists()
    }

    @Test
    fun toSortShowsTheSuggestionAndConfirmsInOneTap() {
        val item = ToSortItem(
            captureId = 7,
            text = "send the quarterly report",
            createdAt = 1_800_000_000_000L,
            state = ToSortState.Suggested,
            suggestedType = SuggestedItemType.Task,
            suggestedTitle = "Send the quarterly report",
        )
        var accepted: ToSortItem? = null
        setReview(ReviewUiState(toSort = listOf(item)), onAccept = { accepted = it })

        // The suggestion is one check button; its label says what it does.
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription("Make it a task"))
        composeRule.onNodeWithContentDescription("Make it a task").performClick()
        assertEquals(7L, accepted?.captureId)
    }

    @Test
    fun sortOneByOneShowsOneThoughtAndEndsWithAllSorted() {
        val first = ToSortItem(
            captureId = 1,
            text = "buy milk",
            createdAt = 1_800_000_000_000L,
            state = ToSortState.Suggested,
            suggestedType = SuggestedItemType.Task,
        )
        val second = first.copy(captureId = 2, text = "garden ideas", suggestedType = SuggestedItemType.Note)
        var accepted: ToSortItem? = null
        setReview(ReviewUiState(toSort = listOf(first, second)), onAccept = { accepted = it })

        composeRule.onNodeWithText("Sort one by one").performClick()
        composeRule.onNodeWithText("1 of 2").assertExists()
        composeRule.onNodeWithText("Make it a task").performClick()
        assertEquals(1L, accepted?.captureId)

        // Skipping the rest ends the flow with a clear finish.
        composeRule.onNodeWithText("Skip for now").performClick()
        composeRule.onNodeWithText("Skip for now").performClick()
        composeRule.onNodeWithText("All sorted").assertExists()
    }

    @Test
    fun aReminderWithoutTimeAsksForAChoiceInsteadOfConfirming() {
        val item = ToSortItem(
            captureId = 9,
            text = "remind me to call",
            createdAt = 1_800_000_000_000L,
            state = ToSortState.NeedsChoice,
            suggestedType = SuggestedItemType.Reminder,
            suggestedTitle = "Call",
        )
        var changed: ToSortItem? = null
        setReview(ReviewUiState(toSort = listOf(item)), onChange = { changed = it })

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Pick a time"))
        composeRule.onNodeWithText("Pick a time").performClick()
        assertEquals(9L, changed?.captureId)
    }

    /**
     * Shows Review with feedback held like the ViewModel holds it: clearing only the
     * exact feedback the screen reports as shown.
     */
    private fun setReviewWithFeedback(
        token: SortUndoToken,
        onUndo: (SortUndoToken) -> Unit,
        currentFeedback: (SortUndoToken?) -> Unit,
    ) {
        var state by mutableStateOf(ReviewUiState(pendingSortUndo = token))
        composeRule.setContent {
            MaterialTheme {
                Box(Modifier.width(360.dp).height(720.dp)) {
                    ReviewScreen(
                        uiState = state,
                        timeFormat = OrbitTimeFormat(uses24HourClock = true),
                        onReviewItemSelected = {},
                        onKeepTaskActive = {},
                        onConfirmCapture = {},
                        onArchive = {},
                        onCompleteTask = {},
                        onDeferTask = {},
                        onDismissCapture = {},
                        onMakeSmaller = {},
                        onUndoTaskMutation = {},
                        onTaskUndoExpired = {},
                        onCarryForwardTomorrow = {},
                        onCarryForwardToDate = { _, _ -> },
                        onKeepCarryForwardUnscheduled = {},
                        onCompleteCarryForward = {},
                        onWeeklyLookBackVisible = {},
                        onUndoSort = onUndo,
                        onSortFeedbackShown = { shownToken, shownMessage ->
                            if (state.pendingSortUndo == shownToken && state.sortMessage == shownMessage) {
                                state = state.copy(pendingSortUndo = null, sortMessage = null)
                            }
                            currentFeedback(state.pendingSortUndo)
                        },
                    )
                }
            }
        }
    }

    @Test
    fun undoAfterSortingStaysOnScreenAndUndoes() {
        val token = SortUndoToken.Accepted(captureId = 3, itemType = SuggestedItemType.Task, itemId = 30)
        var undone: SortUndoToken? = null
        setReviewWithFeedback(token, onUndo = { undone = it }, currentFeedback = {})

        // Well after the first frame, the Undo action is still there to tap.
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithText("Undo").assertExists()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.waitForIdle()

        assertEquals(token, undone)
    }

    @Test
    fun theSortFeedbackIsClearedOnlyAfterItWasShown() {
        val token = SortUndoToken.LetGo(captureId = 4)
        var remaining: SortUndoToken? = token
        setReviewWithFeedback(token, onUndo = {}, currentFeedback = { remaining = it })

        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithText("Undo").assertExists()
        // Long snackbars stay about ten seconds; afterwards the feedback is cleared.
        composeRule.mainClock.advanceTimeBy(12_000)
        composeRule.waitForIdle()
        assertNull(remaining)
    }
}
