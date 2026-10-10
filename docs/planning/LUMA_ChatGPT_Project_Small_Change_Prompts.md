
# LUMA Small-Change Prompts for a ChatGPT Project

This file contains the small-step implementation prompts for the LUMA project. The authoritative source is the configured Google Drive file. Each prompt is deliberately narrow: one chat, one bounded change, followed by the state-based Drive publication workflow.

## Recommended Project setup

1. Create a new ChatGPT Project and select **project-only memory** when available. This keeps the coding context isolated from unrelated chats.
2. Add the planning files and this agent kit to the ChatGPT Project. Keep the application source in the configured Google Drive file rather than repeatedly uploading source ZIPs to Project Sources.
3. Put the Project Instruction below into the Project's instructions.
4. Begin with the Baseline Audit prompt.
5. Use **a new chat for each small change**. This limits context drift and makes failures easier to isolate.
6. After a successful change, the agent must package the new ZIP at an exact `/mnt/data/...zip` path, pass that path as the Drive action's top-level `file_uri`, then verify the downloaded authoritative ZIP by SHA-256 and archive integrity.
7. Keep numbered backups only in the configured Archive folder.
8. Do not rely on chat memory as a substitute for the Drive source or project log. Memory is context, not version control.

## Project Instruction

```text
You are modifying the LUMA Android source project stored in the authoritative Google Drive file configured in `DRIVE_CONFIG.md`.

LUMA is a calm, capture-first, local-first personal life-management application. Preserve its ideology, current architecture, visual identity, data model, Room source of truth, and working behaviour.

For every implementation request:

1. Fetch and inspect the authoritative Google Drive source ZIP and the relevant planning files before editing.
2. Identify the existing implementation and affected files.
3. Make only the requested small change.
4. Do not perform unrelated cleanup, redesign, dependency upgrades, architectural migrations, or speculative refactors.
5. Reuse existing components, repositories, navigation, state models, design tokens, and test patterns where practical.
6. Preserve database schemas, migrations, user data, navigation contracts, and finalized item behaviour unless the prompt explicitly requires a change.
7. Add or update focused tests for changed behaviour.
8. Run the most relevant available tests and a debug build when the environment supports them.
9. Fix failures caused by the change.
10. Never claim a build or test passed unless it was actually run.
11. Update the existing PROGRESS.md or project change log with:
    - completed change;
    - files changed;
    - tests/builds run;
    - unresolved limitations.
12. Return:
    - a concise description of the existing implementation;
    - a concise implementation summary;
    - exact files changed;
    - tests and build results;
    - unresolved issues or assumptions;
    - confirmation that the authoritative Drive ZIP was replaced in place using the exact mounted path as top-level `file_uri`, with downloaded SHA-256 and ZIP-integrity verification, plus a downloadable local ZIP.

Do not merely return code snippets or instructions. Modify the fetched project and update the authoritative Drive file itself.

Before packaging, check that generated build directories, IDE caches, local secrets, signing files, and unrelated archives are not added to the output ZIP.

If the requested change conflicts with the current architecture or cannot be safely completed from the provided source, stop and explain the exact conflict instead of inventing missing behaviour.
```

## Baseline Audit prompt

Run this once before the first implementation chat.

```text
Inspect the latest authoritative LUMA source ZIP and the UI/UX planning files in this Project.

Do not modify the source yet.

Create a concise baseline report containing:
- detected project root;
- Android modules;
- build system and important versions;
- architecture and state-management patterns;
- navigation structure;
- relevant Home, Spaces, Review, Situation AI, theme, accessibility, and test files;
- commands that should build and test the project;
- existing PROGRESS.md or project-state files;
- any missing files or source-extraction problem;
- a proposed mapping from each small-change prompt to likely source files.

Return the report as `LUMA_UI_UX_BASELINE.md`.
Do not guess about files you did not inspect.
```

## Standard implementation wrapper

Every numbered prompt below already states the requested change. For maximum reliability, begin each new chat with:

```text
Use the latest authoritative LUMA source ZIP in this Project.

Follow the Project instructions. Implement exactly one numbered change from `LUMA_ChatGPT_Project_Small_Change_Prompts.md`.

First inspect the existing implementation. Then modify the source, run focused verification, update the project log, and package the completed change, follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`, and report the verified Drive state.

