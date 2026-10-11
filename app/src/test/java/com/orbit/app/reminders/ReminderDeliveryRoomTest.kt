package com.orbit.app.reminders

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

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
    fun tearDown() {
        database.close()
        ShadowAlarmManager.reset()
        context.deleteDatabase(RecoveryDatabaseName)
    }

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
    fun doneOnARepeatingReminderMovesItToTheNextTimeAndArmsIt() = runBlocking {
        val due = now - minute
        val id = repository.insert(reminder(dueAt = due).copy(repeatRule = ReminderRepeat.Daily.storageToken))
        database.reminderDao().claimDelivery(id, due)

        handleReminderAction(repository, ReminderActionReceiver.ACTION_DONE, id, now)

        val moved = requireNotNull(repository.getById(id))
        assertNull(moved.completedAt)
        assertTrue(moved.dueAt > now)
        assertEquals(id to moved.dueAt, scheduler.scheduled.last())
        // The next occurrence is deliverable exactly once.
        assertEquals(1, database.reminderDao().claimDelivery(id, moved.dueAt))
        assertEquals(0, database.reminderDao().claimDelivery(id, moved.dueAt))
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

    // Recovery. A database write and an Android alarm are never one transaction, and the
    // three ways the app can stop lose different things, so each has its own test.

    @Test
    fun processDeathKeepsTheAlarmsAndTheNextStartDuplicatesNothing() = runBlocking {
        useRealAlarms()
        val first = openRecoveryDatabase()
        val firstRepository = RoomReminderRepository(first.reminderDao(), WorkManagerReminderScheduler(context) { now })
        val soon = firstRepository.insert(reminder(dueAt = now + 30 * minute))
        val tomorrow = firstRepository.insert(reminder(dueAt = now + day))
        val armed = armedAlarms()
        assertEquals(listOf(soon to now + 30 * minute, tomorrow to now + day), armed)

        // The process dies: Room goes away, AlarmManager keeps the alarms.
        first.close()
        val second = openRecoveryDatabase()
        val secondScheduler = WorkManagerReminderScheduler(context) { now }
        RoomReminderRepository(second.reminderDao(), secondScheduler)
        assertEquals(armed, armedAlarms())

        // The next app start reconciles: each alarm is replaced in place, never doubled,
        // and nothing rings early.
        val result = reconcileReminders(
            dao = second.reminderDao(),
            scheduler = secondScheduler,
            now = now,
            deliver = { _, _, _ -> error("nothing is due yet") },
            deliverSummary = { error("nothing was missed") },
        )
        assertEquals(armed, armedAlarms())
        assertEquals(0, result.delivered)
        assertEquals(0, result.failures)
        second.close()
    }

    @Test
    fun rebootClearsTheAlarmsAndBootCompletedReArmsExactlyOnePerFutureReminder() = runBlocking {
        useRealAlarms()
        val alarmScheduler = WorkManagerReminderScheduler(context) { now }
        val realRepository = RoomReminderRepository(database.reminderDao(), alarmScheduler)
        val dao = database.reminderDao()
        val soon = realRepository.insert(reminder(dueAt = now + 30 * minute))
        val tomorrow = realRepository.insert(reminder(dueAt = now + day))
        val completed = realRepository.insert(reminder(dueAt = now + 2 * day))
        realRepository.update(requireNotNull(realRepository.getById(completed)).copy(completedAt = now))
        val alreadyRang = dao.insert(
            reminder(dueAt = now - 2 * minute).copy(
                notificationWorkId = "w",
                deliveredNotificationAt = now - 2 * minute,
            ),
        )
        assertEquals(2, armedAlarms().size)

        // Reboot: Android drops every alarm.
        clearAllAlarms()
        assertTrue(armedAlarms().isEmpty())
        ReminderBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(1, WorkManager.getInstance(context).getWorkInfosForUniqueWork(RescheduleWorkName).get().size)

        // What the queued reconciliation runs.
        reconcileReminders(
            dao = dao,
            scheduler = alarmScheduler,
            now = now,
            deliver = { id, _, _ -> error("reminder $id is not due") },
            deliverSummary = { error("nothing was missed") },
        )

        assertEquals(listOf(soon to now + 30 * minute, tomorrow to now + day), armedAlarms())
        assertNull(dao.getById(completed)?.notificationWorkId)
        assertEquals(now - 2 * minute, dao.getById(alreadyRang)?.deliveredNotificationAt)
    }

    @Test
    fun forceStopRecoversOnTheNextLaunchWithAtMostOneMissedNotificationPerReminder() = runBlocking {
        useRealAlarms()
        val alarmScheduler = WorkManagerReminderScheduler(context) { now }
        val realRepository = RoomReminderRepository(database.reminderDao(), alarmScheduler)
        val dao = database.reminderDao()
        val soon = realRepository.insert(reminder(dueAt = now + 30 * minute))
        val tomorrow = realRepository.insert(reminder(dueAt = now + day))
        val tomorrowAt = now + day

        // Force-stop: alarms and pending work are cancelled, and no receiver runs
        // until the user opens the app again. Meanwhile the first reminder's time passes.
        clearAllAlarms()
        WorkManager.getInstance(context).cancelAllWork().result.get()
        now += 2 * 60 * minute

        // MainActivity.onCreate queues the reconciliation; it may run more than once.
        val shown = mutableListOf<Triple<Long, Long, Boolean>>()
        val deliver: suspend (Long, Long, Boolean) -> ReminderDeliveryOutcome = { id, time, missed ->
            if (dao.claimDelivery(id, time) == 1) {
                shown += Triple(id, time, missed)
                ReminderDeliveryOutcome.Shown
            } else {
                ReminderDeliveryOutcome.AlreadyHandled
            }
        }
        repeat(2) {
            reconcileReminders(dao, alarmScheduler, now, deliver, { error("one miss is not a summary") })
        }

        assertEquals(listOf(Triple(soon, now - 90 * minute, true)), shown)
        assertEquals(listOf(tomorrow to tomorrowAt), armedAlarms())
    }

    private fun useRealAlarms() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun openRecoveryDatabase(): OrbitDatabase =
        Room.databaseBuilder(context, OrbitDatabase::class.java, RecoveryDatabaseName)
            .allowMainThreadQueries()
            .build()

    /** Armed exact alarms as (reminder id, trigger time), sorted by time. */
    private fun armedAlarms(): List<Pair<Long, Long>> =
        shadowOf(alarmManager).scheduledAlarms
            .map { alarm ->
                assertEquals(AlarmManager.RTC_WAKEUP, alarm.type)
                assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
                val intent = shadowOf(requireNotNull(alarm.operation)).savedIntent
                intent.getLongExtra(ReminderNotificationWorker.KEY_REMINDER_ID, -1L) to alarm.triggerAtMs
            }
            .sortedBy { it.second }

    private fun clearAllAlarms() {
        shadowOf(alarmManager).scheduledAlarms.toList().forEach { alarm ->
            alarm.operation?.let(alarmManager::cancel)
        }
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val alarmManager: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    private companion object {
        const val RecoveryDatabaseName = "reminder-recovery-test.db"
        const val RescheduleWorkName = "luma_reschedule_reminders"
    }

    private fun reminder(dueAt: Long) = ReminderEntity(
        title = "Water the plants",
        dueAt = dueAt,
    )
}
