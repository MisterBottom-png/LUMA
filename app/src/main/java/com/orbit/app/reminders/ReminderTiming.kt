package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity

data class ReminderOffsetOption(
    val minutes: Long,
    val label: String,
)

val reminderOffsetOptions = listOf(
    ReminderOffsetOption(0L, "At target time"),
    ReminderOffsetOption(5L, "5 min before"),
    ReminderOffsetOption(15L, "15 min before"),
    ReminderOffsetOption(30L, "30 min before"),
    ReminderOffsetOption(60L, "1 hour before"),
    ReminderOffsetOption(24L * 60L, "1 day before"),
)

fun reminderOffsetLabel(offsetMinutes: Long): String =
    reminderOffsetOptions.firstOrNull { it.minutes == offsetMinutes }?.label
        ?: if (offsetMinutes >= 0L) {
            "$offsetMinutes min before"
        } else {
            "Invalid notification offset"
        }

internal fun reminderNotificationTimeMillis(
    targetTimeMillis: Long,
    offsetMinutes: Long,
): Long? {
    if (targetTimeMillis <= 0L || offsetMinutes < 0L) return null
    val offsetMillis = try {
        Math.multiplyExact(offsetMinutes, MILLIS_PER_MINUTE)
    } catch (_: ArithmeticException) {
        return null
    }
    val notificationTime = try {
        Math.subtractExact(targetTimeMillis, offsetMillis)
    } catch (_: ArithmeticException) {
        return null
    }
    return notificationTime.takeIf { it > 0L }
}

internal fun ReminderEntity.notificationTimeMillis(): Long? =
    reminderNotificationTimeMillis(dueAt, notificationOffsetMinutes)

internal fun ReminderEntity.shouldScheduleNotification(): Boolean =
    notificationEnabled && completedAt == null && notificationTimeMillis() != null

internal fun ReminderEntity.matchesScheduledNotificationTime(expectedTimeMillis: Long): Boolean =
    shouldScheduleNotification() && notificationTimeMillis() == expectedTimeMillis

private const val MILLIS_PER_MINUTE = 60_000L
