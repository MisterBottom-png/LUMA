# LUMA Feature Questions

Use these question sets for broad feature ideas.

Ask no more than 5 questions at once.

## Mini calendar

Ask:

1. Should it live on Home?
2. Should the first version show one week or a full month?
3. Should weekday names have small date numbers under them?
4. Should tapping it open a full custom calendar?
5. Should selecting a date show local LUMA items/reminders from that date?

Default V1 if user says “use your judgment”:

```text
Home week strip with weekday names and small date numbers. Tapping opens a custom full calendar. Selecting a date updates selected date state. Show date-linked local LUMA items only if the app already has the data. No Google Calendar sync, backend, accounts, recurring events, or cloud sync.
```

## Review improvements

Ask:

1. Is the main issue navigation, visual clutter, unclear item actions, or AI suggestions?
2. Should Review feel like a daily ritual or a cleanup queue?
3. Should items be grouped by urgency, type, or age?
4. Should AI suggest smaller next steps?
5. Should Review include missing-information prompts?

Default V1:

```text
Group Review into clear sections, make item actions obvious, add calm empty states, keep AI suggestions optional, and avoid dashboard clutter.
```

## Situation AI improvements

Ask:

1. Should it summarize the current day, current context, or stuck items?
2. Should it suggest actions, reminders, or missing information?
3. Should suggestions require confirmation before changes?
4. Should it use local app data only?
5. Should suggestions appear on Home or inside Situation AI only?

Default V1:

```text
Situation AI summarizes what matters now and suggests optional next steps. It does not directly create reminders or edit data without confirmation.
```

## Language switching

Ask:

1. Which languages should V1 support?
2. Should language affect UI only or AI responses too?
3. Should language be in Settings?
4. Should app follow system language by default?
5. Should date/time formatting change with language?

Default V1:

```text
Add language setting in Settings with English and one additional chosen language. Apply to visible UI strings first. Do not rewrite AI behavior unless explicitly requested.
```

## Context-aware suggestions

Ask:

1. What context should be used: time, reminders, location-like opening hours, item age, or user behavior?
2. Should suggestions appear on Home, Situation AI, Review, or item detail?
3. Should suggestions create actions or only propose them?
4. What should require confirmation?
5. Should the first version be rules-based, AI-based, or mixed?

Default V1:

```text
Start with local rules-based suggestions plus AI phrasing if available. Show suggestions in Situation AI. Require confirmation for any data-changing action.
```
