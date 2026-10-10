# LUMA Decisions

Store product and architecture decisions with reasons.

## Format

```md
## Decision: <name>

Status:
Active / Pending / Replaced

Reason:
<why this decision exists>

Applies to:
- ...

Notes:
- ...
```

## Decision: Local-first by default

Status:
Active

Reason:
LUMA is currently for personal use and should remain useful without backend complexity unless explicitly requested.

Applies to:
- data storage
- AI fallback
- reminders
- calendar/date features
- settings

Notes:
- Do not add backend, cloud sync, accounts, or paid APIs unless explicitly requested.

## Decision: Calendar starts as local LUMA date awareness

Status:
Active

Reason:
The first useful calendar behavior should help the user understand local LUMA items/reminders by date before external sync exists.

Applies to:
- Home mini calendar
- full calendar
- reminders
- date-linked items

Notes:
- No Google Calendar sync unless explicitly requested.
- No recurring event engine unless explicitly requested.

## Decision: Custom backgrounds are local appearance data

Status:
Active

Reason:
User-uploaded backgrounds personalize LUMA without changing life-inbox data, AI behavior, or adding cloud dependencies.

Applies to:
- Settings
- Appearance
- background rendering
- local-first behavior

Notes:
- Use Android local document/image access and persist only local URI/settings references.
- Do not add cloud backup, remote image hosting, or account sync for backgrounds unless explicitly requested.

## Decision: Version-1 restore uses explicit full replacement

Status:
Active

Reason:
The version-1 export preserves table identifiers and relationships but has no conflict metadata for a safe merge. Validated full replacement avoids duplicate or ambiguous records.

Applies to:
- local JSON export and restore
- Room transactions
- reminder reconciliation
- Settings confirmation

Notes:
- Parse and validate the complete file before reading or changing local data.
- Show current and restored counts, then require explicit confirmation.
- Replace supported Room data in one transaction and reconcile reminder work only after commit.
- Preserve settings and AI configuration because version 1 does not export them.

## Decision: Glass is role-based and bounded

Status:
Active

Reason:
Real-time backdrop blur is valuable on small fixed chrome but becomes an app-wide rendering cost when it is used for repeated or scrolling content. A visually related non-blur material keeps routes coherent without making translucency synonymous with Haze.

Applies to:
- app shell and ambient background
- Home navigation and capture
- Calendar, Spaces, Review, Settings, Search, and Item Details
- Situation AI and app-owned modals
- Appearance preview and custom backgrounds

Notes:
- LiveGlass is allowed only for small fixed surfaces under a policy selected before rendering.
- SoftGlass is the default for cards, lists, panels, details, and fallbacks.
- ModalSurface is a behavioral role and uses SoftGlass by default.
- User-image background blur is cached image processing, not interface Haze.

## Decision: Settings separates appearance from system behavior

Status:
Active

Reason:
Visual personalization and device/application behavior are different mental models. Time format, AI configuration, and local-data tools should not compete with colors, backgrounds, and surface controls.

Applies to:
- Settings navigation
- Appearance
- System settings

Notes:
- Appearance contains Profile, Colors, Background, and Transparency.
- System contains Time, AI, and Local data.
- Focused subsections return to their parent menu before returning to the Settings index.

## Decision: Review is one contextual destination

Status:
Active

Reason:
Review should match the user's local moment without asking them to choose between competing modes or turning midday into another productivity workflow.

Applies to:
- Review navigation
- Morning Review
- Midday breathing space
- Evening Review
- weekend Weekly Review
- open-loop sorting

Notes:
- Device-local time resolves Morning before 12:00, Midday from 12:00 until 17:00, and Evening from 17:00 onward through centralized boundaries.
- Midday is passive and contains no tasks, suggestions, prompts, questions, checklists, or open-loop action.
- Weekend Morning and Evening keep their time-of-day content and add a dismissible three-page Weekly Review: Look back, Loose ends, and Look ahead.
- Review has no mode tabs, chips, or manual time-of-day selector.
- Sort open loops remains one secondary workflow below actionable contextual content and reuses existing item state and actions.

## Decision: Situation AI V1 is a grounded local briefing

Status:
Active

Reason:
Situation AI must stay trustworthy offline and must not turn source-linked Gemini output into permission to invent item facts.

Applies to:
- Situation AI briefing
- Ask LUMA local retrieval
- Gemini Situation and Ask boundaries
- source-item navigation

Notes:
- Room-backed notes, tasks, reminders, captures, and spaces are the source of truth.
- The active briefing selects only a few items and gives a deterministic reason for each one.
- Ask LUMA answers are assembled from traceable local source items and remain useful offline.
- Gemini may improve wording or interpret a question only when the output cannot add unsupported items, dates, priorities, states, or relationships.
- Situation AI never mutates user data; Review remains the place for resolution actions.
