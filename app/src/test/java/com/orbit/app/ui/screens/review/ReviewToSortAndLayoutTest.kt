package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewToSortAndLayoutTest {
    private val now = 1_800_000_000_000L

    @Test
    fun everyReviewSectionIsAvailableAtEveryTimeOfDay() {
        ReviewPeriod.entries.forEach { period ->
            assertEquals(ReviewSection.entries.toSet(), reviewSectionOrder(period).toSet())
        }
    }

    @Test
    fun timeOfDayOnlyChangesTheOrder() {
        assertEquals(ReviewSection.Today, reviewSectionOrder(ReviewPeriod.Morning)[1])
        assertEquals(ReviewSection.ToSort, reviewSectionOrder(ReviewPeriod.Midday)[1])
        assertEquals(ReviewSection.CarryForward, reviewSectionOrder(ReviewPeriod.Evening)[1])
    }

    @Test
    fun weeklyLookBackIsSuggestedOnlyAtTheWeekend() {
        assertTrue(isWeeklyLookBackSuggested(LocalDateTime.of(2026, 10, 10, 10, 0)))
        assertFalse(isWeeklyLookBackSuggested(LocalDateTime.of(2026, 10, 7, 10, 0)))
    }

    @Test
    fun toSortListsEveryUnresolvedThoughtWithItsState() {
        val captures = listOf(
            capture(1, createdAt = now - 10_000),
            capture(2, createdAt = now - 3_600_000),
            capture(3, createdAt = now - 7_200_000),
            capture(4, createdAt = now - 9_000_000),
            capture(5, createdAt = now - 9_500_000),
            capture(6, createdAt = now - 1_000, status = CaptureStatus.Processed),
            capture(7, createdAt = now - 9_900_000),
        )
        val suggestions = mapOf(
            3L to suggestion(3, SuggestedItemType.Task),
            4L to suggestion(4, SuggestedItemType.Reminder, at = null),
            5L to suggestion(5, SuggestedItemType.Note, dismissed = true),
            7L to suggestion(7, SuggestedItemType.Reminder, at = now + 3_600_000, status = "Resolved"),
        )

        val items = buildToSort(captures, suggestions, mapOf(2L to 3), setOf(2L), now)

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 7L), items.map { it.captureId })
        assertEquals(ToSortState.Analyzing, items[0].state)
        assertEquals(ToSortState.BrainDump, items[1].state)
        assertEquals(3, items[1].brainDumpPending)
        assertEquals(ToSortState.Suggested, items[2].state)
        assertEquals(ToSortState.NeedsChoice, items[3].state)
        assertEquals(ToSortState.NoSuggestion, items[4].state)
        assertEquals(ToSortState.Suggested, items[5].state)
        assertEquals(now + 3_600_000, items[5].reminderAt)
    }

    @Test
    fun aStaleUnanalysedThoughtOffersManualSorting() {
        val items = buildToSort(listOf(capture(1, createdAt = now - AnalysisGraceMillis - 1)), emptyMap(), emptyMap(), emptySet(), now)
        assertEquals(ToSortState.NoSuggestion, items.single().state)
    }

    @Test
    fun allSortedIsOnlyClaimedWhenNothingWaits() {
        assertTrue(ReviewUiState().nothingWaiting)
        assertFalse(ReviewUiState(toSort = listOf(ToSortItem(1, "x", 0, ToSortState.Analyzing))).nothingWaiting)
    }

    private fun capture(id: Long, createdAt: Long, status: CaptureStatus = CaptureStatus.Inbox) =
        CaptureEntity(id = id, rawText = "thought $id", createdAt = createdAt, updatedAt = createdAt, status = status)

    private fun suggestion(
        id: Long,
        type: SuggestedItemType,
        at: Long? = null,
        status: String = "Unspecified",
        dismissed: Boolean = false,
    ) = CaptureSuggestionEntity(
        captureId = id,
        suggestedType = type,
        suggestedTitle = "title $id",
        suggestedReminderAt = at,
        reminderTimeStatus = status,
        confidence = 0.8f,
        analyzerSource = "Local",
        dismissed = dismissed,
    )
}
