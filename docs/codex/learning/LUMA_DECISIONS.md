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