Do not combine this with another numbered change.
```

## Efficient verification loop

After the agent reports the Drive update:

```text
Review the updated ZIP you just produced against the requested change.

Check:
- only intended files changed;
- no generated build output, caches, secrets, signing material, or nested source ZIPs were included;
- the requested acceptance behaviour is implemented;
- tests support the claimed result;
- PROGRESS.md accurately records the work.

Do not make new feature changes during this review.
If corrections are necessary, apply only those corrections and replace the authoritative Drive ZIP again with the corrected complete source.
```

---
# Numbered implementation prompts
## 1. Remove internal AI IDs

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Remove all user-visible internal identifiers such as `task:5`, `capture:26`, reminder IDs, database IDs, or similar technical references. Preserve the identifiers internally for lookup and navigation. Add or update a focused test proving they are not rendered.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 2. Create the Source Row component

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Create a reusable Compose Source Row for AI evidence. It must support a human-readable title, item type, optional date/time, open affordance, click callback, long-title handling, and accessibility text. Add previews or focused UI tests. Do not migrate existing screens yet.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 3. Use Source Rows in Situation AI

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Replace Situation AI source chips or technical source controls with the existing Source Row component. Preserve item-opening behaviour. Do not change recommendation ordering, wording, or action hierarchy.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 4. Hide the Home microphone

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Hide the Home microphone whenever voice capture is unavailable. Do not leave a disabled placeholder. Preserve capture-card spacing and all other Home behaviour.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 5. Hide unavailable Monday actions

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Hide Monday.com actions when the integration is unavailable or not configured. Keep setup in Settings only if it already exists. Preserve genuinely working configured behaviour.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 6. Remove remaining dead controls

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Inspect visible controls and remove or hide only those proven to have no working result. List each removed control and the source evidence showing that it was dead. Do not remove unfamiliar but functional controls.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 7. Fix the Situation AI header

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Make the Situation AI header fixed while content scrolls. Keep the title and Close control visible at every scroll position. Do not reorder or redesign the content.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 8. Make the Situation AI body scrollable

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Place the Situation AI body in one independently scrolling region beneath the fixed header. Avoid unnecessary nested scrolling. Keep all current content reachable.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 9. Pin the Ask composer

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Pin the existing Ask LUMA composer at the bottom of the Situation AI shell above system navigation and the keyboard. Do not redesign the composer in this change.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 10. Fix Situation AI Back behaviour

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Make Android Back dismiss Situation AI and restore focus to the central AI control. Preserve normal Back behaviour when the sheet is closed.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 11. Make Situation AI keyboard-safe

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Use IME and system-bar insets so the keyboard never covers the composer or relevant content. Do not use hardcoded keyboard heights.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 12. Embed the send icon

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Replace the separate Ask button with an embedded send icon inside the composer. Give it a 48 dp target, enabled/disabled/pressed states, and an accessible label. Preserve submission logic.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 13. Add Ask loading state

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Add a calm loading state while Ask LUMA generates a response. Prevent duplicate submissions and restore the send control on success or failure. Add focused state tests.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 14. Support IME Send

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Submit Ask LUMA through the keyboard IME Send action using the same code path as the send icon. Do not submit blank queries or duplicate requests.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 15. Clear stale Ask responses

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

When a displayed Ask answer's question is meaningfully edited, clear or mark the answer and its evidence/actions stale. Add state tests for edit, loading, and submission transitions.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 16. Separate Review modes and Open Loops

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Keep Morning, Evening, and Weekly as time-based Review modes. Move Open Loops or Sort Pending into a distinct workflow control. Preserve underlying routes and data.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 17. Make Review rows tappable

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Make every Review row that looks interactive tappable, with pressed feedback and correct accessibility role. Do not make informational rows appear interactive.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 18. Open the correct Review item

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Wire Review row taps to the correct existing detail destination for tasks, captures, and reminders. Reuse current navigation and item IDs. Do not create duplicate detail screens.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 19. Add carry-forward decisions

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Add explicit carry-forward actions: Tomorrow, Choose date, Keep unscheduled, and Mark complete. Reuse existing scheduling and completion logic. Require confirmation where current behaviour requires it.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 20. Add bottom-navigation selection indicators

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Add a selected container, indicator, or shape change so selection remains clear without colour. Preserve the current navigation structure.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 21. Label bottom-navigation destinations

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Add visible destination labels, at least for the selected destination and the central AI destination. Label the central control `AI` or `Situation` according to existing terminology.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 22. Raise touch targets to 48 dp

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Audit affected UI controls and increase any target below 48 × 48 dp without unnecessarily enlarging its visible artwork. Add focused tests or previews for representative controls.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 23. Add navigation accessibility semantics

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Expose bottom-navigation items as tabs, announce selected state, and add precise labels to icon-only controls touched by this work. Do not perform a whole-app accessibility refactor.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 24. Create the contrast-test baseline

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Create reusable visual or screenshot fixtures for light/dark mode, accent themes, and representative bright, dark, detailed, and high-contrast backgrounds. Establish the baseline only; do not broadly restyle screens.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 25. Add AI trust presentation states

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Introduce explicit presentation models for Observed fact, Inference, Suggestion, Insufficient context, and No action. Add calibrated labels/wording and tests. Do not yet reorder Situation AI cards.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 26. Require confirmation for AI data changes

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Ensure every AI-originated action that changes a task, capture, reminder, date, schedule, or completion state requires explicit confirmation. Dismissal and feedback must not mutate source items.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 27. Add Suggested Next Move

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

After confirming the AI trust-state model exists, add a Suggested next move section near the top of Situation AI. Show guidance only when evidence supports it; otherwise show neutral choices or a calm no-action state.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 28. Limit Situation AI to one primary action

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Give Situation AI at most one visually primary action. Keep secondary actions quieter. Do not invent a preferred action when evidence is weak.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 29. Move uncommon AI actions into overflow

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Move lower-frequency Situation AI commands into a More actions menu. Keep no more than two or three immediate secondary actions.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 30. Place generated results beside their trigger

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Render generated output directly below the action that produced it, scroll it into view when required, and replace stale output when another action is selected.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 31. Remove repeated Review headings

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Remove duplicated Morning/Evening/Weekly headings and repeated status sentences. Keep one principal heading and convert the upper summary into a compact status or metric strip.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 32. Simplify Open Loops actions

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Remove equal visual weight from Open Loops actions. Use a neutral Open item path when no recommendation is supported, move Archive/infrequent actions to overflow, and visually separate destructive actions.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 33. Reorganise Review sections

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Give Morning, Evening, Weekly, and Open Loops distinct content purposes as defined in the improvement plan. Avoid showing the same long section tail in every mode.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 34. Build Weekly Review synthesis

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Replace concatenated Weekly Review titles with a concise synthesis of themes, progress, and unresolved areas. Distinguish facts, inferences, and suggestions using the trust-state model.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 35. Use Source Rows in Weekly Review

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Render Weekly Review evidence using the shared Source Row component in a collapsible `Based on N items` section. Mention the source count once and preserve item opening.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 36. Add the Home month label

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Display only the current month name directly above the Home weekday strip. Style it as quiet context, aligned with the strip. Do not show the year or create a large calendar header.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 37. Lower the weekday strip

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Move the Home weekday strip slightly downward to create comfortable spacing below the month label. Do not alter date selection or capture layout.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 38. Update month across visible weeks

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Update the Home month label when the visible week moves into another month. Use a subtle transition and define predictable behaviour for weeks spanning two months.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 39. Remove the hardcoded profile fallback

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Remove any developer-specific or hardcoded personal-name fallback on Home. Use a neutral fallback or omit the name line while preserving named greetings when valid profile data exists.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 40. Rename Life Feed

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Rename the Spaces section currently called Life Feed to the clearest literal term supported by the actual ordering and contents, such as Recent items, Items, or Contents. Update tests and accessibility labels.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 41. Clarify Space row actions

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Keep the main Space content row tappable for opening the item, add a chevron if appropriate, and make Move to Space clearly distinct through a labelled menu or overflow action.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 42. Make Create Space primary

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Use the existing accent-filled treatment for Create Space/Add while preserving its size and alignment. Do not redesign the Spaces header.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 43. Make Search secondary

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Give Search a neutral tonal or transparent treatment while keeping it easy to find and at least 48 dp.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 44. Make the AI control theme-aware

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Build the central AI control gradient and states from existing theme/accent tokens instead of fixed colours. Validate readable icon contrast in light and dark mode.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 45. Introduce shared spacing tokens

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Add a small shared spacing scale and replace only obvious one-off spacing values in the screens touched by this plan. Document tokens and avoid broad visual churn.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 46. Introduce shared shape tokens

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Add shared shape tokens for small, standard, prominent, and modal surfaces. Migrate only comparable components touched by this plan and document pill/circle exceptions.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 47. Test supported screen sizes

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Run or add UI/visual checks for the smallest supported phone, a typical phone, and a tall phone across affected screens. Fix only regressions attributable to this UI/UX plan.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 48. Test light and dark mode

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Run affected-screen checks in light and dark mode. Record and fix clipped, unreadable, or inconsistent states caused by the UI changes.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 49. Test every accent theme

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Run affected-screen checks with every selectable accent. Verify primary, selected, disabled, and AI-control states remain distinct.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 50. Test custom backgrounds

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Test affected screens against bright, dark, detailed, low-contrast, and high-contrast custom backgrounds. Ensure readability does not depend on blur alone.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 51. Test 200% text scaling

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Test affected screens at 100%, 130%, 150%, and 200% font scaling. Fix clipping by allowing wrapping, growth, scrolling, or adaptive layout.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 52. Test screen-reader behaviour

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Verify labels, roles, selected states, action results, and focus order on affected screens with a screen reader. Fix discovered issues within the changed scope.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 53. Test grayscale readability

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Verify selected, disabled, primary, and destructive states remain understandable in grayscale. Fix states that rely only on colour.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 54. Test keyboard behaviour

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Verify IME Send, keyboard opening/closing, Android Back, focus restoration, and inset handling in Situation AI and other affected input surfaces.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 55. Test empty, normal, and stress data

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Exercise affected screens with empty states, typical data, long titles, many items, and missing optional fields. Fix layout or state failures caused by the changes.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```

