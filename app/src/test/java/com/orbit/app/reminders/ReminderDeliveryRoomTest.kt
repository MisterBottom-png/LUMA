package com.orbit.app.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDeliveryRoomTest {
    private lateinit var database: OrbitDatabase
    private var now = 1_800_000_000_000L
    private val minute = 60_000L
    private val day = 24L * 60L * minute
    private lateinit var scheduler: PolicyRecordingScheduler
    private lateinit var repository: RoomReminderRepository

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        scheduler = PolicyRecordingScheduler { now }
        repository = RoomReminderRepository(database.reminderDao(), scheduler)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun alarmAndWorkerCannotBothDeliverTheSameReminder() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + 10 * minute))
        val time = now + 10 * minute
        val dao = database.reminderDao()

        assertEquals(1, dao.claimDelivery(id, time))
        assertEquals(0, dao.claimDelivery(id, time))
        assertEquals(time, dao.getById(id)?.deliveredNotificationAt)
    }

    @Test
    fun staleDeliveryForAnEditedTimeIsRejected() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + 10 * minute))
        val original = now + 10 * minute
        repository.update(requireNotNull(repository.getById(id)).copy(dueAt = now + 60 * minute))

        assertEquals(0, database.reminderDao().claimDelivery(id, original))
        assertEquals(1, database.reminderDao().claimDelivery(id, now + 60 * minute))
    }

    @Test
    fun completedReminderCannotBeDelivered() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + 10 * minute))
        repository.update(requireNotNull(repository.getById(id)).copy(completedAt = now))
        assertEquals(0, database.reminderDao().claimDelivery(id, now + 10 * minute))
    }

    @Test
    fun releasedClaimCanBeRetriedLater() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + 10 * minute))
        val dao = database.reminderDao()
        assertEquals(1, dao.claimDelivery(id, now + 10 * minute))
        dao.releaseDelivery(id, now + 10 * minute)
        assertEquals(1, dao.claimDelivery(id, now + 10 * minute))
    }

    @Test
    fun movingOrRenamingAReminderDoesNotReArmIt() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + day))
        val armed = scheduler.scheduled.size
        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(title = "Renamed", spaceId = null, notes = "Details"))

        assertEquals(armed, scheduler.scheduled.size)
        assertTrue(scheduler.cancelled.isEmpty())
        assertNotNull(repository.getById(id)?.notificationWorkId)
    }

    @Test
    fun editingAPastReminderNeverSchedulesItAgain() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + 5 * minute))
        now += day
        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(notificationOffsetMinutes = 1))
        repository.update(requireNotNull(repository.getById(id)).copy(title = "Edited later"))

        assertEquals(listOf(id to (stored.dueAt)), scheduler.scheduled.take(1))
        assertEquals(1, scheduler.scheduled.size)
        assertNull(repository.getById(id)?.notificationWorkId)
    }

    @Test
    fun schedulingBookkeepingDoesNotChangeTheEditedTimestamp() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + day).copy(updatedAt = 123L))
        val stored = requireNotNull(repository.getById(id))
        assertEquals(123L, stored.updatedAt)
        database.reminderDao().updateNotificationWorkId(id, "other")
        assertEquals(123L, repository.getById(id)?.updatedAt)
    }

    @Test
    fun snoozeMovesOnlyTheNotificationTimeAndKeepsTheReminderTime() = runBlocking {
        val due = now - minute
        val id = repository.insert(reminder(dueAt = due).copy(updatedAt = 55L))
        database.reminderDao().claimDelivery(id, due)

        handleReminderAction(repository, ReminderActionReceiver.ACTION_SNOOZE, id, now)

        val snoozed = requireNotNull(repository.getById(id))
        assertEquals(due, snoozed.dueAt)
        assertEquals(55L, snoozed.updatedAt)
        assertEquals(now + ReminderActionReceiver.SnoozeMillis, snoozed.snoozedUntil)
        assertEquals(id to now + ReminderActionReceiver.SnoozeMillis, scheduler.scheduled.last())
        assertEquals(1, database.reminderDao().claimDelivery(id, now + ReminderActionReceiver.SnoozeMillis))
    }

    @Test
    fun editingTheTimeClearsAnEarlierSnooze() = runBlocking {
        val id = repository.insert(reminder(dueAt = now + 5 * minute))
        handleReminderAction(repository, ReminderActionReceiver.ACTION_SNOOZE, id, now)
        val snoozed = requireNotNull(repository.getById(id))
        repository.update(snoozed.copy(dueAt = now + 3 * day))

        val edited = requireNotNull(repository.getById(id))
        assertNull(edited.snoozedUntil)
        assertEquals(id to now + 3 * day, scheduler.scheduled.last())
    }

    @Test
    fun doneActionCompletesAndCancels() = runBlocking {
        val id = repository.insert(reminder(dueAt = now - minute))
        handleReminderAction(repository, ReminderActionReceiver.ACTION_DONE, id, now)

        val done = requireNotNull(repository.getById(id))
        assertEquals(now, done.completedAt)
        assertTrue(id in scheduler.cancelled)
    }

    @Test
    fun restartReconciliationSchedulesFutureSurfacesMissedAndQuietsOldOnes() = runBlocking {
        val dao = database.reminderDao()
        val future = dao.insert(reminder(dueAt = now + day).copy(notificationWorkId = "w"))
        val recent = dao.insert(reminder(dueAt = now - 5 * minute).copy(notificationWorkId = "w"))
        val missed = dao.insert(reminder(dueAt = now - 2 * day).copy(notificationWorkId = "w"))
        val ancient = dao.insert(reminder(dueAt = now - 30 * day).copy(notificationWorkId = "w"))
        val neverArmed = dao.insert(reminder(dueAt = now - 2 * day))
        val delivered = mutableListOf<Triple<Long, Long, Boolean>>()

        val result = reconcileReminders(
            dao = dao,
            scheduler = scheduler,
            now = now,
            deliver = { id, time, isMissed ->
                delivered += Triple(id, time, isMissed)
                dao.claimDelivery(id, time)
                ReminderDeliveryOutcome.Shown
            },
            deliverSummary = { error("summary not expected for few misses") },
        )

        assertEquals(listOf(future to now + day), scheduler.scheduled)
        assertEquals(
            listOf(Triple(recent, now - 5 * minute, false), Triple(missed, now - 2 * day, true)),
            delivered,
        )
        assertEquals(now - 30 * day, dao.getById(ancient)?.deliveredNotificationAt)
        assertEquals(now - 2 * day, dao.getById(neverArmed)?.deliveredNotificationAt)
        assertEquals(0, result.failures)

        // A second reconciliation (e.g. the next app start) rings nothing again.
        delivered.clear()
        reconcileReminders(dao, scheduler, now, { id, time, m -> delivered += Triple(id, time, m); ReminderDeliveryOutcome.Shown }, { ReminderDeliveryOutcome.Shown })
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun manyMissedRemindersBecomeOneCalmSummary() = runBlocking {
        val dao = database.reminderDao()
        val ids = (1..5).map { dao.insert(reminder(dueAt = now - it * day / 2).copy(notificationWorkId = "w")) }
        var summary: List<Pair<Long, Long>> = emptyList()

        reconcileReminders(
            dao = dao,
            scheduler = scheduler,
            now = now,
            deliver = { _, _, _ -> error("individual notifications not expected") },
            deliverSummary = { summary = it; ReminderDeliveryOutcome.Shown },
        )

        assertEquals(ids.toSet(), summary.map { it.first }.toSet())
    }

    private fun reminder(dueAt: Long) = ReminderEntity(
        title = "Water the plants",
        dueAt = dueAt,
    )
}
