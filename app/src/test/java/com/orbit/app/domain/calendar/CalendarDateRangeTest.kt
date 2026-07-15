package com.orbit.app.domain.calendar

import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CalendarDateRangeTest {
    @Test
    fun dayRangeUsesHalfOpenLocalBoundariesAcrossYearChange() {
        val zone = ZoneId.of("UTC")
        val range = CalendarDateRange.day(LocalDate.of(2026, 12, 31), zone)

        assertEquals(LocalDate.of(2026, 12, 31).toEpochDay(), range.startEpochDay)
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), range.endEpochDay)
        assertEquals(Duration.ofDays(1).toMillis(), range.endMillis - range.startMillis)
    }

    @Test
    fun monthRangeHandlesLeapYearBoundary() {
        val range = CalendarDateRange.month(
            monthDate = LocalDate.of(2028, 2, 18),
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(LocalDate.of(2028, 2, 1), range.startDate)
        assertEquals(LocalDate.of(2028, 3, 1), range.endDateExclusive)
        assertEquals(29L, range.endEpochDay - range.startEpochDay)
    }

    @Test
    fun daylightSavingDaysUseActualLocalDayLength() {
        val zone = ZoneId.of("Europe/Tallinn")
        val spring = CalendarDateRange.day(LocalDate.of(2026, 3, 29), zone)
        val autumn = CalendarDateRange.day(LocalDate.of(2026, 10, 25), zone)

        assertEquals(Duration.ofHours(23).toMillis(), spring.endMillis - spring.startMillis)
        assertEquals(Duration.ofHours(25).toMillis(), autumn.endMillis - autumn.startMillis)
    }

    @Test
    fun dateOnlyEpochDayDoesNotChangeAcrossTimezones() {
        val date = LocalDate.of(2026, 7, 14)
        val east = CalendarDateRange.day(date, ZoneId.of("Pacific/Auckland"))
        val west = CalendarDateRange.day(date, ZoneId.of("America/Los_Angeles"))

        assertEquals(east.startEpochDay, west.startEpochDay)
        assertEquals(east.endEpochDay, west.endEpochDay)
    }

    @Test
    fun emptyRangeIsRejected() {
        val date = LocalDate.of(2026, 7, 14)

        assertThrows(IllegalArgumentException::class.java) {
            CalendarDateRange(date, date, ZoneId.of("UTC"))
        }
    }
}
