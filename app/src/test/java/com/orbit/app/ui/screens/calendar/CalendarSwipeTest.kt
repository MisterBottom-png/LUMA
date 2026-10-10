package com.orbit.app.ui.screens.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSwipeTest {
    @Test
    fun horizontalSwipe_movesExactlyOneCalendarPageAfterThreshold() {
        assertEquals(-1L, calendarSwipeDateDelta(horizontalDrag = 80f, threshold = 64f))
        assertEquals(1L, calendarSwipeDateDelta(horizontalDrag = -80f, threshold = 64f))
        assertEquals(0L, calendarSwipeDateDelta(horizontalDrag = 40f, threshold = 64f))
    }
}
