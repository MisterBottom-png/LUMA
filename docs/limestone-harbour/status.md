# Limestone Harbour: status

Read this first in every session. Then `implementation-plan.md`, the proposal sections for the phase, and the affected code. States: Not started / In progress / Blocked / Verified / Needs phone verification.

| Phase | State | Branch | Commit | PR | Tests | Next task |
|---|---|---|---|---|---|---|
| P0 Evidence, constraints, baseline | Verified (awaiting owner review) | `ccr-bc6d0795-mkakke` | head of PR #14 (content complete at the "QA review fixes" commit) | #14 | Local: 735 JVM tests PASS, lint 0 errors, debug build PASS; CI on `main` and on the PR head PASS (incl. API 36 emulator); 9 P0 characterization tests | Review and merge #14; answer the **Owner** items in `decisions.md` (D-06, D-08, D-09, D-13) when their phase comes |
| P1 Visual foundation | Not started | — | — | — | — | Start after P0 is merged |
| P2 Navigation and finding | Not started | — | — | — | — | — |
| P3 Capture and trust | Not started | — | — | — | — | — |
| P4 Remaining experiences | Not started | — | — | — | — | — |
| P5 Settings, migration, language, data safety | Not started | — | — | — | — | — |
| P6 Final verification and owner handoff | Not started | — | — | — | — | — |

## Baseline

- `main` inspected: `f2662dd9c7efad43cf657325beb30e6be167b6f7` (app code identical to `43a1642`, the commit the proposal read).
- Gates before redesign: workplace identity purge COMPLETE, cleanup baseline COMPLETE (`docs/codex/PROJECT_STATE.md`).
- Build/test path: local Gradle with a per-session SDK install, and GitHub Actions on every PR (both green at baseline; details in `verification.md`).

## P1 start checklist (next session)

1. Confirm P0 is merged into `main`; branch from current `main`.
2. Read `CLAUDE.md`, this file, `implementation-plan.md` (P1 rows), `decisions.md` (D-02, D-03, D-19), proposal §6 and §7.17.
3. Install the SDK (`CLAUDE.md`), run the baseline tests once.
4. Single writers: theme files (compose-implementer), `res/font` + backgrounds + licences (assets-i18n), `AppSettings` additive enums (data-implementer). Glass tab bar after tokens exist.
5. Exit: contrast tests for every token pair in both themes, fallback tests, previews at 200 %, APK size delta, QA review, draft PR.
