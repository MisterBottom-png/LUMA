# Limestone Harbour: status

Read this first in every session. Then `implementation-plan.md`, the proposal sections for the phase, and the affected code. States: Not started / In progress / Blocked / Verified / Needs phone verification.

| Phase | State | Branch | Commit | PR | Tests | Next task |
|---|---|---|---|---|---|---|
| P0 Evidence, constraints, baseline | Verified (merged) | `ccr-bc6d0795-mkakke` | `d4c24cc` (merge) | #14 | Local and CI PASS; 9 P0 characterization tests | Answer the **Owner** items in `decisions.md` (D-06, D-08, D-09, D-13) when their phase comes |
| P1 Visual foundation | Verified locally; needs phone verification (awaiting owner review) | `ccr-f940f7dc-udg2hj` | head of PR #15 | #15 (draft) | Local: 837 JVM tests PASS, lint 0 errors, debug build PASS, APK +1.38 MB; CI on PR #15: pending at hand-off | Owner reviews the PR and the decisions D-22–D-29; phone checks in `verification.md` → "Glass and look (after P1)"; then P2 |
| P2 Navigation and finding | Not started | — | — | — | — | Start after P1 is merged |
| P3 Capture and trust | Not started | — | — | — | — | — |
| P4 Remaining experiences | Not started | — | — | — | — | — |
| P5 Settings, migration, language, data safety | Not started | — | — | — | — | — |
| P6 Final verification and owner handoff | Not started | — | — | — | — | — |

## Baseline

- `main` inspected: `f2662dd9c7efad43cf657325beb30e6be167b6f7` (app code identical to `43a1642`, the commit the proposal read).
- Gates before redesign: workplace identity purge COMPLETE, cleanup baseline COMPLETE (`docs/codex/PROJECT_STATE.md`).
- Build/test path: local Gradle with a per-session SDK install, and GitHub Actions on every PR (both green at baseline; details in `verification.md`).

## P1 outcome (what landed)

- Limestone & Ink colour tokens (§6.1) in light and dark, four accents (§7.17), Space colours; mapped onto Material so every existing screen picks them up. Ochre reachable only through `TalleleTheme.colors`.
- Literata (the person's words) + Noto Sans (the app's words), bundled with their OFL texts; Google Sans Flex removed; Russian uses the same fonts.
- §6.3 shapes, §6.5 motion, 4 dp grid, 20/16 dp margins.
- Glass only on the tab bar (`TalleleTabBar`, Glass/Frosted/Solid) with automatic fallback (Android ≤ 12, battery saver, Remove animations, SoftOnly routes); the bar shows the screen behind it (D-27). Every other surface is solid; the page is plain (no gradients, light pools, noise or photo).
- Ten WebP backgrounds and `CaptureBackground` with per-image washes (D-23), not yet placed on a screen (D-26).
- Appearance model derived from the old settings (D-22): nothing stored changes until P5.
- Shared parts (§10.3) with previews at 100 %/200 %: KeepMark, KeepButton, CaptureBox, Receipt, DecisionCard, ItemRow, FactRow, SectionHeader, ChoiceSheet, SourceRow, ReminderStatus, UndoMessage, DayAgenda, TalleleTabBar.
- Docs updated: visual system, glass system, design language, regression checklist, project state, glass skill.
- No Room, export, DataStore key, reminder, filing or string changes. No new store for the erase inventory.

Carried forward: P2 wires the measured tab-bar height into screens (D-28) and moves tab switching to the 180 ms fade; P3 places `CaptureBackground` and the parts on Capture; P5 gives switches a Limestone colour set (D-29), removes the old Appearance controls and converts the stored values once.

## P2 start checklist (next session)

1. Confirm P1 is merged into `main`; branch from current `main`.
2. Read `CLAUDE.md`, this file, `implementation-plan.md` (P2 rows), `decisions.md` (D-06 owner question on diacritics, D-10, D-28), `p0-findings.md` entry-point inventory, proposal §3, §7.6–7.8, §7.10, §7.12.
3. Install the SDK (`CLAUDE.md`), run the baseline tests once.
4. Single writers: `OrbitApp.kt` / `OrbitDestination.kt` / `FloatingBottomNavigation.kt` (compose-implementer, nav), `domain/search/LocalSearch.kt` (data-implementer), strings (assets-i18n).
5. Exit: three-tab shell, every entry point lands where it did (tests), Search with kept thoughts and no duplicates, state restored after Back, badge counts To decide only, QA review, draft PR.
