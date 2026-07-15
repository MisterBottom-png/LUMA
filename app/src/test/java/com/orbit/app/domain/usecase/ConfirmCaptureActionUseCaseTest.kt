package com.orbit.app.domain.usecase

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.EntityRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmCaptureActionUseCaseTest {
    @Test
    fun saveNotePersistsConfirmedTitleAndSurvivesRepositoryRecreation() = runBlocking {
        val captureStore = CaptureStore()
        val noteStore = NoteStore()
        val captureRepository = FakeCaptureRepository(captureStore)
        val noteRepository = FakeNoteRepository(noteStore)
        val captureId = captureRepository.insert(CaptureEntity(rawText = "Original raw capture"))
        val useCase = useCase(captureRepository, noteRepository)

        val noteId = useCase.saveNote(
            captureId = captureId,
            spaceId = 7L,
            title = "Confirmed final title",
            scheduledDateEpochDay = 20_000L,
        )

        val recreatedCaptureRepository = FakeCaptureRepository(captureStore)
        val recreatedNoteRepository = FakeNoteRepository(noteStore)
        val storedCapture = recreatedCaptureRepository.getById(captureId)
        val storedNote = recreatedNoteRepository.getById(noteId)

        assertNotNull(storedCapture)
        assertNotNull(storedNote)
        assertEquals("Original raw capture", storedCapture?.rawText)
        assertEquals(CaptureStatus.Processed, storedCapture?.status)
        assertEquals(noteId, storedCapture?.linkedItemId)
        assertEquals("Confirmed final title", storedNote?.title)
        assertEquals("Original raw capture", storedNote?.body)
        assertEquals(7L, storedNote?.spaceId)
        assertEquals(20_000L, storedNote?.scheduledDateEpochDay)
        assertEquals(null, storedNote?.scheduledAt)
    }

    @Test
    fun calendarContextFinalizesOneDateOnlyTaskOnOriginalCapture() = runBlocking {
        val captureStore = CaptureStore()
        val taskStore = TaskStore()
        val captureRepository = FakeCaptureRepository(captureStore)
        val taskRepository = FakeTaskRepository(taskStore)
        val captureId = captureRepository.insert(CaptureEntity(rawText = "Plan the day"))
        val useCase = ConfirmCaptureActionUseCase(
            captureRepository = captureRepository,
            noteRepository = FakeNoteRepository(NoteStore()),
            taskRepository = taskRepository,
            reminderRepository = UnusedReminderRepository(),
        )

        val taskId = useCase.createTask(
            captureId = captureId,
            spaceId = null,
            title = "Day task",
            dueAt = null,
            scheduledDateEpochDay = 20_001L,
        )

        assertEquals(1, taskStore.entities.size)
        assertEquals(taskId, captureRepository.getById(captureId)?.linkedItemId)
        assertEquals(CaptureStatus.Processed, captureRepository.getById(captureId)?.status)
        assertEquals(20_001L, taskRepository.getById(taskId)?.scheduledDateEpochDay)
        assertEquals(null, taskRepository.getById(taskId)?.dueAt)
    }

    @Test
    fun concurrentFinalizationCreatesExactlyOneNote() = runBlocking {
        val captureStore = CaptureStore(getDelayMillis = 20L)
        val noteStore = NoteStore()
        val captureRepository = FakeCaptureRepository(captureStore)
        val noteRepository = FakeNoteRepository(noteStore)
        val captureId = captureRepository.insert(CaptureEntity(rawText = "One raw capture"))
        val useCase = useCase(captureRepository, noteRepository)

        val results = coroutineScope {
            listOf("First title", "Second title").map { title ->
                async(Dispatchers.Default) {
                    try {
                        useCase.saveNote(captureId, null, title)
                        true
                    } catch (_: IllegalStateException) {
                        false
                    }
                }
            }.map { it.await() }
        }

        assertEquals(1, results.count { it })
        assertEquals(1, noteStore.entities.size)
        assertEquals(CaptureStatus.Processed, captureRepository.getById(captureId)?.status)
    }

    @Test
    fun captureUpdateFailureRollsBackNoteAndKeepsRawCapture() = runBlocking {
        val captureStore = CaptureStore()
        val noteStore = NoteStore()
        val captureRepository = FakeCaptureRepository(captureStore)
        val noteRepository = FakeNoteRepository(noteStore)
        val captureId = captureRepository.insert(CaptureEntity(rawText = "Raw capture stays safe"))
        captureStore.failOnUpdate = true

        val succeeded = try {
            useCase(captureRepository, noteRepository).saveNote(
                captureId = captureId,
                spaceId = null,
                title = "Final title",
            )
            true
        } catch (_: IllegalStateException) {
            false
        }

        assertFalse(succeeded)
        assertTrue(noteStore.entities.isEmpty())
        assertEquals("Raw capture stays safe", captureRepository.getById(captureId)?.rawText)
        assertEquals(CaptureStatus.Inbox, captureRepository.getById(captureId)?.status)
    }

    @Test
    fun noteInsertFailureKeepsRawCaptureInInbox() = runBlocking {
        val captureStore = CaptureStore()
        val noteStore = NoteStore(failOnInsert = true)
        val captureRepository = FakeCaptureRepository(captureStore)
        val noteRepository = FakeNoteRepository(noteStore)
        val captureId = captureRepository.insert(CaptureEntity(rawText = "Raw capture remains"))

        val succeeded = try {
            useCase(captureRepository, noteRepository).saveNote(
                captureId = captureId,
                spaceId = null,
                title = "Final title",
            )
            true
        } catch (_: IllegalStateException) {
            false
        }

        assertFalse(succeeded)
        assertTrue(noteStore.entities.isEmpty())
        assertEquals("Raw capture remains", captureRepository.getById(captureId)?.rawText)
        assertEquals(CaptureStatus.Inbox, captureRepository.getById(captureId)?.status)
    }

    @Test
    fun confirmedReminderPersistsTheAcceptedCanonicalTimestampExactlyOnce() = runBlocking {
        val captureStore = CaptureStore()
        val captureRepository = FakeCaptureRepository(captureStore)
        val reminderRepository = RecordingReminderRepository()
        val captureId = captureRepository.insert(CaptureEntity(rawText = "Set reminder for 1600 today"))
        val acceptedTimestamp = 1_784_034_000_000L
        val useCase = ConfirmCaptureActionUseCase(
            captureRepository = captureRepository,
            noteRepository = FakeNoteRepository(NoteStore()),
            taskRepository = UnusedTaskRepository(),
            reminderRepository = reminderRepository,
        )

        val reminderId = useCase.createReminder(
            captureId = captureId,
            spaceId = null,
            title = "Reminder test",
            dueAt = acceptedTimestamp,
        )

        assertEquals(1, reminderRepository.entities.size)
        assertEquals(acceptedTimestamp, reminderRepository.getById(reminderId)?.dueAt)
        assertEquals(reminderId, captureRepository.getById(captureId)?.linkedItemId)
    }

    private fun useCase(
        captureRepository: CaptureRepository,
        noteRepository: NoteRepository,
    ) = ConfirmCaptureActionUseCase(
        captureRepository = captureRepository,
        noteRepository = noteRepository,
        taskRepository = UnusedTaskRepository(),
        reminderRepository = UnusedReminderRepository(),
    )
}

