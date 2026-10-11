package com.orbit.app.ui.navigation

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MidnightRefreshTest {
    @Test
    fun homeRefreshesJustAfterTheNextLocalMidnight() {
        val zone = ZoneId.of("Europe/Tallinn")
        assertEquals(60_000L + 1_000L, millisUntilNextLocalMidnight(ZonedDateTime.of(2026, 10, 10, 23, 59, 0, 0, zone)))
        // The day the clocks go back is 25 hours long.
        assertEquals(25 * 3_600_000L + 1_000L, millisUntilNextLocalMidnight(ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, zone)))
    }
}
