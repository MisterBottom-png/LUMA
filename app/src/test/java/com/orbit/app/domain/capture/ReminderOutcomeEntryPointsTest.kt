package com.orbit.app.domain.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderDeliveryOutcome
import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.reconcileReminders
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every place that turns a thought into a reminder stores what the save achieved on
 * the thought: only a reminder that can ring is `ReminderSet`; one that cannot is a
 * `NeedsYou` with the reason. Later reconciliation keeps it in step.
 */
@RunWith(AndroidJUnit4::class)
class ReminderOutcomeEntryPointsTest {
    private lateinit var database: OrbitDatabase
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private var notificationsAllowed = true
    private var schedulerWorks = true

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun eachSaveOutcomeMapsToOneStoredOutcome() {
        assertEquals(
            StoredCaptureOutcome(CaptureOutcome.ReminderSet, CaptureOutcomeReason.None),
            ReminderSaveOutcome.Saved.toCaptureOutcome(),
        )
        assertEquals(
            StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff),
            ReminderSaveOutcome.SavedNotificationsBlocked.toCaptureOutcome(),
        )
        assertEquals(
            StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotScheduled),
            ReminderSaveOutcome.SavedNotScheduled.toCaptureOutcome(),
        )
        // Every value is covered.
        assertEquals(3, ReminderSaveOutcome.entries.map { it.toCaptureOutcome() }.distinct().size)
    }

    @Test
    fun acceptingASuggestedReminderStoresWhatTheSaveAchieved() = runBlocking {
        expectations().forEach { (setup, expected) ->
            setup()
            val id = analysedThought("remind me tomorrow at 9:00 to water plants")
            val accepted = requireNotNull(resolution().acceptSuggestion(id))
            assertEquals(expected, accepted.reminderOutcome?.toCaptureOutcome())
            assertStored(id, expected)
        }
    }

    @Test
    fun theQuickReminderFromHomeStoresWhatTheSaveAchieved() = runBlocking {
        expectations().forEach { (setup, expected) ->
            setup()
            val id = analysedThought("remind me to call the bank")
            val outcome = resolution().createQuickReminder(id, "Call the bank", now + 86_400_000L)
            assertEquals(expected, outcome.toCaptureOutcome())
            assertStored(id, expected)
        }
    }

    @Test
    fun theSortSheetPathRecordsTheOutcomeItReadAfterThePermissionAnswer() = runBlocking {
        // The sort sheet creates the reminder itself, then records what the reporter read.
        val id = analysedThought("remind me to call the bank")
        val reminderId = confirm().createReminder(id, spaceId = null, title = "Call the bank", dueAt = now + 86_400_000L)
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.ReminderSet))

        resolution().recordReminderOutcome(id, reminderId, ReminderSaveOutcome.SavedNotificationsBlocked)
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff))
    }

    @Test
    fun aLateOutcomeForAnUndoneReminderIsIgnored() = runBlocking {
        val id = analysedThought("remind me to call the bank")
        val reminderId = confirm().createReminder(id, spaceId = null, title = "Call the bank", dueAt = now + 86_400_000L)
        resolution().undo(id, com.orbit.app.data.local.entity.SuggestedItemType.Reminder, reminderId)

        resolution().recordReminderOutcome(id, reminderId, ReminderSaveOutcome.SavedNotificationsBlocked)

        assertStored(id, StoredCaptureOutcome(CaptureOutcome.Saved))
    }

    @Test
    fun reArmingAfterNotificationsComeBackClearsTheQuestion() = runBlocking {
        notificationsAllowed = false
        val id = analysedThought("remind me to call the bank")
        val reminderId = requireNotNull(resolution().quickReminder(id))
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff))

        // Still off: reconciliation leaves the question in place.
        reconcile()
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff))

        notificationsAllowed = true
        reconcile()
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.ReminderSet))
        assertEquals(id, database.reminderDao().getById(reminderId)?.linkedCaptureId)
    }

    @Test
    fun aReminderThatCouldNotBeArmedIsASetReminderOnceItIsArmed() = runBlocking {
        schedulerWorks = false
        val id = analysedThought("remind me to call the bank")
        resolution().quickReminder(id)
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotScheduled))

        schedulerWorks = true
        reconcile()
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.ReminderSet))
    }

    @Test
    fun aReminderMissedWhileThePhoneWasOffNeedsTheUser() = runBlocking {
        val id = analysedThought("remind me to call the bank")
        val reminderId = requireNotNull(resolution().quickReminder(id))
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.ReminderSet))

        // The phone was off for an hour past the reminder.
        val dueAt = requireNotNull(database.reminderDao().getById(reminderId)).dueAt
        reconcile(at = dueAt + 60L * 60_000L)

        assertStored(id, StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimePassed))
    }

    @Test
    fun reconciliationNeverTouchesAThoughtThatIsNotSortedIntoThatReminder() = runBlocking {
        val id = inbox().save("a plain idea")
        // A reminder that claims the thought, but the thought was never sorted into it.
        database.reminderDao().insert(
            ReminderEntity(title = "Stray", dueAt = now - 60L * 60_000L, linkedCaptureId = id, notificationWorkId = "w"),
        )
        reconcile()
        assertStored(id, StoredCaptureOutcome(CaptureOutcome.Saved))
        assertNull(database.captureDao().getById(id)?.decidedAt)
    }

    private fun expectations(): List<Pair<() -> Unit, StoredCaptureOutcome>> = listOf(
        { notificationsAllowed = true; schedulerWorks = true } to StoredCaptureOutcome(CaptureOutcome.ReminderSet),
        { notificationsAllowed = false; schedulerWorks = true } to
            StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff),
        { notificationsAllowed = true; schedulerWorks = false } to
            StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotScheduled),
    )

    private suspend fun assertStored(captureId: Long, expected: StoredCaptureOutcome) {
        val capture = requireNotNull(database.captureDao().getById(captureId))
        assertEquals(expected, StoredCaptureOutcome(capture.outcome, capture.outcomeReason))
        if (capture.outcome != CaptureOutcome.Saved) {
            requireNotNull(capture.outcomeAt)
            requireNotNull(capture.decidedAt)
        }
    }

    /** A quick reminder from Home for the thought; returns the reminder id. */
    private suspend fun CaptureResolution.quickReminder(captureId: Long): Long? {
        createQuickReminder(captureId, "Call the bank", now + 86_400_000L)
        return database.captureDao().getById(captureId)?.linkedItemId
    }

    private suspend fun reconcile(at: Long = now) {
        reconcileReminders(
            dao = database.reminderDao(),
            scheduler = scheduler,
            now = at,
            deliver = { _, _, _ -> ReminderDeliveryOutcome.Shown },
            deliverSummary = { ReminderDeliveryOutcome.Shown },
            linkedCaptures = LinkedCaptureOutcomes(
                captureRepository = RoomCaptureRepository(database.captureDao()),
                transaction = RoomCaptureFinalizationTransaction(database),
                canNotify = { notificationsAllowed },
            ),
        )
    }

    private suspend fun analysedThought(text: String): Long {
        val inbox = inbox()
        val id = inbox.save(text)
        inbox.analyze(id)
        return id
    }

    private val recording = PolicyRecordingScheduler { now }

    /** Arms like production when it works; returns no token when it does not. */
    private val scheduler = object : ReminderScheduler {
        override fun schedule(reminder: ReminderEntity): String? =
            if (schedulerWorks) recording.schedule(reminder) else null

        override fun cancel(reminderId: Long) = recording.cancel(reminderId)
    }

    private fun inbox(): CaptureInbox {
        val idle = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { it.cancel() }
        return CaptureInbox(
            captureRepository = RoomCaptureRepository(database.captureDao()),
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            spaceRepository = RoomSpaceRepository(database.spaceDao()),
            suggester = { text, _ ->
                LocalRulesCaptureAnalyzer(now = { Instant.ofEpochMilli(now) }, zoneId = { ZoneId.of("Europe/Tallinn") })
                    .analyze(text)
            },
            scope = idle,
            now = { now },
            transaction = RoomCaptureFinalizationTransaction(database),
        )
    }

    private fun confirm() = ConfirmCaptureActionUseCase(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = RoomReminderRepository(database.reminderDao(), scheduler),
        transaction = RoomCaptureFinalizationTransaction(database),
        labelRepository = RoomLabelRepository(database.labelDao()),
    )

    private fun resolution() = CaptureResolution(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = RoomReminderRepository(database.reminderDao(), scheduler),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggestionDao = database.captureSuggestionDao(),
        confirmCaptureAction = confirm(),
        transaction = RoomCaptureFinalizationTransaction(database),
        reminderOutcomes = ReminderSaveOutcomes(database.reminderDao()::getById) {
            NotificationAccess(notificationsAllowed = notificationsAllowed, reminderChannelEnabled = true)
        },
        now = { now },
    )
}
