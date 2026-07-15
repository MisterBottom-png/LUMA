# LUMA MVP Scope

This file defines the practical personal-use MVP target for LUMA.

## MVP goal

LUMA should be useful as a personal local-first AI life inbox.

The MVP is not a public SaaS product. Do not add backend/auth/cloud just to cosplay as a startup.

## Core MVP flow

```text
Capture messy thought/input
→ AI or local processing clarifies/classifies
→ unclear items go to Review
→ finalized items appear in the right places
→ user can search, undo, manage, export/restore, and ask LUMA
```

## MVP must-have areas

```text
- Home capture
- item persistence
- AI processing visibility kept internal
- Review for unclear items
- Spaces / Life Feed for finalized items
- reminders/date handling basic sanity
- Ask LUMA
- search
- undo
- Settings categories
- export/restore
- Reset Mode
- dark/light mode acceptable
- protected done items preserved
```

## MVP should-have areas

```text
- missing-information prompts
- clearer Review navigation
- better Situation AI entry/behavior
- compact date/week strip
- custom full calendar V1 for local LUMA items/reminders
- language switching if already low-risk
```

## Not MVP unless explicitly requested

```text
- backend sync
- accounts/login
- public release billing/subscription
- Google Calendar sync
- recurring event engine
- team collaboration
- web app
- Windows companion app
- heavy analytics dashboard
```

## Release manager rule

Before calling MVP done, use:

```text
luma-mvp-release-manager
luma-regression-qa
luma-release_reviewer
```
