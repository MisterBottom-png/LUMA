# Final MVP QA

```text
Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person; use generic role labels only. Run `python scripts/codex/check_workplace_privacy.py --strict` when text-bearing files change and report only `Workplace privacy checked: yes`.

LUMA: run the final initial-MVP QA gate.

Do not add features or perform broad cleanup. Use luma-regression-qa and, only when useful, the read-only release reviewer.

Verify core paths, protected behaviors, data safety, Gemini fallback, reminders, themes, and the acceptance evidence. Distinguish automated checks from manual checks and pre-existing failures from regressions.

Return exactly one status: MVP PASS, MVP PARTIAL, or MVP BLOCKED, with concise evidence and the next single action if anything remains.
```
