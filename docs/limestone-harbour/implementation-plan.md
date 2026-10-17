# Limestone Harbour: implementation plan

Design authority: `tallele-limestone-harbour-proposal.md` **v1.3 (14 Oct 2026)** in `Tallele_Claude_Code_Cloud_Handoff.zip` at the repository root. Master instructions: `Tallele_Limestone_Harbour_Claude_Code_Prompt.md`. Decision priority: proposal §0 owner decisions → rest of v1.3 → verified current code → repository docs → conservative Android practice. Conflicts are logged in `decisions.md`.

Inputs and checksums: `design-inputs.md`. Current behavior vs design: `p0-findings.md`. Progress: `status.md`. Evidence: `verification.md`.

## Rules for every phase

- One phase per cloud session and per PR. Start from current `main` after the previous phase is merged. Stop at the gate; the owner merges.
- Lead (the main session) owns integration, commits, builds, and sign-off. Subagents get a brief naming owned files, design sections, expected output, boundaries and validation.
- At most two or three concurrent subagents. Parallel code only with disjoint files and an agreed interface.
- Single writer at a time for shared files: `ui/navigation/OrbitApp.kt`, `ui/theme/*`, Gradle files, `AndroidManifest.xml`, `res/values*/strings*.xml`, `domain/model/AppSettings.kt`, `data/repository/AppSettingsRepository.kt`, `OrbitContainer`.
- Not changed without a decision record and tests: Room schema/version (currently 11), export format (currently 7), confidence thresholds, auto-filing rules, reminder semantics, minSdk 26 / targetSdk 36, dependency versions, namespace `com.orbit.app`.
- Optional B12 ("Show the original thought") is **deferred** until the owner approves it.

## Dependency map

```text
P0 Evidence ──► P1 Visual foundation ──► P2 Navigation & finding ──► P3 Capture & trust ──► P4 Remaining screens ──► P5 Settings, migration, data safety ──► P6 Final verification
                    │                         │                          │                       │                        │
                    │ tokens, type, KeepMark, │ 3-tab shell, Saved,      │ CaptureBox, Receipt,  │ Needs you, detail,     │ Appearance conversion
                    │ TalleleTabBar, BG res   │ Search (B2), Calendar    │ Undo (B1), B3, B4,    │ Calendar B6/B7, B10    │ B8, B9, Erase (B5),
                    │                         │ as pushed screen         │ sort sheet, Add to    │                        │ icons, licences
                    ▼                         ▼                          ▼ Space                 ▼                        ▼
               used by all UI           needed by P3 receipt       DecisionCard reused     relies on P3 receipt    relies on P1 settings
                                        "Open", Saved links        by P4 Needs you         states and P2 routes    enums and P2 routes
```

Hard dependencies:

- P1 before everything visual: P2–P5 compose the shared parts (`KeepMark`, `KeepButton`, `CaptureBox`, `Receipt`, `DecisionCard`, `ItemRow`, `FactRow`, `SectionHeader`, `ChoiceSheet`, `SourceRow`, `ReminderStatus`, `UndoMessage`, `TalleleTabBar`, `DayAgenda`).
- P1 introduces the new appearance enums (Theme, Accent ×4, Background ×6, Tab bar ×3) **behind** the existing settings so old values still read; P5 owns the one-time conversion of the settings store and removal of old controls. Backups hold no settings, so restore needs no conversion, only a regression test (`decisions.md` D-05). Until P5 the old Appearance screen keeps working.
- P2 before P3: the receipt's Open/Change targets and Kept thoughts list live in the new shell.
- P3 before P4: `DecisionCard` and receipt states are reused by Needs you and Add to Space.
- B5 (Erase everything) is in P5 but its inventory is fixed in P0 (`p0-findings.md` §Erase inventory) so P1–P4 must register any **new** store they add there.

## File ownership by phase

Paths under `app/src/main/java/com/orbit/app/` unless noted. "Writer" is the agent role that edits; the lead integrates.

