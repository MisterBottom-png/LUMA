package com.orbit.app.ui.screens.spaces

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.capture.AutoFiler
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The Space "+" flow (review §9.2): the thought is in the Space the moment it is saved,
 * and the sheet asks only for what is missing.
 */
@RunWith(AndroidJUnit4::class)
class SpaceCaptureViewModelTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private lateinit var database: OrbitDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workId = 0L

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        workId = runBlocking {
            database.spaceDao().insert(SpaceEntity(name = "Work", icon = "work", colorAccent = "#000000", sortOrder = 0))
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun savingAsANoteFilesItIntoTheSpaceAtOnce_andClearsTheDraft() {
        val viewModel = viewModel()
        viewModel.open(workId)
        viewModel.onDraftChanged(workId, "Parking permit: price and office hours")
        viewModel.onSaveAsChanged(SpaceSaveAs.Note)
        viewModel.save()

        awaitUntil { viewModel.uiState.value.finished != null }
        val note = runBlocking { database.noteDao().observeAll().first() }.single()
        assertEquals(workId, note.spaceId)
        assertEquals("Parking permit: price and office hours", note.body)
        assertEquals("", viewModel.draft(workId))
        assertNull(viewModel.uiState.value.spaceId)
    }

    @Test
    fun aClearReminderWithoutATimeAsksWhen_thenRemindsInTheSpaceWithTheWholeThought() {
        val viewModel = viewModel()
        viewModel.open(workId)
        viewModel.onDraftChanged(workId, "Remind me to renew the parking permit")
        viewModel.save()

        awaitUntil { viewModel.uiState.value.step == SpaceCaptureStep.TimeQuestion }
        val state = viewModel.uiState.value
        val captureId = requireNotNull(state.captureId)
        // Saved into the Space already, before any answer.
        assertEquals(workId, runBlocking { database.captureDao().getById(captureId) }?.suggestedSpaceId)
        assertTrue(state.quickTimes.isNotEmpty())
        assertTrue(state.quickTimes.all { it.at > now })

        viewModel.remindAt(state.quickTimes.first().at)
        awaitUntil { viewModel.uiState.value.finished != null }
        val reminder = runBlocking { database.reminderDao().observeAll().first() }.single()
        assertEquals(workId, reminder.spaceId)
        assertEquals(state.quickTimes.first().at, reminder.dueAt)
        assertEquals(CaptureOutcome.ReminderSet, runBlocking { database.captureDao().getById(captureId) }?.outcome)
    }

    @Test
    fun anUnclearThoughtAsksWhatItShouldBe_andDecideLaterKeepsItUnderNotSortedYet() {
        val viewModel = viewModel()
        viewModel.open(workId)
        viewModel.onDraftChanged(workId, "Parking permit: price and office hours")
        viewModel.save()

        awaitUntil { viewModel.uiState.value.step == SpaceCaptureStep.TypeQuestion }
        val captureId = requireNotNull(viewModel.uiState.value.captureId)
        viewModel.decideLater()

        awaitUntil { viewModel.uiState.value.finished?.keptForLater == true }
        val kept = requireNotNull(runBlocking { database.captureDao().getById(captureId) })
        assertEquals(CaptureStatus.Inbox, kept.status)
        assertEquals(workId, kept.suggestedSpaceId)
        // A decision: automatic filing never files it behind the user's back.
        assertNotNull(kept.decidedAt)
    }

    @Test
    fun aPassedTimeIsRefusedAndNothingIsSet() {
        val viewModel = viewModel()
        viewModel.open(workId)
        viewModel.onDraftChanged(workId, "Remind me to renew the parking permit")
        viewModel.save()
        awaitUntil { viewModel.uiState.value.step == SpaceCaptureStep.TimeQuestion }

        viewModel.remindAt(now - 60_000L)
        idle()
        assertTrue(runBlocking { database.reminderDao().observeAll().first() }.isEmpty())
        assertEquals(SpaceCaptureStep.TimeQuestion, viewModel.uiState.value.step)
    }

    @Test
    fun aDraftIsKeptPerSpaceWhenTheSheetIsClosed() {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        viewModel.open(workId)
        viewModel.onDraftChanged(workId, "Half a thought")
        viewModel.dismiss()

        assertNull(viewModel.uiState.value.spaceId)
        assertEquals("Half a thought", viewModel.draft(workId))
        assertEquals("", viewModel.draft(workId + 1))
        // A new ViewModel over the same saved state (Android stopped the app) still has it.
        assertEquals("Half a thought", viewModel(handle).draft(workId))
    }

    @Test
    fun calendarPlusAddsForThatDay_withoutASpace() {
        // Review: Calendar "+" opens this sheet for the day instead of jumping to Home.
        val day = java.time.LocalDate.of(2026, 7, 20).toEpochDay()
        val viewModel = viewModel()
        viewModel.openForDay(day)
        assertTrue(viewModel.uiState.value.isOpen)
        viewModel.onDayDraftChanged("Buy a birthday card")
        viewModel.onSaveAsChanged(SpaceSaveAs.Task)
        viewModel.save()

        awaitUntil { viewModel.uiState.value.finished != null }
        val task = runBlocking { database.taskDao().observeAll().first() }.single()
        assertEquals(day, task.scheduledDateEpochDay)
        assertNull(task.spaceId)
        assertEquals("", runBlocking { viewModel.dayDraftFlow().first() })
    }

    @Test
    fun quickTimesNeverOfferAPassedEvening() {
        val evening = Instant.parse("2026-07-14T15:50:00Z").toEpochMilli() // 18:50 in Tallinn
        val times = viewModel(clock = evening).quickTimes()
        assertTrue(times.none { it.day == QuickDay.Today })
        assertTrue(times.all { it.at > evening })
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(), clock: Long = now): SpaceCaptureViewModel {
        val captures = RoomCaptureRepository(database.captureDao())
        val notes = RoomNoteRepository(database.noteDao())
        val tasks = RoomTaskRepository(database.taskDao())
        val reminders = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { clock })
        val spaces = RoomSpaceRepository(database.spaceDao())
        val transaction = RoomCaptureFinalizationTransaction(database)
        val inbox = CaptureInbox(
            captureRepository = captures,
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            spaceRepository = spaces,
            suggester = { text, _ ->
                LocalRulesCaptureAnalyzer(now = { Instant.ofEpochMilli(clock) }, zoneId = { zone }).analyze(text)
            },
            scope = scope,
            now = { clock },
            transaction = transaction,
        )
        val confirm = ConfirmCaptureActionUseCase(
            captureRepository = captures,
            noteRepository = notes,
            taskRepository = tasks,
            reminderRepository = reminders,
            transaction = transaction,
            labelRepository = RoomLabelRepository(database.labelDao()),
        )
        val resolution = CaptureResolution(
            captureRepository = captures,
            noteRepository = notes,
            taskRepository = tasks,
            reminderRepository = reminders,
            spaceRepository = spaces,
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = confirm,
            transaction = transaction,
            reminderOutcomes = ReminderSaveOutcomes(database.reminderDao()::getById) {
                NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
            },
            now = { clock },
        )
        return SpaceCaptureViewModel(
            captureInbox = inbox,
            captureRepository = captures,
            spaceRepository = spaces,
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = confirm,
            captureResolution = resolution,
            autoFiler = AutoFiler(
                captureRepository = captures,
                suggestionDao = database.captureSuggestionDao(),
                brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
                captureResolution = resolution,
                transaction = transaction,
                now = { clock },
            ),
            savedStateHandle = handle,
            now = { clock },
            zoneId = { zone },
            lookingTimeoutMillis = Long.MAX_VALUE / 4,
        )
    }

    // Advances the main looper clock too, so the short polling delay can run.
    private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))

    private fun awaitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            idle()
            if (System.currentTimeMillis() > deadline) fail("Timed out waiting")
            Thread.sleep(10)
        }
    }
}
