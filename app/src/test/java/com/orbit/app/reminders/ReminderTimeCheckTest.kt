package com.orbit.app.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** B5: a chosen reminder time is refused when it, or its heads-up, has passed. */
class ReminderTimeCheckTest {
    private val now = 1_800_000_000_000L
    private val minute = 60_000L

    @Test
    fun aFutureTimeWithAFittingHeadsUpIsFine() {
        assertNull(ReminderTimeCheck.problem(now + 60 * minute, 30, now))
        // "Now" still rings, inside the scheduling grace.
        assertNull(ReminderTimeCheck.problem(now - 30_000L, 0, now))
    }

    @Test
    fun aPassedTimeIsTargetPassed() {
        assertEquals(ReminderTimeProblem.TargetPassed, ReminderTimeCheck.problem(now - 5 * minute, 0, now))
    }

    @Test
    fun aHeadsUpInThePastIsAlertPassed() {
        assertEquals(ReminderTimeProblem.AlertPassed, ReminderTimeCheck.problem(now + 10 * minute, 30, now))
    }
}
