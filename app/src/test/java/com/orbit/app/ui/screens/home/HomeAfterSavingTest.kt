package com.orbit.app.ui.screens.home

import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** What Home does after a thought is analysed. */
class HomeAfterSavingTest {
    private val now = Instant.parse("2026-07-14T10:00:00Z")
    private val analyzer = LocalRulesCaptureAnalyzer(now = { now }, zoneId = { ZoneId.of("Europe/Tallinn") })

    @Test
    fun aThoughtAddedFromACalendarDayOpensTheSortSheet() {
        val analysis = analyzer.analyze("Garden ideas")
        assertEquals(AfterSaving.OpenSortSheet, afterSaving(analysis, sortRightAfterSaving = false, addedForCalendarDay = true, now.toEpochMilli()))
        assertEquals(AfterSaving.Nothing, afterSaving(analysis, sortRightAfterSaving = false, addedForCalendarDay = false, now.toEpochMilli()))
        assertEquals(AfterSaving.OpenSortSheet, afterSaving(analysis, sortRightAfterSaving = true, addedForCalendarDay = false, now.toEpochMilli()))
    }

    @Test
    fun aClearReminderStillGetsTheQuickQuestionWhenNotFromCalendar() {
        val analysis = analyzer.analyze("Remind me tomorrow at 16:00 to call the bank")
        assertEquals(AfterSaving.AskForTime, afterSaving(analysis, sortRightAfterSaving = false, addedForCalendarDay = false, now.toEpochMilli()))
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
