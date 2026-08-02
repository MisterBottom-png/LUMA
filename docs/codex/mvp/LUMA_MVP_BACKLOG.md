# LUMA MVP backlog

This file contains only current work and concise disposition of completed MVP candidates. Detailed historical repair narratives belong in feature progress documents or the archive, not in the active backlog.

Every status is tied to the current working tree. Historical evidence is supporting context, not proof of current behavior.

## Status values

```text
unverified
verified-broken
in-progress
fixed-unverified
verified-fixed
deferred
not-applicable
```

## Current release blockers

None confirmed. The current automated baseline passes. The remaining items below are bounded manual-verification gaps or explicitly deferred product scope.

## Active backlog

| ID | Area | Priority | Status | Current state | Next evidence or decision |
|---|---|---:|---|---|---|
| MVP-008 | Calendar V1 physical acceptance | P1 | fixed-unverified | Calendar V1 is implemented: Home week navigation, full Day/Month destination, finalized-item projection, item routing, date-aware capture, note/task scheduling, and reminder integrity. Current Calendar and full JVM tests pass. | Run the compiled navigation/Room tests and the documented device matrix for Home-to-Calendar continuity, scheduling, process recreation, themes, font scale, and accessibility. |
| MVP-009 | Language switching | P1 | in-progress | English remains the fallback; Estonian and Russian are declared. Settings offers system default and all three explicit languages. All 304 currently externalized translatable keys have locale variants, and Estonian/Russian capture and reminder rule packs have focused tests. Hardcoded visible copy and English local presentation output remain. | Follow `../localization/LUMA_LOCALIZATION_PLAN.md`: finish presentation resources, localize domain fallbacks, make per-app-locale propagation explicit, then complete notification, device, accessibility, and linguistic verification. |
| MVP-011 | Situation AI expansion | P2 | deferred | Current Situation AI and Ask LUMA are implemented with local analysis, bounded source retrieval, optional Gemini routing, source-linked answers, stale-result rejection, and a dismissible sheet. The broader V2 section model remains intentionally unstarted. | Define a concrete expansion only if the current surface no longer meets the product need. |
| MVP-012 | Advanced local memory controls | P2 | deferred | Local learning/history repositories and routing context exist, but a complete inspect/edit/delete management surface is not part of the current MVP. | Define user-control requirements before exposing or expanding learned memory. |
| MVP-015 | Brain Dump device acceptance | P1 | fixed-unverified | Multi-item capture review, resumable pending sessions, per-item note/task/reminder/inbox/skip actions, Room version 5 persistence, and export format 3 support are implemented. Current JVM tests pass and Room instrumentation exists. | Execute end-to-end device checks for interruption/resume, exactly-once item handling, reminder scheduling, Review resume navigation, restore, rotation, and process recreation. |
| MVP-016 | Complete accessibility traversal | P1 | fixed-unverified | Core semantics, touch targets, large-font reachability, and sampled physical accessibility behavior have evidence. Complete assistive-technology traversal was not established. | Perform a hands-on traversal of Home, capture/Brain Dump, Calendar, Review, Search, item detail, Situation AI, Settings, and restore confirmation. |

## Completed or not-applicable candidates

| ID | Area | Status | Current disposition |
|---|---|---|---|
| MVP-001 | AI title persistence | verified-fixed | Exact user-confirmed titles persist through the serialized confirmation path; current tests pass. |
| MVP-002 | Finalized-only Spaces/Life Feed | verified-fixed | User-facing lists are built from finalized notes, tasks, and reminders rather than raw/internal capture rows. |
| MVP-003 | Review clarity | verified-fixed | Review reasons and actions map to explicit persisted outcomes; current Review tests pass. |
| MVP-004 | Reminder date/time integrity | verified-fixed | Target time and notification offset remain separate; editing, replacement, cancellation, restore reconciliation, and routing have automated and prior device evidence. |
| MVP-005 | Theme and custom-background readability | verified-fixed | Light/Dark/Auto and custom-background protection were repaired and physically verified; current lint and tests pass. |
| MVP-006 | Week strip/date grouping | verified-fixed | Home now has locale-aware week navigation, selected date state, item-presence indicators, and Calendar routing without becoming a dashboard. |
| MVP-007 | Item detail, Search, and archive Undo | verified-fixed | Finalized-only Search, original-item detail/editing, protected deletion, and one-shot archive Undo are implemented and tested. |
| MVP-010 | Optional Gemini capture suggestions | verified-fixed | Raw capture is stored first; Gemini is optional and locally gated; malformed/unavailable provider results fall back locally; important actions require confirmation. |
| MVP-013 | Local export/restore | verified-fixed | Export format 3 covers current local data, accepts older supported formats, validates before transactional replacement, and reconciles reminder work after commit. |
| MVP-014 | Static analysis gate | verified-fixed | Current `:app:lintDebug` passes with zero errors. Warning count is recorded in `PROJECT_STATE.md` and must be reclassified if a warning changes release relevance. |

## Current evidence

- Canonical state and latest automated results: `../PROJECT_STATE.md`
- Calendar implementation and remaining device checks: `../../calendar/CALENDAR_V1_PROGRESS.md`
- Product gate: `LUMA_MVP_GATE.md`
- Verification rules: `LUMA_MVP_VERIFICATION_POLICY.md`

## Backlog entry template

```text
ID:
Area:
Priority:
Status:
Expected behavior:
Current behavior:
Evidence:
Affected files/flows:
Acceptance:
Protected behavior affected:
Last verified:
```
