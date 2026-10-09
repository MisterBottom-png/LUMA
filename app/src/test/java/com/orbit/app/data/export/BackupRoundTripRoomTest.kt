package com.orbit.app.data.export

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.BrainDumpReminderStatus
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.testing.PolicyRecordingScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** export → clean database → restore → compare, on real Room databases. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripRoomTest {
    private lateinit var source: OrbitDatabase
    private lateinit var target: OrbitDatabase
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        source = inMemoryOrbitDatabase()
        target = inMemoryOrbitDatabase()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun everyItemTypeSpacesLabelsAndBrainDumpProgressSurviveARoundTrip() = runBlocking {
        seed(source)
        val original = RoomLocalDataRestoreStore(source).read()

        val json = buildLocalDataExportPayload(RoomLocalDataRestoreStore(source), exportedAt = now)
        val scheduler = PolicyRecordingScheduler { now }
        val restorer = LocalDataRestorer(
            RoomLocalDataRestoreStore(target),
            LocalReminderRestoreReconciler(scheduler, target.reminderDao(), now = { now }),
        )
        val result = restorer.restore(requireNotNull(restorer.prepare(json)))
        val restored = RoomLocalDataRestoreStore(target).read()

        assertTrue(result.remindersReconciled)
        assertEquals(original.spaces, restored.spaces)
        assertEquals(original.captures, restored.captures)
        assertEquals(original.notes, restored.notes)
        assertEquals(original.tasks, restored.tasks)
        assertEquals(original.labels, restored.labels)
        assertEquals(original.noteLabels, restored.noteLabels)
        assertEquals(original.taskLabels, restored.taskLabels)
        assertEquals(original.reminderLabels, restored.reminderLabels)
        assertEquals(original.brainDumpSessions, restored.brainDumpSessions)
        assertEquals(original.brainDumpItems, restored.brainDumpItems)
        assertEquals(1, original.captureSuggestions.size)
        assertEquals(original.captureSuggestions, restored.captureSuggestions)
        assertEquals(
            original.reminders.map { it.copy(notificationWorkId = null, deliveredNotificationAt = null) },
            restored.reminders.map { it.copy(notificationWorkId = null, deliveredNotificationAt = null) },
        )
    }

    @Test
    fun restoreNeverRingsHistoricalRemindersAndArmsFutureOnes() = runBlocking {
        seed(source)
        val json = buildLocalDataExportPayload(RoomLocalDataRestoreStore(source), exportedAt = now)
        val scheduler = PolicyRecordingScheduler { now }
        val restorer = LocalDataRestorer(
            RoomLocalDataRestoreStore(target),
            LocalReminderRestoreReconciler(scheduler, target.reminderDao(), now = { now }),
        )
        restorer.restore(requireNotNull(restorer.prepare(json)))

        assertEquals(listOf(31L to now + 86_400_000L), scheduler.scheduled)
        val past = requireNotNull(target.reminderDao().getById(30))
        assertEquals(now - 86_400_000L, past.deliveredNotificationAt)
        assertNull(past.notificationWorkId)
    }

    @Test
    fun aLegacyUntitledNoteStillProducesARestorableBackup() = runBlocking {
        source.noteDao().insert(NoteEntity(id = 5, title = "  ", body = "\nFirst real line\nmore"))
        source.taskDao().insert(TaskEntity(id = 6, title = "", notes = ""))
        val json = buildLocalDataExportPayload(RoomLocalDataRestoreStore(source), exportedAt = now)

        val decoded = LocalDataBackupCodec.decode(json)

        assertEquals("First real line", decoded.notes.single().title)
        assertEquals("Untitled", decoded.tasks.single().title)
        assertEquals("  ", source.noteDao().getById(5)?.title)
    }

    @Test
    fun anInvalidBackupLeavesCurrentDataUntouched() = runBlocking {
        seed(target)
        val before = RoomLocalDataRestoreStore(target).read()
        val restorer = LocalDataRestorer(
            RoomLocalDataRestoreStore(target),
            LocalReminderRestoreReconciler(PolicyRecordingScheduler { now }, target.reminderDao(), now = { now }),
        )
        val broken = buildLocalDataExportPayload(RoomLocalDataRestoreStore(source), now)
            .replace("\"labels\": []", "\"labels\": [{\"id\": 1}]")

        runCatching { restorer.prepare(broken) }

        assertEquals(before, RoomLocalDataRestoreStore(target).read())
    }

    private suspend fun seed(db: OrbitDatabase) {
        db.spaceDao().insertAll(
            listOf(
                SpaceEntity(id = 1, name = "Home", icon = "home", colorAccent = "#D7798D", sortOrder = 0, createdAt = 1, updatedAt = 1),
                SpaceEntity(id = 2, name = "Old", icon = "work", colorAccent = "#6D7CFF", sortOrder = 1, archived = true, createdAt = 1, updatedAt = 2),
            ),
        )
        db.captureDao().insertAll(
            listOf(
                CaptureEntity(id = 10, rawText = "unsorted thought", createdAt = 3, updatedAt = 3, status = CaptureStatus.Inbox, suggestedType = SuggestedItemType.Task, suggestedSpaceId = 1),
                CaptureEntity(id = 11, rawText = "a\nb", createdAt = 4, updatedAt = 4, status = CaptureStatus.Inbox),
                CaptureEntity(id = 12, rawText = "done one", createdAt = 4, updatedAt = 5, status = CaptureStatus.Processed, linkedItemId = 20),
            ),
        )
        db.brainDumpDao().insertSessionWithItems(
            BrainDumpSessionEntity(captureId = 11, analyzerSource = "local", createdAt = 4, updatedAt = 4),
            listOf(
                BrainDumpItemEntity(id = 1, captureId = 11, sourceKey = "brain:1", ordinal = 1, rawText = "a", suggestedTitle = "a", suggestedType = SuggestedItemType.Note, suggestedSpaceName = "Inbox", confidence = 0.5f, tinyNextAction = "x", reason = "y", outcome = BrainDumpItemOutcome.Saved, createdAt = 4, updatedAt = 4),
                BrainDumpItemEntity(id = 2, captureId = 11, sourceKey = "brain:2", ordinal = 2, rawText = "b", suggestedTitle = "b", suggestedType = SuggestedItemType.Reminder, suggestedSpaceName = "Home", confidence = 0.8f, tinyNextAction = "x", reason = "y", reminderStatus = BrainDumpReminderStatus.NeedsClarification, createdAt = 4, updatedAt = 4),
            ),
        )
        db.noteDao().insertAll(listOf(NoteEntity(id = 20, title = "Recipe", body = "Body", spaceId = 1, createdAt = 5, updatedAt = 6, scheduledDateEpochDay = 20_000)))
        db.taskDao().insertAll(
            listOf(
                TaskEntity(id = 21, title = "Fix tap", notes = "", spaceId = 1, status = TaskStatus.Open, dueAt = now + 1_000, createdAt = 5, updatedAt = 5),
                TaskEntity(id = 22, title = "Done task", status = TaskStatus.Done, completedAt = 9, createdAt = 5, updatedAt = 9),
            ),
        )
        db.reminderDao().insertAll(
            listOf(
                ReminderEntity(id = 30, title = "Yesterday", dueAt = now - 86_400_000L, spaceId = 1, linkedCaptureId = 10, createdAt = 5, updatedAt = 5),
                ReminderEntity(id = 31, title = "Tomorrow", dueAt = now + 86_400_000L, notificationOffsetMinutes = 0, linkedTaskId = 21, createdAt = 5, updatedAt = 5),
            ),
        )
        db.labelDao().insertAll(
            listOf(
                LabelEntity(id = 1, name = "Errands", normalizedName = "errands", createdAt = 1, updatedAt = 1),
                LabelEntity(id = 2, name = "Ödeme", normalizedName = "ödeme", createdAt = 1, updatedAt = 1),
            ),
        )
        db.labelDao().insertNoteLabels(listOf(NoteLabelCrossRef(20, 1)))
        db.labelDao().insertTaskLabels(listOf(TaskLabelCrossRef(21, 2)))
        db.labelDao().insertReminderLabels(listOf(ReminderLabelCrossRef(31, 1)))
        db.captureSuggestionDao().upsert(
            com.orbit.app.data.local.entity.CaptureSuggestionEntity(
                captureId = 10,
                suggestedType = SuggestedItemType.Task,
                suggestedTitle = "Unsorted thought",
                suggestedSpaceName = "Home",
                suggestedLabels = "Errands\nWeekend",
                reminderTimeStatus = "Unspecified",
                confidence = 0.7f,
                analyzerSource = "Local",
                contextDateEpochDay = 20_100,
                createdAt = 3,
                updatedAt = 3,
            ),
        )
    }
}
