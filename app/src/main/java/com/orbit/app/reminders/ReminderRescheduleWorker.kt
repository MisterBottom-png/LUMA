package com.orbit.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.orbit.app.data.local.dao.ReminderDao

/**
 * Re-establishes reminder delivery after a restart, an app update, a force-stop or
 * a change of the exact-alarm permission. Future reminders are scheduled again;
 * reminders that were missed meanwhile are surfaced once (see [ReminderDeliveryPolicy]).
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
        )
        return if (result.failures == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val UniqueWorkName = "luma_reschedule_reminders"

        /** Queues a reconciliation; repeated requests while one is pending are merged. */
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
): ReminderReconcileResult {
    var scheduled = 0
    var delivered = 0
    var handled = 0
    var failures = 0
    val recent = mutableListOf<Pair<Long, Long>>()
    val missed = mutableListOf<Pair<Long, Long>>()
    dao.getAll().forEach { reminder ->
        when (val action = ReminderDeliveryPolicy.reconcile(reminder, now)) {
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
                if (token != null) scheduled += 1
            }
            is ReconcileAction.DeliverNow -> {
                if (action.missed) {
                    missed += reminder.id to action.notificationTime
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
