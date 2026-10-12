package com.orbit.app.ui.screens.item

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.SharedPreferencesArchivedTaskStatusMemory
import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.local.statusAfterRestore
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.reminders.ReminderRepeat
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemTypeChangeUndoTest {
    private lateinit var database: OrbitDatabase
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aTypeChangeNamesWhatItWouldRemove() {
        assertEquals(
            listOf(TypeChangeLoss.Repeat, TypeChangeLoss.Notification),
            typeChangeLosses(ItemDetailType.Reminder, ItemDetailType.Task, ReminderRepeat.Weekly, true, null),
        )
        assertEquals(
            listOf(TypeChangeLoss.Status(TaskStatus.WaitingFor)),
            typeChangeLosses(ItemDetailType.Task, ItemDetailType.Note, null, null, TaskStatus.WaitingFor),
        )
        assertTrue(typeChangeLosses(ItemDetailType.Note, ItemDetailType.Task, null, null, null).isEmpty())
        assertTrue(typeChangeLosses(ItemDetailType.Task, ItemDetailType.Note, null, null, TaskStatus.Open).isEmpty())
    }

    @Test
    fun undoBringsBackTheReminderExactlyWithRepeatAndLabels() = runBlocking {
        val scheduler = PolicyRecordingScheduler { now }
        val conversion = ItemTypeConversion(database, scheduler, now = { now })
        val label = database.labelDao().insert(LabelEntity(name = "Home", normalizedName = "home"))
        val original = ReminderEntity(
            id = 5, title = "Water plants", dueAt = now + 86_400_000L, repeatRule = "weekly@09:00",
            notificationOffsetMinutes = 15, createdAt = 1, updatedAt = 2,
        )
        database.reminderDao().insert(original)
        database.labelDao().insertReminderLabels(listOf(ReminderLabelCrossRef(5, label)))

        val snapshot = requireNotNull(conversion.snapshot(ItemDetailType.Reminder, 5))
        val taskId = (conversion.convert(ItemDetailType.Reminder, 5, ItemDetailType.Task) as TypeConversionOutcome.Converted).itemId
        assertNull(database.reminderDao().getById(5))

        assertTrue(conversion.undo(snapshot, ItemDetailType.Task, taskId))
        assertNull(database.taskDao().getById(taskId))
        val restored = requireNotNull(database.reminderDao().getById(5))
        assertEquals(original.copy(notificationWorkId = restored.notificationWorkId), restored)
        assertEquals(listOf(label), database.labelDao().getLabelIdsForReminder(5))
        assertTrue(scheduler.scheduled.any { it.first == 5L })
    }

    @Test
    fun undoKeepsAnEditMadeAfterTheTypeChange() = runBlocking {
        var clock = now
        val conversion = ItemTypeConversion(database, PolicyRecordingScheduler { clock }, now = { clock })
        val label = database.labelDao().insert(LabelEntity(name = "Home", normalizedName = "home"))
        database.noteDao().insert(NoteEntity(id = 7, title = "Paint the fence", body = "", createdAt = 1, updatedAt = 2))

        var taskId = 0L
        fun convertToTask(): TypeConversionSnapshot = runBlocking {
            val before = requireNotNull(conversion.snapshot(ItemDetailType.Note, 7))
            taskId = (conversion.convert(ItemDetailType.Note, 7, ItemDetailType.Task) as TypeConversionOutcome.Converted).itemId
            conversion.afterConversion(before, ItemDetailType.Task, taskId)
        }

        // Nothing changed since: Undo works, under the note's own id.
        assertTrue(conversion.undo(convertToTask(), ItemDetailType.Task, taskId))
        assertNotNull(database.noteDao().getById(7))

        // The title was edited after the change: Undo refuses and the edit stays.
        var snapshot = convertToTask()
        clock += 1_000
        val task = requireNotNull(database.taskDao().getById(taskId))
        database.taskDao().update(task.copy(title = "Paint the fence white", updatedAt = clock))
        assertFalse(conversion.undo(snapshot, ItemDetailType.Task, taskId))
        assertEquals("Paint the fence white", database.taskDao().getById(taskId)?.title)
        assertNull(database.noteDao().getById(7))

        // A label added after the change counts as an edit too.
        database.taskDao().deleteById(taskId)
        database.noteDao().insert(NoteEntity(id = 7, title = "Paint the fence", body = "", createdAt = 1, updatedAt = 2))
        snapshot = convertToTask()
        database.labelDao().insertTaskLabels(listOf(TaskLabelCrossRef(taskId, label)))
        assertFalse(conversion.undo(snapshot, ItemDetailType.Task, taskId))
        assertNotNull(database.taskDao().getById(taskId))
    }

    @Test
    fun restoringAnArchivedTaskReturnsItsEarlierStatus() = runBlocking {
        val memory = SharedPreferencesArchivedTaskStatusMemory(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val tasks = RoomTaskRepository(database.taskDao())
        val archive = ItemArchiveUndo(
            noteRepository = RoomNoteRepository(database.noteDao()),
            taskRepository = tasks,
            captureRepository = RoomCaptureRepository(database.captureDao()),
            archivedTaskStatus = memory,
        )
        database.taskDao().insert(TaskEntity(id = 8, title = "Reply from the plumber", status = TaskStatus.WaitingFor))
        database.taskDao().insert(TaskEntity(id = 9, title = "Filed taxes", status = TaskStatus.Done, completedAt = 5))
        database.taskDao().insert(TaskEntity(id = 10, title = "Plain task"))
        listOf(8L, 9L, 10L).forEach { archive.archive(ItemDetailType.Task, it) }

        val archived = listOf(8L, 9L, 10L).map { requireNotNull(database.taskDao().getById(it)) }
        assertTrue(archived.all { it.status == TaskStatus.Archived })
        assertEquals(
            listOf(TaskStatus.WaitingFor, TaskStatus.Done, TaskStatus.Open),
            archived.map { it.statusAfterRestore(memory) },
        )
        // Without the device memory (for example after a backup restore) a done task stays done.
        memory.forget(9)
        assertEquals(TaskStatus.Done, archived[1].statusAfterRestore(memory))
    }
}
