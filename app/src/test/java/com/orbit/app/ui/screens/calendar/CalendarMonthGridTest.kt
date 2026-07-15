package com.orbit.app.ui.screens.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMonthGridTest {
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun leapYearFebruary_containsAllTwentyNineVisibleDates() {
        val grid = grid(YearMonth.of(2024, 2), Locale.GERMANY)
        val visibleDates = grid.visibleCells.map { it.date }

        assertEquals(29, visibleDates.size)
        assertTrue(LocalDate.of(2024, 2, 29) in visibleDates)
    }

    @Test
    fun monthLayouts_useFourFiveAndSixRowsWhenRequired() {
        assertEquals(4, grid(YearMonth.of(2021, 2), Locale.GERMANY).weeks.size)
        assertEquals(5, grid(YearMonth.of(2026, 7), Locale.GERMANY).weeks.size)
        assertEquals(6, grid(YearMonth.of(2026, 8), Locale.GERMANY).weeks.size)
    }

    @Test
    fun locale_controlsFirstWeekdayAndGridAlignment() {
        val usGrid = grid(YearMonth.of(2026, 7), Locale.US)
        val germanGrid = grid(YearMonth.of(2026, 7), Locale.GERMANY)

        assertEquals(DayOfWeek.SUNDAY, usGrid.weekdayLabels.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, usGrid.weeks.first().first().date.dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, germanGrid.weekdayLabels.first().dayOfWeek)
        assertEquals(DayOfWeek.MONDAY, germanGrid.weeks.first().first().date.dayOfWeek)
    }

    @Test
    fun monthsStartingOnEachWeekday_alignWithoutLosingVisibleDates() {
        val months = (1..12).map { YearMonth.of(2026, it) } +
            (1..12).map { YearMonth.of(2027, it) }
        val startingWeekdays = months.groupBy { it.atDay(1).dayOfWeek }

        assertEquals(DayOfWeek.entries.toSet(), startingWeekdays.keys)
        months.forEach { month ->
            val grid = grid(month, Locale.GERMANY)
            assertEquals(month.lengthOfMonth(), grid.visibleCells.size)
            assertEquals(DayOfWeek.MONDAY, grid.weeks.first().first().date.dayOfWeek)
        }
    }

    @Test
    fun adjacentMonthCells_crossYearBoundaryAndRemainSelectable() {
        val selectedDate = LocalDate.of(2027, 1, 1)
        val grid = buildCalendarMonthGrid(
            visibleMonth = YearMonth.of(2026, 12),
            selectedDate = selectedDate,
            today = today,
            datesWithItems = emptySet(),
            locale = Locale.GERMANY,
        )
        val selectedCell = grid.weeks.flatten().single { it.isSelected }

        assertEquals(selectedDate, selectedCell.date)
        assertFalse(selectedCell.isInVisibleMonth)
        assertEquals(YearMonth.of(2027, 1), YearMonth.from(selectedCell.date))
    }

    @Test
    fun cellStates_includeTodaySelectionAndOneItemIndicator() {
        val grid = buildCalendarMonthGrid(
            visibleMonth = YearMonth.from(today),
            selectedDate = today,
            today = today,
            datesWithItems = setOf(today),
            locale = Locale.US,
        )
        val cell = grid.weeks.flatten().single { it.date == today }
        val description = calendarMonthCellContentDescription(cell, Locale.US)

        assertTrue(cell.isToday)
        assertTrue(cell.isSelected)
        assertTrue(cell.hasItems)
        assertTrue(description.startsWith("Today,"))
        assertTrue(description.contains("selected"))
        assertTrue(description.contains("has scheduled items"))
    }

    private fun grid(month: YearMonth, locale: Locale) = buildCalendarMonthGrid(
        visibleMonth = month,
        selectedDate = month.atDay(1),
        today = today,
        datesWithItems = emptySet(),
        locale = locale,
    )

    private val CalendarMonthGrid.visibleCells: List<CalendarMonthCell>
        get() = weeks.flatten().filter { it.isInVisibleMonth }
}
