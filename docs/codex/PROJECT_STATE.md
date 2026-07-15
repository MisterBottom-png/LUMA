# LUMA project state

This is a compact evidence ledger, not an automatic diary.

## Repository baseline

```text
Workplace identity purge: COMPLETE
Cleanup baseline: COMPLETE
Last cleanup audit: 2026-07-13
Baseline commit: 09c56f0e1cc566f8bfeaca6bb5cea824e6b9d25e
Baseline build: :app:assembleDebug PASS; :app:test PASS; :app:lint FAIL with one error and 44 warnings
Post-cleanup build: :app:assembleDebug PASS; :app:test PASS; :app:lint unchanged at one error and 44 warnings
Workplace privacy check: PASS - strict full working-tree scan on 2026-07-13
Identity-purge evidence: 505 eligible text surfaces and 47,530 lines semantically reviewed; 84 repository-controlled occurrences replaced with generic role labels; checker tests passed
```

Set `Workplace identity purge` to `COMPLETE` only after the full tracked working tree passes strict scanning and semantic review. Set `Cleanup baseline` to `COMPLETE` only after the cleanup skill's completion gate is met.

## MVP status

```text
MVP status: PARTIAL
Last MVP audit: 2026-07-14
Current blocker batch: REMINDER-PARSED-TIME-11 automated repair passes; exact physical capture verification remains pending
```

## Evidence table

| Area | Status | Evidence | Last checked |
|---|---|---|---|
| App launch | verified | Final initial-MVP QA clean-installed and launched the debug APK on a physical API 36 device, verified Home as the start destination, force-stop/relaunch with a new process, package replacement, and device reboot. Android backup restored prior app data after uninstall/reinstall; clearing this app's data then established and verified the controlled empty first-launch condition | 2026-07-14 |
| Capture reliability and persistence | verified | Physical-device capture saved raw data before the local fallback result, preserved the exact displayed or edited confirmation title, produced one finalized item, hid its linked source record, and remained persisted after force-stop and relaunch. Focused failure and rollback tests continue to pass | 2026-07-13 |
| Final item confirmation and visibility | verified | MVP-CAPTURE-01 passes the displayed note title through confirmation, records the same final title, serializes capture finalization, and has focused tests for exact title persistence, concurrent exactly-once finalization, and rollback. Spaces/Life Feed continue to exclude capture records | 2026-07-13 |
| Spaces/Life Feed filtering | verified | Source trace and focused tests build Spaces and Life Feed from notes, tasks, and reminders only. Final physical QA populated Life Feed with a note, task, and reminder and observed no raw, processed, or internal records | 2026-07-14 |
| Review clarity | verified | Physical QA exercised Keep active, Defer to Someday, Mark task done, Archive task, Confirm as Someday task, Dismiss capture, and Archive source. Each visible result matched its wording; confirm produced one finalized item, while dismiss and source archive produced none. Fifteen focused transition tests also pass | 2026-07-14 |
| Reminder scheduling and date/time | partial | REMINDER-PARSED-TIME-11 adds deterministic compact-time interpretation, makes the locally resolved epoch authoritative over conflicting AI phrase/epoch pairs, rejects invalid compact values into review, and verifies non-UTC round trips plus exact confirmation, persistence, and scheduler propagation. The full automated gate passes; the exact capture still requires physical UI verification because no device or emulator was attached. Existing target/offset separation, replacement, cancellation, completion, disabling, deletion, and restore coverage continues to pass | 2026-07-14 |
| Gemini fallback | verified | Router source falls back to local analysis when Gemini is disabled, unavailable, or invalid; capture is already local before analysis and analyzer/router unit tests pass | 2026-07-13 |
| Navigation and core surfaces | verified | Physical QA covered Home, capture, Review, populated Spaces and Life Feed, Search, note/task/reminder details, Settings, export/restore, Reset Mode, back navigation, Situation AI through a completed Ask LUMA response, landscape rotation, and force-stop process recreation | 2026-07-14 |
| Search | verified | MVP-SEARCH-07A maps finalized notes, tasks, and reminders only. Eight focused tests cover capture exclusion, linked-source deduplication, archived finalized items, Someday tasks, and short queries; a physical-device linked-capture regression returned exactly one finalized Note result with no capture/internal result | 2026-07-13 |
| Undo and protected delete | verified | MVP-UNDO-07B keys the archive snapshot and visible Undo callback to the exact operation, updates the same existing row, rejects repeated and stale callbacks, and safely retains the archive on failure or expiry. Eight focused JVM tests and two Room tests pass. Physical-device regressions restored one note and one completed task exactly once, with the task completion state retained. Protected-delete behavior remains unchanged from the prior verified device pass | 2026-07-13 |
| Export | verified | A physical-device version-1 export contained the expected generic note, task, reminder, three linked source captures, nine Spaces, identifiers, relationships, target time, and independently stored notification offset | 2026-07-13 |
| Restore | verified | Physical checks passed cancellation, malformed-input protection, explicit replacement summary, confirmation cancellation, and repeated replacement of the same version-1 export. Both restores completed with exactly one note, one task, one reminder, three source captures, and nine Spaces; package replacement and reboot retained that exact dataset. Reminder reconciliation and rollback are covered by focused JVM and physical Room tests | 2026-07-14 |
| Reset Mode | verified | Safe-area scrolling and reachable actions pass across required themes and font scales. The labeled Reset action itself is the explicit user confirmation required by the current product contract; representative and complete Review action outcomes were exercised physically and matched their descriptions | 2026-07-14 |
| Light/dark themes and custom backgrounds | verified | MVP-CUSTOM-BACKGROUND-CONTRAST-10 adds a theme-toned localized top gradient for custom images and strengthens existing glass tint only while a custom image is active. Physical API 36 verification passed dark, bright, neutral, and detailed images in Light, Dark, and Auto with both system night states at 1.0 and 1.3 font scale. Home heading, week/date text, capture controls, bottom navigation, status-bar icons, and the background picker remained readable; selection, relaunch persistence, removal, and preset fallback passed | 2026-07-14 |
| Accessibility | partial | Accessibility trees expose labels for sampled core controls with reasonably sized targets; lint has no accessibility findings, large-font Reset actions remain reachable, and sampled dialog reading order is logical. Complete assistive-technology focus traversal remains a manual evidence gap. The confirmed custom-background contrast failure is tracked separately as the current blocker | 2026-07-14 |

