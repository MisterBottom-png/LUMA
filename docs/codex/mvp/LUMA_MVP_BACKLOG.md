# LUMA MVP backlog

Entries are hypotheses until verified in the current repository revision.

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

## Initial candidates

| ID | Area | Priority | Status | Expected behavior | Evidence |
|---|---|---:|---|---|---|
| MVP-001 | AI title persistence | P0 | verified-fixed | Accepted/generated title persists and renders after restart | MVP-CAPTURE-01 passes the exact displayed note title through UI, ViewModel, and confirmation use case. Focused JVM tests verify the saved title, linked processed capture, concurrent exactly-once finalization, failure rollback, and repository recreation. MVP-DEVICE-QA-08 confirmed on a physical API 36 device that an edited confirmation title produced exactly one finalized note, remained searchable after force-stop and relaunch, and did not expose its linked source capture. |
| MVP-002 | Spaces raw-thought filtering | P0 | verified-fixed | Spaces/Life Feed show one finalized item and hide raw/internal records | Source trace builds displayed contents and counts from finalized notes, tasks, and reminders and supplies no capture rows. MVP-CAPTURE-01 adds concurrent exactly-once finalization coverage. |
| MVP-003 | Review clarity | P0 | verified-fixed | User understands what needs attention and status wording is truthful | MVP-REVIEW-03 labels unresolved captures and stale loops with specific reasons, uses Review-decision progress language, separates task and capture actions by persisted outcome, reuses exactly-once capture finalization, and excludes processed captures from `Done today`. Fifteen focused JVM tests pass. Final physical QA exercised Keep active, Defer to Someday, Mark task done, Archive task, Confirm as Someday task, Dismiss capture, and Archive source; every visible result matched its wording, confirming one finalized item only on confirm. |
| MVP-004 | Reminder date/time | P0 | verified-fixed | Target time, offset, persistence, scheduling, and cancellation agree | MVP-REMINDER-02 independently persists target time and notification offset with a safe version-2-to-3 migration. MVP-REMINDER-ROUTE-09 gives reminders a dedicated editor. Fifteen scheduling JVM tests, two navigation tests, and two physical Room tests pass. Physical QA verified target/offset independence, rescheduling, disable/re-enable, completion, protected deletion, repeated-restore reconciliation, package replacement, and reboot persistence. Reminder archive is unsupported by the current contract. Exact alarms are not applicable because the scheduler uses an inexact alarm API and requests no exact-alarm permission. |
| MVP-005 | Dark mode core screens | P0 | verified-fixed | Core content remains readable and usable | MVP-CUSTOM-BACKGROUND-CONTRAST-10 adds a theme-toned gradient localized to the status-bar and Home header/date region and strengthens existing glass tint only for custom images. Five focused tests and 224 debug/release JVM executions pass. Physical API 36 verification passed dark, bright, neutral, and detailed images in Light, Dark, and Auto with both system night states at 1.0 and 1.3 font scale. Home heading/date text, capture controls, bottom navigation, status-bar icons, picker readability, persistence, relaunch, removal, and preset fallback passed. Complete assistive-technology focus traversal remains a non-blocking manual evidence gap. |
| MVP-006 | Week strip/date grouping | P1 | not-applicable | Compact date awareness and grouping work without crowding Home | A compact week strip exists, but interactive date grouping is not required by the initial MVP gate and remains outside the core repair sequence. |
| MVP-007 | Item detail/edit flow | P1 | verified-fixed | Final item can be inspected and edited safely | MVP-SEARCH-07A excludes raw and processed capture records from normal Search while retaining finalized and archived item behavior. MVP-UNDO-07B keys each archive snapshot and visible Undo callback to the exact operation, updates the existing row, rejects repeated or stale callbacks, and places the action above system and app navigation. Eight focused Search tests, eight focused archive/Undo JVM tests, and two Room archive/Undo tests pass. Physical-device regressions returned one finalized Search result for linked capture data and restored one note and one completed task exactly once with state retained. Reminder archive is not supported by the current item-detail contract. |
| MVP-008 | Calendar V1 | P1 | deferred | Local mini/full calendar without external sync | Post-core stability |
| MVP-009 | Language switching | P1 | deferred | Persisted Android localization for core UI | Post-core stability unless required |
| MVP-010 | Gemini capture suggestions | P1 | verified-fixed | Useful optional suggestions with local fallback and confirmation | Source saves raw capture locally before analysis, routes unavailable or invalid Gemini results to local analysis, and requires explicit confirmation. A focused router test verifies local analysis is retained when Gemini fails. |
| MVP-011 | Situation AI V2 | P2 | deferred | Grounded, explainable, dismissible suggestions | Post-MVP expansion |
| MVP-012 | Advanced local memory | P2 | deferred | Editable/deletable local learning rules without profiling | Post-MVP expansion |
| MVP-013 | Local data restore | P0 | verified-fixed | A user can restore a valid local export without silent overwrite or loss | MVP-RESTORE-04 provides validated version-1 full replacement, an explicit summary and confirmation, stale-confirmation protection, transactional replacement, relationship validation, repeat-restore duplicate prevention, and post-commit reminder reconciliation. Seventeen focused JVM tests and two physical restore tests pass. Final physical QA restored the same export twice; each replacement completed with exactly one note, one task, one reminder, three source captures, and nine Spaces, and the exact dataset persisted through package replacement and device reboot. |
| MVP-014 | Static analysis gate | P1 | verified-fixed | Relevant lint completes without errors | MVP-LINT-05 reproduces and removes the custom-background `ProduceStateDoesNotAssignValue` blocker without suppression. `:app:lintDebug` passes with 0 errors and 47 warnings; all remaining warnings are classified safe to defer: 42 dependency updates, 3 build-plugin updates, 1 themed-launcher-icon notice, and 1 redundant resource-qualifier notice. |

