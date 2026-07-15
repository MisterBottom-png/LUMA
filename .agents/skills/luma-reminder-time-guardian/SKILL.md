---
name: luma-reminder-time-guardian
description: Use for reminders, notifications, natural-language date/time parsing, timezone behavior, 24-hour time, notification offsets, calendar/date UI, and ambiguity around schedule intent.
---

# LUMA Reminder Time Guardian

Use this skill whenever a task touches dates, times, reminders, notifications, or calendar interpretation.

## Core contract

Keep these separate:

```text
target/event time      = when the thing happens
notification time      = when the user is reminded
reminder offset        = e.g. one hour earlier
timezone               = local device/user timezone
format                 = display/parsing preference, usually 24-hour for LUMA
```

Example:

```text
Input: send a package tomorrow at 10, remind me one hour earlier
Target/event time: tomorrow 10:00
Notification time: tomorrow 09:00
Reminder offset: -1 hour
```

## Protected behavior

- Preserve 24-hour time.
- Prefer missing-information prompts over guessing.
- Do not silently schedule reminders without confirmation if the interpretation is uncertain.
- Do not add external calendar sync unless explicitly requested.

## Ambiguity handling

If input lacks required detail:

```text
- Missing date → ask date.
- Missing time → ask time.
- Ambiguous “morning” → propose a default only if app has a defined preference, otherwise ask.
- Ambiguous reminder offset → ask.
- Conflicting times → ask.
```

## Regression examples

Check cases like:

```text
- tomorrow at 10
- tomorrow at 10 in the morning
- remind me one hour earlier
- remind me at 09:00
- next Friday
- evening
- no time given
- no date given
```

## Calendar rule

Home may show compact calendar/date awareness. Full calendar detail should live in a secondary screen/modal unless explicitly approved.