private class CaptureStore(
    val entities: LinkedHashMap<Long, CaptureEntity> = linkedMapOf(),
    var nextId: Long = 1L,
    var failOnUpdate: Boolean = false,
    val getDelayMillis: Long = 0L,
)

private class FakeCaptureRepository(
    private val store: CaptureStore,
) : CaptureRepository {
    override fun observeAll(): Flow<List<CaptureEntity>> = flowOf(store.entities.values.toList())

    override suspend fun getById(id: Long): CaptureEntity? {
        if (store.getDelayMillis > 0) delay(store.getDelayMillis)
        return store.entities[id]
    }

    override suspend fun insert(entity: CaptureEntity): Long {
        val id = entity.id.takeIf { it != 0L } ?: store.nextId++
        store.entities[id] = entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: CaptureEntity) {
        if (store.failOnUpdate) throw IllegalStateException("capture update failed")
        store.entities[entity.id] = entity
    }

    override suspend fun delete(entity: CaptureEntity) {
        store.entities.remove(entity.id)
    }

    override suspend fun deleteById(id: Long) {
        store.entities.remove(id)
    }
}

private class NoteStore(
    val entities: LinkedHashMap<Long, NoteEntity> = linkedMapOf(),
    var nextId: Long = 1L,
    val failOnInsert: Boolean = false,
)

