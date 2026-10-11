# Reminder contract

An event time, reminder target time, and notification offset are separate values. All user-facing times retain 24-hour behavior. Reminders are scheduled locally and receivers are not exported.

The app asks for notification permission where required and handles denial without presenting a false success state. Date and timezone uncertainty is surfaced for user confirmation rather than guessed.

## Delivery

- Each reminder has one notification time: `snoozedUntil` if set, otherwise target time minus offset. `ReminderDeliveryPolicy` is the single place that decides whether that time is armed, delivered now (missed while the phone was off), or treated as already handled.
- Exact alarms are used when Android allows them (`SCHEDULE_EXACT_ALARM`); otherwise an inexact alarm. A WorkManager job is always queued as a backup path. Whichever path runs first claims the delivery with one atomic SQL update, so a reminder is shown once.
- Reboot, app update, exact-alarm permission change and app launch run a reconcile pass. Alarms are absolute instants, so a time-zone change does not move them. Reminders missed while the phone was off are shown once (a summary notification when more than three were missed); historical reminders are never re-fired after a migration or restore.
- Editing title, Space or labels never re-arms a reminder; only a change of timing, enablement or completion does. A time in the past is never scheduled.
- Settings > Capture & reminders shows whether notifications are allowed and whether exact timing is available, with links to the Android screens.

## What a save reports

Every place that saves a reminder (the quick question on Home, one tap and "Accept all" in Review, the sort sheet, and Brain Dump one by one or ticked) reports one outcome, read after the database commit (`ReminderSaveOutcome`):

- **Saved**: "Reminder set."
- **Saved, not scheduled** (the scheduler gave no token): "Reminder saved, but its notification couldn't be set up."
- **Saved, notifications blocked** (Tallele's notifications, or its reminder channel, are off): "Reminder saved, but notifications are off, so it won't ring.", with **Turn on**, which opens the same notification settings as Settings › Reminder delivery.

Reminders created through the capture flow are inserted inside the finalization transaction and armed only after it commits. When the sort sheet or Brain Dump asks for the notification permission, the outcome is read after the user answers.

## Notification actions

- **Done** completes the reminder. A repeating reminder instead moves to its next occurrence and stays active.
- **Snooze** moves only the notification time by 15 minutes; the target time and edited timestamp are unchanged.

## Repeating reminders

`repeatRule` stores the rule with the chosen time of day (and, for monthly, the chosen day), for example `weekly@09:00` or `monthly@09:00/31`. Rules: daily, weekdays (Mon–Fri), weekly, monthly (the 31st falls back to the last day of shorter months and returns to the 31st afterwards). Nothing is created for the user. A spring-forward night moves only that one occurrence; later ones return to the chosen time. A long-missed repeat moves to the next future occurrence rather than producing a backlog. Moving the reminder to a new time moves later occurrences too. An unknown token behaves as a one-off reminder and is kept unchanged.

The Calendar and the Home week strip show later occurrences ahead of time, marked with the repeat rule. They are computed for display only: one reminder row is stored and one notification is scheduled (for the current occurrence). Past days are never filled in for an overdue repeat, and opening a later occurrence opens the same reminder.