| Phase | Area | Files | Writer |
|---|---|---|---|
| P1 | Theme | `ui/theme/Theme.kt`, `Type.kt`, `DesignTokens.kt`, `ColorReadability.kt` | compose-implementer (single theme writer) |
| P1 | Fonts, licences | `res/font/` (Literata, Noto Sans), `assets/licenses/` | assets-i18n |
| P1 | Backgrounds | `res/drawable-nodpi/bg_*_{light,dark}.webp`, background catalogue + wash table | assets-i18n (resources), compose-implementer (composable) |
| P1 | Glass | `ui/components/GlassSurface.kt`, `FloatingBottomNavigation.kt` → `TalleleTabBar`, `OrbitBackground.kt` | compose-implementer |
| P1 | Settings model (additive only) | `domain/model/AppSettings.kt`, `data/repository/AppSettingsRepository.kt` | data-implementer |
| P2 | Shell, routes, badge | `ui/navigation/OrbitApp.kt`, `OrbitDestination.kt`, `FloatingBottomNavigation.kt` | compose-implementer (single nav writer) |
| P2 | Search | `domain/search/LocalSearch.kt`, `ui/screens/search/*` | data-implementer (query), compose-implementer (screen) |
| P2 | Saved, Kept thoughts | `ui/screens/spaces/SpacesScreen.kt` (→ Saved), new `ui/screens/saved/*` | compose-implementer |
| P2 | Calendar push | `ui/screens/calendar/*` (route only) | compose-implementer |
| P3 | Capture | `ui/screens/home/HomeScreen.kt`, `HomeCaptureViewModel.kt`, `CaptureReceipt.kt`, `HomeSendFeedback.kt`, `HomeWeekViewModel.kt` (week strip removed) | compose-implementer (UI), data-implementer (VM) |
| P3 | Filing & Undo | `domain/capture/CaptureDecision.kt`, `AutoFiler.kt`, `CaptureInbox.kt`, `CaptureResolution*` | data-implementer |
| P3 | Sort, Brain Dump, Add to Space | `ui/screens/home/CaptureSort*`, `BrainDump*`, `ui/screens/spaces/SpaceCaptureSheet.kt` | compose-implementer |
| P4 | Needs you | `ui/screens/review/*` | compose-implementer |
| P4 | Ask | `ui/screens/situation/*` | compose-implementer |
| P4 | Detail | `ui/screens/item/*`, reminder detail | compose-implementer (UI), data-implementer (live observe) |
| P4 | Calendar fixes | `ui/screens/calendar/*`, `data/repository/CalendarRepository.kt` | data-implementer |
| P5 | Settings UI | `ui/screens/settings/*` | compose-implementer |
| P5 | Conversion, erase | `AppSettingsRepository.kt` (write-once DataStore migration), `LocalDataToolsViewModel.kt` + new erase use case; `data/export/*` untouched (restore regression test only, D-05) | data-implementer |
| P5 | Icons, notification, shortcuts | `reminders/ReminderNotifier.kt`, `capture/NewThoughtTileService.kt`, `res/xml/shortcuts.xml`, `res/mipmap*`, manifest | assets-i18n |
| All | Strings | `res/values/strings*.xml`, `values-et/`, `values-ru/` | assets-i18n (single strings writer per phase) |

## Phase acceptance checks

Each phase: affected tests + `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` green (locally or CI), strict privacy checker, locale parity, independent QA review, `status.md` and `verification.md` updated, draft PR.

### P0 — Evidence, constraints, baseline
- [ ] `main` SHA, toolchain and build path recorded; baseline build/test result recorded as PASS/FAIL/BLOCKED.
- [ ] Behavior-vs-proposal matrix for B1–B11 written with code evidence.
- [ ] B7 and B10 reproduced or marked unreproduced (by test, not by document claim).
- [ ] Erase-everything inventory: every store, alarm, worker, channel, draft, key, setting.
- [ ] Deep link / share / tile / shortcut / notification entry inventory.
- [ ] Design inputs listed with checksums.
- [ ] Characterization tests added where safe. No UI redesign.

