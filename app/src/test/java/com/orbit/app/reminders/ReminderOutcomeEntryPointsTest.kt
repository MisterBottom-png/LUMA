package com.orbit.app.reminders

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import com.orbit.app.ui.screens.home.ReminderOutcomeReporter
import com.orbit.app.ui.screens.review.SortUndoToken
import com.orbit.app.ui.screens.review.reminderOutcome
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every place that saves a reminder reports what it achieved, read after the commit:
 * notifications blocked, the reminder channel turned off, and a scheduler that could
 * not arm it are never reported as "Reminder set".
 */
@RunWith(AndroidJUnit4::class)
class ReminderOutcomeEntryPointsTest {
    private lateinit var database: OrbitDatabase
    private val now = Instant.parse("2026-07-14T10:00:00Z").toEpochMilli()
    private val inOneDay = now + 86_400_000L
    private var access = NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
    private var schedulerArms = true
    private val scheduledInTransaction = mutableListOf<Boolean>()

    private val scheduler = object : ReminderScheduler {
        private val policy = PolicyRecordingScheduler { now }
        override fun schedule(reminder: ReminderEntity): String? {
            scheduledInTransaction += database.inTransaction()
            return if (schedulerArms) policy.schedule(reminder) else null
        }
        override fun cancel(reminderId: Long) = Unit
    }

