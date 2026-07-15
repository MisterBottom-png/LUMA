package com.orbit.app.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.SpaceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataRestoreRoomTest {
    private lateinit var database: OrbitDatabase
    private lateinit var store: RoomLocalDataRestoreStore

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomLocalDataRestoreStore(database)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replacementCommitsOneExactDataset() = runBlocking {
        database.spaceDao().insert(space(1, "Existing"))
        database.noteDao().insert(NoteEntity(id = 1, title = "Existing", body = "Old", spaceId = 1))
        val restored = LocalDataSnapshot(
            spaces = listOf(space(2, "Restored")),
            captures = emptyList(),
            notes = listOf(NoteEntity(id = 2, title = "Restored", body = "New", spaceId = 2)),
            tasks = emptyList(),
            reminders = emptyList(),
        )

        store.replace(restored)

        assertEquals(restored, store.read())
    }

    @Test
    fun relationshipFailureRollsBackEntireReplacement() = runBlocking {
        val existing = LocalDataSnapshot(
            spaces = listOf(space(1, "Existing")),
            captures = emptyList(),
            notes = listOf(NoteEntity(id = 1, title = "Existing", body = "Old", spaceId = 1)),
            tasks = emptyList(),
            reminders = emptyList(),
        )
        database.spaceDao().insertAll(existing.spaces)
        database.noteDao().insertAll(existing.notes)
        val invalid = existing.copy(
            spaces = listOf(space(2, "Replacement")),
            notes = listOf(NoteEntity(id = 2, title = "Invalid", body = "New", spaceId = 999)),
        )

        assertThrows(Exception::class.java) {
            runBlocking { store.replace(invalid) }
        }

        assertEquals(existing, store.read())
    }

    private fun space(id: Long, name: String) = SpaceEntity(
        id = id,
        name = name,
        icon = "home",
        colorAccent = "violet",
        sortOrder = id.toInt(),
    )
}
