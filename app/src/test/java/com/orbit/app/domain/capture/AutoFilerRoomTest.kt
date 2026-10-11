package com.orbit.app.domain.capture

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderNotificationWorker
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.WorkManagerReminderScheduler
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Act / ask / keep applied to stored thoughts: a clear thought is filed without a
 * question, Undo and "Just keep it" always win, and an automatic filing never counts
 * as the user agreeing.
 */
@RunWith(AndroidJUnit4::class)
class AutoFilerRoomTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private val day = 24L * 60L * 60_000L
    private lateinit var database: OrbitDatabase
    private val scopes = mutableListOf<CoroutineScope>()
    private var paused = false
    private var notificationsAllowed = true
    private var scheduler: ReminderScheduler = PolicyRecordingScheduler { now }

    /** Thoughts whose analysis is fixed here; everything else goes through the local rules. */
    private val fixedAnalyses = mapOf(
        CabinNote to CaptureAnalysis(
            rawText = CabinNote,
            suggestedType = SuggestedItemType.Note,
            suggestedSpaceName = "Home",
            suggestedTitle = "Cabin wifi password",
            suggestedNextAction = "",
            relatedTopics = listOf("Home"),
            reminderPossible = false,
            confidence = 0.9f,
        ),
        PlumberReminder to CaptureAnalysis(
            rawText = PlumberReminder,
            suggestedType = SuggestedItemType.Reminder,
            suggestedSpaceName = "Home",
            suggestedTitle = "Call the plumber",
            suggestedNextAction = "",
            relatedTopics = listOf("Home"),
            reminderPossible = true,
            suggestedReminderAt = now + day - 60L * 60_000L,
            reminderTimeStatus = ReminderTimeStatus.Resolved,
            confidence = 0.9f,
        ),
    )

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        runBlocking {
            database.spaceDao().insert(SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0))
        }
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        database.close()
        ShadowAlarmManager.reset()
    }

    @Test
    fun actFilesAClearNoteAndStampsTheDecisionWithoutALearningEvent() = runBlocking {
        val id = analysed(CabinNote)

        assertTrue(autoFiler().apply(id))

        val note = database.noteDao().observeAll().first().single()
        val capture = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Processed, capture.status)
        assertEquals(CaptureOutcome.Filed, capture.outcome)
        assertEquals(note.id, capture.linkedItemId)
        assertNotNull(capture.decidedAt)
        // An automatic filing is not the user agreeing with anything.
        assertTrue(database.aiSuggestionHistoryDao().observeAll().first().isEmpty())
        assertTrue(database.aiCorrectionHistoryDao().observeAll().first().isEmpty())
    }

    @Test
    fun undoRestoresSavedDeletesTheNoteAndIsNeverFiledAgain() = runBlocking {
        val id = analysed(CabinNote)
        autoFiler().apply(id)
        val noteId = database.noteDao().observeAll().first().single().id

        resolution().undo(id, SuggestedItemType.Note, noteId)

        assertTrue(database.noteDao().observeAll().first().isEmpty())
        val capture = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertEquals(CaptureOutcome.Saved, capture.outcome)
        assertNull(capture.linkedItemId)
        assertNotNull(capture.decidedAt)
        assertFalse(autoFiler().apply(id))
        assertEquals(0, autoFiler().applyPending())
        assertTrue(database.noteDao().observeAll().first().isEmpty())
    }

    @Test
    fun theListenerFilesAThoughtAsSoonAsItsAnalysisLands() = runBlocking {
        val live = liveScope()
        val inbox = inbox(scope = live)
        autoFiler().listen(live, inbox.events)

        // Analysis runs on its own after saving; nothing else is called.
        val id = inbox.save("Buy milk tomorrow")

        awaitUntil { database.captureDao().getById(id)?.outcome == CaptureOutcome.Filed }
        assertEquals(1, database.taskDao().observeAll().first().size)
        assertNotNull(database.captureDao().getById(id)?.decidedAt)
    }

    @Test
    fun aLateAnalyzedEventForAnUndoneThoughtDoesNothing() = runBlocking {
        val events = MutableSharedFlow<CaptureInboxEvent>(extraBufferCapacity = 16)
        autoFiler().listen(liveScope(), events)
        val undone = analysed(CabinNote)
        autoFiler().apply(undone)
        resolution().undo(undone, SuggestedItemType.Note, database.noteDao().observeAll().first().single().id)
        val undoneRow = requireNotNull(database.captureDao().getById(undone))
        val other = analysed("Buy milk tomorrow")

        // A late result for the undone thought, then one for a thought still pending.
        events.emit(CaptureInboxEvent.Analyzed(undone, requireNotNull(fixedAnalyses[CabinNote])))
        events.emit(CaptureInboxEvent.Analyzed(other, requireNotNull(fixedAnalyses[CabinNote])))

        // Events are handled in order, so once the second is filed the first was seen.
        awaitUntil { database.captureDao().getById(other)?.outcome == CaptureOutcome.Filed }
        assertEquals(undoneRow, database.captureDao().getById(undone))
        assertTrue(database.noteDao().observeAll().first().isEmpty())
    }

    @Test
    fun anExplicitReminderWithoutATimeAsksAndJustKeepItEndsTheQuestion() = runBlocking {
        val id = analysed("remind me about the car")
        assertTrue(autoFiler().apply(id))
        val asked = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureOutcome.NeedsYou, asked.outcome)
        assertEquals(CaptureOutcomeReason.TimeUnclear, asked.outcomeReason)
        assertTrue(database.reminderDao().observeAll().first().isEmpty())

        resolution().keep(id)

        val kept = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, kept.status)
        assertEquals(CaptureOutcome.Saved, kept.outcome)
        assertEquals(CaptureOutcomeReason.None, kept.outcomeReason)
        assertNotNull(kept.decidedAt)
        assertTrue(database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun justKeepItOnAReminderThatCannotRingDeletesItAndCancelsItsAlarmAndBackupWork() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        scheduler = WorkManagerReminderScheduler(context) { now }
        notificationsAllowed = false
        val id = analysed(PlumberReminder)

        assertTrue(autoFiler().apply(id))

        val reminder = database.reminderDao().observeAll().first().single()
        val waiting = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureOutcome.NeedsYou, waiting.outcome)
        assertEquals(CaptureOutcomeReason.NotificationsOff, waiting.outcomeReason)
        assertEquals(reminder.id, waiting.linkedItemId)
        assertEquals(listOf(reminder.id), alarmReminderIds())
        assertTrue(backupWork(reminder.id).any { it.state == WorkInfo.State.ENQUEUED })

        resolution().keep(id)

        assertNull(database.reminderDao().getById(reminder.id))
        assertTrue(alarmReminderIds().isEmpty())
        assertTrue(backupWork(reminder.id).none { it.state == WorkInfo.State.ENQUEUED })
        val kept = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, kept.status)
        assertEquals(CaptureOutcome.Saved, kept.outcome)
        assertEquals(CaptureOutcomeReason.None, kept.outcomeReason)
        assertNull(kept.linkedItemId)
        assertNotNull(kept.decidedAt)
        // Kept for good: a later run leaves it alone.
        assertEquals(0, autoFiler().applyPending())
        assertTrue(database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun justKeepItLeavesAThoughtFiledAsANoteAsItIs() = runBlocking {
        val id = analysed(CabinNote)
        autoFiler().apply(id)
        val before = requireNotNull(database.captureDao().getById(id))

        resolution().keep(id)

        assertEquals(before, database.captureDao().getById(id))
        assertEquals(1, database.noteDao().observeAll().first().size)
    }

    @Test
    fun sortRightAfterSavingTurnsAutomaticFilingOff() = runBlocking {
        val id = analysed(CabinNote)
        paused = true

        assertFalse(autoFiler().apply(id))
        assertEquals(0, autoFiler().applyPending())
        val capture = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertNull(capture.decidedAt)
        assertTrue(database.noteDao().observeAll().first().isEmpty())

        // Turned off again: the thought was never decided, so it is filed now.
        paused = false
        assertEquals(1, autoFiler().applyPending())
        assertEquals(CaptureOutcome.Filed, database.captureDao().getById(id)?.outcome)
    }

    @Test
    fun aThoughtAddedFromACalendarDayIsLeftForTheSortSheet() = runBlocking {
        val inbox = inbox()
        val id = inbox.save("Buy milk", contextDateEpochDay = 20_650L)
        inbox.analyze(id)

        assertFalse(autoFiler().apply(id))
        val capture = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertNull(capture.decidedAt)
        assertTrue(database.taskDao().observeAll().first().isEmpty())
    }

    /** Saves [text] and stores its suggestion, stopping before act / ask / keep. */
    private suspend fun analysed(text: String): Long {
        val inbox = inbox()
        val id = inbox.save(text)
        assertNotNull(inbox.analyze(id))
        return id
    }

    private fun liveScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scopes += it }

    /** By default analysis runs only when a test calls it, so each test stops exactly where it says. */
    private fun inbox(scope: CoroutineScope = CoroutineScope(SupervisorJob()).also { it.cancel() }) = CaptureInbox(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        suggestionDao = database.captureSuggestionDao(),
        brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggester = { text, _ ->
            fixedAnalyses[text] ?: LocalRulesCaptureAnalyzer(
                now = { Instant.ofEpochMilli(now) },
                zoneId = { ZoneId.of("Europe/Tallinn") },
            ).analyze(text)
        },
        scope = scope,
        now = { now },
        transaction = RoomCaptureFinalizationTransaction(database),
    )

    private fun resolution(): CaptureResolution {
        val captures = RoomCaptureRepository(database.captureDao())
        val notes = RoomNoteRepository(database.noteDao())
        val tasks = RoomTaskRepository(database.taskDao())
        val reminders = RoomReminderRepository(database.reminderDao(), scheduler)
        val transaction = RoomCaptureFinalizationTransaction(database)
        return CaptureResolution(
            captureRepository = captures,
            noteRepository = notes,
            taskRepository = tasks,
            reminderRepository = reminders,
            spaceRepository = RoomSpaceRepository(database.spaceDao()),
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = ConfirmCaptureActionUseCase(
                captureRepository = captures,
                noteRepository = notes,
                taskRepository = tasks,
                reminderRepository = reminders,
                transaction = transaction,
                labelRepository = RoomLabelRepository(database.labelDao()),
            ),
            transaction = transaction,
            reminderOutcomes = ReminderSaveOutcomes(database.reminderDao()::getById) {
                NotificationAccess(notificationsAllowed = notificationsAllowed, reminderChannelEnabled = true)
            },
            now = { now },
        )
    }

    private fun autoFiler() = AutoFiler(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        suggestionDao = database.captureSuggestionDao(),
        brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
        captureResolution = resolution(),
        transaction = RoomCaptureFinalizationTransaction(database),
        now = { now },
        paused = { paused },
    )

    private suspend fun awaitUntil(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(20)
    }

    private fun alarmReminderIds(): List<Long> =
        shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.map { alarm ->
            shadowOf(requireNotNull(alarm.operation)).savedIntent.getLongExtra(ReminderNotificationWorker.KEY_REMINDER_ID, -1L)
        }

    private fun backupWork(reminderId: Long): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("orbit_reminder_$reminderId").get()

    private companion object {
        const val CabinNote = "Wifi password for the cabin is on the fridge"
        const val PlumberReminder = "Call the plumber tomorrow at 9:00"
    }
}
