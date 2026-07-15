package com.orbit.app.domain.analyzer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal data class ReminderTimeInterpretation(
    val status: ReminderTimeStatus,
    val epochMillis: Long? = null,
    val phrase: String? = null,
)

internal fun interpretReminderTime(
    rawText: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): ReminderTimeInterpretation {
    val normalized = rawText.lowercase(Locale.ROOT)
    val hasToday = TodayRegex.containsMatchIn(normalized)
    val hasTomorrow = TomorrowRegex.containsMatchIn(normalized)
    if (hasToday && hasTomorrow) return needsClarification()

    val occupiedRanges = mutableListOf<IntRange>()
    val times = mutableListOf<LocalTime>()
    var invalidTimeFound = false

    AmPmRegex.findAll(normalized).forEach { match ->
        occupiedRanges += match.range
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val isPm = match.groupValues[3].startsWith("p")
        val resolvedHour = when {
            isPm && hour != 12 -> hour + 12
            !isPm && hour == 12 -> 0
            else -> hour
        }
        times += LocalTime.of(resolvedHour, minute)
    }

    ColonTimeRegex.findAll(normalized).forEach { match ->
        if (occupiedRanges.none { it.overlaps(match.range) }) {
            occupiedRanges += match.range
            times += LocalTime.of(match.groupValues[1].toInt(), match.groupValues[2].toInt())
        }
    }

    val contextClearlyIndicatesTime = hasToday || hasTomorrow || TimeContextRegex.containsMatchIn(normalized)
    CompactTimeRegex.findAll(normalized).forEach { match ->
        if (occupiedRanges.any { it.overlaps(match.range) }) return@forEach
        val digits = match.value
        if (digits.length == 3 && !contextClearlyIndicatesTime) return@forEach
        val hourDigits = digits.dropLast(2)
        val minuteDigits = digits.takeLast(2)
        val hour = hourDigits.toInt()
        val minute = minuteDigits.toInt()
        if (hour !in 0..23 || minute !in 0..59) {
            invalidTimeFound = true
        } else {
            times += LocalTime.of(hour, minute)
        }
    }

    if (invalidTimeFound) return needsClarification()
    val distinctTimes = times.distinct()
    if (distinctTimes.size > 1) return needsClarification()
    val time = distinctTimes.singleOrNull()
        ?: return ReminderTimeInterpretation(ReminderTimeStatus.Unspecified)

    val today = now.atZone(zoneId).toLocalDate()
    val date = if (hasTomorrow) today.plusDays(1) else today
    val epochMillis = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    val dayLabel = if (date == today) "today" else "tomorrow"
    return ReminderTimeInterpretation(
        status = ReminderTimeStatus.Resolved,
        epochMillis = epochMillis,
        phrase = "%02d:%02d %s".format(Locale.ROOT, time.hour, time.minute, dayLabel),
    )
}

private fun needsClarification() = ReminderTimeInterpretation(
    status = ReminderTimeStatus.NeedsClarification,
)

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

private val TodayRegex = Regex("(?<![\\p{L}\\p{N}])today(?![\\p{L}\\p{N}])")
private val TomorrowRegex = Regex("(?<![\\p{L}\\p{N}])tomorrow(?![\\p{L}\\p{N}])")
private val AmPmRegex = Regex("(?<![\\p{L}\\p{N}])(1[0-2]|0?[1-9])(?:[:.]([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\p{L}\\p{N}])")
private val ColonTimeRegex = Regex("(?<![\\p{L}\\p{N}])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\p{L}\\p{N}])")
private val CompactTimeRegex = Regex("(?<![\\p{L}\\p{N}])\\d{3,4}(?![\\p{L}\\p{N}])")
private val TimeContextRegex = Regex("(?<![\\p{L}\\p{N}])(remind(?:er)?|time|at|for|set\\s+for|schedule(?:d)?)(?![\\p{L}\\p{N}])")
