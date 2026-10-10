package com.orbit.app.ui.screens.review

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewModeTest {
    @Test
    fun `review schedule selects centralized device-local time periods`() {
        val weekday = LocalDateTime.of(2026, 7, 16, 0, 0)

        assertEquals(12, ReviewSchedule.MIDDAY_START_HOUR)
        assertEquals(17, ReviewSchedule.EVENING_START_HOUR)
        assertEquals(ReviewPeriod.Morning, ReviewSchedule.resolve(weekday).period)
        assertEquals(
            ReviewPeriod.Morning,
            ReviewSchedule.resolve(weekday.withHour(11).withMinute(59)).period,
        )
        assertEquals(ReviewPeriod.Midday, ReviewSchedule.resolve(weekday.withHour(12)).period)
        assertEquals(
            ReviewPeriod.Midday,
            ReviewSchedule.resolve(weekday.withHour(16).withMinute(59)).period,
        )
        assertEquals(ReviewPeriod.Evening, ReviewSchedule.resolve(weekday.withHour(17)).period)
    }

    @Test
    fun `weekly review is available only on device-local weekends`() {
        val thursday = LocalDateTime.of(2026, 7, 16, 9, 0)
        val saturday = LocalDateTime.of(2026, 7, 18, 9, 0)
        val sunday = LocalDateTime.of(2026, 7, 19, 18, 0)

        assertFalse(ReviewSchedule.resolve(thursday).weeklyReviewAvailable)
        assertTrue(ReviewSchedule.resolve(saturday).weeklyReviewAvailable)
        assertTrue(ReviewSchedule.resolve(sunday).weeklyReviewAvailable)
    }

    @Test
    fun `review context refresh waits for the next period boundary`() {
        assertEquals(1_000L, reviewContextRefreshDelayMillis(LocalDateTime.of(2026, 7, 16, 11, 59, 59)))
        assertEquals(1_000L, reviewContextRefreshDelayMillis(LocalDateTime.of(2026, 7, 16, 16, 59, 59)))
    }
}
