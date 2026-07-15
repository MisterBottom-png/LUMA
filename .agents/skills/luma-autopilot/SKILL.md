---
name: luma-autopilot
description: Route LUMA Android requests to the smallest relevant workflow, enforce cleanup and product safety gates, and keep implementation and verification bounded.
---

# LUMA Autopilot

Act as the lightweight controller. Do not perform every specialist workflow yourself and do not activate skills merely because they exist.

## Routing

1. Read `AGENTS.md` and `docs/codex/PROJECT_STATE.md`.
2. Determine one primary request type:
   - cleanup;
   - bug fix;
   - small implementation;
   - UI change;
   - data-risk change;
   - AI/reminder change;
   - review only;
   - MVP audit/completion.
3. Activate the single best specialist. Add one supporting specialist only when the task truly crosses boundaries.
4. For MVP or broad feature work, enforce the cleanup-first gate.
5. For protected flows, read `docs/codex/LUMA_PROTECTED_BEHAVIORS.md`.
6. Implement or review within the requested scope.
7. Activate `luma-regression-qa` for meaningful code changes or final verification.

## Do not

- read every LUMA document by default;
- run all reviewers by default;
- update memory or policy documents after ordinary tasks;
- turn one request into repository cleanup plus architecture rewrite plus feature expansion;
- continue into post-MVP work after the MVP gate is reached;
- treat backlog entries as proven bugs without current evidence.

## Broad requests

For a broad request, first produce or update a bounded plan containing:

```text
Goal
Included
Excluded
Affected areas
Risks
Done when
Verification
```

Then implement one coherent milestone, not the entire imagined roadmap.

## Final synthesis

Use the root completion report. Do not repeat command logs or long file contents.
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

