package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.repository.TaskRepository

data class ReviewTaskUndoToken(
    val operationId: Long,
    val action: ReviewTaskMutationAction,
)

internal enum class ReviewTaskUndoOutcome {
    Restored,
    Stale,
}

private data class PendingReviewTaskUndo(
    val token: ReviewTaskUndoToken,
    val original: TaskEntity,
    val expectedCurrent: TaskEntity,
)

internal class ReviewTaskUndoController(
    private val taskRepository: TaskRepository,
) {
    private var nextOperationId = 1L
    private var pending: PendingReviewTaskUndo? = null

    fun record(mutation: ReviewTaskMutation): ReviewTaskUndoToken {
        val token = ReviewTaskUndoToken(
            operationId = nextOperationId++,
            action = mutation.action,
        )
        pending = PendingReviewTaskUndo(
            token = token,
            original = mutation.original,
            expectedCurrent = mutation.updated,
        )
        return token
    }

    suspend fun undo(operationId: Long): ReviewTaskUndoOutcome {
        val currentPending = pending ?: return ReviewTaskUndoOutcome.Stale
        if (currentPending.token.operationId != operationId) return ReviewTaskUndoOutcome.Stale

        val current = taskRepository.getById(currentPending.original.id)
        if (current != currentPending.expectedCurrent) {
            pending = null
            return ReviewTaskUndoOutcome.Stale
        }

        taskRepository.update(currentPending.original)
        pending = null
        return ReviewTaskUndoOutcome.Restored
    }

    fun expire(operationId: Long) {
        if (pending?.token?.operationId == operationId) pending = null
    }
}
