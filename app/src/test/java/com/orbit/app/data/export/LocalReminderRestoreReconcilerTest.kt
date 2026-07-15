package com.orbit.app.data.export

import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReminderRestoreReconcilerTest {
    @Test
    fun cancelsOldWorkAndSchedulesOnlyActiveRestoredReminders() = runBlocking {
        val scheduler = RecordingRestoreScheduler()
        val dao = RestoreReminderDao()
        val active = ReminderEntity(id = 2, title = "Active", dueAt = 5_000_000)
        val completed = ReminderEntity(
            id = 3,
            title = "Completed",
            dueAt = 6_000_000,
            completedAt = 4_000_000,
            notificationWorkId = "old-work",
        )

        val result = LocalReminderRestoreReconciler(scheduler, dao).reconcile(
            previousReminderIds = setOf(1),
            restoredReminders = listOf(active, completed),
        )

        assertTrue(result)
        assertEquals(setOf(1L, 2L, 3L), scheduler.cancelled.toSet())
        assertEquals(listOf(2L), scheduler.scheduled.map { it.id })
        assertEquals("restored-work-2", dao.entities.getValue(2).notificationWorkId)
        assertNull(dao.entities.getValue(3).notificationWorkId)
    }

    @Test
    fun schedulingFailureLeavesReminderReadableWithoutWorkId() = runBlocking {
        val scheduler = RecordingRestoreScheduler(failSchedule = true)
        val dao = RestoreReminderDao()
        val reminder = ReminderEntity(id = 4, title = "Local reminder", dueAt = 7_000_000)

        val result = LocalReminderRestoreReconciler(scheduler, dao).reconcile(
            previousReminderIds = emptySet(),
            restoredReminders = listOf(reminder),
        )

        assertFalse(result)
        assertEquals(reminder.copy(notificationWorkId = null), dao.entities.getValue(4))
    }
}

private class RecordingRestoreScheduler(
    private val failSchedule: Boolean = false,
) : ReminderScheduler {
    val cancelled = mutableListOf<Long>()
    val scheduled = mutableListOf<ReminderEntity>()

    override fun schedule(reminder: ReminderEntity): String? {
        if (failSchedule) throw IllegalStateException("schedule failed")
        scheduled += reminder
        return "restored-work-${reminder.id}"
    }

    override fun cancel(reminderId: Long) {
        cancelled += reminderId
    }
}

private class RestoreReminderDao : ReminderDao {
    val entities = linkedMapOf<Long, ReminderEntity>()

    override fun observeAll(): Flow<List<ReminderEntity>> = flowOf(entities.values.toList())
    override fun observeCalendarRange(startMillis: Long, endMillis: Long): Flow<List<ReminderEntity>> =
        flowOf(entities.values.filter { it.dueAt >= startMillis && it.dueAt < endMillis })
    override suspend fun getById(id: Long): ReminderEntity? = entities[id]

    override suspend fun insert(entity: ReminderEntity): Long {
        entities[entity.id] = entity
        return entity.id
    }

    override suspend fun insertAll(entities: List<ReminderEntity>) {
        entities.forEach { insert(it) }
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

    override suspend fun deleteAll() {
        entities.clear()
    }
}