private class FakeNoteRepository(
    private val store: NoteStore,
) : NoteRepository {
    override fun observeAll(): Flow<List<NoteEntity>> = flowOf(store.entities.values.toList())
    override suspend fun getById(id: Long): NoteEntity? = store.entities[id]

    override suspend fun insert(entity: NoteEntity): Long {
        if (store.failOnInsert) throw IllegalStateException("note insert failed")
        val id = entity.id.takeIf { it != 0L } ?: store.nextId++
        store.entities[id] = entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: NoteEntity) {
        store.entities[entity.id] = entity
    }

    override suspend fun delete(entity: NoteEntity) {
        store.entities.remove(entity.id)
    }

    override suspend fun deleteById(id: Long) {
        store.entities.remove(id)
    }
}

private class TaskStore(
    val entities: LinkedHashMap<Long, TaskEntity> = linkedMapOf(),
    var nextId: Long = 1L,
)

private class FakeTaskRepository(
    private val store: TaskStore,
) : TaskRepository {
    override fun observeAll(): Flow<List<TaskEntity>> = flowOf(store.entities.values.toList())
    override suspend fun getById(id: Long): TaskEntity? = store.entities[id]
    override suspend fun insert(entity: TaskEntity): Long {
        val id = entity.id.takeIf { it != 0L } ?: store.nextId++
        store.entities[id] = entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: TaskEntity) {
        store.entities[entity.id] = entity
    }
    override suspend fun delete(entity: TaskEntity) {
        store.entities.remove(entity.id)
    }
    override suspend fun deleteById(id: Long) {
        store.entities.remove(id)
    }
}

private abstract class UnusedEntityRepository<T> : EntityRepository<T> {
    override fun observeAll(): Flow<List<T>> = flowOf(emptyList())
    override suspend fun getById(id: Long): T? = null
    override suspend fun insert(entity: T): Long = error("unused")
    override suspend fun update(entity: T) = error("unused")
    override suspend fun delete(entity: T) = error("unused")
    override suspend fun deleteById(id: Long) = error("unused")
}

private class UnusedTaskRepository : UnusedEntityRepository<TaskEntity>(), TaskRepository
private class UnusedReminderRepository : UnusedEntityRepository<ReminderEntity>(), ReminderRepository

private class RecordingReminderRepository : ReminderRepository {
    val entities = linkedMapOf<Long, ReminderEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderEntity>> = flowOf(entities.values.toList())
    override suspend fun getById(id: Long): ReminderEntity? = entities[id]
    override suspend fun insert(entity: ReminderEntity): Long {
        val id = entity.id.takeIf { it != 0L } ?: nextId++
        entities[id] = entity.copy(id = id)
        return id
    }
    override suspend fun update(entity: ReminderEntity) {
        entities[entity.id] = entity
    }
    override suspend fun delete(entity: ReminderEntity) {
        entities.remove(entity.id)
    }
    override suspend fun deleteById(id: Long) {
        entities.remove(id)
    }
}
