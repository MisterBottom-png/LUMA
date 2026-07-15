# LUMA post-MVP briefs

This file preserves useful add-on knowledge without loading it into every MVP task.

## Calendar V1

Goal: a calm local calendar for dated LUMA items.

Included:

- compact week strip or mini calendar on Home;
- tap to open a full LUMA calendar;
- month navigation and selected-day items;
- local reminders/tasks/dated items;
- light and dark themes.

Excluded initially: external sync, accounts, shared calendars, complex recurring editing, drag-and-drop scheduling.

## Language switching

- Persist a language setting.
- Use Android string resources rather than hardcoded UI strings.
- Prioritize navigation, Home, capture, Spaces, Review, Situation AI, Settings, item detail, and calendar.
- Fall back safely for missing strings.
- AI output should follow the selected language where practical.

## Situation AI V2

Situation AI should answer what matters now, why, and what optional next step may help.

Suggested sections:

```text
Now
Later today
Missing information
Suggested next steps
```

Each suggestion should include a short title, reason/source, action, and dismiss/inspect option. Suggestions remain grounded in local items and never silently mutate data.

## Advanced local memory

LUMA may learn from accepted/rejected suggestions and user corrections, but learned rules must be local, inspectable, editable, deletable, and optional.

Do not infer sensitive traits, emotional diagnoses, medical conclusions, or hidden permanent profiles.

Related-thought and duplicate detection remain suggestions. Never merge automatically. People/project detection stays lightweight and editable. Pattern insights remain non-judgmental.
