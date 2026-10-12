# LUMA AI Behavior Contract

AI is helpful, but not sovereign. Small mercy.

## Allowed AI behavior

AI may:

```text
- summarize
- classify
- suggest
- explain
- extract possible dates/times/tasks
- prepare drafts
- ask missing-information questions
- suggest related thoughts/items
- suggest duplicate candidates
- suggest people/project tags
```

## Automatic filing (owner decision, 12 October 2026)

A newly saved thought may be filed **by itself** as exactly one note, task or reminder
when the act/ask/keep rule (`domain/capture/CaptureDecision.kt`) says it is clear. This
applies to local and Gemini suggestions alike. The boundary:

```text
- only High confidence acts; anything less is kept (or one question is asked)
- a reminder is set only for one clear future time; a missing or past time is asked
- negated, conditional or quoted thoughts are never acted on (IntentGuard), in EN/ET/RU
- a Brain Dump, a failed analysis and a thought added for a Calendar day are never filed by themselves
- every automatic filing shows a receipt with Change, and can be undone
- "Sort right after saving" (Settings > Saving) pauses automatic filing entirely
```

Everything else below still requires confirmation. In particular AI never edits,
completes, archives, deletes, merges, moves or sends existing data, never creates more
than one item from one thought, and never changes learned rules by itself.

## Not allowed without confirmation

AI must not silently:

```text
- create important tasks (beyond the automatic-filing boundary above)
- schedule reminders (beyond the automatic-filing boundary above)
- complete items
- archive items
- delete items
- send messages
- mutate user data
- merge duplicates
- change learned rules
```

## Missing-information prompt pattern

When required details are missing:

```text
1. Show the AI's proposed interpretation.
2. Ask only for missing details.
3. Let the user confirm, edit, or dismiss.
4. Save/schedule only after confirmation.
```

## Reminder parsing contract

Keep these separate:

```text
- target/event time: when the thing is supposed to happen
- notification/reminder time: when the user should be reminded
- reminder offset: e.g. one hour earlier
```

Example:

```text
Input: send a package tomorrow at 10, remind me one hour earlier
Target time: tomorrow 10:00
Reminder notification time: tomorrow 09:00
Offset: -1 hour
```

## Learning/memory contract

AI may learn from accepted/rejected/corrected suggestions only when the app has a visible rule-management path or the agent package has a controlled learning document.

Learned rules should be editable, deletable, and reviewable.
