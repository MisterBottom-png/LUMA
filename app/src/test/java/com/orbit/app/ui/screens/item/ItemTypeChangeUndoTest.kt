package com.orbit.app.ui.screens.item

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.SharedPreferencesArchivedTaskStatusMemory
import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.TaskEntity
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
        assertEquals(TypeConversionOutcome.Converted, conversion.convert(ItemDetailType.Reminder, 5, ItemDetailType.Task))
        assertNull(database.reminderDao().getById(5))

        assertTrue(conversion.undo(snapshot, ItemDetailType.Task, 5))
        assertNull(database.taskDao().getById(5))
        val restored = requireNotNull(database.reminderDao().getById(5))
        assertEquals(original.copy(notificationWorkId = restored.notificationWorkId), restored)
        assertEquals(listOf(label), database.labelDao().getLabelIdsForReminder(5))
        assertTrue(scheduler.scheduled.any { it.first == 5L })
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
