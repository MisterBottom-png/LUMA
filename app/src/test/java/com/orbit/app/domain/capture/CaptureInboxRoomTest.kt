package com.orbit.app.domain.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureInboxRoomTest {
    private lateinit var database: OrbitDatabase
    private lateinit var scope: CoroutineScope
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private var suggesterFails = false

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        // Background analysis is not launched automatically in these tests; each test
        // calls analyze() explicitly so assertions are deterministic.
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        scope.cancel()
    }

    @After
    fun tearDown() = database.close()

    private fun inbox(
        scope: CoroutineScope = this.scope,
        beforeResult: suspend () -> Unit = {},
        onAnalysis: () -> Unit = {},
    ): CaptureInbox = CaptureInbox(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        suggestionDao = database.captureSuggestionDao(),
        brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggester = { text, _ ->
            onAnalysis()
            beforeResult()
            if (suggesterFails) error("analysis unavailable")
            LocalRulesCaptureAnalyzer(now = { Instant.ofEpochMilli(now) }, zoneId = { ZoneId.of("Europe/Tallinn") })
                .analyze(text)
        },
        scope = scope,
        now = { now },
        transaction = RoomCaptureFinalizationTransaction(database),
    )

    private fun confirm() = ConfirmCaptureActionUseCase(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now }),
        transaction = RoomCaptureFinalizationTransaction(database),
        labelRepository = RoomLabelRepository(database.labelDao()),
    )

    /** Holds the suggester until [release] completes, so a test can act on the thought meanwhile. */
    private class SlowAnalysis {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val hook: suspend () -> Unit = {
            started.complete(Unit)
            release.await()
        }
    }

    private fun resolution() = CaptureResolution(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now }),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggestionDao = database.captureSuggestionDao(),
        confirmCaptureAction = ConfirmCaptureActionUseCase(
            captureRepository = RoomCaptureRepository(database.captureDao()),
            noteRepository = RoomNoteRepository(database.noteDao()),
            taskRepository = RoomTaskRepository(database.taskDao()),
            reminderRepository = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now }),
            transaction = RoomCaptureFinalizationTransaction(database),
            labelRepository = RoomLabelRepository(database.labelDao()),
        ),
        transaction = RoomCaptureFinalizationTransaction(database),
        reminderOutcomes = ReminderSaveOutcomes(database.reminderDao()::getById) {
            NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
        },
        now = { now },
    )

    @Test
    fun theCalendarDayIsKeptWithTheThoughtAndSurvivesARestartBeforeAnalysis() = runBlocking {
        val day = java.time.LocalDate.of(2026, 7, 20).toEpochDay()
        // The test scope is cancelled, so no analysis runs: the app "stops" right after saving.
        val note = inbox().save("Ideas for the garden", contextDateEpochDay = day)
        val dump = inbox().save("Ideas for the garden\nCall the plumber", contextDateEpochDay = day)
        assertEquals(day, database.captureDao().getById(note)?.contextDateEpochDay)

        // After the restart a new inbox picks the pending thoughts up.
        inbox().analyzePending()

        assertEquals(day, database.captureSuggestionDao().getByCaptureId(note)?.contextDateEpochDay)
        assertEquals(day, database.brainDumpDao().getSession(dump)?.calendarDateContextEpochDay)
    }

    @Test
    fun aThoughtIsSafeInTheInboxBeforeAnyAnalysis() = runBlocking {
        val id = inbox().save("  call the bank tomorrow  ")
        val capture = requireNotNull(database.captureDao().getById(id))
        assertEquals("call the bank tomorrow", capture.rawText)
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertNull(database.captureSuggestionDao().getByCaptureId(id))
    }

    @Test
    fun theSavedIdIsHandedOverBeforeAnalysisCanReportBack() = runBlocking {
        val liveScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var awaited: Long? = null
        var awaitedWhenAnalysed: Long? = null
        val analysed = CompletableDeferred<Unit>()
        val inbox = inbox(liveScope, onAnalysis = {
            awaitedWhenAnalysed = awaited
            analysed.complete(Unit)
        })

        val id = inbox.save("call the bank tomorrow") { awaited = it }
        withTimeout(5_000) { analysed.await() }
        liveScope.cancel()

        assertEquals(id, awaitedWhenAnalysed)
    }

    @Test
    fun analysisStoresASuggestionButNeverCreatesItems() = runBlocking {
        database.spaceDao().insert(SpaceEntity(name = "Work", icon = "work", colorAccent = "#000000", sortOrder = 0))
        val inbox = inbox()
        val id = inbox.save("Send the quarterly report to the team")
        inbox.analyze(id)

        val suggestion = requireNotNull(database.captureSuggestionDao().getByCaptureId(id))
        assertEquals(SuggestedItemType.Task, suggestion.suggestedType)
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(id)?.status)
        assertTrue(database.noteDao().observeAll().first().isEmpty())
        assertTrue(database.taskDao().observeAll().first().isEmpty())
        assertTrue(database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun aFailedAnalysisLeavesTheThoughtInToSortWithoutASuggestion() = runBlocking {
        suggesterFails = true
        val inbox = inbox()
        val id = inbox.save("something to think about")
        assertNull(inbox.analyze(id))
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(id)?.status)
        assertNull(database.captureSuggestionDao().getByCaptureId(id))
    }

    @Test
    fun pendingThoughtsAreAnalysedLaterAndOnlyOnce() = runBlocking {
        val inbox = inbox()
        val first = inbox.save("buy milk")
        val second = inbox.save("remind me tomorrow at 9:00 to water plants")
        assertEquals(2, inbox.analyzePending())
        assertNotNull(database.captureSuggestionDao().getByCaptureId(first))
        assertNotNull(database.captureSuggestionDao().getByCaptureId(second))
        assertEquals(0, inbox.analyzePending())
    }

    @Test
    fun aMultiLineDumpBecomesAPersistedBrainDumpSession() = runBlocking {
        val inbox = inbox()
        val id = inbox.save("buy milk\ncall the dentist\nremind me tomorrow at 1600 to pay rent")
        inbox.analyze(id)
        val session = RoomBrainDumpRepository(database.brainDumpDao()).getSession(id)
        assertNotNull(session)
        assertEquals(3, session?.items?.size)
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(id)?.status)
    }

    @Test
    fun hidingASuggestionKeepsTheThought() = runBlocking {
        val inbox = inbox()
        val id = inbox.save("an idea for the garden")
        inbox.analyze(id)
        inbox.dismissSuggestion(id)
        assertTrue(database.captureSuggestionDao().getByCaptureId(id)?.dismissed == true)
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(id)?.status)
        inbox.restoreSuggestion(id)
        assertTrue(database.captureSuggestionDao().getByCaptureId(id)?.dismissed == false)
    }

    @Test
    fun acceptingASuggestionCreatesTheItemAndUndoPutsTheThoughtBack() = runBlocking {
        val inbox = inbox()
        val id = inbox.save("Send the quarterly report to the team")
        inbox.analyze(id)
        val resolution = resolution()

        val accepted = requireNotNull(resolution.acceptSuggestion(id))
        assertEquals(SuggestedItemType.Task, accepted.itemType)
        assertEquals(CaptureStatus.Processed, database.captureDao().getById(id)?.status)
        assertNotNull(database.taskDao().getById(accepted.itemId))

        resolution.undo(id, accepted.itemType, accepted.itemId)
        assertNull(database.taskDao().getById(accepted.itemId))
        val restored = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, restored.status)
        assertNull(restored.linkedItemId)
    }

    @Test
    fun aReminderSuggestionWithoutAFutureTimeNeedsAChoiceFirst() = runBlocking {
        val inbox = inbox()
        val id = inbox.save("remind me to call grandma")
        inbox.analyze(id)
        assertNull(resolution().acceptSuggestion(id))
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(id)?.status)
        assertTrue(database.reminderDao().observeAll().first().isEmpty())
    }

    @Test
    fun lettingGoArchivesAndUndoRestores() = runBlocking {
        val id = inbox().save("old thought")
        val resolution = resolution()
        resolution.archive(id)
        assertEquals(CaptureStatus.Archived, database.captureDao().getById(id)?.status)
        resolution.unarchive(id)
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(id)?.status)
    }

    @Test
    fun theQuickReminderCreatesAReminderFromAUserTap() = runBlocking {
        val inbox = inbox()
        val id = inbox.save("remind me tomorrow at 9:00 to water plants")
        inbox.analyze(id)
        val at = now + 86_400_000L
        resolution().createQuickReminder(id, "Water plants", at)
        val reminder = database.reminderDao().observeAll().first().single()
        assertEquals(at, reminder.dueAt)
        assertEquals(CaptureStatus.Processed, database.captureDao().getById(id)?.status)
    }

    @Test
    fun lettingGoWhileAnalysisRunsIsNotUndoneByTheLateResult() = runBlocking {
        val slow = SlowAnalysis()
        val inbox = inbox(beforeResult = slow.hook)
        val id = inbox.save("Send the quarterly report to the team")
        val result = async { inbox.analyze(id) }
        withTimeout(5_000) { slow.started.await() }

        resolution().archive(id)
        slow.release.complete(Unit)

        assertNull(withTimeout(5_000) { result.await() })
        assertEquals(CaptureStatus.Archived, database.captureDao().getById(id)?.status)
        assertNull(database.captureSuggestionDao().getByCaptureId(id))
    }

    @Test
    fun sortingWhileAnalysisRunsKeepsTheItemLinkAndCreatesNoDuplicate() = runBlocking {
        val slow = SlowAnalysis()
        val inbox = inbox(beforeResult = slow.hook)
        val id = inbox.save("Send the quarterly report to the team")
        val result = async { inbox.analyze(id) }
        withTimeout(5_000) { slow.started.await() }

        val noteId = confirm().saveNote(captureId = id, spaceId = null, title = "Quarterly report")
        slow.release.complete(Unit)

        assertNull(withTimeout(5_000) { result.await() })
        val capture = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Processed, capture.status)
        assertEquals(noteId, capture.linkedItemId)
        assertNull(database.captureSuggestionDao().getByCaptureId(id))
        assertEquals(1, database.noteDao().observeAll().first().size)
        assertTrue(database.taskDao().observeAll().first().isEmpty())
    }

    @Test
    fun aBrainDumpSortedWhileAnalysisRunsGetsNoSession() = runBlocking {
        val slow = SlowAnalysis()
        val inbox = inbox(beforeResult = slow.hook)
        val id = inbox.save("buy milk\ncall the dentist\nremind me tomorrow at 1600 to pay rent")
        val result = async { inbox.analyze(id) }
        withTimeout(5_000) { slow.started.await() }

        confirm().saveNote(captureId = id, spaceId = null, title = "Errands")
        slow.release.complete(Unit)

        assertNull(withTimeout(5_000) { result.await() })
        assertNull(RoomBrainDumpRepository(database.brainDumpDao()).getSession(id))
        assertEquals(CaptureStatus.Processed, database.captureDao().getById(id)?.status)
    }

    @Test
    fun aSpaceChosenWhileAnalysisRunsIsKept() = runBlocking {
        val home = database.spaceDao().insert(
            SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0),
        )
        database.spaceDao().insert(SpaceEntity(name = "Work", icon = "work", colorAccent = "#000000", sortOrder = 1))
        val slow = SlowAnalysis()
        val inbox = inbox(beforeResult = slow.hook)
        val id = inbox.save("Send the quarterly report to the team")
        val result = async { inbox.analyze(id) }
        withTimeout(5_000) { slow.started.await() }

        val capture = requireNotNull(database.captureDao().getById(id))
        database.captureDao().update(capture.copy(suggestedSpaceId = home))
        slow.release.complete(Unit)

        assertNotNull(withTimeout(5_000) { result.await() })
        val stored = requireNotNull(database.captureDao().getById(id))
        assertEquals(CaptureStatus.Inbox, stored.status)
        assertEquals(home, stored.suggestedSpaceId)
        // Sorting reads the suggestion's Space, so it must name the chosen one too.
        assertEquals("Home", database.captureSuggestionDao().getByCaptureId(id)?.suggestedSpaceName)
    }

    @Test
    fun aThoughtAddedFromASpaceGoesIntoThatSpace() = runBlocking {
        val household = database.spaceDao().insert(
            SpaceEntity(name = "Household", icon = "home", colorAccent = "#000000", sortOrder = 0),
        )
        database.spaceDao().insert(SpaceEntity(name = "Work", icon = "work", colorAccent = "#000000", sortOrder = 1))
        val inbox = inbox()
        val id = inbox.save("Send the quarterly report to the team", spaceId = household)
        inbox.analyze(id)

        assertEquals(household, database.captureDao().getById(id)?.suggestedSpaceId)
        val accepted = requireNotNull(resolution().acceptSuggestion(id))
        val spaceOfItem = when (accepted.itemType) {
            SuggestedItemType.Note -> database.noteDao().getById(accepted.itemId)?.spaceId
            SuggestedItemType.Reminder -> database.reminderDao().getById(accepted.itemId)?.spaceId
            else -> database.taskDao().getById(accepted.itemId)?.spaceId
        }
        assertEquals(household, spaceOfItem)
    }
}
