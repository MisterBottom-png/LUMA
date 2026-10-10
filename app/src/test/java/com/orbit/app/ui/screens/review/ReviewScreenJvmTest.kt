package com.orbit.app.ui.screens.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
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

        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Make it a task"))
        composeRule.onNodeWithText("Make it a task").performClick()
        assertEquals(7L, accepted?.captureId)
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
}
