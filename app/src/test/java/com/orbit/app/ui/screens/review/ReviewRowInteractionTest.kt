package com.orbit.app.ui.screens.review

import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewRowInteractionTest {
    @Test
    fun `review row click forwards the exact item for every review item type`() {
        ReviewItemType.entries.forEachIndexed { index, type ->
            val item = ReviewItem(
                id = index + 1L,
                type = type,
                title = type.name,
                timestamp = 100L + index,
            )
            var selected: ReviewItem? = null

            reviewRowClick(item) { selected = it }.invoke()

            assertEquals(item, selected)
        }
    }

    @Test
    fun taskResetLoopHasOneSafePrimaryActionAndSecondaryActionsInMore() {
        val plan = reviewLoopActionPlan(
            ReviewLoop(id = 1L, type = ReviewLoopType.Task, title = "Keep task", updatedAt = 1L),
        )

        assertEquals(ReviewLoopAction.KeepActive, plan.primary)
        assertEquals(
            listOf(
                ReviewLoopAction.CompleteTask,
                ReviewLoopAction.DeferTask,
                ReviewLoopAction.MakeSmaller,
                ReviewLoopAction.Archive,
            ),
            plan.more,
        )
    }

    @Test
    fun captureResetLoopUsesConfirmationPrimaryAndKeepsSecondaryActionsInMore() {
        val standard = reviewLoopActionPlan(
            ReviewLoop(id = 2L, type = ReviewLoopType.Capture, title = "Confirm source", updatedAt = 1L),
        )
        val brainDump = reviewLoopActionPlan(
            ReviewLoop(
                id = 3L,
                type = ReviewLoopType.Capture,
                title = "Resume source",
                updatedAt = 1L,
                hasPendingBrainDump = true,
            ),
        )

        assertEquals(ReviewLoopAction.ConfirmCapture, standard.primary)
        assertEquals(ReviewLoopAction.ResumeBrainDump, brainDump.primary)
        assertEquals(
            listOf(
                ReviewLoopAction.MakeSmaller,
                ReviewLoopAction.DismissCapture,
                ReviewLoopAction.Archive,
            ),
            standard.more,
        )
    }
}
