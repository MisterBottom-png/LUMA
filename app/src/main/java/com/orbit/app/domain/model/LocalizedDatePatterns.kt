package com.orbit.app.domain.model

import java.util.Locale

/**
 * Date patterns per app language, in each language's own order: "Wed, Oct 15",
 * "K, 15. oktoober", "ср, 15 октября". Other languages use the English order with
 * their own month and day names.
 */
data class LocalizedDatePatterns(
    /** Weekday, day and month: "Wed, Oct 15". */
    val weekdayDate: String,
    /** Day and month: "Oct 15". */
    val date: String,
    /** Day, month and year: "Oct 15, 2026". */
    val dateWithYear: String,
    /** Full weekday, day and month: "Wednesday, October 15". */
    val longWeekdayDate: String,
) {
    companion object {
        private val English = LocalizedDatePatterns("EEE, MMM d", "MMM d", "MMM d, yyyy", "EEEE, MMMM d")
        private val Estonian = LocalizedDatePatterns("EEE, d. MMMM", "d. MMMM", "d. MMMM yyyy", "EEEE, d. MMMM")
        private val Russian = LocalizedDatePatterns("EEE, d MMMM", "d MMMM", "d MMMM yyyy", "EEEE, d MMMM")

        fun forLocale(locale: Locale): LocalizedDatePatterns = when (locale.language) {
            "et" -> Estonian
            "ru" -> Russian
            else -> English
        }

        fun timePattern(uses24HourClock: Boolean): String = if (uses24HourClock) "HH:mm" else "h:mm a"
    }
}
