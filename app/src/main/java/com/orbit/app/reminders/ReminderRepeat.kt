package com.orbit.app.reminders

import com.orbit.app.data.local.entity.ReminderEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** How a reminder repeats. */
enum class ReminderRepeat(val storageToken: String) {
    Daily("daily"),
    Weekdays("weekdays"),
    Weekly("weekly"),
    Monthly("monthly"),
    ;

    /** The day after [date] on which this repeat falls. */
    internal fun nextDate(date: LocalDate, anchor: LocalDate): LocalDate = when (this) {
        Daily -> date.plusDays(1)
        Weekdays -> generateSequence(date.plusDays(1)) { it.plusDays(1) }
            .first { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        Weekly -> date.plusWeeks(1)
        // Counted from the anchor so the 31st becomes the 30th or 28th only where needed.
        Monthly -> generateSequence(1L) { it + 1 }
            .map { anchor.plusMonths(it) }
            .first { it.isAfter(date) }
    }

    companion object {
        fun fromStorage(token: String?): ReminderRepeat? = RepeatSpec.parse(token)?.rule
    }
}

/**
 * The stored repeat: "weekly@08:30". The time is the one the user chose, kept apart
 * from dueAt so a daylight-saving jump on one night never shifts later occurrences.
 * A token this version does not understand is kept as-is and acts as "no repeat".
 */
data class RepeatSpec(val rule: ReminderRepeat, val timeOfDay: LocalTime?) {
    fun toStorage(): String =
        rule.storageToken + (timeOfDay?.let { "@" + it.format(TimeFormat) } ?: "")

    companion object {
        private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm")

        fun parse(token: String?): RepeatSpec? {
            if (token.isNullOrBlank()) return null
            val rule = ReminderRepeat.entries.firstOrNull { it.storageToken == token.substringBefore('@') }
                ?: return null
            val time = token.substringAfter('@', "").takeIf { it.isNotEmpty() }
                ?.let { runCatching { LocalTime.parse(it, TimeFormat) }.getOrNull() }
            return RepeatSpec(rule, time)
        }

        fun forReminder(rule: ReminderRepeat, dueAt: Long, zoneId: ZoneId): RepeatSpec =
            RepeatSpec(rule, Instant.ofEpochMilli(dueAt).atZone(zoneId).toLocalTime().withSecond(0).withNano(0))
    }
}

val ReminderEntity.repeat: ReminderRepeat? get() = ReminderRepeat.fromStorage(repeatRule)

object ReminderRepeats {
    private const val MaxSteps = 4_000

    /** The first occurrence after both the current one and [now], at the chosen time of day. */
    fun nextOccurrence(reminder: ReminderEntity, now: Long, zoneId: ZoneId): Long? {
        val spec = RepeatSpec.parse(reminder.repeatRule) ?: return null
        val current = Instant.ofEpochMilli(reminder.dueAt).atZone(zoneId)
        val time = spec.timeOfDay ?: current.toLocalTime()
        val anchor = current.toLocalDate()
        var date = anchor
        repeat(MaxSteps) {
            date = spec.rule.nextDate(date, anchor)
            // A time skipped by a spring-forward jump moves forward for that day only.
            val candidate = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
            if (candidate > now && candidate > reminder.dueAt) return candidate
        }
        return null
    }

    /**
     * "Done" on a reminder: a repeating one moves to its next occurrence and stays
     * active; any other one is completed.
     */
    fun markDone(reminder: ReminderEntity, now: Long, zoneId: ZoneId = ZoneId.systemDefault()): ReminderEntity {
        val next = nextOccurrence(reminder, now, zoneId)
            ?: return reminder.copy(completedAt = now, updatedAt = now)
        return reminder.copy(
            dueAt = next,
            completedAt = null,
            deliveredNotificationAt = null,
            snoozedUntil = null,
            updatedAt = now,
        )
    }

    /**
     * When the user moves a repeating reminder to another time, later occurrences
     * follow the new time. Moving to the next occurrence (same chosen time, or that
     * time pushed forward by a daylight-saving gap) keeps the chosen time.
     */
    fun reanchored(previous: ReminderEntity?, updated: ReminderEntity, zoneId: ZoneId = ZoneId.systemDefault()): ReminderEntity {
        if (previous == null || previous.dueAt == updated.dueAt || updated.repeatRule != previous.repeatRule) return updated
        val spec = RepeatSpec.parse(updated.repeatRule) ?: return updated
        val newTime = Instant.ofEpochMilli(updated.dueAt).atZone(zoneId)
        val chosen = spec.timeOfDay ?: return updated.copy(repeatRule = RepeatSpec.forReminder(spec.rule, updated.dueAt, zoneId).toStorage())
        val chosenOnThatDay = newTime.toLocalDate().atTime(chosen).atZone(zoneId)
        val keepsChosenTime = chosenOnThatDay.toInstant() == newTime.toInstant()
        return if (keepsChosenTime) updated else updated.copy(repeatRule = RepeatSpec.forReminder(spec.rule, updated.dueAt, zoneId).toStorage())
    }
}
