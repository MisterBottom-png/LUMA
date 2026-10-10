package com.orbit.app.domain.capture

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.reminders.NotificationAccess
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import com.orbit.app.ui.screens.home.initialBrainDumpDraft
import com.orbit.app.ui.screens.home.initialTaskDue
import com.orbit.app.ui.screens.review.ToSortState
import com.orbit.app.ui.screens.review.buildToSort
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Buy milk tomorrow" becomes the same task whichever way it is sorted: one tap in
 * Review, the sort sheet, or a Brain Dump draft. The task has a day and no time.
 */
@RunWith(AndroidJUnit4::class)
class TaskDateParityTest {
    private lateinit var database: OrbitDatabase
    private val zone = ZoneId.systemDefault()
    private val now = LocalDate.of(2026, 7, 14).atTime(10, 0).atZone(zone).toInstant()
    private val tomorrow = LocalDate.of(2026, 7, 15).toEpochDay()
    private val inputs = mapOf(
        Locale.ENGLISH to "Buy milk tomorrow",
        Locale.forLanguageTag("et") to "Osta homme piima",
        Locale.forLanguageTag("ru") to "Купить молоко завтра",
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { it.cancel() }

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() = database.close()

    private fun analyzer(locale: Locale) = LocalRulesCaptureAnalyzer(now = { now }, zoneId = { zone }, locale = { locale })

    private fun inbox(locale: Locale) = CaptureInbox(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        suggestionDao = database.captureSuggestionDao(),
        brainDumpRepository = RoomBrainDumpRepository(database.brainDumpDao()),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggester = { text, _ -> analyzer(locale).analyze(text) },
        scope = scope,
        now = { now.toEpochMilli() },
    )

    private fun confirm() = ConfirmCaptureActionUseCase(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now.toEpochMilli() }),
        transaction = RoomCaptureFinalizationTransaction(database),
        labelRepository = RoomLabelRepository(database.labelDao()),
    )

    private fun resolution() = CaptureResolution(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = RoomReminderRepository(database.reminderDao(), PolicyRecordingScheduler { now.toEpochMilli() }),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggestionDao = database.captureSuggestionDao(),
        confirmCaptureAction = confirm(),
        transaction = RoomCaptureFinalizationTransaction(database),
        reminderOutcomes = ReminderSaveOutcomes(database.reminderDao()::getById) { NotificationAccess(true, true) },
        now = { now.toEpochMilli() },
    )

    private suspend fun task(id: Long): TaskEntity = requireNotNull(database.taskDao().getById(id))

    private fun assertDayOnly(label: String, task: TaskEntity) {
        assertEquals(label, tomorrow, task.scheduledDateEpochDay)
        assertNull(label, task.dueAt)
    }

    @Test
    fun oneTapAndTheSheetCreateTheSameDateOnlyTaskInEveryLanguage() = runBlocking {
        inputs.forEach { (locale, text) ->
            val label = "${locale.language}: $text"
            val inbox = inbox(locale)

            val oneTapId = inbox.save(text)
            inbox.analyze(oneTapId)
            val stored = requireNotNull(database.captureSuggestionDao().getByCaptureId(oneTapId))
            assertEquals(label, SuggestedItemType.Task, stored.suggestedType)
            assertNull("$label: no 23:59 placeholder is stored", stored.suggestedReminderAt)
            val accepted = requireNotNull(resolution().acceptSuggestion(oneTapId))
            assertDayOnly("$label (one tap)", task(accepted.itemId))

            val sheetId = inbox.save(text)
            inbox.analyze(sheetId)
            val analysis = requireNotNull(database.captureSuggestionDao().getByCaptureId(sheetId)).toAnalysis(text)
            val due = initialTaskDue(analysis, calendarDateContext = null)
            val sheetTaskId = confirm().createTask(sheetId, null, analysis.suggestedTitle, due.at, due.dayEpochDay)
            assertDayOnly("$label (sheet)", task(sheetTaskId))
        }
    }

    @Test
    fun aBrainDumpTaskDraftGetsTheSameDay() = runBlocking {
        inputs.forEach { (locale, text) ->
            val label = "${locale.language}: $text"
            val inbox = inbox(locale)
            val captureId = inbox.save("$text\n$text")
            inbox.analyze(captureId)
            val stored = requireNotNull(database.brainDumpDao().getItems(captureId).firstOrNull())
            // Loaded the way the sort sheet loads a stored Brain Dump row.
            val item = com.orbit.app.domain.analyzer.BrainDumpSuggestion(
                    id = stored.sourceKey,
                    rawText = stored.rawText,
                    title = stored.suggestedTitle,
                    suggestedType = stored.suggestedType,
                    suggestedSpaceName = stored.suggestedSpaceName,
                    confidence = stored.confidence,
                    tinyNextAction = stored.tinyNextAction,
                    reason = stored.reason,
                    suggestedReminderAt = stored.reminderTime(zone),
                    taskDateEpochDay = stored.taskDateEpochDay(zone),
                )
            assertEquals(label, SuggestedItemType.Task, item.suggestedType)
            val draft = initialBrainDumpDraft(item, emptyList())
            assertNull(label, draft.scheduledAt)
            BrainDumpActions(database, PolicyRecordingScheduler { now.toEpochMilli() }, zoneId = zone)
                .saveTask(captureId, item.id, draft.title, dueAt = draft.scheduledAt, spaceId = null, scheduledDateEpochDay = draft.scheduledDateEpochDay)
            val saved = database.taskDao().observeAll().first().last()
            assertDayOnly("$label (Brain Dump)", saved)
        }
    }

    @Test
    fun anOlderSuggestionWithA2359PlaceholderStillCreatesADateOnlyTask() = runBlocking {
        val captureId = database.captureDao().insert(CaptureEntity(rawText = "Buy milk tomorrow", status = CaptureStatus.Inbox))
        val placeholder = LocalDate.ofEpochDay(tomorrow).atTime(23, 59).atZone(zone).toInstant().toEpochMilli()
        database.captureSuggestionDao().upsert(
            CaptureSuggestionEntity(
                captureId = captureId,
                suggestedType = SuggestedItemType.Task,
                suggestedTitle = "Buy milk",
                suggestedReminderAt = placeholder,
                confidence = 0.8f,
                analyzerSource = "Local",
            ),
        )
        val accepted = requireNotNull(resolution().acceptSuggestion(captureId))
        assertDayOnly("one tap", task(accepted.itemId))

        val analysis = requireNotNull(database.captureSuggestionDao().getByCaptureId(captureId)).toAnalysis("Buy milk tomorrow")
        assertNull(analysis.suggestedReminderAt)
        assertEquals(tomorrow, initialTaskDue(analysis, null).dayEpochDay)
    }

    @Test
    fun noNewTaskIsTimedAt2359() = runBlocking {
        inputs.forEach { (locale, text) ->
            val inbox = inbox(locale)
            val id = inbox.save(text)
            inbox.analyze(id)
            resolution().acceptSuggestion(id)
        }
        database.taskDao().observeAll().first().forEach { task ->
            val time = task.dueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            assertFalse("${task.title} is timed 23:59", time == LocalTime.of(23, 59))
        }
    }

    @Test
    fun aLowConfidenceSuggestionNeedsAChoiceAndIsLeftOutOfAcceptAll() = runBlocking {
        val captureId = database.captureDao().insert(
            CaptureEntity(rawText = "hmm", status = CaptureStatus.Inbox, createdAt = 0L),
        )
        val suggestion = CaptureSuggestionEntity(
            captureId = captureId,
            suggestedType = SuggestedItemType.Note,
            suggestedTitle = "hmm",
            confidence = 0.4f,
            analyzerSource = "Local",
        )
        database.captureSuggestionDao().upsert(suggestion)

        val row = buildToSort(
            captures = listOfNotNull(database.captureDao().getById(captureId)),
            suggestions = mapOf(captureId to suggestion),
            brainDumpPending = emptyMap(),
            brainDumpCaptureIds = emptySet(),
            now = now.toEpochMilli(),
        ).single()
        assertEquals(ToSortState.NeedsChoice, row.state)
        assertTrue(row.lowConfidence)
        // "Accept all" takes only Suggested rows, and one tap refuses it too.
        assertNull(resolution().acceptSuggestion(captureId))
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(captureId)?.status)
    }
}
