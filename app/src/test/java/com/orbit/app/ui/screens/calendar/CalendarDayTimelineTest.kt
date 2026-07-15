package com.orbit.app.ui.screens.calendar

import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarDayTimelineTest {
    private val zoneId = ZoneId.of("Europe/Tallinn")
    private val selectedDate = LocalDate.of(2026, 7, 15)
    private val midday = selectedDate.atTime(12, 0).atZone(zoneId).toInstant()

    @Test
    fun noItemsOnAnotherDay_usesCalmEmptyState() {
        val timeline = timeline(emptyList(), now = midday.minusSeconds(86_400))

        assertEquals(listOf(CalendarDayRow.EmptyDay), timeline.rows)
        assertEquals(0, timeline.recommendedScrollIndex)
    }

    @Test
    fun dateOnlyAndTimedItems_areSeparatedAndSorted() {
        val entries = listOf(
            dateOnly(id = 1, title = "Later title"),
            timed(id = 2, title = "Afternoon", hour = 15, minute = 30),
            dateOnly(id = 3, title = "Earlier title"),
            timed(id = 4, title = "Morning", hour = 8, minute = 15),
        )

        val timeline = timeline(entries, now = midday.minusSeconds(86_400))
        val anyTimeTitles = timeline.rows.filterIsInstance<CalendarDayRow.AnyTimeItem>().map { it.entry.title }
        val timedMinutes = timeline.rows.filterIsInstance<CalendarDayRow.TimedItems>().map { it.group.minuteOfDay }

        assertEquals(listOf("Earlier title", "Later title"), anyTimeTitles)
        assertEquals(listOf(8 * 60 + 15, 15 * 60 + 30), timedMinutes)
    }

    @Test
    fun overlappingItems_stackInOneDeterministicTimeGroup() {
        val timeline = timeline(
            listOf(
                timed(id = 1, title = "Second title", hour = 9, minute = 0),
                timed(id = 2, title = "First title", hour = 9, minute = 0),
            ),
            now = midday.minusSeconds(86_400),
        )
        val group = timeline.rows.filterIsInstance<CalendarDayRow.TimedItems>().single().group

        assertEquals(9 * 60, group.minuteOfDay)
        assertEquals(listOf("First title", "Second title"), group.entries.map { it.title })
    }

    @Test
    fun longTitlesRemainIntactInPresentationModel() {
        val title = "A long scheduled title that the compact card may wrap or truncate visually without changing data"
        val timeline = timeline(listOf(dateOnly(id = 1, title = title)), now = midday.minusSeconds(86_400))

        assertEquals(title, timeline.rows.filterIsInstance<CalendarDayRow.AnyTimeItem>().single().entry.title)
    }

    @Test
    fun manyItems_remainIndividuallyPresent() {
        val entries = (0 until 100).map { index ->
            timed(
                id = index.toLong(),
                title = "Scheduled item $index",
                hour = index / 6,
                minute = (index % 6) * 10,
            )
        }

        val renderedEntries = timeline(entries, now = midday.minusSeconds(86_400))
            .rows
            .filterIsInstance<CalendarDayRow.TimedItems>()
            .sumOf { it.group.entries.size }

        assertEquals(100, renderedEntries)
    }

    @Test
    fun completedItems_remainVisible() {
        val completed = dateOnly(
            id = 1,
            title = "Completed task",
            type = CalendarItemType.Task,
            taskStatus = TaskStatus.Done,
            completedAt = midday.toEpochMilli(),
        )

        val visible = timeline(listOf(completed), now = midday.minusSeconds(86_400))
            .rows
            .filterIsInstance<CalendarDayRow.AnyTimeItem>()
            .single()
            .entry

        assertEquals(TaskStatus.Done, visible.taskStatus)
        assertEquals(completed.completedAt, visible.completedAt)
    }

    @Test
    fun currentTimeIndicator_appearsOnlyForTodayAndSetsInitialPosition() {
        val todayTimeline = timeline(
            listOf(timed(id = 1, title = "Morning", hour = 8, minute = 0)),
            now = midday,
        )
        val otherDayTimeline = timeline(
            listOf(timed(id = 1, title = "Morning", hour = 8, minute = 0)),
            now = midday.plusSeconds(86_400),
        )

        val currentIndex = todayTimeline.rows.indexOfFirst { it is CalendarDayRow.CurrentTime }
        assertTrue(currentIndex >= 0)
        assertEquals(currentIndex, todayTimeline.recommendedScrollIndex)
        assertFalse(otherDayTimeline.rows.any { it is CalendarDayRow.CurrentTime })
    }

    @Test
    fun midnightBoundaries_excludeNeighborsAndKeepBothDayEdges() {
        val start = selectedDate.atStartOfDay(zoneId)
        val entries = listOf(
            timedAt(1, "Previous", start.minusMinutes(1).toInstant()),
            timedAt(2, "First", start.plusMinutes(1).toInstant()),
            timedAt(3, "Last", start.plusHours(23).plusMinutes(59).toInstant()),
            timedAt(4, "Next", start.plusDays(1).toInstant()),
        )

        val titles = timeline(entries, now = midday.minusSeconds(86_400))
            .rows
            .filterIsInstance<CalendarDayRow.TimedItems>()
            .flatMap { it.group.entries }
            .map { it.title }

        assertEquals(listOf("First", "Last"), titles)
    }

    private fun timeline(entries: List<CalendarEntry>, now: Instant) = buildCalendarDayTimeline(
        entries = entries,
        selectedDate = selectedDate,
        now = now,
        zoneId = zoneId,
    )

    private fun dateOnly(
        id: Long,
        title: String,
        type: CalendarItemType = CalendarItemType.Note,
        taskStatus: TaskStatus? = null,
        completedAt: Long? = null,
    ) = CalendarEntry(
        id = CalendarEntryId(type, id),
        title = title,
        spaceId = null,
        schedule = CalendarSchedule.DateOnly(selectedDate),
        taskStatus = taskStatus,
        completedAt = completedAt,
    )

    private fun timed(id: Long, title: String, hour: Int, minute: Int) = timedAt(
        id = id,
        title = title,
        instant = selectedDate.atTime(hour, minute).atZone(zoneId).toInstant(),
    )

    private fun timedAt(id: Long, title: String, instant: Instant) = CalendarEntry(
        id = CalendarEntryId(CalendarItemType.Reminder, id),
        title = title,
        spaceId = null,
        schedule = CalendarSchedule.Timed(instant),
    )
}