## Current MVP verification evidence

```text
:app:assembleDebug: PASS
:app:test: PASS - 214 debug/release JVM test executions, 0 failures, 0 errors, 0 skipped
:app:testDebugUnitTest focused MVP-SEARCH-07A run: PASS - 8 tests
:app:testDebugUnitTest focused MVP-UNDO-07B run: PASS - 8 tests
:app:testDebugUnitTest focused MVP-UI-06 Review run: PASS - 11 tests
:app:testDebugUnitTest focused MVP-CAPTURE-01 run: PASS - 7 tests
:app:testDebugUnitTest focused MVP-REMINDER-02 run: PASS - 14 tests
:app:testDebugUnitTest focused MVP-REVIEW-03 run: PASS - 15 tests across Review actions, Review state mapping, capture confirmation, and the local Review analyzer
:app:testDebugUnitTest focused MVP-RESTORE-04 run: PASS - 17 export/restore and reminder-reconciliation tests
:app:compileDebugAndroidTestKotlin: PASS - Room migration and restore transaction tests compile
:app:lintDebug: PASS - 0 errors and 47 warnings after MVP-LINT-05
Lint warning classification: 42 dependency-update notices, 3 build-plugin update notices, 1 themed-launcher-icon notice, and 1 redundant resource-qualifier notice are safe to defer; 0 correctness/release-blocking, 0 accessibility-relevant, 0 privacy/data-safety-relevant, and 0 confirmed false positives remain
Codex stack validator: FAIL - 25 legacy package-governance errors and 73 warnings, unchanged in scope from cleanup evidence
Workplace privacy check: PASS - strict heuristic scan; semantic review remains required for every future text-bearing change
Physical-device MVP-SEARCH-07A regression: PASS - one linked generic capture produced exactly one finalized Note result with no capture/internal result
MVP-UNDO-07B Room instrumentation: PASS - 2 tests on a physical device
Physical-device MVP-UNDO-07B regression: PASS - one note and one completed task were each archived and restored exactly once; the task retained its completion state, and a separate un-undone note archive remained absent from its normal Search location after process restart
Physical-device MVP-UI-06 regression: PASS - API 36 Reset Mode top-to-bottom scrolling kept visible text at or below the 103 px status-bar boundary in Light and Dark at 1.0 and 1.3 font scale and Auto in both system night states; all eight visible actions remained reachable and initial header placement was unchanged
Instrumentation tests: archive/Undo Room tests and the version-2-to-3 migration test executed on a physical device; restore transaction tests compile but were not executed in this pass
Physical-device MVP-DEVICE-QA-08: PARTIAL - debug build on SM-S931B, Android 16/API 36, 2026-07-13; reminder-editing navigation is the confirmed MVP blocker
Physical-device Room migration: PASS - version-2-to-3 migration executed 1 test, 0 failures
Physical-device restore: PARTIAL - valid export, cancellation, malformed input, replacement summary, confirmation cancellation, and one successful three-item restore passed; repeat restore and reboot persistence were not tested
Clean-install condition: PARTIAL - uninstall/install passed, but Android automatic backup restored prior local state; approved app-data clear established the empty-data test condition
MVP-REMINDER-ROUTE-09 focused JVM tests: PASS - 15 reminder scheduling tests and 2 navigation routing tests
MVP-REMINDER-ROUTE-09 physical-device Room instrumentation: PASS - 2 tests covering target/offset persistence, work replacement, disabling, re-enabling, completion cancellation, deletion cancellation, and row isolation
Physical-device MVP-REMINDER-ROUTE-09 regression: PASS - reminder-specific routing, target date/time edit, independent offset edit, reschedule, disable/re-enable, completion, protected deletion cancellation, and confirmed deletion
Final initial-MVP automated gate: PASS - :app:test, :app:assembleDebug, :app:lintDebug, and :app:assembleDebugAndroidTest; 214 JVM executions, 0 failures, 0 errors, 0 skipped; lint 0 errors and 47 previously classified warnings
Final initial-MVP physical instrumentation: PASS - 7 tests on SM-S931B Android 16/API 36: restore transaction, database migration, reminder editing/scheduling, and archive/Undo
Pre-repair final initial-MVP device QA: BLOCKED - repeated restore, reminder reconciliation evidence, complete Review transitions, Reset confirmation contract, populated Life Feed, Ask LUMA response flow, rotation, process recreation, package replacement, reboot persistence, and controlled empty first launch passed; custom-background Home text contrast failed
Pre-repair final initial-MVP custom-background regression: FAIL - a neutral mid-tone custom image made Home secondary heading and calendar text insufficiently readable; picker dialog content and controls remained readable
Final initial-MVP accessibility evidence: PARTIAL - sampled semantics, target sizing, large-font reachability, and logical dialog order pass; complete assistive-technology focus traversal was not executed
Exact-alarm acceptance: NOT APPLICABLE - the current scheduler uses an inexact alarm API and the application requests no exact-alarm permission
MVP-CUSTOM-BACKGROUND-CONTRAST-10 focused tests: PASS - 5 tests cover Light and Dark tonal protection, localized gradient bounds, custom-only capture/navigation glass strengthening, and retained translucency
MVP-CUSTOM-BACKGROUND-CONTRAST-10 automated gate: PASS - 224 debug/release JVM executions, 0 failures, 0 errors, 0 skipped; :app:assembleDebug and :app:assembleDebugAndroidTest pass; :app:lintDebug passes with 0 errors and the previously classified 47 warnings
MVP-CUSTOM-BACKGROUND-CONTRAST-10 physical matrix: PASS - SM-S931B Android 16/API 36; dark, bright, neutral, and detailed images; Light, Dark, and Auto in both system night states; 1.0 and 1.3 font scale; Home heading, week/date text, capture controls, bottom navigation, status-bar icons, picker, persistence, relaunch, removal, and preset fallback
Post-repair final initial-MVP device QA: PASS - the sole confirmed product blocker is resolved without a regression in the previously verified MVP paths; 7 physical Room/instrumentation tests also pass
Working-tree reproducibility: DOCUMENTED LIMITATION - tested branch master at 09c56f0e1cc566f8bfeaca6bb5cea824e6b9d25e with 68 pre-existing tracked changes and 52 untracked top-level status entries preserved. No commit was created because doing so would absorb unrelated pre-existing work. The tested debug APK is identified by SHA-256 43D9D5ECCEFF11D67D8AFACB22541D321C422C279BAA5D89797AAF222FA42242
REMINDER-PARSED-TIME-11 focused JVM gate: PASS - explicit `1600`, `0830`, contextual `800`, `16:00`, `4 PM`, today/tomorrow handling, invalid compact values, Gemini conflict replacement, accepted payload persistence, non-UTC round trip, scheduler target propagation, offset separation, replacement, and reminder navigation
REMINDER-PARSED-TIME-11 full automated gate: PASS - :app:test, :app:assembleDebug, :app:lintDebug, and :app:assembleDebugAndroidTest; 362 debug/release JVM executions, 0 failures, 0 errors, 0 skipped
REMINDER-PARSED-TIME-11 physical exact-capture verification: NOT RUN - no Android device or emulator was attached; manual acceptance remains required
```

## UI/UX small-change evidence

```text
Small change 1: COMPLETE - internal AI IDs removed from user-visible source labels
Small change 2: COMPLETE - reusable AI evidence SourceRow added
Small change 3: COMPLETE - Situation AI evidence lists use SourceRow while preserving item-opening callbacks and source order
Small change 4: COMPLETE - unavailable Home microphone placeholder removed; save/analyze action remains right-aligned
Small change 6: COMPLETE - two proven dead controls removed: unavailable Situation AI Monday action and no-op selected reminder offset button
Small change 7: COMPLETE - Situation AI header and Close control remain outside the independently scrolling body
Small change 8: COMPLETE - Situation AI body fills one independently scrolling region beneath the fixed header
Small change 10: COMPLETE - Android Back dismisses Situation AI and focus returns to the central AI control without changing closed-sheet Back behavior
```

## Durable decisions

Add only decisions that are current, evidence-based, and likely to affect future implementation. Prefer tests and canonical product rules over prose memories.

- LUMA is local-first.
- Raw thoughts and AI processing records are not separate user-facing Space cards.
- Important AI actions require confirmation.
- Initial cleanup and MVP implementation are separate runs.
- No coworker or workplace-associated person is mentioned in repository-controlled or generated output; generic role labels are used instead.
