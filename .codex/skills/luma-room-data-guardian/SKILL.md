---
name: luma-room-data-guardian
description: Use for Room database, entities, DAOs, repositories, migrations, local-first data behavior, export/restore, Reset Mode, undo/search persistence, and any user-data risk.
---

# LUMA Room Data Guardian

Use this skill whenever a task touches persistent data or data risk.

## Scope

```text
- Room entities
- DAOs
- database version/migrations
- repositories
- export/restore
- Reset Mode
- undo persistence
- search indexing/querying
- local-first storage
- data deletion/archive/complete behavior
```

## Hard rules

- Do not change Room entities without migration review.
- Do not wipe user data without explicit approval.
- Do not silently mutate important user data.
- Preserve local-first behavior.
- Do not add backend/cloud/auth unless explicitly requested.
- Protect export/restore and Reset Mode.

## Migration checklist

If schema changes are proposed:

```text
- What entity/table changed?
- Is database version updated?
- Is migration required?
- What happens to existing user data?
- Are defaults safe?
- Is export/restore affected?
- Is Reset Mode affected?
```

## Data-flow checklist

Before implementation:

```text
- Identify source of truth.
- Identify repository path.
- Identify ViewModel state path.
- Identify UI consumer.
- Identify write actions and confirmation points.
```

## Protected done items

Always check these if touched:

```text
undo
search
export/restore
Reset Mode
Waiting For / Someday
Make Smaller
Brain Dump
Ask LUMA
hidden Processed items
```