    private val blocked = NotificationAccess(notificationsAllowed = false, reminderChannelEnabled = true)
    private val channelOff = NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = false)

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() = database.close()

    private fun reminders() = RoomReminderRepository(database.reminderDao(), scheduler)
    private fun outcomes() = ReminderSaveOutcomes(database.reminderDao()::getById) { access }

    private fun confirm() = ConfirmCaptureActionUseCase(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = reminders(),
        transaction = RoomCaptureFinalizationTransaction(database),
        labelRepository = RoomLabelRepository(database.labelDao()),
    )

    private fun resolution() = CaptureResolution(
        captureRepository = RoomCaptureRepository(database.captureDao()),
        noteRepository = RoomNoteRepository(database.noteDao()),
        taskRepository = RoomTaskRepository(database.taskDao()),
        reminderRepository = reminders(),
        spaceRepository = RoomSpaceRepository(database.spaceDao()),
        suggestionDao = database.captureSuggestionDao(),
        confirmCaptureAction = confirm(),
        transaction = RoomCaptureFinalizationTransaction(database),
        reminderOutcomes = outcomes(),
        now = { now },
    )

    private suspend fun thoughtWithReminderSuggestion(text: String = "call the bank"): Long {
        val id = database.captureDao().insert(CaptureEntity(rawText = text, status = CaptureStatus.Inbox))
        database.captureSuggestionDao().upsert(
            CaptureSuggestionEntity(
                captureId = id,
                suggestedType = SuggestedItemType.Reminder,
                suggestedTitle = text,
                suggestedReminderAt = inOneDay,
                reminderTimeStatus = "Resolved",
                confidence = 0.9f,
                analyzerSource = "local",
            ),
        )
        return id
    }

    private fun cases(): List<Pair<String, () -> Unit>> = listOf(
        "notifications blocked" to { access = blocked },
        "reminder channel off" to { access = channelOff },
        "scheduler returned null" to { schedulerArms = false },
    )

    private fun expected(case: String) = if (case == "scheduler returned null") {
        ReminderSaveOutcome.SavedNotScheduled
    } else {
        ReminderSaveOutcome.SavedNotificationsBlocked
    }

    private fun reset() {
        access = NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
        schedulerArms = true
    }

    @Test
    fun homeQuickReminderReportsEachOutcome() = runBlocking {
        val allowed = resolution().createQuickReminder(thoughtWithReminderSuggestion(), "Call", inOneDay)
        assertEquals(ReminderSaveOutcome.Saved, allowed)
        cases().forEach { (case, arrange) ->
            reset()
            arrange()
            val outcome = resolution().createQuickReminder(thoughtWithReminderSuggestion(), "Call", inOneDay)
            assertEquals(case, expected(case), outcome)
        }
    }

    @Test
    fun reviewOneTapReportsEachOutcome() = runBlocking {
        assertEquals(
            ReminderSaveOutcome.Saved,
            resolution().acceptSuggestion(thoughtWithReminderSuggestion())?.reminderOutcome,
        )
        cases().forEach { (case, arrange) ->
            reset()
            arrange()
            val accepted = requireNotNull(resolution().acceptSuggestion(thoughtWithReminderSuggestion()))
            assertEquals(case, SuggestedItemType.Reminder, accepted.itemType)
            assertEquals(case, expected(case), accepted.reminderOutcome)
        }
    }

    @Test
    fun acceptAllReportsTheWorstOutcomeOfEverythingItSaved() = runBlocking {
        cases().forEach { (case, arrange) ->
            reset()
            val first = requireNotNull(resolution().acceptSuggestion(thoughtWithReminderSuggestion()))
            arrange()
            val second = requireNotNull(resolution().acceptSuggestion(thoughtWithReminderSuggestion()))
            val token = SortUndoToken.AcceptedMany(
                listOf(first, second).mapIndexed { index, accepted ->
                    SortUndoToken.Accepted(index.toLong(), accepted.itemType, accepted.itemId, accepted.reminderOutcome)
                },
            )
            assertEquals(case, expected(case), token.reminderOutcome)
        }
    }

    @Test
    fun sortSheetReportsEachOutcomeAfterTheCommit() = runBlocking {
        cases().forEach { (case, arrange) ->
            reset()
            arrange()
            val captureId = thoughtWithReminderSuggestion()
            val reminderId = confirm().createReminder(captureId, null, "Call", inOneDay)
            val reporter = ReminderOutcomeReporterHarness(outcomes(), granted = true)
            assertEquals(case, expected(case), reporter.report(reminderId))
        }
        assertFalse("never scheduled inside the transaction", scheduledInTransaction.any { it })
    }

    @Test
    fun brainDumpSingleAndTickedSavesReportEachOutcome() = runBlocking {
        cases().forEach { (case, arrange) ->
            reset()
            arrange()
            val captureId = database.captureDao().insert(CaptureEntity(rawText = "a\nb", status = CaptureStatus.Inbox))
            database.brainDumpDao().insertSession(BrainDumpSessionEntity(captureId = captureId, analyzerSource = "local"))
            database.brainDumpDao().insertItems(
                listOf("a", "b").mapIndexed { index, key ->
                    BrainDumpItemEntity(
                        captureId = captureId,
                        sourceKey = key,
                        ordinal = index,
                        rawText = key,
                        suggestedType = SuggestedItemType.Reminder,
                        suggestedTitle = key,
                        suggestedSpaceName = "Inbox",
                        confidence = 0.9f,
                        tinyNextAction = "",
                        reason = "",
                    )
                },
            )
            val actions = BrainDumpActions(database, scheduler, now = { now })
            val result = actions.saveReminder(captureId, "a", "a", inOneDay, null)
            val reminderId = requireNotNull(result.reminderId)
            val outcome = ReminderOutcomeReporterHarness(outcomes(), granted = true).report(reminderId)
            assertEquals(case, expected(case), outcome)
        }
    }

    @Test
    fun outcomeIsReadOnlyAfterTheUserAnsweredThePermissionQuestion() = runBlocking {
        access = blocked
        val reminderId = confirm().createReminder(thoughtWithReminderSuggestion(), null, "Call", inOneDay)
        val harness = ReminderOutcomeReporterHarness(outcomes(), granted = false) {
            // The user allows notifications in the system dialog.
            access = NotificationAccess(notificationsAllowed = true, reminderChannelEnabled = true)
        }
        assertEquals(ReminderSaveOutcome.Saved, harness.report(reminderId))
        assertTrue(harness.asked)
    }
}

/** Drives [ReminderOutcomeReporter] the way the sort sheet's host does. */
private class ReminderOutcomeReporterHarness(
    outcomes: ReminderSaveOutcomes,
    granted: Boolean,
    onAsk: () -> Unit = {},
) {
    var asked = false
        private set
    private lateinit var reporter: ReminderOutcomeReporter

    init {
        reporter = ReminderOutcomeReporter(
            outcomes = outcomes,
            permissionGranted = { granted },
            requestPermission = {
                asked = true
                onAsk()
                reporter.onPermissionAnswer(granted = true)
            },
        )
    }

    suspend fun report(reminderId: Long): ReminderSaveOutcome = reporter.report(reminderId)
}
