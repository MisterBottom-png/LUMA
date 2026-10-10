package com.orbit.app.ui.screens.item

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.reminders.ReminderScheduler
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
class ItemTypeConversionRoomTest {
    private lateinit var database: OrbitDatabase
    private lateinit var scheduler: ConversionScheduler
    private lateinit var conversion: ItemTypeConversion

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = ConversionScheduler()
        conversion = ItemTypeConversion(database, scheduler, now = { 900L })
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun taskToReminderPreservesIdentityContentAndSchedulesOneReminder() = runBlocking {
        database.taskDao().insert(
            TaskEntity(id = 41, title = "Calm task", notes = "Keep this", spaceId = null, dueAt = 5_000, createdAt = 100),
        )

        assertEquals(
            TypeConversionOutcome.Converted,
            conversion.convert(ItemDetailType.Task, 41, ItemDetailType.Reminder, 5_000),
        )

        assertNull(database.taskDao().getById(41))
        val reminder = requireNotNull(database.reminderDao().getById(41))
        assertEquals(41, reminder.id)
        assertEquals("Calm task", reminder.title)
        assertEquals("Keep this", reminder.notes)
        assertEquals(100, reminder.createdAt)
        assertEquals(listOf(41L), scheduler.scheduledIds)
    }

    @Test
    fun missingReminderTimeAndIdentityConflictLeaveOriginalTaskUntouched() = runBlocking {
        val task = TaskEntity(id = 42, title = "Original task")
        database.taskDao().insert(task)

        assertEquals(
            TypeConversionOutcome.Unsupported,
            conversion.convert(ItemDetailType.Task, 42, ItemDetailType.Reminder),
        )
        database.reminderDao().insert(ReminderEntity(id = 42, title = "Existing reminder", dueAt = 8_000))
        assertEquals(
            TypeConversionOutcome.Conflict,
            conversion.convert(ItemDetailType.Task, 42, ItemDetailType.Reminder, 7_000),
        )

        assertEquals(task, database.taskDao().getById(42))
        assertTrue(scheduler.scheduledIds.isEmpty())
    }

    @Test
    fun reminderToTaskPreservesScheduleAndCancelsObsoleteWork() = runBlocking {
        database.reminderDao().insert(
            ReminderEntity(id = 43, title = "Reminder", notes = "Notes", dueAt = 12_000, createdAt = 200),
        )

        assertEquals(
            TypeConversionOutcome.Converted,
            conversion.convert(ItemDetailType.Reminder, 43, ItemDetailType.Task),
        )

        assertNull(database.reminderDao().getById(43))
        val task = requireNotNull(database.taskDao().getById(43))
        assertEquals(43, task.id)
        assertEquals(12_000L, task.dueAt)
        assertEquals(TaskStatus.Open, task.status)
        assertEquals(listOf(43L), scheduler.cancelledIds)
    }

    @Test
    fun reminderToNotePreservesIdentityAndContentAndCancelsWork() = runBlocking {
        database.reminderDao().insert(
            ReminderEntity(id = 44, title = "Reminder", notes = "Body", dueAt = 13_000, createdAt = 300),
        )

        assertEquals(
            TypeConversionOutcome.Converted,
            conversion.convert(ItemDetailType.Reminder, 44, ItemDetailType.Note),
        )

        val note: NoteEntity = requireNotNull(database.noteDao().getById(44))
        assertEquals("Reminder", note.title)
        assertEquals("Body", note.body)
        assertEquals(300, note.createdAt)
        assertEquals(listOf(44L), scheduler.cancelledIds)
    }
}

private class ConversionScheduler : ReminderScheduler {
    val scheduledIds = mutableListOf<Long>()
    val cancelledIds = mutableListOf<Long>()

    override fun schedule(reminder: ReminderEntity): String {
        scheduledIds += reminder.id
        return "conversion-work-${reminder.id}"
    }

    override fun cancel(reminderId: Long) {
        cancelledIds += reminderId
    }
}
