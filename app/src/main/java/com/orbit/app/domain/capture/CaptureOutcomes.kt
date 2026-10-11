package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.domain.usecase.CaptureFinalizationTransaction
import com.orbit.app.reminders.ReminderSaveOutcome
import com.orbit.app.reminders.notificationTimeMillis

/** One stored answer to "what did Tallele do with this thought?". */
data class StoredCaptureOutcome(
    val outcome: CaptureOutcome,
    val reason: CaptureOutcomeReason = CaptureOutcomeReason.None,
)

/**
 * What a saved reminder means for its thought. Only a reminder that can reach the
 * user is [CaptureOutcome.ReminderSet]; one that cannot ring needs the user.
 */
fun ReminderSaveOutcome.toCaptureOutcome(): StoredCaptureOutcome = when (this) {
    ReminderSaveOutcome.Saved -> StoredCaptureOutcome(CaptureOutcome.ReminderSet)
    ReminderSaveOutcome.SavedNotificationsBlocked ->
        StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotificationsOff)
    ReminderSaveOutcome.SavedNotScheduled ->
        StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.NotScheduled)
}

/**
 * The thought with [stored] as its outcome. Writing an outcome is a decision, so
 * [CaptureEntity.decidedAt] is stamped if it was still pending.
 */
fun CaptureEntity.withOutcome(stored: StoredCaptureOutcome, at: Long): CaptureEntity = copy(
    outcome = stored.outcome,
    outcomeReason = stored.reason,
    outcomeAt = at,
    decidedAt = decidedAt ?: at,
)

/** True when [reminder] is the item this thought was sorted into. */
fun CaptureEntity.isSortedInto(reminder: ReminderEntity): Boolean =
    status == CaptureStatus.Processed && linkedItemId == reminder.id && reminder.linkedCaptureId == id

/**
 * Keeps a thought's outcome in step with what later happens to its reminder, from
 * the reminder reconciliation that runs on boot, update and app start.
 */
class LinkedCaptureOutcomes(
    private val captureRepository: CaptureRepository,
    private val transaction: CaptureFinalizationTransaction,
    private val canNotify: () -> Boolean,
) {
    /**
     * The reminder is armed again. A thought that was waiting because notifications
     * were off, or because the alarm could not be set, is a set reminder once Android
     * lets it ring. So is one whose time had passed, once the reminder has a time
     * ahead again (a repeat moved on to its next occurrence).
     */
    suspend fun armed(reminder: ReminderEntity, now: Long) {
        if (!canNotify()) return
        val timeAhead = reminder.notificationTimeMillis()?.let { it > now } == true
        update(reminder) { capture ->
            val waiting = capture.outcome == CaptureOutcome.NeedsYou && (
                capture.outcomeReason in DeliveryReasons ||
                    (capture.outcomeReason == CaptureOutcomeReason.TimePassed && timeAhead)
                )
            if (waiting) capture.withOutcome(StoredCaptureOutcome(CaptureOutcome.ReminderSet), now) else null
        }
    }

    /** The reminder's time passed while it could not ring (for example the phone was off). */
    suspend fun missed(reminder: ReminderEntity, now: Long) {
        update(reminder) { capture ->
            val reminderOutcome = capture.outcome == CaptureOutcome.ReminderSet ||
                (capture.outcome == CaptureOutcome.NeedsYou && capture.outcomeReason in DeliveryReasons)
            if (reminderOutcome) {
                capture.withOutcome(StoredCaptureOutcome(CaptureOutcome.NeedsYou, CaptureOutcomeReason.TimePassed), now)
            } else {
                null
            }
        }
    }

    private suspend fun update(reminder: ReminderEntity, change: (CaptureEntity) -> CaptureEntity?) {
        val captureId = reminder.linkedCaptureId ?: return
        transaction.run {
            val capture = captureRepository.getById(captureId) ?: return@run
            if (!capture.isSortedInto(reminder)) return@run
            change(capture)?.let { captureRepository.update(it) }
        }
    }

    private companion object {
        val DeliveryReasons = setOf(CaptureOutcomeReason.NotificationsOff, CaptureOutcomeReason.NotScheduled)
    }
}
