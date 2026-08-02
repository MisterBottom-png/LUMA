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
import com.orbit.app.ui.screens.item.ItemScheduleActions
import java.time.LocalDate
import java.time.ZoneId
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

    @Test
    fun carryForwardTaskActionsReuseDateOnlySchedulingAndCompletion() = runBlocking {
        val captures = FakeCaptureRepository()
        val tasks = FakeTaskRepository()
        val reminders = FakeReminderRepository()
        val zoneId = ZoneId.of("Europe/Tallinn")
        val today = LocalDate.of(2026, 7, 15)
        val actions = actions(captures, tasks, reminders, now = 500L, zoneId = zoneId, today = today)
        val taskId = tasks.insert(TaskEntity(title = "Carry this", dueAt = 100L))
        val item = ReviewItem(taskId, ReviewItemType.Task, "Carry this", 100L)

        actions.carryForwardTomorrow(item)
        assertEquals(today.plusDays(1).toEpochDay(), tasks.getById(taskId)?.scheduledDateEpochDay)
        assertNull(tasks.getById(taskId)?.dueAt)

        actions.keepUnscheduled(item)
        assertNull(tasks.getById(taskId)?.scheduledDateEpochDay)
        assertNull(tasks.getById(taskId)?.dueAt)

        actions.completeCarryForward(item)
        assertEquals(TaskStatus.Done, tasks.getById(taskId)?.status)
        assertEquals(500L, tasks.getById(taskId)?.completedAt)
    }

    @Test
    fun carryForwardReminderPreservesLocalTimeOffsetAndUsesRepositoryUpdate() = runBlocking {
        val captures = FakeCaptureRepository()
        val tasks = FakeTaskRepository()
        val reminders = FakeReminderRepository()
        val zoneId = ZoneId.of("Europe/Tallinn")
        val original = LocalDate.of(2026, 7, 14).atTime(9, 30).atZone(zoneId).toInstant().toEpochMilli()
        val reminderId = reminders.insert(
            ReminderEntity(
                title = "Carry reminder",
                dueAt = original,
                notificationOffsetMinutes = 45,
            ),
        )
        val actions = actions(
            captures,
            tasks,
            reminders,
            now = 600L,
            zoneId = zoneId,
            today = LocalDate.of(2026, 7, 15),
        )
        val item = ReviewItem(reminderId, ReviewItemType.Reminder, "Carry reminder", original)
        val selectedDate = LocalDate.of(2026, 7, 20)

        actions.carryForwardToDate(item, selectedDate.toEpochDay())

        val rescheduled = reminders.getById(reminderId)!!
        assertEquals(
            selectedDate.atTime(9, 30).atZone(zoneId).toInstant().toEpochMilli(),
            rescheduled.dueAt,
        )
        assertEquals(45L, rescheduled.notificationOffsetMinutes)
        assertEquals(1, reminders.updateCount)

        actions.completeCarryForward(item)
        assertEquals(600L, reminders.getById(reminderId)?.completedAt)
        assertEquals(2, reminders.updateCount)
    }

    private fun actions(
        captures: FakeCaptureRepository,
        tasks: FakeTaskRepository,
        reminders: FakeReminderRepository = FakeReminderRepository(),
        now: Long = 10L,
        zoneId: ZoneId = ZoneId.of("UTC"),
        today: LocalDate = LocalDate.of(2026, 7, 15),
    ): ReviewActions {
        val confirm = ConfirmCaptureActionUseCase(
            captureRepository = captures,
            noteRepository = UnusedNoteRepository(),
            taskRepository = tasks,
            reminderRepository = reminders,
        )
        return ReviewActions(
            captureRepository = captures,
            taskRepository = tasks,
            reminderRepository = reminders,
            confirmCaptureAction = confirm,
            scheduleActions = ItemScheduleActions(UnusedNoteRepository(), tasks) { now },
            now = { now },
            zoneId = zoneId,
            today = { today },
        )
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

private class FakeReminderRepository : ReminderRepository {
    val entities = linkedMapOf<Long, ReminderEntity>()
    var updateCount = 0
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderEntity>> = flowOf(entities.values.toList())
    override suspend fun getById(id: Long): ReminderEntity? = entities[id]
    override suspend fun insert(entity: ReminderEntity): Long =
        (entity.id.takeIf { it != 0L } ?: nextId++).also { entities[it] = entity.copy(id = it) }

    override suspend fun update(entity: ReminderEntity) {
        updateCount += 1
        entities[entity.id] = entity
    }

    override suspend fun delete(entity: ReminderEntity) {
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
