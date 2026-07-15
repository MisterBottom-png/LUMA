package com.orbit.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            RescheduleWorkName,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderRescheduleWorker>().build(),
        )
    }

    private companion object {
        const val RescheduleWorkName = "luma_reschedule_reminders"
    }
}
