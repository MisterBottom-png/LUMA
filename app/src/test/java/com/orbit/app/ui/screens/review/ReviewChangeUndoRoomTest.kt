package com.orbit.app.ui.screens.review

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import com.orbit.app.ui.screens.item.ItemScheduleActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Review's "To tomorrow", carry-forward and thought actions can be undone, and only as they left things. */
@RunWith(AndroidJUnit4::class)
class ReviewChangeUndoRoomTest {
    private lateinit var database: OrbitDatabase
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 15)
    private val now = Instant.parse("2026-07-15T09:00:00Z").toEpochMilli()
    private val scheduler = PolicyRecordingScheduler { now }

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() = database.close()

    private val tasks get() = RoomTaskRepository(database.taskDao())
    private val reminders get() = RoomReminderRepository(database.reminderDao(), scheduler)
    private val captures get() = RoomCaptureRepository(database.captureDao())
    private val brainDumps get() = RoomBrainDumpRepository(database.brainDumpDao())

    private fun actions(withBrainDump: Boolean = false) = ReviewActions(
        captureRepository = captures,
        taskRepository = tasks,
        reminderRepository = reminders,
        confirmCaptureAction = ConfirmCaptureActionUseCase(
            captureRepository = captures,
            noteRepository = RoomNoteRepository(database.noteDao()),
            taskRepository = tasks,
            reminderRepository = reminders,
            transaction = RoomCaptureFinalizationTransaction(database),
        ),
        scheduleActions = ItemScheduleActions(RoomNoteRepository(database.noteDao()), tasks) { now },
        brainDumpActions = if (withBrainDump) BrainDumpActions(database, scheduler, zone) { now } else null,
        now = { now },
        zoneId = zone,
        today = { today },
    )

    private fun undo() = ReviewChangeUndo(tasks, reminders, captures, brainDumps)

    @Test
    fun movingEverythingToTomorrowIsOneUndo() = runBlocking {
        val yesterday = today.minusDays(1).toEpochDay()
        val taskId = tasks.insert(TaskEntity(title = "Water plants", scheduledDateEpochDay = yesterday))
        val reminderAt = Instant.parse("2026-07-14T18:30:00Z").toEpochMilli()
        val reminderId = reminders.insert(ReminderEntity(title = "Call the bank", dueAt = reminderAt))
        val before = tasks.getById(taskId)
        val items = listOf(
            ReviewItem(taskId, ReviewItemType.Task, "Water plants", 0L),
            ReviewItem(reminderId, ReviewItemType.Reminder, "Call the bank", reminderAt),
        )
        val actions = actions()
        val changeUndo = undo()

        val token = changeUndo.record(ReviewChangeKind.MovedTomorrow, ReviewChangeTargets(listOf(taskId), listOf(reminderId))) {
            items.forEach { actions.carryForwardTomorrow(it) }
        }
        assertEquals(2, token.count)
        assertEquals(today.plusDays(1).toEpochDay(), tasks.getById(taskId)?.scheduledDateEpochDay)
        assertEquals(reminderAt + 2 * 86_400_000L, reminders.getById(reminderId)?.dueAt)

        assertTrue(changeUndo.undo(token.operationId))
        assertEquals(before, tasks.getById(taskId))
        assertEquals(reminderAt, reminders.getById(reminderId)?.dueAt)
    }

    @Test
    fun undoIsRefusedOnceTheItemChangedAgainOrTheUndoExpired() = runBlocking {
        val taskId = tasks.insert(TaskEntity(title = "Renew card", scheduledDateEpochDay = today.minusDays(2).toEpochDay()))
        val item = ReviewItem(taskId, ReviewItemType.Task, "Renew card", 0L)
        val actions = actions()
        val changeUndo = undo()

        val first = changeUndo.record(ReviewChangeKind.MovedTomorrow, ReviewChangeTargets(taskIds = listOf(taskId))) {
            actions.carryForwardTomorrow(item)
        }
        tasks.update(requireNotNull(tasks.getById(taskId)).copy(title = "Renew card today"))
        assertFalse(changeUndo.undo(first.operationId))
        assertEquals("Renew card today", tasks.getById(taskId)?.title)

        val second = changeUndo.record(ReviewChangeKind.Completed, ReviewChangeTargets(taskIds = listOf(taskId))) {
            actions.completeCarryForward(item)
        }
        changeUndo.expire(second.operationId)
        assertFalse(changeUndo.undo(second.operationId))
        assertEquals(TaskStatus.Done, tasks.getById(taskId)?.status)
    }

    @Test
    fun keepingAThoughtForSomedayIsUndoneWithoutLeavingTheTask() = runBlocking {
        val captureId = captures.insert(CaptureEntity(rawText = "Learn to bake bread"))
        val loop = ReviewLoop(captureId, ReviewLoopType.Capture, "Learn to bake bread", 0L)
        val changeUndo = undo()

        val token = changeUndo.record(ReviewChangeKind.ThoughtKept, ReviewChangeTargets(captureIds = listOf(captureId))) {
            actions().confirmCapture(loop)
        }
        assertEquals(1, tasks.observeAll().first().size)

        assertTrue(changeUndo.undo(token.operationId))
        assertEquals(emptyList<TaskEntity>(), tasks.observeAll().first())
        val restored = captures.getById(captureId)
        assertEquals(CaptureStatus.Inbox, restored?.status)
        assertNull(restored?.linkedItemId)
    }

    @Test
    fun dismissingABrainDumpThoughtIsUndoneWithItsSession() = runBlocking {
        val captureId = captures.insert(CaptureEntity(rawText = "Groceries, call the plumber"))
        brainDumps.createSession(
            BrainDumpSessionEntity(captureId = captureId, analyzerSource = "Local"),
            listOf(
                BrainDumpItemEntity(
                    captureId = captureId,
                    sourceKey = "a",
                    ordinal = 0,
                    rawText = "Groceries",
                    suggestedTitle = "Groceries",
                    suggestedType = SuggestedItemType.Task,
                    suggestedSpaceName = "",
                    confidence = 0.8f,
                    tinyNextAction = "",
                    reason = "",
                ),
            ),
        )
        val loop = ReviewLoop(captureId, ReviewLoopType.Capture, "Groceries", 0L)
        val changeUndo = undo()

        val token = changeUndo.record(ReviewChangeKind.ThoughtLetGo, ReviewChangeTargets(captureIds = listOf(captureId))) {
            actions(withBrainDump = true).archive(loop)
        }
        assertEquals(CaptureStatus.Archived, captures.getById(captureId)?.status)
        assertNull(brainDumps.getSession(captureId))

        assertTrue(changeUndo.undo(token.operationId))
        assertEquals(CaptureStatus.Inbox, captures.getById(captureId)?.status)
        assertNotNull(brainDumps.getSession(captureId))
        assertEquals(listOf("Groceries"), brainDumps.getSession(captureId)?.items?.map { it.rawText })
    }

    @Test
    fun earlierDaysCannotBeChosenForCarryForward() {
        assertFalse(carryForwardDayAllowed(today.minusDays(1).toEpochDay(), today))
        assertTrue(carryForwardDayAllowed(today.toEpochDay(), today))
        assertTrue(carryForwardDayAllowed(today.plusDays(3).toEpochDay(), today))
    }
}
