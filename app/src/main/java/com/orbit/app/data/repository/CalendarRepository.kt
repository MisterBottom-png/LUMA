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
import com.orbit.app.reminders.reminderNotificationTimeMillis
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface CalendarRepository {
    fun observeRange(range: CalendarDateRange): Flow<List<CalendarEntry>>
}

class RoomCalendarRepository(
    private val noteDao: NoteDao,
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
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
    ) { notes, tasks, reminders ->
        buildList {
            notes.mapNotNullTo(this) { it.toCalendarEntryOrNull() }
            tasks.mapNotNullTo(this) { it.toCalendarEntryOrNull() }
            reminders.mapTo(this) { it.toCalendarEntry() }
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

internal fun ReminderEntity.toCalendarEntry(): CalendarEntry = CalendarEntry(
    id = CalendarEntryId(CalendarItemType.Reminder, id),
    title = title,
    spaceId = spaceId,
    schedule = CalendarSchedule.Timed(Instant.ofEpochMilli(dueAt)),
    completedAt = completedAt,
    reminderTargetAt = dueAt,
    notificationOffsetMinutes = notificationOffsetMinutes,
    notificationAt = reminderNotificationTimeMillis(dueAt, notificationOffsetMinutes),
    notificationEnabled = notificationEnabled,
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