### P1 — Visual foundation
_Done locally in P1 (evidence `verification.md` P1); phone checks pending. Washes changed by D-23; bar clearance carried to P2 (D-28)._
- [x] Light/dark tokens exactly as §6.1; four accents as §7.17; contrast unit test for every text/background pair in both themes (≥ 4.5:1 body, ≥ 3:1 large/UI).
- [x] Literata + Noto Sans bundled with OFL texts; Google Sans Flex removed only once nothing references it; APK size delta recorded.
- [x] `TalleleTabBar` Glass/Frosted/Solid with automatic fallback (API < 33, battery saver, animations off) unit-tested; nothing else blurs.
- [x] Ten WebP backgrounds from the supplied PNGs, underscore names, no upscaling, size measured; per-image wash table (§7.17) encoded and tested; light/dark selection tested.
- [x] Previews at 100% and 200% font scale for each shared part.

### P2 — Navigation and finding
- [ ] Three tabs; Calendar pushed; badge counts To decide only (test).
- [ ] Every existing entry point from `p0-findings.md` lands where it did (tests).
- [ ] Search includes kept thoughts by default, own group, no duplicates after filing, Brain Dump one-to-many correct, matching semantics unchanged (tests).
- [ ] Query, filters, scroll, tab and selected date restored after Back (tests).

### P3 — Capture and trust
- [ ] Store-first receipt; failure keeps text; retry creates no duplicate; two rapid keeps with out-of-order sorting update the right receipts; late classifier cannot override Undo or an edit; voice never saves (tests).
- [ ] Undo after auto-filing restores a kept thought, hidden once edited/completed, cancels linked reminder alarms (tests).
- [ ] B3 and B4 receipt states truthful (tests).

### P4 — Remaining experiences
- [ ] Needs you order, To decide vs Waiting optional, badge (tests).
- [ ] B6 neighbour days; B10 live detail (flip `P0ItemDetailStaleCharacterizationTest`). B7 was not reproduced: `P0CalendarDateTimeCharacterizationTest` stays green as a guard (D-12).
- [ ] Due / notify / repeat / real scheduling state shown separately; notification actions unchanged (tests).

### P5 — Settings, migration, language, data safety
- [ ] One-time appearance conversion of the settings store for every old value and unknown values, idempotent (test per value); every old backup format still restores (regression test; backups carry no settings, D-05).
- [ ] B8, B9 fixed (tests).
- [ ] Erase everything clears every store in the P0 inventory and cancels every alarm/worker; cancel path tested; fresh-launch state tested; Reset appearance and Clear learned data stay narrow.
- [ ] EN/ET/RU parity; icons and licences.

### P6 — Final verification
- [ ] Clean build, all tests, lint, APK size/perf vs P0 baseline, traceability for B1–B11 and every §11 check, phone checklist marked `NEEDS OWNER PHONE VERIFICATION` until the owner reports.

## Risk list

See `p0-findings.md` §Risks for evidence. Summary, highest first:

| # | Risk | Phase | Mitigation |
|---|---|---|---|
| R1 | Erase everything misses a store or leaves alarms/workers → stale notifications or data after "erased" | P5 | P0 inventory is the checklist; each new store added later must be registered; test per store; fresh-launch test |
| R2 | Undo races (late classifier, edit, completion, linked reminder alarm) → data loss or double filing | P3 | Reuse existing `decidedAt`/outcome guards; idempotent rollback; race tests with controlled dispatchers |
| R3 | Appearance conversion crashes or loses settings on update or device transfer | P1, P5 | P1 additive only; P5 write-once DataStore migration composed with the existing read-time remaps, one test per old value + unknown values; old backups still restore (D-05) |
| R4 | Search including kept thoughts creates duplicates or slows down | P2 | De-dup by capture→item link; measure with a large fixture |
| R5 | Deep links/notification actions break when tabs change | P2 | Inventory-driven tests per entry point |
| R6 | Glass performance / fallback detection wrong on real phones | P1 | Fallback unit tests; frame check is phone-only, marked pending |
| R7 | Font swap grows APK or breaks Cyrillic | P1 | Subset only if licence allows; size recorded; RU preview |
| R8 | Canonical docs (visual system, glass system, product rules, protected behaviors) contradict the owner decisions | P1–P5 | Update the doc in the phase that changes the behavior; see `decisions.md` |
| R9 | Phone-only checks (TalkBack, notifications, glass FPS) cannot run in cloud | P6 | Listed in `verification.md` manual checklist; never claimed |
