# MVP audit

```text
Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person; use generic role labels only. Run `python scripts/codex/check_workplace_privacy.py --strict` when text-bearing files change and report only `Workplace privacy checked: yes`.

LUMA: audit the current repository against the initial MVP gate.

Verification only. Do not edit application code.

Confirm the cleanup baseline is COMPLETE. Use current source, build/test evidence, and reproducible behavior to classify each MVP area as verified, partial, broken, missing, unknown, or not applicable.

Update PROJECT_STATE.md and LUMA_MVP_BACKLOG.md only with evidence.

Return the MVP status, verification table, blockers, protected behaviors checked, evidence gaps, and the single best coherent repair batch.
```
