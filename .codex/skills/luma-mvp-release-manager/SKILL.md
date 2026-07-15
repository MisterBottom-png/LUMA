---
name: luma-mvp-release-manager
description: Use for deciding what remains for LUMA MVP, protecting scope, prioritizing finish-line work, checking release readiness, and avoiding non-MVP scope creep.
---

# LUMA MVP Release Manager

Use this skill when the user asks to finish MVP, check readiness, or decide what remains.

## MVP identity

LUMA MVP is a personal local-first AI life inbox.

It does not need backend/auth/cloud/public-release machinery unless explicitly requested.

## MVP core checks

```text
- Home capture works
- captured items persist
- AI processing/internal records are not exposed as cards
- Review handles unclear items
- Spaces/Life Feed show finalized items
- reminders/date basics are sane
- Ask LUMA works
- search works
- undo works where expected
- Settings are categorized
- export/restore works
- Reset Mode works
- dark/light mode acceptable
- protected done items preserved
```

## MVP prioritization

Use categories:

```text
Blocker: prevents normal personal use
Should-fix: rough but usable
Later: useful but not MVP
Explicitly excluded: backend/cloud/auth/etc.
```

## Release report

```text
MVP status:
Blockers:
Should-fix before calling it done:
Can wait:
Protected done items checked:
Recommended next Codex prompts:
```
