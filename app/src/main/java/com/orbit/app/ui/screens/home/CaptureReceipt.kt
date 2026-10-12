package com.orbit.app.ui.screens.home

import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.domain.capture.CaptureResolution
import com.orbit.app.reminders.ReminderSaveOutcomes
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** What the last thought became. Opening one goes to its item, or to the thought itself. */
enum class ReceiptItemType { Capture, Note, Task, Reminder }

data class ReceiptTarget(val type: ReceiptItemType, val id: Long)

/** The one button a receipt offers. */
enum class ReceiptAction(@param:StringRes val labelRes: Int) {
    View(R.string.receipt_action_view),
    Change(R.string.receipt_action_change),
    ChooseTime(R.string.receipt_action_choose_time),
    TurnOn(R.string.receipt_action_turn_on),
    TryAgain(R.string.receipt_action_try_again),
}

/**
 * The save receipt: one quiet line under the capture box about the last thought. It is
 * read from the stored thought and its item, never from a one-off event, so it is the
 * same after the app is killed and reopened.
 */
data class CaptureReceipt(
    val captureId: Long,
    val outcome: CaptureOutcome,
    val reason: CaptureOutcomeReason = CaptureOutcomeReason.None,
    /** True once act/ask/keep ran or the user decided; until then the thought is just "Saved." */
    val decided: Boolean = false,
    val title: String,
    val spaceName: String? = null,
    /** The item the thought became (Note, Task or Reminder), or null while it is a saved thought. */
    val itemType: ReceiptItemType? = null,
    val itemId: Long? = null,
    /** A timed task's time. */
    val taskDueAt: Long? = null,
    /** A date-only task's day. */
    val taskDateEpochDay: Long? = null,
    val reminderAt: Long? = null,
    /** The reminder's alarm exists (`notificationWorkId != null`). Read live, never from the outcome. */
    val reminderArmed: Boolean = false,
)

/** When a receipt line names a day, with or without a time of day. */
sealed interface ReceiptTime {
    data class Day(val epochDay: Long) : ReceiptTime
    data class Moment(val epochMillis: Long) : ReceiptTime
}

/** One receipt sentence: a string and what fills its placeholder. */
data class ReceiptLine(
    @param:StringRes val textRes: Int,
    val space: String? = null,
    val time: ReceiptTime? = null,
)

fun CaptureReceipt.line(): ReceiptLine = when (outcome) {
    // A decided Saved thought was kept as it is: it waits in To sort, and no note exists.
    CaptureOutcome.Saved -> ReceiptLine(if (decided) R.string.receipt_kept else R.string.receipt_saved)
    CaptureOutcome.Filed -> when (itemType) {
        ReceiptItemType.Task -> when {
            taskDueAt != null -> ReceiptLine(R.string.receipt_filed_task, time = ReceiptTime.Moment(taskDueAt))
            taskDateEpochDay != null -> ReceiptLine(R.string.receipt_filed_task, time = ReceiptTime.Day(taskDateEpochDay))
            spaceName != null -> ReceiptLine(R.string.receipt_filed_task_in_space, space = spaceName)
            else -> ReceiptLine(R.string.receipt_saved_task)
        }
        else -> if (spaceName != null) {
            ReceiptLine(R.string.receipt_filed_note, space = spaceName)
        } else {
            ReceiptLine(R.string.receipt_saved_note)
        }
    }
    // "Reminder set" only while the alarm really exists.
    CaptureOutcome.ReminderSet -> if (reminderArmed && reminderAt != null) {
        ReceiptLine(R.string.receipt_reminder_set, time = ReceiptTime.Moment(reminderAt))
    } else {
        ReceiptLine(R.string.receipt_reminder_not_armed)
    }
    CaptureOutcome.NeedsYou -> when (reason) {
        CaptureOutcomeReason.TimeUnclear -> ReceiptLine(R.string.receipt_needs_time)
        CaptureOutcomeReason.TimePassed -> ReceiptLine(R.string.receipt_time_passed)
        CaptureOutcomeReason.NotificationsOff -> ReceiptLine(R.string.receipt_notifications_off)
        CaptureOutcomeReason.NotScheduled -> ReceiptLine(R.string.receipt_not_scheduled)
        CaptureOutcomeReason.TypeUnclear, CaptureOutcomeReason.None -> ReceiptLine(R.string.receipt_saved)
    }
}

fun CaptureReceipt.action(): ReceiptAction = when (outcome) {
    CaptureOutcome.Saved -> ReceiptAction.View
    CaptureOutcome.Filed -> ReceiptAction.Change
    CaptureOutcome.ReminderSet -> if (reminderArmed) ReceiptAction.Change else ReceiptAction.TryAgain
    CaptureOutcome.NeedsYou -> when (reason) {
        CaptureOutcomeReason.TimeUnclear, CaptureOutcomeReason.TimePassed -> ReceiptAction.ChooseTime
        CaptureOutcomeReason.NotificationsOff -> ReceiptAction.TurnOn
        CaptureOutcomeReason.NotScheduled -> ReceiptAction.TryAgain
        CaptureOutcomeReason.TypeUnclear, CaptureOutcomeReason.None -> ReceiptAction.View
    }
}

/** View and Change open the item the thought became, or the thought while it is only saved. */
fun CaptureReceipt.openTarget(): ReceiptTarget =
    if (itemType != null && itemId != null) ReceiptTarget(itemType, itemId) else ReceiptTarget(ReceiptItemType.Capture, captureId)

/** The reminder this receipt is about, if one exists (for Try again and for a new time). */
val CaptureReceipt.reminderId: Long?
    get() = itemId?.takeIf { itemType == ReceiptItemType.Reminder }

