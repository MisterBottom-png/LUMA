package com.orbit.app.domain.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.testing.PolicyRecordingScheduler
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A database write and an Android alarm are never one transaction, and the app can
 * be stopped between any two steps. A pending decision is a row (`decidedAt == null`),
 * so each "process" here is a fresh set of objects over the same on-disk database:
 * nothing in memory survives between them.
 */
@RunWith(AndroidJUnit4::class)
class AutoFilerRecoveryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "autofiler-recovery.db"
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private val opened = mutableListOf<OrbitDatabase>()

    /** Thoughts whose analysis is fixed here; everything else goes through the local rules. */
    private val fixedAnalyses = mapOf(
        "Wifi password for the cabin is on the fridge" to CaptureAnalysis(
            rawText = "Wifi password for the cabin is on the fridge",
            suggestedType = SuggestedItemType.Note,
            suggestedSpaceName = "Home",
            suggestedTitle = "Cabin wifi password",
            suggestedNextAction = "",
            relatedTopics = listOf("Home"),
            reminderPossible = false,
            confidence = 0.9f,
        ),
        "Call the plumber at 8:00 this morning" to CaptureAnalysis(
            rawText = "Call the plumber at 8:00 this morning",
            suggestedType = SuggestedItemType.Reminder,
            suggestedSpaceName = "Home",
            suggestedNextAction = "",
            relatedTopics = listOf("Home"),
            reminderPossible = true,
            suggestedReminderAt = now - 2L * 60L * 60_000L,
            reminderTimeStatus = ReminderTimeStatus.Resolved,
            confidence = 0.9f,
        ),
        "Sometime remind me about the gutters" to CaptureAnalysis(
            rawText = "Sometime remind me about the gutters",
            suggestedType = SuggestedItemType.Reminder,
            suggestedSpaceName = "Home",
            suggestedNextAction = "",
            relatedTopics = listOf("Home"),
            reminderPossible = true,
            confidence = 0.67f,
        ),
    )

    @Before
    fun setUp() {
        context.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        opened.forEach { if (it.isOpen) it.close() }
        context.deleteDatabase(name)
    }

    @Test
    fun aDecisionInterruptedAfterAnalysisIsFinishedOnceOnTheNextStartAndUndoSticks() = runBlocking {
        // Process 1: the thought is saved and analysed, then the app stops before act/ask/keep.
        val first = startProcess()
        first.database.spaceDao().insert(SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0))
        val id = first.inbox.save("Wifi password for the cabin is on the fridge")
        first.inbox.analyze(id)
        assertNotNull(first.database.captureSuggestionDao().getByCaptureId(id))
        assertNull(first.database.captureDao().getById(id)?.decidedAt)
        first.stop()

        // Process 2: the next start finishes the decision, exactly once.
        val second = startProcess()
        assertEquals(1, second.autoFiler.applyPending())
        assertEquals(0, second.autoFiler.applyPending())
        val notes = second.database.noteDao().observeAll().first()
        assertEquals(1, notes.size)
        val filed = requireNotNull(second.database.captureDao().getById(id))
        assertEquals(CaptureStatus.Processed, filed.status)
        assertEquals(CaptureOutcome.Filed, filed.outcome)
        assertEquals(notes.single().id, filed.linkedItemId)
        assertNotNull(filed.decidedAt)
        // An automatic filing is not the user agreeing with anything.
        assertTrue(second.database.aiSuggestionHistoryDao().observeAll().first().isEmpty())

        // The user takes it back, then the app stops again.
        second.resolution.undo(id, SuggestedItemType.Note, notes.single().id)
        second.stop()

        // Process 3: Undo wins over the stored suggestion; nothing is filed again.
        val third = startProcess()
        assertEquals(0, third.autoFiler.applyPending())
        assertEquals(1, third.database.captureDao().observeAll().first().size)
        assertTrue(third.database.noteDao().observeAll().first().isEmpty())
        val kept = requireNotNull(third.database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, kept.status)
        assertEquals(CaptureOutcome.Saved, kept.outcome)
        assertNotNull(kept.decidedAt)
    }

    @Test
    fun twoRunsRacingForTheSameThoughtMakeOneItem() = runBlocking {
        val first = startProcess()
        val id = first.inbox.save("Buy milk tomorrow")
        first.inbox.analyze(id)
        first.stop()

        // Two starts at once (for example app start and an analysis event), each with its own objects.
        val a = startProcess()
        val b = startProcess(reuse = a.database)
        listOf(a, b).map { process -> async(Dispatchers.Default) { process.autoFiler.applyPending() } }.awaitAll()

        assertEquals(1, a.database.taskDao().observeAll().first().size)
        assertTrue(a.database.noteDao().observeAll().first().isEmpty())
        val capture = requireNotNull(a.database.captureDao().getById(id))
        assertEquals(CaptureOutcome.Filed, capture.outcome)
        assertEquals(a.database.taskDao().observeAll().first().single().id, capture.linkedItemId)
    }

    @Test
    fun aReminderWithoutATimeBecomesOneQuestionAfterARestartAndNoReminderIsCreated() = runBlocking {
        val first = startProcess()
        val id = first.inbox.save("remind me about the car")
        first.inbox.analyze(id)
        first.stop()

        val second = startProcess()
        assertEquals(1, second.autoFiler.applyPending())
        val capture = requireNotNull(second.database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertEquals(CaptureOutcome.NeedsYou, capture.outcome)
        assertEquals(CaptureOutcomeReason.TimeUnclear, capture.outcomeReason)
        assertNotNull(capture.decidedAt)
        assertTrue(second.database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun aReminderWhoseTimeAlreadyPassedIsAQuestionNeverAReminder() = runBlocking {
        val first = startProcess()
        first.database.spaceDao().insert(SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0))
        val id = first.inbox.save("Call the plumber at 8:00 this morning")
        first.inbox.analyze(id)
        first.stop()

        val second = startProcess()
        second.autoFiler.applyPending()
        val capture = requireNotNull(second.database.captureDao().getById(id))
        assertEquals(CaptureOutcome.NeedsYou, capture.outcome)
        assertEquals(CaptureOutcomeReason.TimePassed, capture.outcomeReason)
        assertTrue(second.database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun aReminderGuessBelowHighConfidenceIsKeptWithoutAQuestion() = runBlocking {
        val first = startProcess()
        val id = first.inbox.save("Sometime remind me about the gutters")
        first.inbox.analyze(id)
        first.stop()

        val second = startProcess()
        assertEquals(1, second.autoFiler.applyPending())
        val capture = requireNotNull(second.database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertEquals(CaptureOutcome.Saved, capture.outcome)
        assertEquals(CaptureOutcomeReason.None, capture.outcomeReason)
        assertEquals(now, capture.decidedAt)
        assertTrue(second.database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun aPlainThoughtIsKeptQuietlyAndLookedAtOnlyOnce() = runBlocking {
        val first = startProcess()
        val id = first.inbox.save("parking code 4826")
        first.inbox.analyze(id)
        first.stop()

        val second = startProcess()
        assertEquals(1, second.autoFiler.applyPending())
        assertEquals(0, second.autoFiler.applyPending())
        val capture = requireNotNull(second.database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertEquals(CaptureOutcome.Saved, capture.outcome)
        assertEquals(CaptureOutcomeReason.None, capture.outcomeReason)
        assertEquals(now, capture.decidedAt)
        assertTrue(second.database.noteDao().observeAll().first().isEmpty())
        assertTrue(second.database.taskDao().observeAll().first().isEmpty())
    }

    @Test
    fun aThoughtWithoutAStoredSuggestionWaitsForAnalysis() = runBlocking {
        val first = startProcess()
        // Stopped right after saving: no suggestion yet.
        val id = first.inbox.save("Buy milk tomorrow")
        first.stop()

        val second = startProcess()
        assertEquals(0, second.autoFiler.applyPending())
        assertNull(second.database.captureDao().getById(id)?.decidedAt)
        second.inbox.analyzePending()
        assertEquals(1, second.autoFiler.applyPending())
        assertEquals(1, second.database.taskDao().observeAll().first().size)
    }

    private inner class Process(val database: OrbitDatabase) {
        private val scheduler = PolicyRecordingScheduler { now }
        private val transaction = RoomCaptureFinalizationTransaction(database)
        private val captures = RoomCaptureRepository(database.captureDao())

        val inbox = CaptureInbox(
            captureRepository = captures,
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            spaceRepository = RoomSpaceRepository(database.spaceDao()),
            suggester = { text, _ ->
                fixedAnalyses[text] ?: LocalRulesCaptureAnalyzer(
                    now = { Instant.ofEpochMilli(now) },
                    zoneId = { ZoneId.of("Europe/Tallinn") },
                ).analyze(text)
            },
            // Analysis runs only when a test calls it, so each "process" stops exactly where the test says.
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { it.cancel() },
            now = { now },
            transaction = transaction,
        )

        val resolution = CaptureResolution(
            captureRepository = captures,
            noteRepository = RoomNoteRepository(database.noteDao()),
            taskRepository = RoomTaskRepository(database.taskDao()),
            reminderRepository = RoomReminderRepository(database.reminderDao(), scheduler),
            spaceRepository = RoomSpaceRepository(database.spaceDao()),
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = ConfirmCaptureActionUseCase(
                captureRepository = captures,
                noteRepository = RoomNoteRepository(database.noteDao()),
                taskRepository = RoomTaskRepository(database.taskDao()),
                reminderRepository = RoomReminderRepository(database.reminderDao(), scheduler),
                transaction = transaction,
                labelRepository = RoomLabelRepository(database.labelDao()),
            ),
            transaction = transaction,
            reminderOutcomes = ReminderSaveOutcomes(database.reminderDao()::getById) {
                NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
            },
            now = { now },
        )

        val autoFiler = AutoFiler(
            captureRepository = captures,
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            captureResolution = resolution,
            transaction = transaction,
            now = { now },
        )

        fun stop() = database.close()
    }

    private fun startProcess(reuse: OrbitDatabase? = null): Process {
        val database = reuse ?: Room.databaseBuilder(context, OrbitDatabase::class.java, name)
            .addMigrations(*OrbitDatabase.AllMigrations)
            .allowMainThreadQueries()
            .build()
            .also { opened += it }
        return Process(database)
    }
}
