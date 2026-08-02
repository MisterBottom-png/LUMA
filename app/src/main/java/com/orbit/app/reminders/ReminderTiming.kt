package com.orbit.app.reminders

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orbit.app.R
import com.orbit.app.data.local.entity.ReminderEntity

data class ReminderOffsetOption(
    val minutes: Long,
    @param:StringRes val labelResId: Int,
) {
    @get:Composable
    val label: String
        get() = stringResource(labelResId)
}

val reminderOffsetOptions = listOf(
    ReminderOffsetOption(0L, R.string.reminder_offset_around_target),
    ReminderOffsetOption(5L, R.string.reminder_offset_about_five_minutes),
    ReminderOffsetOption(15L, R.string.reminder_offset_about_fifteen_minutes),
    ReminderOffsetOption(30L, R.string.reminder_offset_about_thirty_minutes),
    ReminderOffsetOption(60L, R.string.reminder_offset_about_one_hour),
    ReminderOffsetOption(24L * 60L, R.string.reminder_offset_about_one_day),
)

@Composable
fun reminderOffsetLabel(offsetMinutes: Long): String {
    val knownLabel = reminderOffsetOptions
        .firstOrNull { it.minutes == offsetMinutes }
        ?.labelResId
    return when {
        knownLabel != null -> stringResource(knownLabel)
        offsetMinutes >= 0L -> stringResource(
            R.string.reminder_offset_about_custom_minutes,
            offsetMinutes,
        )

        else -> stringResource(R.string.reminder_offset_invalid)
    }
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