## Completed repair batches

```text
MVP-CAPTURE-01 - Capture-to-final-item integrity and persistence
- Exact displayed note title is passed through confirmation and persisted.
- Capture finalization is serialized to prevent concurrent duplicate final items.
- Focused tests cover title persistence, exactly-once finalization, fallback, rollback, and repository recreation.
- Real Room/database restart remains a manual or instrumentation evidence gap.

MVP-REMINDER-02 - Reminder target time, notification offset, persistence, and scheduling
- Target time remains the displayed event time; notification offset persists independently.
- Scheduling uses target time minus offset and rejects invalid or stale schedule values.
- Focused JVM tests cover calculation, editing, replacement, completion, disabling, deletion, failure safety, overdue handling, and repository recreation.
- Migration 2-to-3 is additive with a zero-offset default and has a compiled Android migration test; device execution remains required.

MVP-REVIEW-03 - Review state, wording, and actions
- Review progress is described as pending review decisions rather than item completion.
- Unresolved captures and stale loops explain why they need attention.
- Task and capture actions use distinct labels that match their state transitions.
- Capture confirmation uses the existing exactly-once finalizer; dismiss and archive create no final item.
- Processed capture records are excluded from `Done today`.
- Focused Review, confirmation, and analyzer JVM tests pass; manual UI and comprehension checks remain required.

MVP-RESTORE-04 - Safe local data restore
- Version-1 exports are parsed and fully validated before any write.
- Restore uses explicit full replacement rather than merge and blocks confirmation if local data changed after the summary.
- Supported Room data is replaced in one transaction with original identifiers and relationships.
- Repeating the same restore replaces the same identifiers instead of adding duplicates.
- Reminder work identifiers are discarded and scheduling is reconciled after the transaction commits.
- Settings and AI configuration remain unchanged because they are not part of the version-1 export contract.
- Focused JVM tests pass and Room instrumentation tests compile; device execution remains required.

MVP-LINT-05 - Static analysis gate
- Reproduced the custom-background state-producer error before editing.
- Replaced the unrecognized producer with equivalent remembered state and URI-keyed loading; no suppression was added.
- `:app:lintDebug` passes with zero errors.
- All 47 remaining warnings were classified as safe to defer; none concerns current correctness, release blocking, accessibility, privacy, or data safety.
- Full debug/release JVM tests and `:app:assembleDebug` pass; custom-background selection and removal still require a device visual check.

MVP-SEARCH-07A - Finalized-only Search results
- Local Search maps finalized notes, tasks, and reminders only; capture storage and finalization are unchanged.
- Raw, processed, and archived capture records are excluded even when archived results are enabled.
- Focused tests cover finalized content, linked-source deduplication, archived finalized items, Someday tasks, and short queries.
- A physical-device linked-capture regression returned exactly one finalized Note result and no capture/internal result.

MVP-UNDO-07B - Reliable archive Undo
- Archive snapshots and snackbar callbacks are keyed by an exact one-shot operation identifier.
- Undo updates the archived row by identifier, creates no replacement row, rejects stale or repeated callbacks, and clears failed operations safely.
- The snackbar is hosted above the floating bottom navigation so its visible action is reachable on the current detail screen.
- Eight focused JVM tests and two Room instrumentation tests cover notes, tasks, unsupported reminders, identifier preservation, duplicate prevention, rapid and stale actions, persistence, and repository failures.
- Physical-device regressions restored one note and one completed task exactly once; the task retained its completion state, and a separate un-undone note archive remained absent from its normal Search location after process restart.
- MVP-007 is `verified-fixed` because both finalized-only Search and Archive Undo have automated and physical-device evidence.

MVP-UI-06 - Review Reset Mode status-bar inset
- Review's lazy scrolling viewport now respects the status-bar inset instead of relying on initial content spacing.
- Initial top placement is preserved by subtracting the applied inset from the existing top clearance with a 24 dp minimum.
- Physical API 36 verification passed Light and Dark at 1.0 and 1.3 font scale and Auto in both system night states.
- Every measured scrolled state kept visible text at or below the 103 px safe boundary, with bottom Reset actions reachable and no duplicated top gap.
- Eleven focused Review JVM tests, the 208-test debug/release JVM suite, debug assembly, and lint pass.
- No Compose UI-test dependency exists; the exact physical-device hierarchy regression is the focused layout evidence.

MVP-DEVICE-QA-08 - Physical-device MVP acceptance pass
- Tested the debug variant from commit `09c56f0e1cc566f8bfeaca6bb5cea824e6b9d25e` plus the recorded pre-existing working-tree changes on a physical API 36 device on 2026-07-13.
- A package uninstall and reinstall succeeded, but Android automatic backup repopulated old local state; an explicitly approved app-data clear was required to establish the empty-data test condition.
- Capture confirmation, exact title persistence, exactly-once finalization, linked-source hiding, local fallback, edit/reopen, completion, archive/Undo, protected deletion, process relaunch, Search filtering, Review reasons, notification permission denial/grant, and one notification delivery passed with generic fixtures.
- A version-1 JSON export contained the expected note, task, reminder, three source captures, nine Spaces, reminder relationship, and independently stored offset. File-picker cancellation, malformed-file rejection, replacement summary, confirmation cancellation, and a three-item restore passed.
- Reminder navigation is release-blocking: opening a reminder reaches generic item detail and exposes no notification-offset, reschedule, or disable controls.
- Reset Mode's Defer action changed state immediately without a second confirmation; the result was visible and reversible by restore, but the requested confirmation contract needs product clarification or a bounded repair.
- The physical-device Room migration test, 208 debug/release JVM executions, `:app:assembleDebug`, and `:app:lintDebug` pass. Package-replacement persistence, reboot persistence, exact-alarm behavior, repeated device restore, complete focus traversal, and the full theme/surface matrix were not tested in this pass.

MVP-REMINDER-ROUTE-09 - Reminder-specific routing and editing
- Reminder links from Review, Search, Spaces, Situation sources, and notification opens resolve to `reminder/{id}` instead of generic item detail.
- The existing reminder editor is registered in the navigation graph and now exposes notification delivery disable/re-enable while retaining target editing, independent offset choices, rescheduling, completion, and protected deletion.
- Reminder persistence and scheduling remain in `RoomReminderRepository`; no entity, schema, migration, export, restore, or dependency change was required.
- Fifteen reminder-scheduling JVM tests cover target/offset persistence, work replacement, disabling, re-enabling, completion, deletion, invalid values, failure safety, and repository recreation. Two navigation tests protect reminder-specific routing while keeping note/task routes unchanged.
- Two Room instrumentation tests passed on a physical API 36 device and cover target/offset edits, replacement, disable/re-enable, completion cancellation, deletion cancellation, and retention of an unrelated row.
- The full device UI flow passed target date/time editing, independent offset editing, rescheduling, disable/re-enable, completion, deletion cancellation, and confirmed deletion. Reminder archive remains unsupported by the current data contract.
- `:app:test` passes 214 debug/release executions; `:app:assembleDebug` and `:app:lintDebug` pass with zero lint errors and the previously classified 47 warnings.

MVP-CUSTOM-BACKGROUND-CONTRAST-10 - Custom-image contrast protection
- Custom images receive a restrained theme-toned gradient through the status bar and Home heading/date region, fading to transparent by 36% of screen height.
- Existing capture, bottom-navigation, and dialog glass uses stronger theme-toned tint only while a custom image is active; preset and no-background values are unchanged.
- Five focused tests cover Light and Dark state selection, localized bounds, custom-only glass strengthening, and retained translucency.
- Physical API 36 verification passed dark, bright, neutral, and detailed images across Light, Dark, and Auto in both system night states at 1.0 and 1.3 font scale.
- Selection, process relaunch persistence, picker readability, removal, and preset fallback passed; the final initial-MVP gate is `PASS`.
```

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
