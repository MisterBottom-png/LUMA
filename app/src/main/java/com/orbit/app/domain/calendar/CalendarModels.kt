package com.orbit.app.domain.calendar

import com.orbit.app.data.local.entity.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarItemType {
    Note,
    Task,
    Reminder,
}

data class CalendarEntryId(
    val sourceType: CalendarItemType,
    val sourceItemId: Long,
)

sealed interface CalendarSchedule {
    data class DateOnly(val date: LocalDate) : CalendarSchedule
    data class Timed(val start: Instant) : CalendarSchedule
}

data class CalendarEntry(
    val id: CalendarEntryId,
    val title: String,
    val spaceId: Long?,
    val schedule: CalendarSchedule,
    val taskStatus: TaskStatus? = null,
    val completedAt: Long? = null,
    val reminderTargetAt: Long? = null,
    val notificationOffsetMinutes: Long? = null,
    val notificationAt: Long? = null,
    val notificationEnabled: Boolean? = null,
)

fun CalendarEntry.calendarDate(zoneId: ZoneId): LocalDate = when (val value = schedule) {
    is CalendarSchedule.DateOnly -> value.date
    is CalendarSchedule.Timed -> value.start.atZone(zoneId).toLocalDate()
}

data class CalendarDateRange(
    val startDate: LocalDate,
    val endDateExclusive: LocalDate,
    val zoneId: ZoneId,
) {
    init {
        require(endDateExclusive.isAfter(startDate)) { "Calendar range must not be empty" }
    }

    val startEpochDay: Long = startDate.toEpochDay()
    val endEpochDay: Long = endDateExclusive.toEpochDay()
    val startMillis: Long = startDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val endMillis: Long = endDateExclusive.atStartOfDay(zoneId).toInstant().toEpochMilli()

    companion object {
        fun day(date: LocalDate, zoneId: ZoneId): CalendarDateRange =
            CalendarDateRange(date, date.plusDays(1), zoneId)

        fun week(startDate: LocalDate, zoneId: ZoneId): CalendarDateRange =
            CalendarDateRange(startDate, startDate.plusWeeks(1), zoneId)

        fun month(monthDate: LocalDate, zoneId: ZoneId): CalendarDateRange {
            val firstDay = monthDate.withDayOfMonth(1)
            return CalendarDateRange(firstDay, firstDay.plusMonths(1), zoneId)
        }
    }
}
