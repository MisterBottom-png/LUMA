# LUMA Autopilot System

LUMA Autopilot is the single autonomous Codex controller for this repo.

The canonical active stack and routing table live in `LUMA_SKILL_STACK.md`. This document explains the interaction model only; it does not define a second routing contract.

The user interacts with one thing:

```text
LUMA: <short request>
```

The Autopilot then decides the workflow.

## Mental model

```text
AGENTS.md = permanent rules
luma-autopilot skill = autonomous router/workflow
specialist skills = implementation workflows
optional custom agents = independent read-only reviewers
PROJECT_STATE.md = current evidence ledger
user = approval authority
active agent = engineering decision owner inside those boundaries
```

## Main modes

```text
Visual Fix Mode
Bug Fix Mode
Small Implementation Mode
Broad Feature Discovery Mode
UX/Product Exploration Mode
Risky Technical Feature Mode
Review Only Mode
```

## Main gates

```text
UX Gate
Risk Gate
Regression Gate
```

## Core behavior

- Clear screenshot/appshot visual fix: fix directly.
- Clear small change: implement with stated assumptions.
- Broad feature idea: ask questions first.
- Risky data/AI/backend/date/reminder feature: plan and ask approval.
- Review request: inspect only, do not edit.

## Default permission model

```text
Autopilot/Builder: may edit code when safe.
Code/Data Reviewer: review-only.
UI/Accessibility Reviewer: review-only.
Release/Regression Reviewer: review-only.
```

## Why one Autopilot?

Because the user wants simple prompts. The agent system should absorb complexity instead of making the user manage fake departments. Humanity already has enough managers. Codex does not need a badge lanyard.

Autopilot must therefore make normal reversible engineering decisions itself, ask only at real approval boundaries, and never outsource the final decision to a skill or reviewer.
