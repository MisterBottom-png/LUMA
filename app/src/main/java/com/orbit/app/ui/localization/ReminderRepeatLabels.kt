package com.orbit.app.ui.localization

import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.reminders.ReminderRepeat

/** "Every week", "Does not repeat" and so on, for Item Details and Calendar. */
@StringRes
internal fun ReminderRepeat?.labelRes(): Int = when (this) {
    null -> R.string.reminder_repeat_never
    ReminderRepeat.Daily -> R.string.reminder_repeat_daily
    ReminderRepeat.Weekdays -> R.string.reminder_repeat_weekdays
    ReminderRepeat.Weekly -> R.string.reminder_repeat_weekly
    ReminderRepeat.Monthly -> R.string.reminder_repeat_monthly
}
