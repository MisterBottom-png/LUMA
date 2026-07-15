---
name: luma-ai-reminder-guardian
description: Safely change LUMA Gemini behavior, structured AI output, suggestions, missing-information prompts, dates, times, reminders, notifications, alarms, workers, and timezone handling.
---

# LUMA AI and Reminder Guardian

## AI boundaries

AI may suggest, classify, summarize, explain, and prepare drafts.

AI must not silently create, edit, delete, archive, complete, schedule, or send important user data. Make uncertainty visible and require confirmation for important AI-proposed actions.

Local capture and core app behavior must remain usable when Gemini is unavailable, slow, malformed, or rate-limited.

## Reminder/date rules

- Use the device/local timezone unless the user explicitly supplies another timezone.
- Keep the item/event target time separate from notification offsets and notification fire time.
- Preserve 24-hour behavior unless explicitly changed.
- Ask for missing date/time information instead of guessing when ambiguity matters.
- Visible defaults must remain editable.
- Rescheduling and cancellation must update the correct alarm/work request and stored state.
- Consider reboot, timezone change, daylight-saving change, app update, and permission denial where relevant.

## Parsing example

Input:

```text
Send a package tomorrow at 10 in the morning and remind me an hour earlier.
```

Expected:

```text
item target: tomorrow 10:00 local time
notification offset: 60 minutes
notification fire time: tomorrow 09:00 local time
```

## Required regression phrases

```text
tomorrow at 10 in the morning
tomorrow at 10pm
remind me an hour earlier
remind me 30 minutes before
next Monday at 9
this evening
tonight
in 2 hours
after lunch tomorrow
before work tomorrow
```

## Verification

Trace prompt/schema parsing, fallback, persistence, confirmation UI, scheduling/cancellation, and rendered user language. Never validate only the parser while ignoring whether the actual notification is scheduled correctly.
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

