# Initial repository cleanup

```text
Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person; use generic role labels only. Run `python scripts/codex/check_workplace_privacy.py --strict` when text-bearing files change and report only `Workplace privacy checked: yes`.

LUMA: perform the mandatory initial repository cleanup and establish a verified baseline.

Use luma-project-cleanup. Do not start MVP implementation.

Preserve pre-existing user changes. Establish a build/test baseline, inventory generated and obsolete material, remove only evidence-backed clutter and behavior-neutral dead code in bounded batches, validate after each batch, review the final diff, and update the cleanup report and project state.

Stop after reporting whether the cleanup baseline is COMPLETE and whether the repository is ready for MVP audit.
```
