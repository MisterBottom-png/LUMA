# LUMA project state

This is the current evidence ledger. Superseded failures, repair narratives, and batch-by-batch history belong in feature progress documents or `docs/archive/`, not here.

## Snapshot

```text
Updated: 2026-07-26
Branch: master
Baseline commit: 09c56f0e1cc566f8bfeaca6bb5cea824e6b9d25e
Working tree: intentionally dirty and not reproducible from the baseline commit alone
Workplace identity purge: COMPLETE
Cleanup baseline: COMPLETE
MVP status: PASS for the implemented initial-MVP contract
Confirmed release blockers: NONE
Room schema: version 5
Local export format: version 3; decoder accepts versions 1 through 3
```

The current working tree contains the application, tests, Room schemas, canonical documentation, project skills, and reviewer configuration described below. Do not infer current behavior from the baseline commit without the working-tree changes.

## Current automated baseline

Run on 2026-07-19 against the current working tree:

| Gate | Result | Evidence |
|---|---|---|
| JVM tests | PASS | 246 debug and 246 release executions; 0 failures, 0 errors, 0 skipped |
| Debug APK | PASS | `:app:assembleDebug` |
| Instrumentation APK | PASS | `:app:assembleDebugAndroidTest`; compiled and packaged, not executed on a target |
| Android lint | PASS | `:app:lintDebug`; 0 errors and 55 warnings |
| Agent stack | PASS | 9 canonical skills, 3 custom reviewers, routing and linked-reference validation |
| Workplace privacy heuristic | PASS | Strict checker passed; semantic review remains required for changed text. |

Command: `./gradlew --no-daemon :app:test :app:assembleDebug :app:lintDebug`

The 55 lint warnings were not reclassified in this documentation cleanup. A future release claim must inspect any warning whose category or relevance changed.

## Current capability map

| Area | Current implementation | Current evidence | Remaining gap |
|---|---|---|---|
| App shell and navigation | Home, Spaces, Review, Settings, Calendar, Search, note/task detail, dedicated reminder detail, and Situation AI are wired in Navigation Compose. | Current compile, JVM tests, and lint pass; prior API 36 launch, restart, rotation, package-replacement, and reboot checks passed. | Re-run device smoke after future navigation or manifest changes. |
| Capture and confirmation | Raw text is inserted into the local Inbox before optional AI analysis. Single-item and multi-item Brain Dump suggestions remain user-confirmed. | Current analyzer, ViewModel, use-case, and persistence tests pass. | Brain Dump interruption/resume and per-item device acceptance remain current backlog items. |
| Finalized-item visibility | Spaces, Life Feed, Search, Calendar, and normal detail flows use finalized notes, tasks, and reminders rather than raw/internal captures. | Current projection, search, confirmation, and UI-state tests pass; prior finalized-only device checks passed. | Preserve this boundary when adding a new user-facing collection. |
| Review | Morning, Evening, Weekly, Reset, carry-forward, capture decisions, Brain Dump resume, Waiting For, Someday, and Make Smaller paths are implemented. | Current Review tests pass; prior physical transition checks passed. | Complete assistive-technology traversal remains open. |
| Calendar V1 | Home week strip, full Day/Month destination, finalized-item projection, item routing, date-aware capture, note/task scheduling, and reminder integrity are implemented without external calendar sync. | Current Calendar and full JVM suites pass; Calendar instrumentation exists. | Current end-to-end physical acceptance and accessibility matrix remain open. |
| Reminders | Local target time, independent notification offset, dedicated editing, scheduling abstraction, replacement, cancellation, boot/package reconciliation, and notification routing are implemented. | Current scheduling tests pass; prior API 36 scheduling, notification, reboot, and restore checks passed. | Re-run physical delivery checks after scheduler, permission, or manifest changes. |
| Situation AI and Ask LUMA | Local situation analysis, bounded local retrieval, optional source-linked Gemini answers, source opening, and stale-result rejection are implemented. | Current analyzer, router, prompt, validator, and ask-state tests pass; prior API 36 Ask LUMA and IME checks passed. | No live-provider request was run in this cleanup; broader V2 expansion is deferred. |
| Persistence and local data | Room version 5 stores core items, learning/history data, and Brain Dump sessions. Export format 3 includes current local data and accepts versions 1 through 3 for restore. | Current migration/export/restore JVM tests pass; Room instrumentation exists; prior restore and migration device checks passed for earlier formats/schema transitions. | Run current version-5 migration and format-3 restore instrumentation on a connected target before a release claim. |
| Appearance and glass | Light, Dark, Auto, presets, custom backgrounds, contrast protection, and route-aware glass policies are implemented. | Current theme/glass tests and lint pass; prior API 36 custom-background matrix passed. | Re-run the visual matrix when theme, glass, insets, or typography changes. |
| Accessibility | Semantics, selected states, practical touch targets, scalable layouts, and custom Calendar actions are present across core surfaces. | Current lint has zero errors and focused semantics tests pass; sampled prior physical behavior passed. | Complete hands-on assistive-technology traversal remains open. |
| Project agent stack | One canonical `.agents/skills` tree and three bounded `.codex/agents` reviewers are configured. | Structural validator passes. | Runtime discovery is outside the structural validator. |

## Current backlog summary

No confirmed P0 release blocker is open.

| Backlog item | State |
|---|---|
| Calendar V1 physical acceptance | Implemented and automated; current device matrix pending. |
| Brain Dump physical acceptance | Implemented and automated; interruption/resume and exactly-once device checks pending. |
| Complete accessibility traversal | Partial physical evidence; full traversal pending. |
| Language switching | In progress: English, Estonian, and Russian are declared; the per-app selector and all 304 currently externalized translatable resource keys have locale variants. Estonian and Russian capture/reminder rule packs and focused tests exist. Remaining hardcoded visible copy, English local presentation output, explicit per-app-locale propagation, and device/linguistic acceptance are documented in `localization/LUMA_LOCALIZATION_PLAN.md`. |
| Situation AI V2 expansion | Deferred; current Situation AI and Ask LUMA remain implemented. |
| Advanced local memory controls | Deferred pending user-control requirements. |

The canonical item-level backlog is `mvp/LUMA_MVP_BACKLOG.md`. Calendar-specific progress and its remaining matrix are in `../calendar/CALENDAR_V1_PROGRESS.md`.

## Historical physical evidence retained as current context

- Initial-MVP and bounded regression checks were executed on a physical Android 16/API 36 device between 2026-07-13 and 2026-07-18.
- Prior evidence covers capture persistence, finalized-only Search and Spaces, Review actions, reminder editing and delivery, archive Undo, restore/repeat restore, process recreation, package replacement, reboot persistence, custom-background contrast, Situation AI keyboard handling, and pinned-header scrolling.
- Those results support unchanged behavior but do not replace current device checks for Calendar V1, Brain Dump, Room version 5, export format 3, or complete accessibility traversal.
- No connected or physical-device test was run during this 2026-07-19 documentation cleanup.

## Durable decisions

- LUMA is local-first.
- Raw thoughts and AI processing records are not separate user-facing Space cards.
- AI/provider data egress is optional, explicit, and bounded.
- Provider responses are validated before use and fall back locally when unavailable or invalid.
- Important AI-proposed mutations require user confirmation.
- Event time, reminder target time, and notification offset remain separate concepts.
- No coworker or workplace-associated person is mentioned in repository-controlled or generated output; generic role labels are used instead.
