package com.orbit.app.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** What Home does after a thought is analysed; filing, asking and keeping are AutoFiler's. */
class HomeAfterSavingTest {
    @Test
    fun aThoughtAddedFromACalendarDayOpensTheSortSheet() {
        assertEquals(AfterSaving.OpenSortSheet, afterSaving(sortRightAfterSaving = false, addedForCalendarDay = true))
        assertEquals(AfterSaving.Nothing, afterSaving(sortRightAfterSaving = false, addedForCalendarDay = false))
        assertEquals(AfterSaving.OpenSortSheet, afterSaving(sortRightAfterSaving = true, addedForCalendarDay = false))
    }
}

class SortPrimaryButtonTest {
    @Test
    fun theButtonWaitsUntilAReminderHasATime() {
        org.junit.Assert.assertFalse(sortPrimaryEnabled(isPerformingAction = false, title = "Call the bank", needsTime = true))
        org.junit.Assert.assertTrue(sortPrimaryEnabled(isPerformingAction = false, title = "Call the bank", needsTime = false))
        org.junit.Assert.assertFalse(sortPrimaryEnabled(isPerformingAction = false, title = " ", needsTime = false))
        org.junit.Assert.assertFalse(sortPrimaryEnabled(isPerformingAction = true, title = "Call the bank", needsTime = false))
    }
}
