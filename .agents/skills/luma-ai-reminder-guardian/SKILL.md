---
name: luma-ai-reminder-guardian
description: Use when changing LUMA Gemini behavior, structured AI output, uncertainty handling, dates, times, reminders, notifications, alarms, workers, or timezone behavior.
---

# LUMA AI and Reminder Guardian

## When to use

- Gemini prompts, schemas, parsing, fallback, classification, summaries, suggestions, or drafts.
- Date/time interpretation, reminders, notification offsets, alarms, workers, reboot/timezone handling, or permissions.
- Confirmation flows that turn AI proposals into important user data or scheduled effects.

## Do not use

- Pure UI layout or styling.
- Silent automation of important actions.
- Parser-only changes without tracing persistence, confirmation, scheduling, and rendered language.

## Product and time boundaries

- AI may suggest, classify, summarize, explain, and draft.
- AI must not silently create, edit, delete, archive, complete, schedule, or send important user data.
- Core capture and local behavior must remain usable when Gemini is disabled, slow, unavailable, malformed, or rate-limited.
- Use the device/local timezone unless the user explicitly supplies another timezone.
- Keep event/item target time, notification offset, and notification fire time separate.
- Preserve 24-hour behavior unless the user explicitly changes it.
- Ask for material missing information instead of confidently guessing; visible defaults remain editable.

## Workflow

1. Trace the current path through prompt/schema, parser, fallback, state, confirmation, persistence, scheduler/cancellation, and user-visible language.
2. Establish ambiguity, timezone, locale, permission, and failure behavior before editing.
3. Add deterministic tests for the exact phrases or payloads that expose the required behavior.
4. Implement the smallest patch while keeping local fallback and explicit confirmation intact.
5. Verify replacement, cancellation, completion, deletion, reboot, timezone change, daylight-saving change, app update, and permission denial where relevant.
6. Confirm persisted target time and offset reach the correct scheduled work without conflation.
7. Run focused parser/scheduler tests, affected compile checks, and broader reminder regression tests once.
8. Review the final diff for prompt drift, overconfident language, or silent side effects.

## Regression phrase set

Use relevant phrases from this set and add the reported phrase verbatim only when it contains no protected identity:

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

- Local fallback, uncertainty, and confirmation behavior are preserved.
- Target time, offset, and fire time remain independently represented.
- Persistence and actual scheduling/cancellation are tested together where affected.
- Timezone, restart, replacement, and permission paths are covered or reported as manual.
- User-visible language does not claim certainty the system does not have.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.