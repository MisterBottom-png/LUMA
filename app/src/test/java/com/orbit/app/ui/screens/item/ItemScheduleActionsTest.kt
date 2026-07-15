package com.orbit.app.ui.screens.item

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.EntityRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemScheduleActionsTest {
    @Test
    fun noteSupportsDateOnlyTimedRemovalAndSingleUseUndo() = runBlocking {
        val original = NoteEntity(id = 1, title = "Plan", body = "Keep", spaceId = 7)
        val notes = ScheduleNoteRepository(listOf(original))
        val actions = actions(notes = notes)

        actions.apply(ItemDetailType.Note, 1, ItemSchedule.DateOnly(20_000))
        assertEquals(20_000L, notes.item(1)?.scheduledDateEpochDay)
        assertNull(notes.item(1)?.scheduledAt)

        actions.apply(ItemDetailType.Note, 1, ItemSchedule.Timed(5_000_000))
        assertNull(notes.item(1)?.scheduledDateEpochDay)
        assertEquals(5_000_000L, notes.item(1)?.scheduledAt)

        val removal = actions.apply(ItemDetailType.Note, 1, ItemSchedule.Unscheduled).appliedId()
        assertNull(notes.item(1)?.scheduledDateEpochDay)
        assertNull(notes.item(1)?.scheduledAt)
        assertEquals(ScheduleUndoOutcome.Restored, actions.undo(removal))
        assertEquals(5_000_000L, notes.item(1)?.scheduledAt)
        assertEquals(ScheduleUndoOutcome.Stale, actions.undo(removal))
        assertEquals(1, notes.size)
    }

    @Test
    fun taskConversionPreservesUnrelatedMetadataAndOriginalIdentifier() = runBlocking {
        val original = TaskEntity(
            id = 2,
            title = "Timed task",
            notes = "Details",
            spaceId = 8,
            status = TaskStatus.WaitingFor,
            dueAt = 7_000_000,
            reminderAt = 6_000_000,
            completedAt = 5_000_000,
            mondayItemId = "external-reference",
        )
        val tasks = ScheduleTaskRepository(listOf(original))
        val actions = actions(tasks = tasks)

        val operation = actions.apply(
            ItemDetailType.Task,
            2,
            ItemSchedule.DateOnly(21_000),
        ).appliedId()

        val changed = requireNotNull(tasks.item(2))
        assertEquals(21_000L, changed.scheduledDateEpochDay)
        assertNull(changed.dueAt)
        assertEquals(original.copy(dueAt = null, scheduledDateEpochDay = 21_000, updatedAt = 9_000), changed)

        assertEquals(ScheduleUndoOutcome.Restored, actions.undo(operation))
        val restored = requireNotNull(tasks.item(2))
        assertEquals(7_000_000L, restored.dueAt)
        assertNull(restored.scheduledDateEpochDay)
        assertEquals(original.title, restored.title)
        assertEquals(original.status, restored.status)
        assertEquals(original.reminderAt, restored.reminderAt)
        assertEquals(original.mondayItemId, restored.mondayItemId)
        assertEquals(1, tasks.size)
    }

    @Test
    fun undoRestoresOnlyScheduleAndKeepsLaterMetadataChanges() = runBlocking {
        val notes = ScheduleNoteRepository(
            listOf(NoteEntity(id = 3, title = "Original", body = "Body", scheduledDateEpochDay = 40)),
        )
        val actions = actions(notes = notes)
        val operation = actions.apply(
            ItemDetailType.Note,
            3,
            ItemSchedule.Timed(8_000_000),
        ).appliedId()
        notes.update(requireNotNull(notes.item(3)).copy(title = "Edited after scheduling"))

        assertEquals(ScheduleUndoOutcome.Restored, actions.undo(operation))
        assertEquals("Edited after scheduling", notes.item(3)?.title)
        assertEquals(40L, notes.item(3)?.scheduledDateEpochDay)
        assertNull(notes.item(3)?.scheduledAt)
    }

    @Test
    fun repeatedEditMakesOlderUndoStaleAndDoesNotDuplicate() = runBlocking {
        val tasks = ScheduleTaskRepository(listOf(TaskEntity(id = 4, title = "One task")))
        val actions = actions(tasks = tasks)
        val first = actions.apply(ItemDetailType.Task, 4, ItemSchedule.DateOnly(50)).appliedId()
        val second = actions.apply(ItemDetailType.Task, 4, ItemSchedule.DateOnly(51)).appliedId()

        assertEquals(ScheduleUndoOutcome.Stale, actions.undo(first))
        assertEquals(ScheduleUndoOutcome.Restored, actions.undo(second))
        assertEquals(50L, tasks.item(4)?.scheduledDateEpochDay)
        assertEquals(1, tasks.size)
    }

    @Test
    fun unchangedUnsupportedMissingAndInvalidRequestsFailSafely() = runBlocking {
        val notes = ScheduleNoteRepository(
            listOf(NoteEntity(id = 5, title = "Date", body = "", scheduledDateEpochDay = 60)),
        )
        val actions = actions(notes = notes)

        assertEquals(
            ScheduleOutcome.Ignored,
            actions.apply(ItemDetailType.Note, 5, ItemSchedule.DateOnly(60)),
        )
        assertEquals(
            ScheduleOutcome.Unsupported,
            actions.apply(ItemDetailType.Reminder, 5, ItemSchedule.DateOnly(60)),
        )
        assertEquals(
            ScheduleOutcome.Missing,
            actions.apply(ItemDetailType.Task, 99, ItemSchedule.Unscheduled),
        )
        assertTrue(
            runCatching {
                actions.apply(ItemDetailType.Note, 5, ItemSchedule.Timed(0))
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertEquals(1, notes.size)
    }

    @Test
    fun repositoryFailureLeavesCurrentScheduleConsistentAndCreatesNoUndo() = runBlocking {
        val tasks = ScheduleTaskRepository(listOf(TaskEntity(id = 6, title = "Safe task"))).apply {
            failNextUpdate = true
        }
        val actions = actions(tasks = tasks)

        assertTrue(
            runCatching {
                actions.apply(ItemDetailType.Task, 6, ItemSchedule.DateOnly(70))
            }.exceptionOrNull() is IllegalStateException,
        )
        assertNull(tasks.item(6)?.scheduledDateEpochDay)
        assertEquals(ScheduleUndoOutcome.Stale, actions.undo(1))
        assertEquals(1, tasks.size)
    }

    private fun actions(
        notes: ScheduleNoteRepository = ScheduleNoteRepository(),
        tasks: ScheduleTaskRepository = ScheduleTaskRepository(),
    ) = ItemScheduleActions(notes, tasks, currentTimeMillis = { 9_000L })
}

private fun ScheduleOutcome.appliedId(): Long {
    assertTrue(this is ScheduleOutcome.Applied)
    return (this as ScheduleOutcome.Applied).operationId
}

private abstract class ScheduleRepository<T>(
    initial: List<T>,
    private val idOf: (T) -> Long,
) : EntityRepository<T> {
    private val items = initial.associateBy(idOf).toMutableMap()
    var failNextUpdate = false
    val size: Int get() = items.size

    fun item(id: Long): T? = items[id]
    override fun observeAll() = flowOf(items.values.toList())
    override suspend fun getById(id: Long): T? = items[id]
    override suspend fun insert(entity: T): Long = idOf(entity).also { items[it] = entity }
    override suspend fun update(entity: T) {
        if (failNextUpdate) {
            failNextUpdate = false
            error("Test update failed")
        }
        items[idOf(entity)] = entity
    }
    override suspend fun delete(entity: T) {
        items.remove(idOf(entity))
    }
    override suspend fun deleteById(id: Long) {
        items.remove(id)
    }
}

private class ScheduleNoteRepository(initial: List<NoteEntity> = emptyList()) :
    ScheduleRepository<NoteEntity>(initial, NoteEntity::id), NoteRepository

private class ScheduleTaskRepository(initial: List<TaskEntity> = emptyList()) :
    ScheduleRepository<TaskEntity>(initial, TaskEntity::id), TaskRepository
