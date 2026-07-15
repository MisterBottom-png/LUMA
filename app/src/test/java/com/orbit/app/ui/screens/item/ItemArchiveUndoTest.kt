package com.orbit.app.ui.screens.item

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.EntityRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemArchiveUndoTest {
    @Test
    fun noteArchiveAndUndoRestoresExactItemWithoutDuplicate() = runBlocking {
        val original = NoteEntity(
            id = 11,
            title = "Planning note",
            body = "Keep every field",
            spaceId = 4,
            createdAt = 100,
            updatedAt = 200,
        )
        val notes = FakeNoteRepository(listOf(original))
        val actions = actions(notes = notes)

        val operationId = actions.archive(ItemDetailType.Note, original.id).operationId()
        assertTrue(notes.item(original.id)!!.archived)

        assertEquals(UndoOutcome.Restored, actions.undo(operationId))
        assertEquals(original, notes.item(original.id))
        assertEquals(1, notes.size)
    }

    @Test
    fun taskArchiveAndUndoRestoresCompletionDatesAndExactIdentifier() = runBlocking {
        val original = TaskEntity(
            id = 12,
            title = "Completed task",
            notes = "Preserve task content",
            spaceId = 5,
            status = TaskStatus.Done,
            dueAt = 5_000,
            reminderAt = 4_000,
            createdAt = 300,
            updatedAt = 400,
            completedAt = 450,
        )
        val tasks = FakeTaskRepository(listOf(original))
        val actions = actions(tasks = tasks)

        val operationId = actions.archive(ItemDetailType.Task, original.id).operationId()
        assertEquals(TaskStatus.Archived, tasks.item(original.id)!!.status)

        assertEquals(UndoOutcome.Restored, actions.undo(operationId))
        assertEquals(original, tasks.item(original.id))
        assertEquals(1, tasks.size)
    }

    @Test
    fun reminderArchiveIsUnsupportedAndChangesNothing() = runBlocking {
        val actions = actions()

        assertEquals(ArchiveOutcome.Unsupported, actions.archive(ItemDetailType.Reminder, 13))
    }

    @Test
    fun staleOrRepeatedUndoCannotRestoreAnotherItemOrCreateDuplicate() = runBlocking {
        val original = NoteEntity(id = 14, title = "Single note", body = "One record")
        val notes = FakeNoteRepository(listOf(original))
        val actions = actions(notes = notes)
        val operationId = actions.archive(ItemDetailType.Note, original.id).operationId()

        assertEquals(UndoOutcome.Stale, actions.undo(operationId + 1))
        assertTrue(notes.item(original.id)!!.archived)
        assertEquals(UndoOutcome.Restored, actions.undo(operationId))
        assertEquals(UndoOutcome.Stale, actions.undo(operationId))
        assertEquals(original, notes.item(original.id))
        assertEquals(1, notes.size)
    }

    @Test
    fun repeatedArchiveAndUndoCallsFailSafely() = runBlocking {
        val original = TaskEntity(id = 15, title = "Rapid action", status = TaskStatus.Open)
        val tasks = FakeTaskRepository(listOf(original))
        val actions = actions(tasks = tasks)
        val operationId = actions.archive(ItemDetailType.Task, original.id).operationId()

        assertEquals(ArchiveOutcome.Ignored, actions.archive(ItemDetailType.Task, original.id))
        assertEquals(UndoOutcome.Restored, actions.undo(operationId))
        assertEquals(UndoOutcome.Stale, actions.undo(operationId))
        assertEquals(original, tasks.item(original.id))
        assertEquals(2, tasks.updateCalls)
    }

    @Test
    fun expiredUndoMatchesNavigationContractAndLeavesArchivePersistent() = runBlocking {
        val original = NoteEntity(id = 16, title = "Persistent archive", body = "No delayed undo")
        val notes = FakeNoteRepository(listOf(original))
        val actions = actions(notes = notes)
        val operationId = actions.archive(ItemDetailType.Note, original.id).operationId()

        actions.expire(operationId)

        assertEquals(UndoOutcome.Stale, actions.undo(operationId))
        assertTrue(notes.item(original.id)!!.archived)
        val recreatedActions = actions(notes = notes)
        assertEquals(UndoOutcome.Stale, recreatedActions.undo(operationId))
        assertTrue(notes.item(original.id)!!.archived)
        assertEquals(1, notes.size)
    }

    @Test
    fun repositoryFailureLeavesArchivedDataConsistentAndExpiresUndo() = runBlocking {
        val original = NoteEntity(id = 17, title = "Failure case", body = "Keep archive")
        val notes = FakeNoteRepository(listOf(original))
        val actions = actions(notes = notes)
        val operationId = actions.archive(ItemDetailType.Note, original.id).operationId()
        notes.failNextUpdate = true

        val failure = runCatching { actions.undo(operationId) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(notes.item(original.id)!!.archived)
        assertEquals(UndoOutcome.Stale, actions.undo(operationId))
        assertEquals(1, notes.size)
    }

    @Test
    fun archiveFailureDoesNotCreateUndoStateOrMutateItem() = runBlocking {
        val original = TaskEntity(id = 18, title = "Archive failure")
        val tasks = FakeTaskRepository(listOf(original)).apply { failNextUpdate = true }
        val actions = actions(tasks = tasks)

        val failure = runCatching { actions.archive(ItemDetailType.Task, original.id) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(original, tasks.item(original.id))
        assertEquals(UndoOutcome.Stale, actions.undo(1))
        assertEquals(1, tasks.size)
    }

    private fun actions(
        notes: FakeNoteRepository = FakeNoteRepository(),
        tasks: FakeTaskRepository = FakeTaskRepository(),
        captures: FakeCaptureRepository = FakeCaptureRepository(),
    ) = ItemArchiveUndo(
        noteRepository = notes,
        taskRepository = tasks,
        captureRepository = captures,
        currentTimeMillis = { 9_000L },
    )
}

private fun ArchiveOutcome.operationId(): Long {
    assertTrue(this is ArchiveOutcome.Archived)
    return (this as ArchiveOutcome.Archived).operationId
}

private abstract class FakeRepository<T>(
    initial: List<T>,
    private val idOf: (T) -> Long,
) : EntityRepository<T> {
    private val items = initial.associateBy(idOf).toMutableMap()
    var failNextUpdate = false
    var updateCalls = 0

    val size: Int get() = items.size

    fun item(id: Long): T? = items[id]

    override fun observeAll() = flowOf(items.values.toList())

    override suspend fun getById(id: Long): T? = items[id]

    override suspend fun insert(entity: T): Long {
        val id = idOf(entity)
        items[id] = entity
        return id
    }

    override suspend fun update(entity: T) {
        if (failNextUpdate) {
            failNextUpdate = false
            throw IllegalStateException("Test repository update failed")
        }
        updateCalls += 1
        items[idOf(entity)] = entity
    }

    override suspend fun delete(entity: T) {
        items.remove(idOf(entity))
    }

    override suspend fun deleteById(id: Long) {
        items.remove(id)
    }
}

private class FakeNoteRepository(initial: List<NoteEntity> = emptyList()) :
    FakeRepository<NoteEntity>(initial, NoteEntity::id), NoteRepository

private class FakeTaskRepository(initial: List<TaskEntity> = emptyList()) :
    FakeRepository<TaskEntity>(initial, TaskEntity::id), TaskRepository

private class FakeCaptureRepository(initial: List<CaptureEntity> = emptyList()) :
    FakeRepository<CaptureEntity>(initial, CaptureEntity::id), CaptureRepository
