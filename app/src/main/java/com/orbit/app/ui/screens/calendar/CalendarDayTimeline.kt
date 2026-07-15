package com.orbit.app.ui.screens.calendar

import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarSchedule
import com.orbit.app.domain.calendar.calendarDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CalendarTimedGroup(
    val minuteOfDay: Int,
    val start: Instant,
    val entries: List<CalendarEntry>,
)

sealed interface CalendarDayRow {
    data object AnyTimeHeader : CalendarDayRow
    data class AnyTimeItem(val entry: CalendarEntry) : CalendarDayRow
    data object TimelineHeader : CalendarDayRow
    data class TimedItems(val group: CalendarTimedGroup) : CalendarDayRow
    data class CurrentTime(val minuteOfDay: Int, val instant: Instant) : CalendarDayRow
    data object EmptyTimeline : CalendarDayRow
    data object EmptyDay : CalendarDayRow
}

data class CalendarDayTimeline(
    val date: LocalDate,
    val rows: List<CalendarDayRow>,
    val recommendedScrollIndex: Int,
)

fun buildCalendarDayTimeline(
    entries: List<CalendarEntry>,
    selectedDate: LocalDate,
    now: Instant,
    zoneId: ZoneId,
): CalendarDayTimeline {
    val selectedEntries = entries
        .filter { it.calendarDate(zoneId) == selectedDate }
    val anyTimeItems = selectedEntries
        .filter { it.schedule is CalendarSchedule.DateOnly }
        .sortedWith(CalendarEntryTitleOrder)
    val timedGroups = selectedEntries
        .filter { it.schedule is CalendarSchedule.Timed }
        .groupBy { entry ->
            val localTime = (entry.schedule as CalendarSchedule.Timed).start.atZone(zoneId).toLocalTime()
            localTime.hour * MinutesPerHour + localTime.minute
        }
        .map { (minuteOfDay, groupedEntries) ->
            CalendarTimedGroup(
                minuteOfDay = minuteOfDay,
                start = groupedEntries
                    .map { (it.schedule as CalendarSchedule.Timed).start }
                    .minOrNull() ?: error("Timed group must contain an entry"),
                entries = groupedEntries.sortedWith(CalendarEntryTitleOrder),
            )
        }
        .sortedBy { it.minuteOfDay }

    val today = now.atZone(zoneId).toLocalDate()
    val currentTimeRow = if (selectedDate == today) {
        val localTime = now.atZone(zoneId).toLocalTime()
        CalendarDayRow.CurrentTime(
            minuteOfDay = localTime.hour * MinutesPerHour + localTime.minute,
            instant = now,
        )
    } else {
        null
    }

    val rows = buildList {
        if (anyTimeItems.isNotEmpty()) {
            add(CalendarDayRow.AnyTimeHeader)
            anyTimeItems.forEach { add(CalendarDayRow.AnyTimeItem(it)) }
        }

        if (timedGroups.isNotEmpty() || currentTimeRow != null) {
            add(CalendarDayRow.TimelineHeader)
            val timelineRows = buildList<CalendarDayRow> {
                timedGroups.forEach { add(CalendarDayRow.TimedItems(it)) }
                currentTimeRow?.let(::add)
            }.sortedWith(
                compareBy<CalendarDayRow> { row ->
                    when (row) {
                        is CalendarDayRow.TimedItems -> row.group.minuteOfDay
                        is CalendarDayRow.CurrentTime -> row.minuteOfDay
                        else -> Int.MIN_VALUE
                    }
                }.thenBy { row -> if (row is CalendarDayRow.CurrentTime) 0 else 1 },
            )
            addAll(timelineRows)
            if (timedGroups.isEmpty()) add(CalendarDayRow.EmptyTimeline)
        } else if (anyTimeItems.isEmpty()) {
            add(CalendarDayRow.EmptyDay)
        }
    }

    val currentTimeIndex = rows.indexOfFirst { it is CalendarDayRow.CurrentTime }
    return CalendarDayTimeline(
        date = selectedDate,
        rows = rows,
        recommendedScrollIndex = currentTimeIndex.takeIf { it >= 0 } ?: 0,
    )
}

private val CalendarEntryTitleOrder = compareBy<CalendarEntry>(
    { it.title.lowercase() },
    { it.id.sourceType.ordinal },
    { it.id.sourceItemId },
)

private const val MinutesPerHour = 60
