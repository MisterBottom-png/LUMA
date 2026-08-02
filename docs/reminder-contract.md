# Reminder contract

An event time, reminder target time, and notification offset are separate values. All user-facing times retain 24-hour behavior. Reminders are scheduled locally and receivers are not exported.

The app asks for notification permission where required and handles denial without presenting a false success state. Reboot and package-replacement handling restore valid scheduled reminders. Date and timezone uncertainty is surfaced for user confirmation rather than guessed.
