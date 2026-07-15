package com.orbit.app.ui.screens.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

data class CalendarWeekdayLabel(
    val dayOfWeek: DayOfWeek,
    val shortLabel: String,
    val contentDescription: String,
)

data class CalendarMonthCell(
    val date: LocalDate,
    val isInVisibleMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasItems: Boolean,
)

data class CalendarMonthGrid(
    val visibleMonth: YearMonth,
    val weekdayLabels: List<CalendarWeekdayLabel>,
    val weeks: List<List<CalendarMonthCell>>,
)

fun buildCalendarMonthGrid(
    visibleMonth: YearMonth,
    selectedDate: LocalDate,
    today: LocalDate,
    datesWithItems: Set<LocalDate>,
    locale: Locale,
): CalendarMonthGrid {
    val firstDayOfWeek = WeekFields.of(locale).firstDayOfWeek
    val firstOfMonth = visibleMonth.atDay(1)
    val leadingDays = daysFrom(firstDayOfWeek, firstOfMonth.dayOfWeek)
    val firstCellDate = firstOfMonth.minusDays(leadingDays.toLong())
    val requiredCells = leadingDays + visibleMonth.lengthOfMonth()
    val rowCount = (requiredCells + DaysPerWeek - 1) / DaysPerWeek
    val cells = List(rowCount * DaysPerWeek) { offset ->
        val date = firstCellDate.plusDays(offset.toLong())
        CalendarMonthCell(
            date = date,
            isInVisibleMonth = YearMonth.from(date) == visibleMonth,
            isToday = date == today,
            isSelected = date == selectedDate,
            hasItems = date in datesWithItems,
        )
    }

    return CalendarMonthGrid(
        visibleMonth = visibleMonth,
        weekdayLabels = List(DaysPerWeek) { offset ->
            val day = firstDayOfWeek.plus(offset.toLong())
            CalendarWeekdayLabel(
                dayOfWeek = day,
                shortLabel = day.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                contentDescription = day.getDisplayName(TextStyle.FULL_STANDALONE, locale),
            )
        },
        weeks = cells.chunked(DaysPerWeek),
    )
}

fun calendarMonthCellContentDescription(cell: CalendarMonthCell, locale: Locale): String = buildList {
    if (cell.isToday) add("Today")
    add(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(cell.date))
    if (cell.isSelected) add("selected")
    if (!cell.isInVisibleMonth) add("outside current month")
    if (cell.hasItems) add("has scheduled items")
}.joinToString(", ")

private fun daysFrom(first: DayOfWeek, target: DayOfWeek): Int =
    (DaysPerWeek + target.value - first.value) % DaysPerWeek

private const val DaysPerWeek = 7
