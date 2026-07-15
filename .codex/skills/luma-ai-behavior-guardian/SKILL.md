---
name: luma-ai-behavior-guardian
description: Use for Gemini, AI analyzer, prompt/schema changes, missing-information prompts, Ask LUMA, Situation AI, related thoughts, learning/memory rules, duplicate detection, people/project detection, and AI trust boundaries.
---

# LUMA AI Behavior Guardian

Use this skill whenever AI behavior changes.

## AI role

AI may help the user think, clarify, classify, summarize, and decide.

AI must not silently act on important user data.

## Scope

```text
- Gemini prompts
- AI analyzer output schemas
- Ask LUMA
- Situation AI
- missing-information prompts
- reminder/task extraction
- related thoughts/backlinks
- duplicate detection
- people/project detection
- learning memory
- pattern insights
```

## Trust boundary

AI may suggest:

```text
- task classification
- reminder interpretation
- project/person tags
- related items
- duplicate candidates
- next actions
- missing info questions
```

AI must not silently:

```text
- create tasks/reminders
- schedule notifications
- complete/delete/archive items
- merge duplicates
- edit learned rules
- send/share data
```

## Missing-information prompt pattern

When AI lacks required information:

```text
1. Show proposed interpretation.
2. Ask for missing details only.
3. Let user confirm, edit, or dismiss.
4. Save/schedule only after confirmation.
```

## Prompt/schema changes

Before changing AI prompts or schemas:

```text
- Identify current schema.
- Identify consumers of the output.
- Identify fallback behavior.
- Identify low-confidence path.
- Identify confirmation points.
- Add/update regression checks.
```

## Ask LUMA rule

Ask LUMA is user-facing. Do not expose raw AI machinery, debug labels, or internal processing artifacts.
