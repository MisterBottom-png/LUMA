---
name: luma-skill-governance
description: Use when adding, changing, importing, reviewing, or cleaning LUMA skills/agents. Enforces skill safety, scope boundaries, non-duplication, external-reference-only policy, and AGENTS.md protection.
---

# LUMA Skill Governance

Use this skill when changing the agent/skill stack itself.

## Goal

Keep the skill stack useful, narrow, safe, and not bloated.

## Rules

- LUMA-owned skills may be used normally.
- Official vendor skills may be installed separately for narrow technical guidance.
- Community skills are external references only unless reviewed and explicitly approved.
- Do not install skills with unknown scripts/hooks.
- Do not let third-party skills override LUMA product laws.
- Do not duplicate existing LUMA skills.
- Do not rewrite `AGENTS.md` without exact user approval.

## Skill quality checklist

```text
- Narrow purpose
- Accurate description
- Clear activation triggers
- Clear exclusions
- No unsafe scripts
- No hidden dependencies
- No conflict with LUMA rules
- No generic motivational fluff
- Easy to delete if wrong
```

## New skill proposal format

```text
Skill name:
Purpose:
When to activate:
What it may edit:
What it must not edit:
References needed:
Risks:
Approval needed:
```
