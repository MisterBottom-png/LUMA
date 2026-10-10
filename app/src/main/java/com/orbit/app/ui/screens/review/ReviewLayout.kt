package com.orbit.app.ui.screens.review

import java.time.DayOfWeek
import java.time.LocalDateTime

/** The parts of Review. Every part is available at every time of day. */
enum class ReviewSection { AskLuma, ToSort, Today, CarryForward, WeeklyLookBack }

/**
 * Time of day changes the order of Review, never what is visible: mornings start
 * with Today, evenings with Carry forward, and To sort is always near the top.
 */
internal fun reviewSectionOrder(period: ReviewPeriod): List<ReviewSection> = when (period) {
    ReviewPeriod.Morning -> listOf(
        ReviewSection.AskLuma,
        ReviewSection.Today,
        ReviewSection.ToSort,
        ReviewSection.CarryForward,
        ReviewSection.WeeklyLookBack,
    )
    ReviewPeriod.Midday -> listOf(
        ReviewSection.AskLuma,
        ReviewSection.ToSort,
        ReviewSection.Today,
        ReviewSection.CarryForward,
        ReviewSection.WeeklyLookBack,
    )
    ReviewPeriod.Evening -> listOf(
        ReviewSection.AskLuma,
        ReviewSection.CarryForward,
        ReviewSection.ToSort,
        ReviewSection.Today,
        ReviewSection.WeeklyLookBack,
    )
}

/** The weekly look back can be opened any day; the weekend only adds a gentle hint. */
internal fun isWeeklyLookBackSuggested(now: LocalDateTime): Boolean =
    now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY

/** How many rows a section shows before "Show more". */
internal const val ReviewSectionPreviewSize = 5
