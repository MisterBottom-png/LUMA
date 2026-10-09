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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.ui.time.OrbitTimeFormat
import org.junit.Rule
import org.junit.Test

class ReviewScreenAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun constrainedWidthAndLargeTextKeepReviewPaneAndHeadingAvailable() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.6f)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).height(640.dp)) {
                        ReviewScreen(
                            uiState = ReviewUiState(),
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
                        )
                    }
                }
            }
        }

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Review"),
        ).assertExists()
        composeRule.onNode(isHeading()).assertExists()
    }

    @Test
    fun taskLoopKeepsOnePrimaryActionAndPlacesSecondaryActionsInAnAccessibleSheet() {
        composeRule.setContent {
            MaterialTheme {
                ResetLoopCard(
                    loop = ReviewLoop(
                        id = 1L,
                        type = ReviewLoopType.Task,
                        title = "A task to review",
                        updatedAt = 1L,
                    ),
                    smallerAction = null,
                    onKeepTaskActive = {},
                    onConfirmCapture = {},
                    onArchive = {},
                    onCompleteTask = {},
                    onDeferTask = {},
                    onDismissCapture = {},
                    onMakeSmaller = {},
                )
            }
        }

        composeRule.onNodeWithText("Keep active").assertIsDisplayed()
        composeRule.onNodeWithText("More actions").performClick()

        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "More actions"),
        ).assertExists()
        composeRule.onNodeWithText("Mark task done").assertIsDisplayed()
        composeRule.onNodeWithText("Defer to Someday").assertIsDisplayed()
        composeRule.onNodeWithText("Make smaller").assertIsDisplayed()
        composeRule.onNodeWithText("Archive task").assertIsDisplayed()
    }
}
