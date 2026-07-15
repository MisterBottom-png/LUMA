package com.orbit.app.ui.navigation

import java.time.LocalDate
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarNavigationTest {
    private val fallback = LocalDate.of(2026, 7, 14)

    @Test
    fun route_withoutDate_usesCalendarBaseRoute() {
        assertEquals(CalendarDestination.BaseRoute, CalendarDestination.route())
    }

    @Test
    fun route_withDate_encodesEpochDay() {
        val date = LocalDate.of(2026, 8, 3)

        assertEquals("calendar?date=${date.toEpochDay()}", CalendarDestination.route(date))
    }

    @Test
    fun initialDate_usesValidArgumentAndFallsBackForMissingOrInvalidArguments() {
        val requested = LocalDate.of(2026, 9, 9)

        assertEquals(
            requested,
            CalendarDestination.initialDate(requested.toEpochDay().toString(), fallback),
        )
        assertEquals(fallback, CalendarDestination.initialDate(null, fallback))
        assertEquals(fallback, CalendarDestination.initialDate("invalid", fallback))
        assertEquals(fallback, CalendarDestination.initialDate(Long.MAX_VALUE.toString(), fallback))
    }

    @Test
    fun navigationRequest_preventsDuplicateTopDestination() {
        val request = CalendarDestination.navigationRequest(fallback)

        assertEquals(CalendarDestination.route(fallback), request.route)
        assertTrue(request.launchSingleTop)
    }

    @Test
    fun captureContext_acceptsValidEpochDayAndRejectsInvalidValue() {
        assertEquals(fallback, CalendarCaptureContext.date(fallback.toEpochDay()))
        assertEquals(null, CalendarCaptureContext.date(Long.MAX_VALUE))
        assertEquals(null, CalendarCaptureContext.date(null))
    }

    @Test
    fun calendarEntries_routeToExistingDetailDestinations() {
        assertEquals(
            ItemDetailDestination.route(ItemDetailType.Note, 1),
            CalendarEntryId(CalendarItemType.Note, 1).toItemDetailRoute(),
        )
        assertEquals(
            ItemDetailDestination.route(ItemDetailType.Task, 2),
            CalendarEntryId(CalendarItemType.Task, 2).toItemDetailRoute(),
        )
        assertEquals(
            ReminderDestination.route(3),
            CalendarEntryId(CalendarItemType.Reminder, 3).toItemDetailRoute(),
        )
    }
}
