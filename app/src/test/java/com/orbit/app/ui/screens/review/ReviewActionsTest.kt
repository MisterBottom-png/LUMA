package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.EntityRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewActionsTest {
    @Test
    fun concurrentCaptureConfirmationCreatesExactlyOneSomedayTask() = runBlocking {
        val captures = FakeCaptureRepository()
        val tasks = FakeTaskRepository()
        val captureId = captures.insert(CaptureEntity(rawText = "Plan a quiet weekend"))
        val actions = actions(captures, tasks)
        val loop = ReviewLoop(captureId, ReviewLoopType.Capture, "Plan a quiet weekend", 1L)

        val outcomes = coroutineScope {
            List(2) {
                async(Dispatchers.Default) {
                    runCatching { actions.confirmCapture(loop) }.isSuccess
                }
            }.map { it.await() }
        }

        assertEquals(2, outcomes.count { it })
        assertEquals(1, tasks.entities.size)
        assertEquals(TaskStatus.Someday, tasks.entities.values.single().status)
        assertEquals(CaptureStatus.Processed, captures.getById(captureId)?.status)
        assertEquals(tasks.entities.keys.single(), captures.getById(captureId)?.linkedItemId)
    }

    @Test
    fun dismissingCaptureCreatesNoFinalItem() = runBlocking {
        val captures = FakeCaptureRepository()
        val tasks = FakeTaskRepository()
        val captureId = captures.insert(CaptureEntity(rawText = "No action needed"))

        actions(captures, tasks).dismissCapture(
            ReviewLoop(captureId, ReviewLoopType.Capture, "No action needed", 1L),
        )

        assertTrue(tasks.entities.isEmpty())
        assertEquals(CaptureStatus.Processed, captures.getById(captureId)?.status)
        assertNull(captures.getById(captureId)?.linkedItemId)
    }

    @Test
    fun archivingCaptureCreatesNoFinalItem() = runBlocking {
        val captures = FakeCaptureRepository()
        val tasks = FakeTaskRepository()
        val captureId = captures.insert(CaptureEntity(rawText = "Store the source"))

        actions(captures, tasks).archive(
            ReviewLoop(captureId, ReviewLoopType.Capture, "Store the source", 1L),
        )

        assertTrue(tasks.entities.isEmpty())
        assertEquals(CaptureStatus.Archived, captures.getById(captureId)?.status)
    }

    @Test
    fun taskActionsHaveDistinctStateTransitions() = runBlocking {
        val captures = FakeCaptureRepository()
        val tasks = FakeTaskRepository()
        val actions = actions(captures, tasks, now = 50L)

        val keepId = tasks.insert(TaskEntity(title = "Keep", updatedAt = 1L))
        actions.keepTaskActive(ReviewLoop(keepId, ReviewLoopType.Task, "Keep", 1L))
        assertEquals(TaskStatus.Open, tasks.getById(keepId)?.status)
        assertEquals(50L, tasks.getById(keepId)?.updatedAt)

        val deferId = tasks.insert(TaskEntity(title = "Defer"))
        actions.deferTask(ReviewLoop(deferId, ReviewLoopType.Task, "Defer", 1L))
        assertEquals(TaskStatus.Someday, tasks.getById(deferId)?.status)
        assertNull(tasks.getById(deferId)?.completedAt)

        val completeId = tasks.insert(TaskEntity(title = "Complete"))
        actions.completeTask(ReviewLoop(completeId, ReviewLoopType.Task, "Complete", 1L))
        assertEquals(TaskStatus.Done, tasks.getById(completeId)?.status)
        assertEquals(50L, tasks.getById(completeId)?.completedAt)

        val archiveId = tasks.insert(TaskEntity(title = "Archive"))
        actions.archive(ReviewLoop(archiveId, ReviewLoopType.Task, "Archive", 1L))
        assertEquals(TaskStatus.Archived, tasks.getById(archiveId)?.status)
    }

    private fun actions(
        captures: FakeCaptureRepository,
        tasks: FakeTaskRepository,
        now: Long = 10L,
    ): ReviewActions {
        val confirm = ConfirmCaptureActionUseCase(
            captureRepository = captures,
            noteRepository = UnusedNoteRepository(),
            taskRepository = tasks,
            reminderRepository = UnusedReminderRepository(),
        )
        return ReviewActions(captures, tasks, confirm) { now }
    }
}

private class FakeCaptureRepository : CaptureRepository {
    val entities = linkedMapOf<Long, CaptureEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<CaptureEntity>> = flowOf(entities.values.toList())
    override suspend fun getById(id: Long): CaptureEntity? = entities[id]
    override suspend fun insert(entity: CaptureEntity): Long =
        (entity.id.takeIf { it != 0L } ?: nextId++).also { entities[it] = entity.copy(id = it) }

    override suspend fun update(entity: CaptureEntity) {
        entities[entity.id] = entity
    }

    override suspend fun delete(entity: CaptureEntity) {
        entities.remove(entity.id)
    }

    override suspend fun deleteById(id: Long) {
        entities.remove(id)
    }
}

private class FakeTaskRepository : TaskRepository {
    val entities = linkedMapOf<Long, TaskEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<TaskEntity>> = flowOf(entities.values.toList())
    override suspend fun getById(id: Long): TaskEntity? = entities[id]
    override suspend fun insert(entity: TaskEntity): Long =
        (entity.id.takeIf { it != 0L } ?: nextId++).also { entities[it] = entity.copy(id = it) }

    override suspend fun update(entity: TaskEntity) {
        entities[entity.id] = entity
    }

    override suspend fun delete(entity: TaskEntity) {
        entities.remove(entity.id)
    }

    override suspend fun deleteById(id: Long) {
        entities.remove(id)
    }
}

private abstract class UnusedReviewRepository<T> : EntityRepository<T> {
    override fun observeAll(): Flow<List<T>> = flowOf(emptyList())
    override suspend fun getById(id: Long): T? = null
    override suspend fun insert(entity: T): Long = error("unused")
    override suspend fun update(entity: T) = error("unused")
    override suspend fun delete(entity: T) = error("unused")
    override suspend fun deleteById(id: Long) = error("unused")
}

private class UnusedNoteRepository : UnusedReviewRepository<NoteEntity>(), NoteRepository
private class UnusedReminderRepository :
    UnusedReviewRepository<ReminderEntity>(), ReminderRepository
