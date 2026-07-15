package com.orbit.app.reminders

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.repository.RoomReminderRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderEditingRoomTest {
    private lateinit var database: OrbitDatabase
    private lateinit var scheduler: RecordingReminderScheduler
    private lateinit var repository: RoomReminderRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        scheduler = RecordingReminderScheduler()
        repository = RoomReminderRepository(database.reminderDao(), scheduler)
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun targetOffsetDeliveryAndCompletionReplaceOrCancelScheduledWork() = runBlocking {
        val id = repository.insert(reminder())
        repository.update(requireNotNull(repository.getById(id)).copy(dueAt = 7_200_000L))
        repository.update(
            requireNotNull(repository.getById(id)).copy(notificationOffsetMinutes = 15L),
        )
        repository.update(requireNotNull(repository.getById(id)).copy(notificationEnabled = false))

        val disabled = requireNotNull(repository.getById(id))
        assertEquals(7_200_000L, disabled.dueAt)
        assertEquals(15L, disabled.notificationOffsetMinutes)
        assertFalse(disabled.notificationEnabled)
        assertNull(disabled.notificationWorkId)

        repository.update(disabled.copy(notificationEnabled = true))
        val reEnabled = requireNotNull(repository.getById(id))
        assertTrue(reEnabled.notificationEnabled)
        assertEquals(7_200_000L, reEnabled.dueAt)
        assertEquals(15L, reEnabled.notificationOffsetMinutes)
        assertEquals("work-4", reEnabled.notificationWorkId)

        repository.update(reEnabled.copy(completedAt = 9_000_000L))
        val completed = requireNotNull(repository.getById(id))
        assertEquals(9_000_000L, completed.completedAt)
        assertNull(completed.notificationWorkId)
        assertEquals(listOf(id, id, id, id, id), scheduler.cancelledIds)
    }

    @Test
    fun deletingReminderCancelsWorkAndRemovesOnlyThatRow() = runBlocking {
        val deletedId = repository.insert(reminder(title = "Delete reminder"))
        val retainedId = repository.insert(reminder(title = "Retain reminder"))

        repository.deleteById(deletedId)

        assertNull(repository.getById(deletedId))
        assertEquals("Retain reminder", repository.getById(retainedId)?.title)
        assertTrue(scheduler.cancelledIds.contains(deletedId))
    }

    private fun reminder(title: String = "Edit reminder") = ReminderEntity(
        title = title,
        dueAt = 3_600_000L,
        notificationOffsetMinutes = 5L,
    )
}

private class RecordingReminderScheduler : ReminderScheduler {
    val cancelledIds = mutableListOf<Long>()
    private var scheduledCount = 0

    override fun schedule(reminder: ReminderEntity): String {
        scheduledCount += 1
        return "work-$scheduledCount"
    }

    override fun cancel(reminderId: Long) {
        cancelledIds += reminderId
    }
}
