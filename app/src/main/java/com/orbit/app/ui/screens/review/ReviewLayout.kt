package com.orbit.app.ui.screens.review

import java.time.DayOfWeek
import java.time.LocalDateTime

/** The weekly look back can be opened any day; the weekend only adds a gentle hint. */
internal fun isWeeklyLookBackSuggested(now: LocalDateTime): Boolean =
    now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY

/** How many rows a section shows before "Show more". */
internal const val ReviewSectionPreviewSize = 5

/** Needs you keeps today's plan short: three rows, then "Show more". */
internal const val NeedsYouTodayPreviewSize = 3
