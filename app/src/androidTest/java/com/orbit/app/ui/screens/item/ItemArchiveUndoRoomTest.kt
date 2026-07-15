package com.orbit.app.ui.screens.item

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItemArchiveUndoRoomTest {
    private lateinit var database: OrbitDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun noteArchiveAndUndoUsesOneExactRoomRow() = runBlocking {
        val original = NoteEntity(id = 21, title = "Room note", body = "Exact content", updatedAt = 200)
        database.noteDao().insert(original)
        val actions = actions()

        val archived = actions.archive(ItemDetailType.Note, original.id) as ArchiveOutcome.Archived
        assertTrue(database.noteDao().getById(original.id)!!.archived)

        assertEquals(UndoOutcome.Restored, actions.undo(archived.operationId))
        assertEquals(original, database.noteDao().getById(original.id))
        assertEquals(1, database.noteDao().observeAll().first().size)
    }

    @Test
    fun taskArchiveAndUndoRestoresCompletedRoomRow() = runBlocking {
        val original = TaskEntity(
            id = 22,
            title = "Room task",
            notes = "Exact task content",
            status = TaskStatus.Done,
            dueAt = 5_000,
            reminderAt = 4_000,
            updatedAt = 300,
            completedAt = 350,
        )
        database.taskDao().insert(original)
        val actions = actions()

        val archived = actions.archive(ItemDetailType.Task, original.id) as ArchiveOutcome.Archived
        assertEquals(TaskStatus.Archived, database.taskDao().getById(original.id)!!.status)

        assertEquals(UndoOutcome.Restored, actions.undo(archived.operationId))
        assertEquals(original, database.taskDao().getById(original.id))
        assertEquals(1, database.taskDao().observeAll().first().size)
    }

    private fun actions() = ItemArchiveUndo(
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        captureRepository = RoomCaptureRepository(database.captureDao()),
        currentTimeMillis = { 9_000L },
    )
}
