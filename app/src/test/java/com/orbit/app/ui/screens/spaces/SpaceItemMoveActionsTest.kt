package com.orbit.app.ui.screens.spaces

import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.repository.EntityRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceItemMoveActionsTest {
    @Test
    fun moveAndUndoRestoresEachFinalizedItemToItsPreviousSpace() = runBlocking {
        val notes = FakeNoteRepository(listOf(NoteEntity(id = 1, title = "Note", body = "", spaceId = 10)))
        val tasks = FakeTaskRepository(listOf(TaskEntity(id = 2, title = "Task", spaceId = 10)))
        val reminders = FakeReminderRepository(listOf(ReminderEntity(id = 3, title = "Reminder", dueAt = 100, spaceId = 10)))
        val actions = SpaceItemMoveActions(notes, tasks, reminders) { 500L }

        val outcomes = listOf(
            actions.move(SpaceItemReference(SpaceItemType.Note, 1), targetSpaceId = null),
            actions.move(SpaceItemReference(SpaceItemType.Task, 2), targetSpaceId = 20),
            actions.move(SpaceItemReference(SpaceItemType.Reminder, 3), targetSpaceId = null),
        )

        assertEquals(null, notes.item(1)?.spaceId)
        assertEquals(20L, tasks.item(2)?.spaceId)
        assertEquals(null, reminders.item(3)?.spaceId)
        outcomes.forEach { outcome ->
            assertTrue(outcome is SpaceItemMoveOutcome.Moved)
            assertTrue(actions.restore((outcome as SpaceItemMoveOutcome.Moved).undo))
        }
        assertEquals(10L, notes.item(1)?.spaceId)
        assertEquals(10L, tasks.item(2)?.spaceId)
        assertEquals(10L, reminders.item(3)?.spaceId)
    }

    @Test
    fun unchangedOrMissingMoveDoesNotCreateUndoState() = runBlocking {
        val notes = FakeNoteRepository(listOf(NoteEntity(id = 4, title = "Note", body = "", spaceId = 10)))
        val actions = SpaceItemMoveActions(notes, FakeTaskRepository(), FakeReminderRepository()) { 500L }

        assertEquals(
            SpaceItemMoveOutcome.Unchanged,
            actions.move(SpaceItemReference(SpaceItemType.Note, 4), targetSpaceId = 10),
        )
        assertEquals(
            SpaceItemMoveOutcome.Missing,
            actions.move(SpaceItemReference(SpaceItemType.Note, 99), targetSpaceId = null),
        )
        assertEquals(10L, notes.item(4)?.spaceId)
    }

    @Test
    fun failedMoveKeepsTheOriginalAssignmentAndReportsFailure() = runBlocking {
        val notes = FakeNoteRepository(listOf(NoteEntity(id = 5, title = "Note", body = "", spaceId = 10)))
            .apply { failNextUpdate = true }
        val actions = SpaceItemMoveActions(notes, FakeTaskRepository(), FakeReminderRepository()) { 500L }

        assertEquals(
            SpaceItemMoveOutcome.Failed,
            actions.move(SpaceItemReference(SpaceItemType.Note, 5), targetSpaceId = null),
        )
        assertEquals(10L, notes.item(5)?.spaceId)
    }
}

private abstract class FakeSpaceItemRepository<T>(
    initial: List<T>,
    private val idOf: (T) -> Long,
) : EntityRepository<T> {
    private val items = initial.associateBy(idOf).toMutableMap()
    var failNextUpdate = false

    fun item(id: Long): T? = items[id]

    override fun observeAll() = flowOf(items.values.toList())
    override suspend fun getById(id: Long): T? = items[id]
    override suspend fun insert(entity: T): Long = idOf(entity).also { items[it] = entity }
    override suspend fun update(entity: T) {
        if (failNextUpdate) {
            failNextUpdate = false
            throw IllegalStateException("Test repository update failed")
        }
        items[idOf(entity)] = entity
    }
    override suspend fun delete(entity: T) { items.remove(idOf(entity)) }
    override suspend fun deleteById(id: Long) { items.remove(id) }
}

private class FakeNoteRepository(initial: List<NoteEntity> = emptyList()) :
    FakeSpaceItemRepository<NoteEntity>(initial, NoteEntity::id), NoteRepository

private class FakeTaskRepository(initial: List<TaskEntity> = emptyList()) :
    FakeSpaceItemRepository<TaskEntity>(initial, TaskEntity::id), TaskRepository

private class FakeReminderRepository(initial: List<ReminderEntity> = emptyList()) :
    FakeSpaceItemRepository<ReminderEntity>(initial, ReminderEntity::id), ReminderRepository
