# Source reconciliation — 2026-10-09

This records how the attached Hermes source snapshot dated 2026-08-01 was
reconciled with the GitHub `currentsource` line (PR #3) before the redesign.

## Starting state

| Ref | SHA | Notes |
| --- | --- | --- |
| `origin/main` | `d1a140fd08e96b4b9d7ec5c10960de4ce8a08291` | Older planning/agent-stack line; ancestor of `currentsource`. |
| `origin/currentsource` (PR #3, open) | `f0a6fe953458afcd0c5439f56cf974fd7eeb87b1` | July 26 Hermes import (`3415f4a`) + 13 audit/fix commits. Room v5, backup v3. |
| `origin/lds/change-015-system-v2-migration` (PR #2, open) | `ba4572438544e3efd327ea65576a17fa2e98a135` | Planning-workflow docs only; not part of this integration. |
| Integration branch | starts at `f0a6fe9` | Work happens on the session-designated branch (see below). |
| Hermes 2026-08-01 snapshot | imported as `43741ee` | Room v6, backup v4. |

CI on PR #3 at handoff: `verify` and `Fast release gates` passed; `API 36 emulator
verification` failed in connected instrumentation tests.

Branch name: the requested name was `integration/hermes-2026-08-01-redesign`;
this environment only permits pushing to the session branch
`ccr-eed43729-xsml4c`, which plays that role.

## Strategy

A directory overwrite would have dropped either the August Hermes work or the
GitHub audit fixes, so the reconciliation is a real three-way merge:

1. The July 26 import commit `3415f4a` is the common ancestor of both lines.
2. Branch `import/hermes-2026-08-01` = `3415f4a` + the August 1 snapshot laid
   over it (commit `43741ee`). Only files the archive actually contains are
   compared; the archive omits dot-directories (`.agents`, `.codex`, `.github`),
   root status files, adaptive launcher XML and the agent-stack validator, so
   those were kept from the base rather than treated as deletions.
3. That branch was merged into `currentsource`; git resolved 140+ files cleanly
   and the 10 true conflicts were resolved by hand (below).

## Hermes-only work (kept)

- Room v6: `LabelEntity`, `note_labels`, `task_labels`, `reminder_labels`,
  `Migration5To6`, schema `6.json`, `LabelRoomRepositoryTest`.
- Backup format v4 with label validation; `LocalDataExporterSnapshotTest`.
- First-time tutorial and replay (`ui/screens/tutorial/*`), optional starter
  Space selection and custom Space creation during onboarding.
- Brain Dump interaction refactor: `BrainDumpFlowCoordinator`,
  `BrainDumpInteractionState`, `BrainDumpSkipUndoController`,
  `BrainDumpSuggestionContent` (+ tests).
- Review: `ReviewLoopActionPlan`, `ReviewRowInteraction`,
  `ReviewTaskUndoController` (+ tests).
- Spaces: `SpaceItemMoveActions` (+ tests).
- Settings: AI sub-pages, header clearance, first-time guide entry,
  typed/localised local-data messages.
- Localisation parity tooling (`check_locale_resource_parity.py`,
  `LocalizationResourceParityTest`), predictive-back manifest test.
- Design notes under `docs/superpowers/`.

## GitHub-only fixes (kept or re-applied)

| Fix | Result |
| --- | --- |
| Settings split into focused files | Kept; Hermes Settings changes ported into the split files. |
| monday.com integration removed | Kept; re-applied to Hermes code (`possibleMondayItem`, `mondayConfigured`, Send-to-Monday). Legacy `CaptureSource.Monday`, `SuggestedItemType.MondayItem`, `TaskEntity.mondayItemId` stay as storage tokens. |
| Gemini key in `x-goog-api-key` header | Kept (auto-merged). |
| Reset local data (confirm + cancel notifications) | Kept; extended to clear Labels; no longer re-seeds starter Spaces because Hermes made them optional. Messages are now typed (`LocalDataToolsMessage.ResetCompleted/ResetFailed`). |
| Reminder notification enable/offset controls in Item Details | Kept (auto-merged). |
| Scheduling-failure warning on capture-created reminders | Re-applied to the Hermes `HomeCaptureViewModel`. |
| Non-positive Gemini reminder times rejected | Kept (auto-merged). |
| Completed reminders excluded from Space counts | Kept (auto-merged). |
| Capture messages extracted to resources | Superseded by Hermes' equivalent `core_home_message_*` resources; the duplicate `core_capture_message_*` set was removed. |
| API-level guard for animator scale | Superseded by Hermes' `ValueAnimator.areAnimatorsEnabled()` (API 26, within minSdk). |
| et/ru translation fixes, env-based release signing, wrapper portability, CI emulator provisioning, removed caches | Kept. The executable bit on `gradlew` was restored after the snapshot overlay. |
| Dead `ReminderDetailScreen`/`ViewModel` removed | Kept deleted. |

## Conflict classification

| File | Decision |
| --- | --- |
| `CaptureAnalyzer.kt` | Keep Hermes (localised next action) minus monday flag. |
| `OrbitApp.kt` | Manual merge: reset callback + tutorial replay + renamed subsection callback. |
| `CaptureSuggestionSheet.kt` | Keep Hermes Brain Dump callbacks; drop monday params. |
| `HomeCaptureViewModel.kt` | Keep Hermes; re-apply scheduling-attention and monday removal. |
| `HomeScreen.kt` | Keep Hermes Brain Dump callbacks; drop monday. |
| `ReviewScreen.kt` | Keep Hermes animator check; remove the test-only backdrop helper whose test Hermes deleted. |
| `SettingsScreen.kt` | Manual merge into the split files. |
| `values-et/ru/strings_localization.xml` | Union, then drop now-unused duplicates. |
| `check_workplace_privacy.py` | Keep GitHub (same vocabulary, commented). |

## Data-model guarantees

- Room stays at version 6 with the full `1→2→…→6` migration chain.
- Backup format stays at version 4; versions 1–4 still decode.
- No destructive migration was introduced.

## Verification after reconciliation

Local (no KVM, so no emulator): `:app:testDebugUnitTest` (371 tests, 0
failures), `:app:testReleaseUnitTest`, `:app:lintRelease`,
`:app:assembleDebugAndroidTest`, `:app:assembleRelease`,
`:app:bundleRelease`, strict privacy check — all PASS.
Device tests: NOT RUN locally; delegated to the API 36 CI job.
