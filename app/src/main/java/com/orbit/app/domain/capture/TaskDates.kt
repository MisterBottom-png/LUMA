package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpReminderStatus
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Brain Dump rows have no date-only column, so a task's day is written into the
 * time column as a marker: the last millisecond of that day in UTC, which no parsed
 * time uses and which reads back as the same day in any time zone. Older versions
 * wrote a local 23:59 instead; that is still read back as its day.
 */
internal object TaskDatePlaceholder {
    private const val DayMillis = 86_400_000L
    private val LegacyPlaceholderTime: LocalTime = LocalTime.of(23, 59)

    fun encode(dateEpochDay: Long): Long = dateEpochDay * DayMillis + (DayMillis - 1)

    /** The day of a placeholder; null for a real reminder time. */
    fun decode(millis: Long?, status: ReminderTimeStatus, zoneId: ZoneId): Long? {
        if (millis == null || status != ReminderTimeStatus.Unspecified) return null
        if (Math.floorMod(millis, DayMillis) == DayMillis - 1) return Math.floorDiv(millis, DayMillis)
        val local = Instant.ofEpochMilli(millis).atZone(zoneId)
        return local.toLocalDate().toEpochDay().takeIf { local.toLocalTime() == LegacyPlaceholderTime }
    }
}

/** A task's day from a resolved clock time: tasks keep the day, not the time. */
private fun dayOfResolvedTime(millis: Long?, status: ReminderTimeStatus, zoneId: ZoneId): Long? =
    millis?.takeIf { status == ReminderTimeStatus.Resolved }
        ?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate().toEpochDay() }

internal fun SuggestedItemType.isTaskLike(): Boolean =
    this == SuggestedItemType.Task || this == SuggestedItemType.MondayItem

/**
 * The day a suggested task is for, without a time: the stored day, or for an older
 * row the day of its 23:59 placeholder.
 */
fun CaptureSuggestionEntity.taskDateEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    if (!suggestedType.isTaskLike()) return null
    return contextDateEpochDay
        ?: TaskDatePlaceholder.decode(suggestedReminderAt, reminderStatus(), zoneId)
        ?: dayOfResolvedTime(suggestedReminderAt, reminderStatus(), zoneId)
}

/** The suggested reminder time, without an older task row's 23:59 placeholder. */
fun CaptureSuggestionEntity.reminderTime(zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    if (suggestedType.isTaskLike() && TaskDatePlaceholder.decode(suggestedReminderAt, reminderStatus(), zoneId) != null) {
        return null
    }
    return suggestedReminderAt
}

private fun BrainDumpItemEntity.timeStatus(): ReminderTimeStatus = when (reminderStatus) {
    BrainDumpReminderStatus.Unspecified -> ReminderTimeStatus.Unspecified
    BrainDumpReminderStatus.Resolved -> ReminderTimeStatus.Resolved
    BrainDumpReminderStatus.NeedsClarification -> ReminderTimeStatus.NeedsClarification
}

/** The day a Brain Dump task is for: its stored placeholder, or the day of a parsed time. */
fun BrainDumpItemEntity.taskDateEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long? =
    if (suggestedType.isTaskLike()) {
        TaskDatePlaceholder.decode(suggestedReminderAt, timeStatus(), zoneId)
            ?: dayOfResolvedTime(suggestedReminderAt, timeStatus(), zoneId)
    } else {
        null
    }

/** The suggested reminder time, without a task's day placeholder. */
fun BrainDumpItemEntity.reminderTime(zoneId: ZoneId = ZoneId.systemDefault()): Long? =
    if (TaskDatePlaceholder.decode(suggestedReminderAt, timeStatus(), zoneId) != null) null else suggestedReminderAt