/**
 * "today 09:00", "tomorrow", "Wed, Oct 15, 09:00": a receipt names today and tomorrow in
 * words and other days by date. [todayWord] and [tomorrowWord] are the app's own words,
 * written in lower case inside the sentence.
 */
fun formatReceiptTime(
    time: ReceiptTime,
    today: LocalDate,
    zone: ZoneId,
    timeFormat: OrbitTimeFormat,
    todayWord: String,
    tomorrowWord: String,
): String {
    val day = when (time) {
        is ReceiptTime.Day -> LocalDate.ofEpochDay(time.epochDay)
        is ReceiptTime.Moment -> Instant.ofEpochMilli(time.epochMillis).atZone(zone).toLocalDate()
    }
    val word = when (day) {
        today -> todayWord
        today.plusDays(1) -> tomorrowWord
        else -> null
    }?.replaceFirstChar { it.lowercase(timeFormat.locale) }
    return when (time) {
        is ReceiptTime.Day -> word ?: timeFormat.formatDate(day)
        is ReceiptTime.Moment -> word?.let { "$it ${timeFormat.formatTime(time.epochMillis)}" }
            ?: timeFormat.formatWeekdayDateTime(time.epochMillis)
    }
}

/**
 * Builds the receipt for one thought from the database: the thought's stored outcome,
 * and, for a reminder, the live reminder row, so "Reminder set" follows whether the
 * alarm exists right now.
 */
class CaptureReceiptReader(
    private val captureRepository: CaptureRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val spaceRepository: SpaceRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(captureId: Long): Flow<CaptureReceipt?> = captureRepository.observeById(captureId)
        .flatMapLatest { capture ->
            when {
                capture == null || capture.status == CaptureStatus.Archived -> flowOf(null)
                capture.isSortedIntoReminder() -> reminderRepository.observeAll()
                    .map { reminders ->
                        reminders.firstOrNull { it.id == capture.linkedItemId && it.linkedCaptureId == capture.id }
                    }
                    .distinctUntilChanged()
                    .map { reminder -> reminder?.let { reminderReceipt(capture, it) } }
                else -> flow { emit(receipt(capture)) }
            }
        }
        .distinctUntilChanged()

    private fun CaptureEntity.isSortedIntoReminder(): Boolean =
        status == CaptureStatus.Processed && linkedItemId != null &&
            (outcome == CaptureOutcome.ReminderSet || outcome == CaptureOutcome.NeedsYou)

    private suspend fun reminderReceipt(capture: CaptureEntity, reminder: ReminderEntity): CaptureReceipt? {
        // A reminder that already rang or was done has nothing left to report.
        val finished = reminder.completedAt != null ||
            (reminder.deliveredNotificationAt != null && reminder.notificationWorkId == null)
        if (capture.outcome == CaptureOutcome.ReminderSet && finished) return null
        return capture.baseReceipt().copy(
            title = reminder.title,
            spaceName = spaceName(reminder.spaceId),
            itemType = ReceiptItemType.Reminder,
            itemId = reminder.id,
            reminderAt = reminder.dueAt,
            reminderArmed = reminder.notificationWorkId != null,
        )
    }

    private suspend fun receipt(capture: CaptureEntity): CaptureReceipt? {
        val linkedId = capture.linkedItemId
        if (capture.status != CaptureStatus.Processed) return capture.baseReceipt()
        // A finished Brain Dump has no one item: its items were each saved on their own.
        if (linkedId == null) return null
        // Filing stores what the thought became in suggestedType next to the link (note
        // and task ids can be equal); older rows fall back to checking what exists.
        val taskFirst = capture.suggestedType == SuggestedItemType.Task || capture.suggestedType == SuggestedItemType.MondayItem
        // Null when the item was removed elsewhere: there is nothing true left to say about it.
        return if (taskFirst) {
            taskReceipt(capture, linkedId) ?: noteReceipt(capture, linkedId)
        } else {
            noteReceipt(capture, linkedId) ?: taskReceipt(capture, linkedId)
        }
    }

    private suspend fun noteReceipt(capture: CaptureEntity, id: Long): CaptureReceipt? =
        noteRepository.getById(id)?.let { note ->
            capture.baseReceipt().copy(
                title = note.title,
                spaceName = spaceName(note.spaceId),
                itemType = ReceiptItemType.Note,
                itemId = note.id,
            )
        }

    private suspend fun taskReceipt(capture: CaptureEntity, id: Long): CaptureReceipt? =
        taskRepository.getById(id)?.let { task ->
            capture.baseReceipt().copy(
                title = task.title,
                spaceName = spaceName(task.spaceId),
                itemType = ReceiptItemType.Task,
                itemId = task.id,
                taskDueAt = task.dueAt,
                taskDateEpochDay = task.scheduledDateEpochDay,
            )
        }

    private suspend fun spaceName(spaceId: Long?): String? =
        spaceId?.let { spaceRepository.getById(it) }?.takeUnless { it.archived }?.name

    private fun CaptureEntity.baseReceipt() = CaptureReceipt(
        captureId = id,
        outcome = outcome,
        reason = outcomeReason,
        decided = decidedAt != null,
        title = rawText,
    )
}

/**
 * "Try again" on a reminder whose alarm is not set: arms the stored reminder again, then
 * stores on its thought what that achieved, so the receipt shows the new truth.
 */
suspend fun rearmReceiptReminder(
    reminderRepository: ReminderRepository,
    outcomes: ReminderSaveOutcomes,
    resolution: CaptureResolution,
    captureId: Long,
    reminderId: Long,
) {
    reminderRepository.scheduleStored(reminderId)
    resolution.recordReminderOutcome(captureId, reminderId, outcomes.of(reminderId))
}
