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

## Not allowed without confirmation

AI must not silently:

```text
- create important tasks
- schedule reminders
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
