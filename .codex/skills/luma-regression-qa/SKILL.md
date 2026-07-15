---
name: luma-regression-qa
description: Use after LUMA changes to verify builds, UI paths, protected done items, dark/light mode, data/AI/reminder risks, manual test steps, and ship-readiness evidence.
---

# LUMA Regression QA

Use this skill after implementation or for review-only validation.

## QA tiers

### Quick QA

Use for tiny fixes.

```text
- Build/compile check if practical.
- Changed screen/component inspected.
- No obvious protected done-item regression.
```

### Standard QA

Use for normal feature/bug work.

```text
- Build/compile/test check where practical.
- Manual path for changed flow.
- Light/dark mode if UI changed.
- Navigation/back behavior if screen changed.
- Protected done items checked.
- Related flow checked.
```

### Exhaustive QA

Use for MVP/release/risky changes.

```text
- Core capture flow
- Review flow
- Spaces/Life Feed
- Ask LUMA
- Search
- Undo
- Settings
- Export/restore
- Reset Mode
- Reminders/date handling
- Dark/light mode
- Protected done items
```

## Evidence rules

Do not say “tested” unless a test or app run actually happened.

Use accurate labels:

```text
- Ran: <command>
- Passed: <result>
- Failed: <result>
- Not run: <reason>
- Checked by inspection: <what>
- Manual test needed: <steps>
```

## Final QA summary

```text
QA tier:
Commands run:
Manual checks:
Protected done items checked:
Blockers:
Should-fix:
Safe-to-continue:
```
