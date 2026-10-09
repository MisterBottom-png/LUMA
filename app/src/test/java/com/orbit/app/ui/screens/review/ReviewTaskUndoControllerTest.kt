package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewTaskUndoControllerTest {
    @Test
    fun undoRestoresTheExactTaskSnapshotAfterAReviewMutation() = runBlocking {
        val original = TaskEntity(
            id = 4L,
            title = "Keep this task",
            notes = "Preserve the details",
            dueAt = 3_000L,
            updatedAt = 100L,
        )
        val updated = original.copy(
            status = TaskStatus.Done,
            updatedAt = 200L,
            completedAt = 200L,
        )
        val tasks = UndoTaskRepository(updated)
        val controller = ReviewTaskUndoController(tasks)

        val token = controller.record(
            ReviewTaskMutation(ReviewTaskMutationAction.Completed, original, updated),
        )

        assertEquals(ReviewTaskUndoOutcome.Restored, controller.undo(token.operationId))
        assertEquals(original, tasks.getById(original.id))
    }

    @Test
    fun undoExpiresInsteadOfOverwritingALaterTaskChange() = runBlocking {
        val original = TaskEntity(id = 5L, title = "Keep current state", updatedAt = 100L)
        val updated = original.copy(status = TaskStatus.Someday, updatedAt = 200L)
        val laterUpdate = updated.copy(status = TaskStatus.Done, updatedAt = 300L, completedAt = 300L)
        val tasks = UndoTaskRepository(laterUpdate)
        val controller = ReviewTaskUndoController(tasks)

        val token = controller.record(
            ReviewTaskMutation(ReviewTaskMutationAction.Deferred, original, updated),
        )

        assertEquals(ReviewTaskUndoOutcome.Stale, controller.undo(token.operationId))
        assertEquals(laterUpdate, tasks.getById(original.id))
    }

    @Test
    fun recordingANewerMutationExpiresThePreviousUndo() = runBlocking {
        val original = TaskEntity(id = 6L, title = "One active undo", updatedAt = 100L)
        val firstUpdated = original.copy(status = TaskStatus.Done, updatedAt = 200L, completedAt = 200L)
        val secondUpdated = firstUpdated.copy(status = TaskStatus.Archived, updatedAt = 300L)
        val tasks = UndoTaskRepository(secondUpdated)
        val controller = ReviewTaskUndoController(tasks)

        val first = controller.record(
            ReviewTaskMutation(ReviewTaskMutationAction.Completed, original, firstUpdated),
        )
        val second = controller.record(
            ReviewTaskMutation(ReviewTaskMutationAction.Archived, firstUpdated, secondUpdated),
        )

        assertEquals(ReviewTaskUndoOutcome.Stale, controller.undo(first.operationId))
        assertEquals(ReviewTaskUndoOutcome.Restored, controller.undo(second.operationId))
        assertEquals(firstUpdated, tasks.getById(original.id))
    }
}

private class UndoTaskRepository(initial: TaskEntity) : TaskRepository {
    private var item = initial

    override fun observeAll() = flowOf(listOf(item))

    override suspend fun getById(id: Long): TaskEntity? = item.takeIf { it.id == id }

    override suspend fun insert(entity: TaskEntity): Long = error("unused")

    override suspend fun update(entity: TaskEntity) {
        item = entity
    }

    override suspend fun delete(entity: TaskEntity) = error("unused")

    override suspend fun deleteById(id: Long) = error("unused")
}
