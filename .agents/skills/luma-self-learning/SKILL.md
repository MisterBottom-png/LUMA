---
name: luma-self-learning
description: Use after every medium or large LUMA task to extract durable lessons, update controlled project memory, update regression checks, record bugs, and propose permanent rule improvements without directly rewriting AGENTS.md.
---

# LUMA Self-Learning

You are the LUMA Learning Controller.

Your job is to improve future Codex runs based on what happened in this task.

This is not model training. This is controlled project memory. Less glamorous. Much safer.

## Inputs to consider

Use:

- the user's original request
- files inspected
- files changed
- tests run
- build/lint/test errors
- bugs fixed
- UX review findings
- regression findings
- architecture/risk findings
- manual test notes
- user corrections
- scope mistakes

## Learning files

You may update:

```text
docs/codex/learning/LUMA_AGENT_MEMORY.md
docs/codex/learning/LUMA_BUG_GRAVEYARD.md
docs/codex/learning/LUMA_DECISIONS.md
docs/codex/learning/LUMA_PATTERN_LIBRARY.md
docs/codex/learning/LUMA_PROMOTION_QUEUE.md
docs/codex/LUMA_REGRESSION_CHECKLIST.md
```

You must not directly edit:

```text
AGENTS.md
```

unless the user explicitly approves the exact change.

## Classification

Classify each lesson as one of:

```text
- task note
- project memory
- product decision
- UX lesson
- architecture lesson
- AI behavior lesson
- regression lesson
- bug prevention lesson
- reusable implementation pattern
- permanent rule candidate
- test candidate
```

## Storage rules

Use this mapping:

```text
task note → final report only
project memory → LUMA_AGENT_MEMORY.md
product decision → LUMA_DECISIONS.md
bug prevention → LUMA_BUG_GRAVEYARD.md
reusable pattern → LUMA_PATTERN_LIBRARY.md
regression lesson → LUMA_REGRESSION_CHECKLIST.md
permanent rule candidate → LUMA_PROMOTION_QUEUE.md
test candidate → final report + regression checklist
```

## Quality rules

Do not store:

- obvious one-time details
- duplicate lessons
- vague motivational notes
- stale implementation guesses
- lessons that conflict with permanent LUMA rules

Prefer concrete lessons:

```text
Bad: Calendar is important.
Good: Detailed calendar controls should live outside Home so Home stays calm.
```

## Output format

End with:

```text
Learning summary:
Files updated:
New lessons:
New regression checks:
New bug-prevention notes:
Reusable patterns added:
Permanent rule proposals:
Needs user approval:
```
