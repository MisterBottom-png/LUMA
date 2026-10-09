package com.orbit.app.ui.screens.review

import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType

internal enum class ReviewLoopAction {
    KeepActive,
    ConfirmCapture,
    ResumeBrainDump,
    CompleteTask,
    DeferTask,
    DismissCapture,
    MakeSmaller,
    Archive,
}

internal data class ReviewLoopActionPlan(
    val primary: ReviewLoopAction,
    val more: List<ReviewLoopAction>,
)

internal fun reviewLoopActionPlan(loop: ReviewLoop): ReviewLoopActionPlan = when (loop.type) {
    ReviewLoopType.Task -> ReviewLoopActionPlan(
        primary = ReviewLoopAction.KeepActive,
        more = listOf(
            ReviewLoopAction.CompleteTask,
            ReviewLoopAction.DeferTask,
            ReviewLoopAction.MakeSmaller,
            ReviewLoopAction.Archive,
        ),
    )

    ReviewLoopType.Capture -> ReviewLoopActionPlan(
        primary = if (loop.hasPendingBrainDump) {
            ReviewLoopAction.ResumeBrainDump
        } else {
            ReviewLoopAction.ConfirmCapture
        },
        more = listOf(
            ReviewLoopAction.MakeSmaller,
            ReviewLoopAction.DismissCapture,
            ReviewLoopAction.Archive,
        ),
    )
}
