# LDS V2 workflow

## Purpose

LDS V2 makes GitHub the single source of truth for LUMA development. Work begins from the latest merged `main`, proceeds on a numbered change branch, and reaches review through a draft pull request. Archives and local copies are outputs or references only; they never outrank GitHub.

The repository `planning/` directory is the canonical product backlog. Its checklist and detailed prompt files must be used without rewriting, renumbering, renaming, reordering, summarising, or regenerating their task definitions.

## Canonical planning file hashes

The following SHA-256 hashes identify the canonical files extracted from `LDS_V1_Complete_Package.zip`:

- `planning/README.md`: `c725d1265ee20e5d134c116a9e219fc3c40ebcd52dcc203911aa75ba45568585`
- `planning/LUMA_UI_UX_Small_Change_Steps.md`: `a4c05ac0224e68ec6903210da017b8a67513d1f266da4845109d670d5185a4cf`
- `planning/LUMA_ChatGPT_Project_Small_Change_Prompts.md`: `32059513d997557488b615aa295f6af070545f3df7013373f39c09acefa71426`

## 1. Start from authority

For every source-changing task:

1. Open `MisterBottom-png/LUMA` through the GitHub integration.
2. Read the latest `main` branch.
3. Read root `AGENTS.md`, `PROJECT_STATE.md`, `PROGRESS.md`, and `CHANGELOG.md`.
4. Read `planning/README.md` and the canonical planning documents relevant to the requested area.
5. Read the protection documents relevant to the requested area.
6. Inspect the existing implementation before proposing edits.
7. Check open pull requests for duplicate or overlapping work.
8. Confirm whether the task needs binary writes and whether the available tools can perform them safely.

Previous branches, chat context, Drive packages, ZIP files, and local folders are not valid substitutes for this startup sequence.

## 2. Assign continuity

Determine the next unpublished change number from repository continuity. Create a branch from the latest `main` using:

```text
lds/change-NNN-short-description
```

Never reuse a published change number and never edit `main` directly.

## 3. Implement the bounded change

- Change only what the request requires.
- Preserve unrelated application behavior.
- Avoid opportunistic refactoring.
- Keep source, tests, planning, and continuity updates on the same branch.
- Do not alter canonical planning task definitions unless the user explicitly requests a planning change.
- Do not create or promote replacement source archives as authority.
- Stop with `BLOCKED` when required repository access or write capability is unavailable.

## 4. Validate

Run the smallest relevant checks during implementation and broader relevant checks before completion. GitHub Actions is authoritative for unit tests, lint, and Android builds.

For text-bearing changes, run the strict workplace-privacy checker when execution is available and semantically review all changed text.

When canonical planning files are copied from an approved source package, compare the repository copies against the extracted source bytes and verify their SHA-256 hashes.

Report validation only with the LDS V2 status terms defined in `docs/LDS_V2_STATUS_MODEL.md`.

## 5. Open a draft pull request

Open a draft pull request targeting `main`. Its description must include:

- requested outcome;
- acceptance criteria;
- important files changed;
- protected behavior preserved;
- local or static checks performed;
- GitHub Actions status;
- manual checks still required.

Inspect the complete PR diff after creation. If CI fails because of the implementation, update the same branch and re-check CI.

## 6. Review and merge boundary

A change is ready for user review when the draft PR exists, the complete diff matches scope, continuity is consistent, and no implementation-caused CI failure remains unresolved.

Do not merge without explicit approval of the specific pull request. Do not enable automatic merge unless explicitly instructed.

## 7. Begin the next task cleanly

After a change is merged, the next task starts from the new latest `main`. Never continue development from an old task branch, even when that seems conveniently human.
