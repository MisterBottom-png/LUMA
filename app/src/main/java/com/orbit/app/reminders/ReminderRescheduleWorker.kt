package com.orbit.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.orbit.app.OrbitApplication
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.domain.capture.LinkedCaptureOutcomes
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import java.time.ZoneId

/**
 * Re-establishes reminder delivery after a restart, an app update, a force-stop,
 * a change of the exact-alarm permission, or a change of clock or time zone. Future
 * reminders are scheduled again; reminders that were missed meanwhile are surfaced
 * once (see [ReminderDeliveryPolicy]).
 *
 * The class name is persisted by WorkManager and must not change.
 */
class ReminderRescheduleWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val result = reconcileReminders(
            dao = ReminderNotifier.reminderDao(applicationContext),
            scheduler = ReminderNotifier.scheduler(applicationContext),
            now = System.currentTimeMillis(),
            deliver = { reminderId, time, missed ->
                ReminderNotifier.showReminderNotification(
                    context = applicationContext,
                    reminderId = reminderId,
                    expectedNotificationTime = time,
                    deliveredBy = ReminderDeliveryPath.Reconcile,
                    missed = missed,
                )
            },
            deliverSummary = { missed ->
                ReminderNotifier.showMissedSummary(applicationContext, missed)
            },
            linkedCaptures = linkedCaptureOutcomes(),
        )
        return if (result.failures == 0) Result.success() else Result.retry()
    }

    /** Keeps each thought's stored outcome in step with its reminder. */
    private fun linkedCaptureOutcomes(): LinkedCaptureOutcomes {
        val database = (applicationContext as? OrbitApplication)?.container?.database
            ?: OrbitDatabase.getInstance(applicationContext)
        return LinkedCaptureOutcomes(
            captureRepository = RoomCaptureRepository(database.captureDao()),
            transaction = RoomCaptureFinalizationTransaction(database),
            canNotify = {
                ReminderCapabilities.notificationsAllowed(applicationContext) &&
                    ReminderCapabilities.reminderChannelEnabled(applicationContext)
            },
        )
    }

    companion object {
        private const val UniqueWorkName = "luma_reschedule_reminders"

        /**
         * Queues a reconciliation; repeated requests collapse into one. A pass replaced
         * while running gives back any claim it had not shown yet (see ReminderNotifier),
         * and a repeat shown just before is moved on by the next pass, so nothing is lost.
         */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UniqueWorkName,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReminderRescheduleWorker>().build(),
            )
        }
    }
}

internal data class ReminderReconcileResult(
    val scheduled: Int,
    val delivered: Int,
    val markedHandled: Int,
    val failures: Int,
)

/** Pure reconciliation step, separated from WorkManager so it can be tested directly. */
internal suspend fun reconcileReminders(
    dao: ReminderDao,
    scheduler: ReminderScheduler,
    now: Long,
    deliver: suspend (reminderId: Long, notificationTime: Long, missed: Boolean) -> ReminderDeliveryOutcome,
    deliverSummary: suspend (List<Pair<Long, Long>>) -> ReminderDeliveryOutcome,
    /** Updates the thought a reminder came from; null leaves thoughts untouched. */
    linkedCaptures: LinkedCaptureOutcomes? = null,
    zoneId: ZoneId = ZoneId.systemDefault(),
): ReminderReconcileResult {
    var scheduled = 0
    var delivered = 0
    var handled = 0
    var failures = 0
    val recent = mutableListOf<Pair<Long, Long>>()
    val missed = mutableListOf<Pair<Long, Long>>()
    dao.getAll().forEach { stored ->
        // A repeating reminder rings at its chosen local time, also after a zone change.
        val reminder = ReminderRepeats.reanchoredToZone(stored, now, zoneId)
            ?.also { dao.update(it) }
            ?: stored
        // A repeating reminder whose occurrence was shown (or is long past) moves on to its
        // next occurrence here too, for example after the app was stopped right after showing it.
        val action = ReminderDeliveryPolicy.reconcile(reminder, now)
        val shownTime = reminder.notificationTimeMillis()
        val occurrenceDone = reminder.repeat != null && reminder.completedAt == null && (
            (action == ReconcileAction.None && shownTime != null && reminder.deliveredNotificationAt == shownTime) ||
                action is ReconcileAction.MarkHandled
            )
        if (occurrenceDone) {
            val next = ReminderRepeats.afterOccurrenceShown(reminder, shownTime ?: now, now, zoneId)
            if (next != null) {
                dao.update(next)
                val token = runCatching { scheduler.reschedule(next) }.onFailure { failures += 1 }.getOrNull()
                dao.updateNotificationWorkId(next.id, token)
                if (token != null) scheduled += 1
                return@forEach
            }
        }
        when (action) {
            ReconcileAction.None -> {
                if (!reminder.shouldScheduleNotification() && reminder.notificationWorkId != null) {
                    runCatching { scheduler.cancel(reminder.id) }
                    dao.updateNotificationWorkId(reminder.id, null)
                }
            }
            is ReconcileAction.Schedule -> {
                val token = runCatching { scheduler.reschedule(reminder) }
                    .onFailure { failures += 1 }
                    .getOrNull()
                dao.updateNotificationWorkId(reminder.id, token)
                if (token != null) {
                    scheduled += 1
                    linkedCaptures?.let { runCatching { it.armed(reminder, now) } }
                }
            }
            is ReconcileAction.DeliverNow -> {
                if (action.missed) {
                    missed += reminder.id to action.notificationTime
                    linkedCaptures?.let { runCatching { it.missed(reminder, now) } }
                } else {
                    recent += reminder.id to action.notificationTime
                }
            }
            is ReconcileAction.MarkHandled -> {
                dao.markHandled(reminder.id, action.notificationTime)
                handled += 1
            }
        }
    }
    recent.forEach { (id, time) ->
        if (deliver(id, time, false) == ReminderDeliveryOutcome.Shown) delivered += 1
    }
    if (missed.size <= ReminderDeliveryPolicy.MaxIndividualMissedNotifications) {
        missed.forEach { (id, time) ->
            if (deliver(id, time, true) == ReminderDeliveryOutcome.Shown) delivered += 1
        }
    } else if (deliverSummary(missed) == ReminderDeliveryOutcome.Shown) {
        delivered += missed.size
    }
    return ReminderReconcileResult(scheduled, delivered, handled, failures)
}