## 56. Verify AI mutation safety

```text
Use the latest authoritative LUMA source ZIP in this ChatGPT Project.

Implement only this small change:

Audit every AI-originated action and test that no user data changes without an explicit, predictable confirmation step. Report any path that cannot be verified.

Before editing, inspect and briefly describe the current implementation and affected files.

Constraints:
- Follow the Project instructions.
- Preserve unrelated behaviour and visual design.
- Avoid broad refactors and dependency upgrades.
- Add or update focused tests where practical.
- Run relevant tests and a debug build when supported.
- Update PROGRESS.md or the existing change log.
- Package the completed change and follow `rules/DRIVE_PUBLICATION_STATE_MACHINE.md`; report exact changed files, verification results, backup state, authoritative readback, and limitations honestly.
```


---

# Recovery prompts

## When ChatGPT changed too much

```text
Compare your modified project with the previous authoritative source ZIP.

Revert every change not strictly required by the current numbered task. Preserve only the minimum implementation and tests needed for that task.

Do not add improvements, cleanup, dependency changes, formatting sweeps, or architectural refactors.

Return a corrected complete source ZIP and list what was reverted.
```

## When a build cannot run

```text
Do not claim successful verification.

Identify the exact command attempted, the exact failure, and whether it was caused by:
- the fetched Drive project;
- the execution environment;
- missing SDK/tooling;
- network or dependency access;
- your code change.

Perform the strongest available static verification and focused source inspection. Return the updated ZIP only if the change is internally consistent, and record the unverified build state in PROGRESS.md.
```

## When the source ZIP is ambiguous

```text
Stop implementation.

List every candidate source archive or project root you found, including timestamps or distinguishing files. State which one appears newest, but do not modify anything until one authoritative source is established.
```

# Suggested batching

Use one prompt per chat for P0 and behaviour changes. Tiny visual siblings may share a chat only after the individual flow is proven safe:

- Home month label + weekday spacing;
- Create Space primary + Search secondary;
- shared spacing + shape tokens;
- closely related QA checks.

Do not batch Situation AI shell, Review navigation, AI trust states, or mutation confirmation. Those are small in wording and surprisingly talented at becoming architectural incidents.
