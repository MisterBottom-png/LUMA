package com.orbit.app.reminders

import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.repository.RoomReminderRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderSchedulingTest {
    @Test
    fun localSixteenHundredTargetIsPersistedAndScheduledAsTheSameInstant() = runBlocking {
        val zone = ZoneId.of("Europe/Tallinn")
        val target = LocalDate.of(2026, 7, 14)
            .atTime(LocalTime.of(16, 0))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)

        val id = repository.insert(
            ReminderEntity(
                title = "Reminder test",
                dueAt = target,
                notificationOffsetMinutes = 0L,
            ),
        )

        val stored = requireNotNull(repository.getById(id))
        assertEquals(target, stored.dueAt)
        assertEquals(target, scheduler.scheduled.single().dueAt)
        assertEquals(target, scheduler.scheduledNotificationTimes.single())
        assertEquals(
            LocalTime.of(16, 0),
            Instant.ofEpochMilli(stored.dueAt).atZone(zone).toLocalTime(),
        )
    }

    @Test
    fun notificationTimeSubtractsOffsetFromTargetTime() {
        assertEquals(
            3_300_000L,
            reminderNotificationTimeMillis(
                targetTimeMillis = 3_600_000L,
                offsetMinutes = 5L,
            ),
        )
    }

    @Test
    fun offsetCanMoveNotificationIntoPreviousDay() {
        val day = 24L * 60L * 60L * 1_000L
        assertEquals(
            day - 60L * 60L * 1_000L,
            reminderNotificationTimeMillis(
                targetTimeMillis = day + 2L * 60L * 60L * 1_000L,
                offsetMinutes = 3L * 60L,
            ),
        )
    }

    @Test
    fun invalidTargetOrOffsetDoesNotProduceNotificationTime() {
        assertNull(reminderNotificationTimeMillis(targetTimeMillis = 0L, offsetMinutes = 0L))
        assertNull(reminderNotificationTimeMillis(targetTimeMillis = 10_000L, offsetMinutes = -1L))
        assertNull(
            reminderNotificationTimeMillis(
                targetTimeMillis = Long.MAX_VALUE,
                offsetMinutes = Long.MAX_VALUE,
            ),
        )
        assertNull(reminderNotificationTimeMillis(targetTimeMillis = 10_000L, offsetMinutes = 1L))
    }

    @Test
    fun futureNotificationDelayUsesCalculatedNotificationTime() {
        val notificationTime = requireNotNull(
            reminderNotificationTimeMillis(
                targetTimeMillis = 3_600_000L,
                offsetMinutes = 5L,
            ),
        )

        assertEquals(
            300_000L,
            reminderDelayMillis(notificationTime = notificationTime, now = 3_000_000L),
        )
    }

    @Test
    fun overdueNotificationDelayIsImmediate() {
        assertEquals(0L, reminderDelayMillis(notificationTime = 9_000L, now = 10_000L))
    }

    @Test
    fun staleScheduledTimeIsRejectedAfterOffsetChanges() {
        val reminder = reminder()
        val originalNotificationTime = requireNotNull(reminder.notificationTimeMillis())
        val changed = reminder.copy(notificationOffsetMinutes = 15L)

        assertTrue(reminder.matchesScheduledNotificationTime(originalNotificationTime))
        assertEquals(false, changed.matchesScheduledNotificationTime(originalNotificationTime))
        assertEquals(
            false,
            reminder.copy(completedAt = 1L)
                .matchesScheduledNotificationTime(originalNotificationTime),
        )
    }

    @Test
    fun insertingReminderPersistsOffsetAndSchedulesCalculatedTime() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)

        val id = repository.insert(reminder())

        assertEquals(5L, repository.getById(id)?.notificationOffsetMinutes)
        assertEquals(listOf(3_300_000L), scheduler.scheduledNotificationTimes)
        assertEquals("work-1", repository.getById(id)?.notificationWorkId)
    }

    @Test
    fun editingTargetTimePreservesOffsetAndReplacesScheduledWork() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(dueAt = 7_200_000L))

        val updated = requireNotNull(repository.getById(id))
        assertEquals(7_200_000L, updated.dueAt)
        assertEquals(5L, updated.notificationOffsetMinutes)
        assertEquals(listOf(3_300_000L, 6_900_000L), scheduler.scheduledNotificationTimes)
        assertEquals(listOf(id), scheduler.cancelledIds)
        assertEquals("work-2", updated.notificationWorkId)
    }

    @Test
    fun editingOffsetPreservesTargetTimeAndReplacesScheduledWork() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(notificationOffsetMinutes = 15L))

        val updated = requireNotNull(repository.getById(id))
        assertEquals(3_600_000L, updated.dueAt)
        assertEquals(15L, updated.notificationOffsetMinutes)
        assertEquals(listOf(3_300_000L, 2_700_000L), scheduler.scheduledNotificationTimes)
        assertEquals(listOf(id), scheduler.cancelledIds)
    }

    @Test
    fun completingReminderCancelsScheduledWork() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(completedAt = 20_000L))

        assertEquals(listOf(id), scheduler.cancelledIds)
        assertNull(repository.getById(id)?.notificationWorkId)
    }

    @Test
    fun disablingNotificationCancelsScheduledWork() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(notificationEnabled = false))

        assertEquals(listOf(id), scheduler.cancelledIds)
        assertNull(repository.getById(id)?.notificationWorkId)
    }

    @Test
    fun reEnablingNotificationPreservesTargetAndOffsetAndSchedulesNewWork() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(notificationEnabled = false))
        val disabled = requireNotNull(repository.getById(id))
        repository.update(disabled.copy(notificationEnabled = true))

        val reEnabled = requireNotNull(repository.getById(id))
        assertEquals(3_600_000L, reEnabled.dueAt)
        assertEquals(5L, reEnabled.notificationOffsetMinutes)
        assertTrue(reEnabled.notificationEnabled)
        assertEquals(listOf(3_300_000L, 3_300_000L), scheduler.scheduledNotificationTimes)
        assertEquals(listOf(id, id), scheduler.cancelledIds)
        assertEquals("work-2", reEnabled.notificationWorkId)
    }

    @Test
    fun reopeningCompletedReminderSchedulesExactlyOneReplacement() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())
        repository.update(requireNotNull(repository.getById(id)).copy(completedAt = 20_000L))

        repository.update(requireNotNull(repository.getById(id)).copy(completedAt = null))

        val reopened = requireNotNull(repository.getById(id))
        assertNull(reopened.completedAt)
        assertEquals("work-2", reopened.notificationWorkId)
        assertEquals(2, scheduler.scheduled.size)
        assertEquals(listOf(id, id), scheduler.cancelledIds)
    }

    @Test
    fun repeatedTargetEditsCancelObsoleteWorkAndCreateOneReplacementPerEdit() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        repeat(3) { edit ->
            val current = requireNotNull(repository.getById(id))
            repository.update(current.copy(dueAt = current.dueAt + (edit + 1) * 60_000L))
        }

        assertEquals(4, scheduler.scheduled.size)
        assertEquals(listOf(id, id, id), scheduler.cancelledIds)
        assertEquals("work-4", repository.getById(id)?.notificationWorkId)
    }

    @Test
    fun invalidOffsetCancelsExistingWorkAndKeepsReminderReadable() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        repository.update(stored.copy(notificationOffsetMinutes = -1L))

        assertEquals(listOf(id), scheduler.cancelledIds)
        assertNull(repository.getById(id)?.notificationWorkId)
        assertEquals("Local reminder", repository.getById(id)?.title)
        assertEquals(3_600_000L, repository.getById(id)?.dueAt)
    }

    @Test
    fun deletingReminderCancelsScheduledWork() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val repository = RoomReminderRepository(dao, scheduler)
        val id = repository.insert(reminder())

        repository.deleteById(id)

        assertEquals(listOf(id), scheduler.cancelledIds)
        assertNull(repository.getById(id))
    }

    @Test
    fun schedulerFailureDoesNotLoseLocalReminder() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler(failOnSchedule = true)
        val repository = RoomReminderRepository(dao, scheduler)

        val id = repository.insert(reminder())

        val stored = requireNotNull(repository.getById(id))
        assertEquals("Local reminder", stored.title)
        assertEquals(5L, stored.notificationOffsetMinutes)
        assertNull(stored.notificationWorkId)
    }

    @Test
    fun offsetPersistsAcrossRepositoryRecreation() = runBlocking {
        val dao = FakeReminderDao()
        val scheduler = FakeReminderScheduler()
        val firstRepository = RoomReminderRepository(dao, scheduler)
        val id = firstRepository.insert(reminder())

        val recreatedRepository = RoomReminderRepository(dao, FakeReminderScheduler())

        assertEquals(5L, recreatedRepository.getById(id)?.notificationOffsetMinutes)
        assertEquals(3_600_000L, recreatedRepository.getById(id)?.dueAt)
    }

    private fun reminder() = ReminderEntity(
        title = "Local reminder",
        dueAt = 3_600_000L,
        notificationOffsetMinutes = 5L,
        linkedCaptureId = 4L,
    )
}

