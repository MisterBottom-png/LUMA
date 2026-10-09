package com.orbit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabelRoomRepositoryTest {
    private lateinit var database: OrbitDatabase
    private lateinit var repository: LabelRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OrbitDatabase::class.java,
        ).build()
        repository = RoomLabelRepository(database.labelDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findOrCreateReusesWhitespaceAndCaseEquivalentLabel() = runBlocking {
        val first = repository.findOrCreate("  Errand  ")
        val second = repository.findOrCreate("errand")

        assertEquals(first.id, second.id)
        assertEquals("Errand", first.name)
        assertEquals("errand", first.normalizedName)
        assertEquals(1, repository.getAll().size)
    }

    @Test
    fun replaceItemLabelsRemovesStaleRelationsAndKeepsOtherItemTypes() = runBlocking {
        database.noteDao().insert(NoteEntity(id = 1, title = "Note", body = ""))
        database.taskDao().insert(TaskEntity(id = 2, title = "Task"))
        database.reminderDao().insert(ReminderEntity(id = 3, title = "Reminder", dueAt = 1_000))
        val errand = repository.findOrCreate("Errand")
        val phone = repository.findOrCreate("Phone")

        repository.replaceNoteLabels(1, setOf(errand.id, phone.id))
        repository.replaceTaskLabels(2, setOf(errand.id))
        repository.replaceReminderLabels(3, setOf(phone.id))
        repository.replaceNoteLabels(1, setOf(phone.id))

        assertEquals(listOf(NoteLabelCrossRef(1, phone.id)), repository.getAllNoteLabels())
        assertEquals(listOf(TaskLabelCrossRef(2, errand.id)), repository.getAllTaskLabels())
        assertEquals(listOf(ReminderLabelCrossRef(3, phone.id)), repository.getAllReminderLabels())
    }

    @Test
    fun deletingLabelCascadesRelationsWithoutDeletingFinalizedItems() = runBlocking {
        database.noteDao().insert(NoteEntity(id = 1, title = "Note", body = ""))
        database.taskDao().insert(TaskEntity(id = 2, title = "Task"))
        database.reminderDao().insert(ReminderEntity(id = 3, title = "Reminder", dueAt = 1_000))
        val label = repository.findOrCreate("Errand")
        repository.replaceNoteLabels(1, setOf(label.id))
        repository.replaceTaskLabels(2, setOf(label.id))
        repository.replaceReminderLabels(3, setOf(label.id))

        repository.delete(label)

        assertEquals(emptyList<NoteLabelCrossRef>(), repository.getAllNoteLabels())
        assertEquals(emptyList<TaskLabelCrossRef>(), repository.getAllTaskLabels())
        assertEquals(emptyList<ReminderLabelCrossRef>(), repository.getAllReminderLabels())
        assertEquals("Note", database.noteDao().getById(1)?.title)
        assertEquals("Task", database.taskDao().getById(2)?.title)
        assertEquals("Reminder", database.reminderDao().getById(3)?.title)
    }
}
