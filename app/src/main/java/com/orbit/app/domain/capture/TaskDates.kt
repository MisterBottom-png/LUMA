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
 * Older versions kept a task's day as a 23:59 "reminder time". Tasks are date-only
 * now; this reads such a value back as its day, and writes it for Brain Dump rows,
 * which have no date-only column.
 */
internal object TaskDatePlaceholder {
    private val PlaceholderTime: LocalTime = LocalTime.of(23, 59)

    fun encode(dateEpochDay: Long, zoneId: ZoneId): Long =
        LocalDate.ofEpochDay(dateEpochDay).atTime(PlaceholderTime).atZone(zoneId).toInstant().toEpochMilli()

    /** The day of a 23:59 placeholder; null for a real reminder time. */
    fun decode(millis: Long?, status: ReminderTimeStatus, zoneId: ZoneId): Long? {
        if (millis == null || status != ReminderTimeStatus.Unspecified) return null
        val local = Instant.ofEpochMilli(millis).atZone(zoneId)
        return local.toLocalDate().toEpochDay().takeIf { local.toLocalTime() == PlaceholderTime }
    }
}

internal fun SuggestedItemType.isTaskLike(): Boolean =
    this == SuggestedItemType.Task || this == SuggestedItemType.MondayItem

/**
 * The day a suggested task is for, without a time: the stored day, or for an older
 * row the day of its 23:59 placeholder.
 */
fun CaptureSuggestionEntity.taskDateEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long? {
    if (!suggestedType.isTaskLike()) return null
    return contextDateEpochDay ?: TaskDatePlaceholder.decode(suggestedReminderAt, reminderStatus(), zoneId)
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

/** The day a Brain Dump task is for, read from its stored placeholder. */
fun BrainDumpItemEntity.taskDateEpochDay(zoneId: ZoneId = ZoneId.systemDefault()): Long? =
    if (suggestedType.isTaskLike()) TaskDatePlaceholder.decode(suggestedReminderAt, timeStatus(), zoneId) else null

/** The suggested reminder time, without a task's day placeholder. */
fun BrainDumpItemEntity.reminderTime(zoneId: ZoneId = ZoneId.systemDefault()): Long? =
    if (taskDateEpochDay(zoneId) != null) null else suggestedReminderAt
