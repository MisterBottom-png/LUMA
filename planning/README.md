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

## LDS V2 canonical planning provenance

- Original canonical prompt filename: `LUMA_ChatGPT_Project_Small_Change_Prompts.md`
- Original prompt SHA-256: `32059513d997557488b615aa295f6af070545f3df7013373f39c09acefa71426`
- Numbered prompt range: 1 through 56
- Canonical checklist filename: `LUMA_UI_UX_Small_Change_Steps.md`
- Canonical checklist SHA-256: `a4c05ac0224e68ec6903210da017b8a67513d1f266da4845109d670d5185a4cf`
- The monolithic prompt document was split into `small-changes/` only for reliable GitHub connector writes and selective agent loading. The numbered prompt text was not rewritten, summarised, simplified, reordered, or regenerated.
- `SMALL_CHANGES.md` preserves the canonical checklist ordering. Completion marks for items 1 through 14 are based on root `PROGRESS.md`; Small Change 15 remains incomplete.
