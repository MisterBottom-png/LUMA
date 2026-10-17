package com.orbit.app.limestone

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.repository.RoomCalendarRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.domain.calendar.CalendarDateRange
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import com.orbit.app.testing.inMemoryOrbitDatabase
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.screens.item.ItemSchedule
import com.orbit.app.ui.screens.item.ItemScheduleActions
import com.orbit.app.ui.screens.item.ScheduleOutcome
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * B7 ("notes and tasks with both a date and a time drop off Calendar") — P0 result:
 * UNREPRODUCED through the Room-backed Calendar.
 *
 * A date with a time is stored as one instant (`NoteEntity.scheduledAt`,
 * `TaskEntity.dueAt`) and shows as [CalendarSchedule.Timed] on that day; a date-only item
 * shows as [CalendarSchedule.DateOnly], never as a 00:00 timed entry. The only way an item
 * leaves Calendar is a row holding BOTH `scheduledDateEpochDay` and a time, which the
 * repositories refuse and the projection skips; both guards are pinned here.
 *
 * Later phases (the Calendar rework) must keep every assertion here green; if B7 is
 * re-reported, a failing case should be added beside these, not instead of them.
 */
@RunWith(AndroidJUnit4::class)
class P0CalendarDateTimeCharacterizationTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val day: LocalDate = LocalDate.of(2026, 7, 14)
    private val noteAt: Instant = Instant.parse("2026-07-14T09:30:00Z")
    private val taskAt: Instant = Instant.parse("2026-07-14T18:45:00Z")
    private lateinit var database: OrbitDatabase
    private lateinit var notes: RoomNoteRepository
    private lateinit var tasks: RoomTaskRepository
    private lateinit var calendar: RoomCalendarRepository

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        notes = RoomNoteRepository(database.noteDao())
        tasks = RoomTaskRepository(database.taskDao())
        calendar = RoomCalendarRepository(
            noteDao = database.noteDao(),
            taskDao = database.taskDao(),
            reminderDao = database.reminderDao(),
            zoneId = { zone },
            clock = { noteAt.toEpochMilli() },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun timedNoteAndTimedTaskAreOnTheirDayAtTheirInstantAndDateOnlyNoteStaysDateOnly() = runBlocking {
        val timedNoteId = notes.insert(NoteEntity(title = "Pick up the parcel", body = "", scheduledAt = noteAt.toEpochMilli()))
        val timedTaskId = tasks.insert(TaskEntity(title = "Water the plants", dueAt = taskAt.toEpochMilli()))
        val dateOnlyNoteId = notes.insert(
            NoteEntity(title = "Library books due", body = "", scheduledDateEpochDay = day.toEpochDay()),
        )

        val entries = dayEntries()

        assertEquals(3, entries.size)
        assertEquals(
            CalendarSchedule.Timed(noteAt),
            entries.single { it.id.sourceType == CalendarItemType.Note && it.id.sourceItemId == timedNoteId }.schedule,
        )
        assertEquals(
            CalendarSchedule.Timed(taskAt),
            entries.single { it.id.sourceType == CalendarItemType.Task && it.id.sourceItemId == timedTaskId }.schedule,
        )
        val dateOnly = entries.single { it.id.sourceType == CalendarItemType.Note && it.id.sourceItemId == dateOnlyNoteId }
        assertEquals(CalendarSchedule.DateOnly(day), dateOnly.schedule)
        // Never a midnight timed entry for a date-only item.
        assertFalse(entries.any { it.schedule == CalendarSchedule.Timed(day.atStartOfDay(zone).toInstant()) })
    }

    @Test
    fun addingATimeToADateOnlyNoteAndTaskThroughTheEditPathKeepsThemOnCalendarAsTimed() = runBlocking {
        val noteId = notes.insert(NoteEntity(title = "Library books due", body = "", scheduledDateEpochDay = day.toEpochDay()))
        val taskId = tasks.insert(TaskEntity(title = "Water the plants", scheduledDateEpochDay = day.toEpochDay()))
        val actions = ItemScheduleActions(notes, tasks) { noteAt.toEpochMilli() }

        assertTrue(actions.apply(ItemDetailType.Note, noteId, ItemSchedule.Timed(noteAt.toEpochMilli())) is ScheduleOutcome.Applied)
        assertTrue(actions.apply(ItemDetailType.Task, taskId, ItemSchedule.Timed(taskAt.toEpochMilli())) is ScheduleOutcome.Applied)

        // The edit replaces the day with one instant; it never leaves both set.
        val storedNote = requireNotNull(notes.getById(noteId))
        assertEquals(null, storedNote.scheduledDateEpochDay)
        assertEquals(noteAt.toEpochMilli(), storedNote.scheduledAt)
        val storedTask = requireNotNull(tasks.getById(taskId))
        assertEquals(null, storedTask.scheduledDateEpochDay)
        assertEquals(taskAt.toEpochMilli(), storedTask.dueAt)

        val entries = dayEntries()
        assertEquals(CalendarSchedule.Timed(noteAt), entries.single { it.id.sourceItemId == noteId && it.id.sourceType == CalendarItemType.Note }.schedule)
        assertEquals(CalendarSchedule.Timed(taskAt), entries.single { it.id.sourceItemId == taskId && it.id.sourceType == CalendarItemType.Task }.schedule)
    }

    @Test
    fun theRepositoriesRefuseANoteOrTaskWithBothADayAndATime() = runBlocking {
        assertRejected {
            notes.insert(
                NoteEntity(title = "Both", body = "", scheduledDateEpochDay = day.toEpochDay(), scheduledAt = noteAt.toEpochMilli()),
            )
        }
        assertRejected {
            tasks.insert(TaskEntity(title = "Both", scheduledDateEpochDay = day.toEpochDay(), dueAt = taskAt.toEpochMilli()))
        }
        assertTrue(database.noteDao().observeAll().first().isEmpty())
        assertTrue(database.taskDao().observeAll().first().isEmpty())
    }

    @Test
    fun aRowWrittenPastTheRepositoryWithBothADayAndATimeIsLeftOffCalendar() = runBlocking {
        database.noteDao().insert(
            NoteEntity(title = "Both", body = "", scheduledDateEpochDay = day.toEpochDay(), scheduledAt = noteAt.toEpochMilli()),
        )
        database.taskDao().insert(
            TaskEntity(title = "Both", scheduledDateEpochDay = day.toEpochDay(), dueAt = taskAt.toEpochMilli()),
        )

        // The SQL range query matches them; the projection drops them.
        assertEquals(1, database.noteDao().observeCalendarRange(range().startEpochDay, range().endEpochDay, range().startMillis, range().endMillis).first().size)
        assertTrue(dayEntries().isEmpty())
    }

    private fun range() = CalendarDateRange.day(day, zone)

    private suspend fun dayEntries(): List<CalendarEntry> = calendar.observeRange(range()).first()

    private suspend fun assertRejected(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected: requireValidSchedule().
        }
    }
}
