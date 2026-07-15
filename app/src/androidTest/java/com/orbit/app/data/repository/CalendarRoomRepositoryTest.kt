package com.orbit.app.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarDateRange
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarRoomRepositoryTest {
    private lateinit var database: OrbitDatabase
    private lateinit var repository: CalendarRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OrbitDatabase::class.java,
        ).build()
        repository = RoomCalendarRepository(
            noteDao = database.noteDao(),
            taskDao = database.taskDao(),
            reminderDao = database.reminderDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun rangeReturnsFinalizedSourcesOnceAndPreservesCompletedItems() = runBlocking {
        val date = LocalDate.of(2026, 7, 14)
        val range = CalendarDateRange.day(date, ZoneId.of("UTC"))
        val rawCaptureId = database.captureDao().insert(
            CaptureEntity(rawText = "Private source", status = CaptureStatus.Processed),
        )
        database.aiSuggestionHistoryDao().insert(
            AiSuggestionHistoryEntity(
                surface = AiSuggestionSurface.Capture,
                outcome = AiSuggestionOutcome.Accepted,
                analyzerSource = "local",
                captureId = rawCaptureId,
            ),
        )
        database.noteDao().insert(
            NoteEntity(id = 11, title = "Date only", body = "", scheduledDateEpochDay = date.toEpochDay()),
        )
        database.noteDao().insert(
            NoteEntity(id = 12, title = "Timed", body = "", scheduledAt = range.startMillis),
        )
        database.noteDao().insert(
            NoteEntity(
                id = 13,
                title = "Archived",
                body = "",
                archived = true,
                scheduledDateEpochDay = date.toEpochDay(),
            ),
        )
        database.taskDao().insert(
            TaskEntity(
                id = 21,
                title = "Completed",
                status = TaskStatus.Done,
                scheduledDateEpochDay = date.toEpochDay(),
                completedAt = range.startMillis,
            ),
        )
        database.taskDao().insert(
            TaskEntity(id = 22, title = "Archived", status = TaskStatus.Archived, dueAt = range.startMillis),
        )
        database.reminderDao().insert(
            ReminderEntity(id = 31, title = "Reminder", dueAt = range.endMillis - 1, completedAt = range.startMillis),
        )
        database.reminderDao().insert(
            ReminderEntity(id = 32, title = "Next day", dueAt = range.endMillis),
        )
        database.noteDao().insert(
            NoteEntity(id = 14, title = "Deleted", body = "", scheduledDateEpochDay = date.toEpochDay()),
        )
        database.noteDao().deleteById(14)

        val entries = repository.observeRange(range).first()

        assertEquals(
            setOf(
                CalendarEntryId(CalendarItemType.Note, 11),
                CalendarEntryId(CalendarItemType.Note, 12),
                CalendarEntryId(CalendarItemType.Task, 21),
                CalendarEntryId(CalendarItemType.Reminder, 31),
            ),
            entries.map { it.id }.toSet(),
        )
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(TaskStatus.Done, entries.single { it.id.sourceItemId == 21L }.taskStatus)
        assertFalse(entries.any { it.id.sourceItemId in setOf(rawCaptureId, 13L, 14L, 22L, 32L) })
    }

    @Test
    fun calendarRangeQueriesUseSchedulingIndexes() {
        val connection = database.openHelper.writableDatabase
        val notesPlan = connection.query(
            """
            EXPLAIN QUERY PLAN
            SELECT * FROM notes
            WHERE archived = 0 AND (
                (scheduledDateEpochDay >= 1 AND scheduledDateEpochDay < 2)
                OR (scheduledAt >= 1 AND scheduledAt < 2)
            )
            """.trimIndent(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(3))
            }.joinToString(" ")
        }
        val tasksPlan = connection.query(
            """
            EXPLAIN QUERY PLAN
            SELECT * FROM tasks
            WHERE status != 'Archived' AND (
                (scheduledDateEpochDay >= 1 AND scheduledDateEpochDay < 2)
                OR (dueAt >= 1 AND dueAt < 2)
            )
            """.trimIndent(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(3))
            }.joinToString(" ")
        }
        val remindersPlan = connection.query(
            "EXPLAIN QUERY PLAN SELECT * FROM reminders WHERE dueAt >= 1 AND dueAt < 2",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(3))
            }.joinToString(" ")
        }

        assertTrue(notesPlan.contains("index_notes_scheduledDateEpochDay"))
        assertTrue(notesPlan.contains("index_notes_scheduledAt"))
        assertTrue(tasksPlan.contains("index_tasks_scheduledDateEpochDay"))
        assertTrue(tasksPlan.contains("index_tasks_dueAt"))
        assertTrue(remindersPlan.contains("index_reminders_dueAt"))
    }
}
