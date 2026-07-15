# LUMA Autopilot System

LUMA Autopilot is the single autonomous Codex controller for this repo.

The user interacts with one thing:

```text
LUMA: <short request>
```

The Autopilot then decides the workflow.

## Mental model

```text
AGENTS.md = permanent rules
luma-autopilot skill = autonomous router/workflow
optional subagents = specialist reviewers
learning files = project memory
promotion queue = proposed permanent rules
user = approval authority
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
Learning Only Mode
```

## Main gates

```text
UX Gate
Risk Gate
Regression Gate
Learning Gate
```

## Core behavior

- Clear screenshot/appshot visual fix: fix directly.
- Clear small change: implement with stated assumptions.
- Broad feature idea: ask questions first.
- Risky data/AI/backend/date/reminder feature: plan and ask approval.
- Review request: inspect only, do not edit.
- Medium/large completed task: update learning files.

## Default permission model

```text
Autopilot/Builder: may edit code when safe.
UX Reviewer: review-only.
Risk Reviewer: review-only.
Regression Reviewer: review-only.
Memory Guardian: review-only.
Learning skill: may edit learning docs, not AGENTS.md.
```

## Why one Autopilot?

Because the user wants simple prompts. The agent system should absorb complexity instead of making the user manage fake departments. Humanity already has enough managers. Codex does not need a badge lanyard.