private class FakeReminderScheduler(
    private val failOnSchedule: Boolean = false,
) : ReminderScheduler {
    val scheduled = mutableListOf<ReminderEntity>()
    val cancelledIds = mutableListOf<Long>()
    val scheduledNotificationTimes: List<Long>
        get() = scheduled.mapNotNull { it.notificationTimeMillis() }

    override fun schedule(reminder: ReminderEntity): String? {
        if (failOnSchedule) error("schedule failed")
        scheduled += reminder
        return "work-${scheduled.size}"
    }

    override fun cancel(reminderId: Long) {
        cancelledIds += reminderId
    }
}

private class FakeReminderDao : ReminderDao {
    private val reminders = linkedMapOf<Long, ReminderEntity>()
    private val observed = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<ReminderEntity>> = observed
    override fun observeCalendarRange(startMillis: Long, endMillis: Long): Flow<List<ReminderEntity>> =
        MutableStateFlow(
            reminders.values.filter { it.dueAt >= startMillis && it.dueAt < endMillis },
        )
    override suspend fun getById(id: Long): ReminderEntity? = reminders[id]

    override suspend fun insert(entity: ReminderEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        reminders[id] = entity.copy(id = id)
        publish()
        return id
    }

    override suspend fun insertAll(entities: List<ReminderEntity>) {
        entities.forEach { insert(it) }
    }

    override suspend fun update(entity: ReminderEntity) {
        reminders[entity.id] = entity
        publish()
    }

    override suspend fun delete(entity: ReminderEntity) {
        reminders.remove(entity.id)
        publish()
    }

    override suspend fun deleteById(id: Long) {
        reminders.remove(id)
        publish()
    }

    override suspend fun deleteAll() {
        reminders.clear()
        publish()
    }

    private fun publish() {
        observed.value = reminders.values.sortedBy { it.dueAt }
    }
}
