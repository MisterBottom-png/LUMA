package com.orbit.app.ui.screens.calendar

import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAgendaTest {
    private val zoneId = ZoneId.of("Europe/Tallinn")
    private val day = LocalDate.of(2026, 10, 10)

    @Test
    fun today_nowSitsInOrder_andFreeTimeShowsOnlyAfterNow() {
        val entries = listOf(
            timed(1, "Dentist", 9, 30),
            timed(2, "Report", 11, 0),
            timed(3, "Stand-up", 14, 0),
            timed(4, "Parcel", 17, 30),
        )

        val rows = agenda(entries, now = at(10, 15)).rows.map(::label)

        assertEquals(
            listOf("Dentist", "now 10:15", "Report", "free until 14:00", "Stand-up", "free until 17:30", "Parcel"),
            rows,
        )
    }

    @Test
    fun anotherDay_hasNoNowLine_andShortGapsStayQuiet() {
        val entries = listOf(timed(1, "Breakfast", 8, 0), timed(2, "Walk", 9, 0), timed(3, "Dinner", 19, 0))

        val rows = agenda(entries, now = at(10, 0).minusSeconds(86_400)).rows.map(::label)

        assertEquals(listOf("Breakfast", "Walk", "free until 19:00", "Dinner"), rows)
    }

    @Test
    fun aDayWithOnlyUntimedThings_isNotEmpty_andHasNoNowLine() {
        val stretch = CalendarEntry(
            id = CalendarEntryId(CalendarItemType.Task, 9),
            title = "Stretch",
            spaceId = null,
            schedule = CalendarSchedule.DateOnly(day),
        )

        val agenda = agenda(listOf(stretch), now = at(12, 0))

        assertEquals(listOf("Stretch"), agenda.anytime.map { it.title })
        assertTrue(agenda.rows.isEmpty())
        assertTrue(!agenda.isEmpty)
    }

    @Test
    fun anEmptyToday_isEmpty() {
        assertTrue(agenda(emptyList(), now = at(12, 0)).isEmpty)
    }

    private fun agenda(entries: List<CalendarEntry>, now: Instant) = buildCalendarAgenda(
        buildCalendarDayTimeline(entries = entries, selectedDate = day, now = now, zoneId = zoneId),
    )

    private fun label(row: CalendarAgendaRow): String = when (row) {
        is CalendarAgendaRow.Item -> row.entry.title
        is CalendarAgendaRow.Now -> "now %02d:%02d".format(row.minuteOfDay / 60, row.minuteOfDay % 60)
        is CalendarAgendaRow.FreeUntil -> "free until %02d:%02d".format(row.minuteOfDay / 60, row.minuteOfDay % 60)
    }

    private fun at(hour: Int, minute: Int): Instant = day.atTime(hour, minute).atZone(zoneId).toInstant()

    private fun timed(id: Long, title: String, hour: Int, minute: Int) = CalendarEntry(
        id = CalendarEntryId(CalendarItemType.Reminder, id),
        title = title,
        spaceId = null,
        schedule = CalendarSchedule.Timed(at(hour, minute)),
    )
}
