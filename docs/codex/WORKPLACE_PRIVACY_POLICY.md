# Zero workplace-person references policy

## Absolute rule

No coworker or other person associated with the user's workplace may be mentioned anywhere in repository-controlled content or generated output.

The rule is not limited to secrets or harmful disclosure. Even harmless attribution, sample text, comments, historical notes, acknowledgements, or jokes are prohibited. The correct representation is a generic role, not a person.

## Prohibited material

Do not write, repeat, preserve, or derive:

- real names, surnames, partial names, nicknames, or initials used as identity;
- usernames, email addresses, phone numbers, account identifiers, signatures, or profile links;
- personal attribution such as author, owner, approver, reviewer, assignee, requester, or contact;
- anecdotes, quotations, schedules, locations, project combinations, job details, or relationships that could identify a person;
- realistic substitute identities that merely disguise the original person;
- identity-bearing filenames, branch names, commit messages, issue text, changelogs, screenshots, logs, reports, examples, fixtures, or generated artifacts.

## Surfaces covered

This applies to production code, configuration, resources, comments, tests, fixtures, examples, documentation, prompts, plans, reports, logs, telemetry, crash text, debug output, filenames, branch and commit text, pull requests, issues, reviewer findings, tool summaries, and final responses.

## Required representation

Use neutral role labels only, for example:

```text
manager
reviewer
stakeholder
team member
operator
administrator
customer
test user
```

Do not invent a human name as a replacement.

## Mandatory repository purge

Before general cleanup, MVP work, or a release-readiness claim:

1. Scan the complete tracked working tree, not only changed files.
2. Review code, comments, resources, tests, fixtures, docs, prompts, configuration, filenames, and generated repository artifacts for person references.
3. Replace repository-controlled references with generic role labels while preserving behavior.
4. Rename identity-bearing files, symbols, test data, or resources when safe, then update references.
5. Remove personal attribution that has no functional purpose.
6. Run `python scripts/codex/check_workplace_privacy.py --strict`.
7. Semantically inspect every strict finding and all changed text. The checker is heuristic and cannot prove absence.
8. Re-run the strict checker and inspect the final Git diff.
9. Set `Workplace identity purge: COMPLETE` only when no repository-controlled workplace-person reference remains.

Never record the discovered identities in a denylist, report, prompt, test, comment, commit message, or generated file. Reports may contain only file locations, categories, counts, and the generic replacement used.

## Existing protected data

Do not silently rewrite databases, exports, backups, imported records, or other real user data during ordinary code cleanup. Do not display identities from those sources. Report only that a protected-data anonymization decision is required.

If runtime integration genuinely needs a person-specific value, keep it outside source control and use an opaque configuration key or identifier. Do not hardcode or document the person's identity.

## Task completion gate

Before completing any task:

1. Review every changed and generated text-bearing surface.
2. Replace repository-controlled person references with generic roles.
3. Run `python scripts/codex/check_workplace_privacy.py --strict`.
4. Inspect the final diff for identity-bearing additions.
5. Report only:

```text
Workplace privacy checked: yes
```

Never list, quote, summarize, or hint at the identities found or removed.
