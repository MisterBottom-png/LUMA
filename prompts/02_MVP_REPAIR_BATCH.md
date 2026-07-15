# MVP repair batch

```text
Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person; use generic role labels only. Run `python scripts/codex/check_workplace_privacy.py --strict` when text-bearing files change and report only `Workplace privacy checked: yes`.

LUMA: implement only MVP repair batch <ID OR AREA>.

Use the current MVP gate, backlog evidence, project state, protected behaviors, and only the specialists needed for this batch.

Make the smallest reliable change. Do not clean unrelated code, implement another backlog item, or expand post-MVP features. Run targeted checks during iteration, broader relevant checks once, review the final diff, and update status only with evidence.

Stop after this batch and report remaining risk and manual device checks.
```
