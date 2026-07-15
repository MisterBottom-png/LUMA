# LUMA LDS V2 repository agent contract

## Authority

- The GitHub repository `MisterBottom-png/LUMA` is the only authoritative source for code, project state, planning, and implementation history.
- The latest merged commit on `main` is the sole starting point for every task.
- Drive files, ZIP archives, local folders, previous chat artifacts, generated packages, and unmerged branches are not authoritative.

## Canonical implementation backlog

- `planning/` is the canonical implementation backlog.
- `planning/SMALL_CHANGES.md` preserves the ordered checklist and completion state.
- Each numbered implementation prompt lives in `planning/small-changes/NNN-short-title.md`.
- For a request such as “Implement Small Change 15”, read both `planning/SMALL_CHANGES.md` and `planning/small-changes/015-clear-stale-ask-responses.md` before inspecting or changing source.
- Use the matching numbered file as the exact bounded task definition. Do not rewrite, summarise, reorder, combine, or regenerate its contents.

## Mandatory startup

For every source-changing task:

1. Open `MisterBottom-png/LUMA` through the GitHub integration.
2. Re-read the latest `main`; never continue from a previous task snapshot.
3. Read this file first.
4. Read root `PROJECT_STATE.md`, `PROGRESS.md`, and `CHANGELOG.md`.
5. Read `planning/SMALL_CHANGES.md` and the matching numbered file under `planning/small-changes/`, then inspect the existing implementation.
6. Check open pull requests to confirm the requested work is not already in progress or complete.
7. Determine whether binary-file writes are required. If they are required and no binary-capable write action is available, stop before editing and report the exact fallback needed.
8. Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before changing any text-bearing repository surface.

## Branch and continuity rules

- Never modify `main` directly.
- Create a branch from the latest `main` named `lds/change-NNN-short-description`.
- Use the next unpublished change number from repository continuity. Never reuse a published number.
- Implement only the requested bounded change.
- Preserve unrelated behavior and avoid opportunistic refactoring.
- Update continuity and planning documents on the same branch.
- Use clear commit messages. Multiple commits are acceptable when repository tooling writes files separately.

## Pull request rules

- Open a draft pull request targeting `main`.
- The pull request description must record:
  - requested outcome;
  - acceptance criteria;
  - important files changed;
  - protected behavior preserved;
  - local or static checks performed;
  - GitHub Actions status;
  - manual checks still required.
- Inspect the complete PR diff before reporting completion.
- GitHub Actions is authoritative for unit tests, lint, and Android builds.
- Fix implementation-caused CI failures on the same branch and re-check CI.
- Do not merge unless the user explicitly approves that pull request or explicitly enables automatic merge after green CI.

## Source and write restrictions

- Do not ask the user to attach, download, upload, copy, extract, or replace LUMA source during the normal workflow.
- Do not use Project files, old ZIP archives, Drive copies, local device folders, or previous chat artifacts as current source.
- Do not create replacement source ZIPs during normal development.
- Do not claim a repository write succeeded without reading the resulting branch or pull request state.
- If repository access or a required write capability is unavailable, stop and report the exact missing permission or capability.

## Product invariants

- LUMA remains a calm, local-first Android life inbox.
- Home remains capture-first rather than becoming a dashboard.
- Raw captures remain private source material.
- Spaces and Life Feed show finalized user-facing items only.
- Internal AI processing records and technical identifiers are not user-facing content.
- AI may suggest, classify, summarize, explain, and draft, but must not silently perform important actions.
- Preserve graceful operation when Gemini is unavailable.
- Avoid guilt, shame, hustle, and productivity-score language.

## Engineering invariants

- Prefer the smallest reliable patch.
- Preserve the existing architecture unless the task requires a justified change.
- Do not add backend services, authentication, cloud sync, paid services, external calendar sync, or production dependencies unless explicitly requested.
- Never hardcode secrets.
- Do not change Room entities, database versions, migrations, export formats, or restore behavior without explicit scope and specialist review.
- Keep event time, reminder target time, and notification offset separate.
- Preserve 24-hour behavior unless the product requirement explicitly changes.
- Do not treat generated output as source of truth.

## Workplace privacy gate

- No workplace-associated person may be identified in repository-controlled content.
- Use neutral role labels only.
- Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes whenever execution is available.
- A passing heuristic scan does not replace semantic review of changed text.

## Verification status language

Use only:

- `PASSED`: the check ran and passed.
- `FAILED`: the check ran and failed.
- `PENDING_CI`: GitHub Actions has not completed.
- `MANUAL_CHECK_REQUIRED`: device, visual, accessibility, or binary verification still needs a human.
- `BLOCKED`: required access or capability is missing.

## Completion gate

A task is ready for review only when:

- a draft pull request exists;
- the complete diff matches the requested scope;
- continuity files are consistent;
- no unresolved implementation-caused CI failure remains;
- remaining manual checks are stated clearly.

Every later task starts again from the latest merged `main`, not from this task branch.
