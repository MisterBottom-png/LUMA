package com.orbit.app.ui.screens.calendar

import com.orbit.app.domain.calendar.CalendarEntry
import java.time.Instant

/**
 * A day as a short list instead of a 24-hour grid: things with no time first, then the
 * timed things in order, a "now" marker on today, and a quiet "Free until 14:00" where a
 * long stretch has nothing in it. Most personal days hold only a few items, so empty hours
 * are not drawn.
 */
data class CalendarAgenda(
    val anytime: List<CalendarEntry>,
    val rows: List<CalendarAgendaRow>,
) {
    val isEmpty: Boolean get() = anytime.isEmpty() && rows.none { it is CalendarAgendaRow.Item }
}

sealed interface CalendarAgendaRow {
    data class Item(val minuteOfDay: Int, val start: Instant, val entry: CalendarEntry) : CalendarAgendaRow
    data class Now(val minuteOfDay: Int, val instant: Instant) : CalendarAgendaRow
    data class FreeUntil(val minuteOfDay: Int, val until: Instant) : CalendarAgendaRow
}

/** A gap at least this long between two things reads "Free until …". */
internal const val CalendarFreeGapMinutes = 120

fun buildCalendarAgenda(
    timeline: CalendarDayTimeline,
    freeGapMinutes: Int = CalendarFreeGapMinutes,
): CalendarAgenda {
    val anytime = timeline.rows.filterIsInstance<CalendarDayRow.AnyTimeItem>().map { it.entry }
    val ordered = buildList {
        timeline.rows.forEach { row ->
            when (row) {
                is CalendarDayRow.TimedItems -> row.group.entries.forEach { entry ->
                    add(CalendarAgendaRow.Item(row.group.minuteOfDay, row.group.start, entry))
                }
                is CalendarDayRow.CurrentTime -> add(CalendarAgendaRow.Now(row.minuteOfDay, row.instant))
                else -> Unit
            }
        }
    }
    val hasItems = ordered.any { it is CalendarAgendaRow.Item }
    val nowIndex = ordered.indexOfFirst { it is CalendarAgendaRow.Now }
    val rows = buildList {
        var previousMinute: Int? = null
        ordered.forEachIndexed { index, row ->
            // Free time only matters from now on; on another day, between any two things.
            val afterNow = nowIndex < 0 || index > nowIndex
            if (row is CalendarAgendaRow.Item && afterNow) {
                val previous = previousMinute
                if (previous != null && row.minuteOfDay - previous >= freeGapMinutes) {
                    add(CalendarAgendaRow.FreeUntil(row.minuteOfDay, row.start))
                }
            }
            // A "now" line on a day with nothing timed adds nothing.
            if (row !is CalendarAgendaRow.Now || hasItems) add(row)
            previousMinute = when (row) {
                is CalendarAgendaRow.Item -> row.minuteOfDay
                is CalendarAgendaRow.Now -> row.minuteOfDay
                is CalendarAgendaRow.FreeUntil -> previousMinute
            }
        }
    }
    return CalendarAgenda(anytime = anytime, rows = rows)
}
