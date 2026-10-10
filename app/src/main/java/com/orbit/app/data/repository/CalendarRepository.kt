package com.orbit.app.data.repository

import com.orbit.app.data.local.dao.NoteDao
import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.dao.TaskDao
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.domain.calendar.CalendarDateRange
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import com.orbit.app.reminders.ReminderRepeats
import com.orbit.app.reminders.reminderNotificationTimeMillis
import com.orbit.app.reminders.repeat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface CalendarRepository {
    fun observeRange(range: CalendarDateRange): Flow<List<CalendarEntry>>
}

class RoomCalendarRepository(
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Long = System::currentTimeMillis,
) : CalendarRepository {
    override fun observeRange(range: CalendarDateRange): Flow<List<CalendarEntry>> = combine(
        noteDao.observeCalendarRange(
            startEpochDay = range.startEpochDay,
            endEpochDay = range.endEpochDay,
            startMillis = range.startMillis,
            endMillis = range.endMillis,
        ),
        taskDao.observeCalendarRange(
            startEpochDay = range.startEpochDay,
            endEpochDay = range.endEpochDay,
            startMillis = range.startMillis,
            endMillis = range.endMillis,
        ),
        reminderDao.observeCalendarRange(
            startMillis = range.startMillis,
            endMillis = range.endMillis,
        ),
        reminderDao.observeRepeatingBefore(endMillis = range.endMillis),
    ) { notes, tasks, reminders, repeating ->
        buildList {
            notes.mapNotNullTo(this) { it.toCalendarEntryOrNull() }
            tasks.mapNotNullTo(this) { it.toCalendarEntryOrNull() }
            reminders.mapTo(this) { it.toCalendarEntry() }
            repeating.forEach { reminder ->
                upcomingOccurrences(reminder, range.startMillis, range.endMillis, clock(), zoneId())
                    .mapTo(this) { at -> reminder.copy(dueAt = at).toCalendarEntry(isRepeatOccurrence = true) }
            }
        }.sortedWith(CalendarEntryOrder)
    }
}

internal fun NoteEntity.toCalendarEntryOrNull(): CalendarEntry? {
    val schedule = calendarScheduleOrNull(scheduledDateEpochDay, scheduledAt) ?: return null
    return CalendarEntry(
        id = CalendarEntryId(CalendarItemType.Note, id),
        title = title,
        spaceId = spaceId,
        schedule = schedule,
    )
}

internal fun TaskEntity.toCalendarEntryOrNull(): CalendarEntry? {
    val schedule = calendarScheduleOrNull(scheduledDateEpochDay, dueAt) ?: return null
    return CalendarEntry(
        id = CalendarEntryId(CalendarItemType.Task, id),
        title = title,
        spaceId = spaceId,
        schedule = schedule,
        taskStatus = status,
        completedAt = completedAt,
    )
}

/**
 * Later occurrences of a repeating reminder inside [startMillis, endMillis). The
 * stored occurrence itself is not included; it comes from the normal range query.
 * Days already past are skipped: "Done" on an overdue reminder moves it to the
 * first occurrence after now, so those days never get their own reminder.
 */
internal fun upcomingOccurrences(
    reminder: ReminderEntity,
    startMillis: Long,
    endMillis: Long,
    nowMillis: Long,
    zoneId: ZoneId,
): List<Long> {
    if (reminder.repeat == null || reminder.completedAt != null) return emptyList()
    val found = mutableListOf<Long>()
    var after = maxOf(nowMillis, startMillis - 1)
    var current = reminder
    repeat(MaxProjectedOccurrences) {
        val next = ReminderRepeats.nextOccurrence(current, now = after, zoneId = zoneId) ?: return found
        if (next >= endMillis) return found
        found += next
        current = current.copy(dueAt = next)
        after = next
    }
    return found
}

/** Enough for a daily reminder across the longest visible range (a month grid). */
private const val MaxProjectedOccurrences = 64

internal fun ReminderEntity.toCalendarEntry(isRepeatOccurrence: Boolean = false): CalendarEntry = CalendarEntry(
    id = CalendarEntryId(CalendarItemType.Reminder, id),
    title = title,
    spaceId = spaceId,
    schedule = CalendarSchedule.Timed(Instant.ofEpochMilli(dueAt)),
    completedAt = completedAt,
    reminderTargetAt = dueAt,
    notificationOffsetMinutes = notificationOffsetMinutes,
    notificationAt = reminderNotificationTimeMillis(dueAt, notificationOffsetMinutes),
    notificationEnabled = notificationEnabled,
    repeat = repeat,
    isRepeatOccurrence = isRepeatOccurrence,
)

private fun calendarScheduleOrNull(
    scheduledDateEpochDay: Long?,
    scheduledAt: Long?,
): CalendarSchedule? = when {
    scheduledDateEpochDay != null && scheduledAt != null -> null
    scheduledDateEpochDay != null -> runCatching {
        CalendarSchedule.DateOnly(LocalDate.ofEpochDay(scheduledDateEpochDay))
    }.getOrNull()
    scheduledAt != null -> CalendarSchedule.Timed(Instant.ofEpochMilli(scheduledAt))
    else -> null
}

private val CalendarEntryOrder = compareBy<CalendarEntry>(
    { if (it.schedule is CalendarSchedule.DateOnly) 0 else 1 },
    {
        when (val schedule = it.schedule) {
            is CalendarSchedule.DateOnly -> schedule.date.toEpochDay()
            is CalendarSchedule.Timed -> schedule.start.toEpochMilli()
        }
    },
    { it.title.lowercase() },
    { it.id.sourceType.ordinal },
    { it.id.sourceItemId },
)
