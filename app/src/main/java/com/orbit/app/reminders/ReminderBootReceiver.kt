package com.orbit.app.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-arms reminders whenever Android may have dropped or shifted them: after a reboot,
 * an app update, an exact-alarm permission change, or a change of clock or time zone.
 * Every case runs the same reconciliation, which merges repeated requests.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            // A one-off reminder is an instant, so a new zone does not move it;
            // a repeating one is re-anchored to its chosen local time.
            Intent.ACTION_TIMEZONE_CHANGED,
            // "android.intent.action.TIME_SET": the clock was set by hand or by the network.
            Intent.ACTION_TIME_CHANGED,
            -> ReminderRescheduleWorker.enqueue(context)
        }
    }
}
