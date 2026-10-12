package com.orbit.app.ui.screens.review

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import java.time.ZoneId
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

/** The Needs you one-item sheet (review §9.3): nothing is filed until the user chooses. */
@RunWith(AndroidJUnit4::class)
class SortOneViewModelTest {
    private val zone = ZoneId.of("Europe/Tallinn")
    private val now = Instant.parse("2026-07-14T07:00:00Z").toEpochMilli()
    private lateinit var database: OrbitDatabase
    private var personal = 0L
    private var work = 0L
    private var captureId = 0L

    @Before
    fun setUp() = runBlocking {
        database = inMemoryOrbitDatabase()
        personal = database.spaceDao().insert(SpaceEntity(name = "Personal", icon = "p", colorAccent = "#000000", sortOrder = 0))
        work = database.spaceDao().insert(SpaceEntity(name = "Work", icon = "w", colorAccent = "#000000", sortOrder = 1))
        captureId = database.captureDao().insert(CaptureEntity(rawText = "Buy milk and coffee", createdAt = now - 3_600_000))
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aSuggestionIsSavedOnlyWhenConfirmed_withTheEditedWords() {
        val viewModel = viewModel()
        viewModel.open(item(ToSortState.Suggested, SuggestedItemType.Task, "Personal"))
        awaitUntil { viewModel.uiState.value.captureId != null }
        assertEquals(SortOneStep.Confirm, viewModel.uiState.value.step)
        assertTrue(runBlocking { database.taskDao().observeAll().first() }.isEmpty())

        viewModel.onTitleChange("Buy oat milk and coffee")
        viewModel.saveSuggested()
        awaitUntil { viewModel.uiState.value.finished != null }

        val task = runBlocking { database.taskDao().observeAll().first() }.single()
        assertEquals("Buy oat milk and coffee", task.title)
        assertEquals(personal, task.spaceId)
        assertEquals(ReviewSortMessage.TaskMade, viewModel.uiState.value.finished)
    }

    @Test
    fun unsure_chooseTheSpaceThenAReminderTime() {
        val viewModel = viewModel()
        viewModel.open(item(ToSortState.NeedsChoice, null, null, lowConfidence = true))
        awaitUntil { viewModel.uiState.value.captureId != null }
        assertEquals(SortOneStep.Type, viewModel.uiState.value.step)

        viewModel.selectSpace(work)
        viewModel.chooseReminder()
        assertEquals(SortOneStep.Time, viewModel.uiState.value.step)
        val at = viewModel.uiState.value.quickTimes.first().at
        viewModel.remindAt(at)
        awaitUntil { viewModel.uiState.value.finished != null }

        val reminder = runBlocking { database.reminderDao().observeAll().first() }.single()
        assertEquals(work, reminder.spaceId)
        assertEquals(at, reminder.dueAt)
    }

    @Test
    fun notNowChangesNothing_decideLaterKeepsItAsTheUsersDecision() {
        val viewModel = viewModel()
        viewModel.open(item(ToSortState.Suggested, SuggestedItemType.Note, null))
        awaitUntil { viewModel.uiState.value.captureId != null }
        viewModel.dismiss()
        assertNull(viewModel.uiState.value.captureId)
        assertNull(runBlocking { database.captureDao().getById(captureId) }?.decidedAt)

        viewModel.open(item(ToSortState.Suggested, SuggestedItemType.Note, null))
        awaitUntil { viewModel.uiState.value.captureId != null }
        viewModel.chooseSomethingElse()
        viewModel.decideLater()
        awaitUntil { viewModel.uiState.value.finished != null }
        val kept = requireNotNull(runBlocking { database.captureDao().getById(captureId) })
        assertEquals(CaptureStatus.Inbox, kept.status)
        assertNotNull(kept.decidedAt)
    }

    @Test
    fun aPassedTimeIsRefused() {
        val viewModel = viewModel()
        viewModel.open(item(ToSortState.NeedsChoice, SuggestedItemType.Reminder, null))
        awaitUntil { viewModel.uiState.value.captureId != null }
        assertEquals(SortOneStep.Time, viewModel.uiState.value.step)
        viewModel.remindAt(now - 60_000)
        idle()
        assertTrue(runBlocking { database.reminderDao().observeAll().first() }.isEmpty())
        assertEquals(SortOneStep.Time, viewModel.uiState.value.step)
    }

    private fun item(state: ToSortState, type: SuggestedItemType?, space: String?, lowConfidence: Boolean = false) = ToSortItem(
        captureId = captureId,
        text = "Buy milk and coffee",
        createdAt = now - 3_600_000,
        state = state,
        suggestedType = type,
        suggestedTitle = "Buy milk and coffee",
        suggestedSpaceName = space,
        lowConfidence = lowConfidence,
    )

    private fun viewModel(): SortOneViewModel {
        val captures = RoomCaptureRepository(database.captureDao())
        val notes = RoomNoteRepository(database.noteDao())
        val tasks = RoomTaskRepository(database.taskDao())
        val reminders = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now })
        val spaces = RoomSpaceRepository(database.spaceDao())
        val transaction = RoomCaptureFinalizationTransaction(database)
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
            now = { now },
        )
        return SortOneViewModel(
            captureRepository = captures,
            spaceRepository = spaces,
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = confirm,
            captureResolution = resolution,
            now = { now },
            zoneId = { zone },
        )
    }

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
