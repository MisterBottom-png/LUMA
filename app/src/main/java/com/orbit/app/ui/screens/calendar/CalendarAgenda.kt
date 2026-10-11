package com.orbit.app.ui.screens.calendar

import com.orbit.app.domain.calendar.CalendarEntry
import java.time.Instant

/**
 * A day as a short list instead of a 24-hour grid: things with no time first, then the
 * timed things in order and a "now" marker on today. Empty hours are not drawn, and the
 * day says "Nothing planned" once: alone on an empty day, otherwise after the last item.
 */
data class CalendarAgenda(
    val anytime: List<CalendarEntry>,
    val rows: List<CalendarAgendaRow>,
) {
    val isEmpty: Boolean get() = anytime.isEmpty() && rows.none { it is CalendarAgendaRow.Item }

    /** The one "Nothing planned" line of the day and where it goes. */
    val fillerPlacement: CalendarFillerPlacement
        get() = if (isEmpty) CalendarFillerPlacement.EmptyDay else CalendarFillerPlacement.AfterLastItem
}

enum class CalendarFillerPlacement { EmptyDay, AfterLastItem }

sealed interface CalendarAgendaRow {
    data class Item(val minuteOfDay: Int, val start: Instant, val entry: CalendarEntry) : CalendarAgendaRow
    data class Now(val minuteOfDay: Int, val instant: Instant) : CalendarAgendaRow
}

fun buildCalendarAgenda(timeline: CalendarDayTimeline): CalendarAgenda {
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
    // A "now" line on a day with nothing timed adds nothing.
    val rows = ordered.filter { it !is CalendarAgendaRow.Now || hasItems }
    return CalendarAgenda(anytime = anytime, rows = rows)
}
