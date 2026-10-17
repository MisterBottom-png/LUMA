package com.orbit.app.limestone

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.capture.AutoFiler
import com.orbit.app.domain.capture.CaptureDecision
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.capture.decide
import com.orbit.app.domain.capture.toAnalysis
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two current automatic-filing behaviors that P3 must change. Setup mirrors
 * `AutoFilerRoomTest` (in-memory Room, real [CaptureInbox], [CaptureResolution] and
 * [AutoFiler]) with a fixed suggester.
 */
@RunWith(AndroidJUnit4::class)
class P0AutoFilingCharacterizationTest {
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private lateinit var database: OrbitDatabase

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        runBlocking {
            database.spaceDao().insert(SpaceEntity(name = "Home", icon = "home", colorAccent = "#000000", sortOrder = 0))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Unsafe today; P3 (B1) must refuse Undo once the item was edited or completed.
     *
     * [CaptureResolution.undo] deletes the filed item by id with no check of what happened
     * to it since filing, so a task the user already completed (and renamed) is deleted,
     * and the thought goes back to To sort as decided.
     */
    @Test
    fun undoAfterAutomaticFilingDeletesATaskThatWasCompletedAndEditedSince() = runBlocking {
        val captureId = analysed(Groceries, highConfidenceTask(Groceries))
        assertTrue(autoFiler().apply(captureId))
        val filed = requireNotNull(database.captureDao().getById(captureId))
        assertEquals(CaptureOutcome.Filed, filed.outcome)
        val taskId = requireNotNull(filed.linkedItemId)

        // The user completes and renames the task afterwards.
        val tasks = RoomTaskRepository(database.taskDao())
        val task = requireNotNull(tasks.getById(taskId))
        tasks.update(task.copy(title = "Buy milk and eggs", status = TaskStatus.Done, completedAt = now + 60_000, updatedAt = now + 60_000))

        resolution().undo(captureId, SuggestedItemType.Task, taskId)

        // Unsafe today: the completed, edited task is gone.
        assertNull(database.taskDao().getById(taskId))
        val capture = requireNotNull(database.captureDao().getById(captureId))
        assertEquals(CaptureStatus.Inbox, capture.status)
        assertEquals(CaptureOutcome.Saved, capture.outcome)
        assertNull(capture.linkedItemId)
        assertNotNull(capture.decidedAt)
    }

    /**
     * Documents a P0 finding; P3 fixes it and flips this assertion.
     *
     * `CaptureSuggestionEntity.toAnalysis` does not carry `analyzerFailed` (the suggestion
     * row has no such column), and [AutoFiler.apply] decides from the stored suggestion.
     * So a high-confidence local fallback after a Gemini failure
     * (`analyzerSource = GeminiFallback`, `analyzerFailed = true`) is filed automatically,
     * although [decide] keeps a failed analysis and the design's receipt for that case reads
     * "Suggestions weren't available. You can sort it yourself."
     */
    @Test
    fun aHighConfidenceGeminiFallbackTaskIsFiledAutomaticallyDespiteAnalyzerFailed() = runBlocking {
        val failed = highConfidenceTask(Groceries).copy(
            analyzerFailed = true,
            analyzerSource = CaptureAnalyzerSource.GeminiFallback,
        )

        // The guard exists: the in-memory analysis is kept for the user.
        assertEquals(CaptureDecision.Keep, decide(failed, hasSpace = true, now = now))

        val captureId = analysed(Groceries, failed)
        val stored = requireNotNull(database.captureSuggestionDao().getByCaptureId(captureId))
        assertEquals(CaptureAnalyzerSource.GeminiFallback.name, stored.analyzerSource)
        // ...but the stored suggestion loses the flag on the way back.
        assertFalse(stored.toAnalysis(Groceries).analyzerFailed)

        // Bypassed today: the thought is filed as a task.
        assertTrue(autoFiler().apply(captureId))
        val capture = requireNotNull(database.captureDao().getById(captureId))
        assertEquals(CaptureStatus.Processed, capture.status)
        assertEquals(CaptureOutcome.Filed, capture.outcome)
        val task = database.taskDao().observeAll().first().single()
        assertEquals(task.id, capture.linkedItemId)
    }

    private fun highConfidenceTask(text: String) = CaptureAnalysis(
        rawText = text,
        suggestedType = SuggestedItemType.Task,
        suggestedSpaceName = "Home",
        suggestedTitle = "Buy milk",
        suggestedNextAction = "",
        relatedTopics = listOf("Home"),
        reminderPossible = false,
        confidence = 0.9f,
    )

    /** Saves [text] and stores [analysis] through the app's capture inbox, stopping before act / ask / keep. */
    private suspend fun analysed(text: String, analysis: CaptureAnalysis): Long {
        val inbox = CaptureInbox(
            captureRepository = RoomCaptureRepository(database.captureDao()),
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
            spaceRepository = RoomSpaceRepository(database.spaceDao()),
            suggester = { _, _ -> analysis },
            // Analysis runs only when called below.
            scope = CoroutineScope(SupervisorJob()).also { it.cancel() },
            now = { now },
            transaction = RoomCaptureFinalizationTransaction(database),
        )
        val id = inbox.save(text)
        assertNotNull(inbox.analyze(id))
        return id
    }

    private fun resolution(): CaptureResolution {
        val captures = RoomCaptureRepository(database.captureDao())
        val notes = RoomNoteRepository(database.noteDao())
        val tasks = RoomTaskRepository(database.taskDao())
        val reminders = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now })
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
                NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
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
    )

    private companion object {
        const val Groceries = "Buy milk tomorrow"
    }
}
