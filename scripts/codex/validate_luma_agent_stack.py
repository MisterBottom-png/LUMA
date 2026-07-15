#!/usr/bin/env python3
"""Validate expected LUMA Codex agent stack files.

Run from the root of the LUMA repo:

    python scripts/codex/validate_luma_agent_stack.py
"""
from pathlib import Path
import sys

REQUIRED = [
    'AGENTS.md',
    '.agents/skills/luma-autopilot/SKILL.md',
    '.agents/skills/luma-feature-intake/SKILL.md',
    '.agents/skills/luma-android-developer/SKILL.md',
    '.agents/skills/luma-compose-ui/SKILL.md',
    '.agents/skills/luma-room-data-guardian/SKILL.md',
    '.agents/skills/luma-ai-behavior-guardian/SKILL.md',
    '.agents/skills/luma-reminder-time-guardian/SKILL.md',
    '.agents/skills/luma-regression-qa/SKILL.md',
    '.agents/skills/luma-docs-package-maintainer/SKILL.md',
    '.agents/skills/luma-mvp-release-manager/SKILL.md',
    '.agents/skills/luma-skill-governance/SKILL.md',
    '.agents/skills/luma-self-learning/SKILL.md',
    '.codex/skills/luma-autopilot/SKILL.md',
    '.codex/agents/luma_ux_reviewer.toml',
    '.codex/agents/luma_risk_reviewer.toml',
    '.codex/agents/luma_regression_reviewer.toml',
    '.codex/agents/luma_memory_guardian.toml',
    '.codex/agents/luma_android_architect.toml',
    '.codex/agents/luma_ai_behavior_reviewer.toml',
    '.codex/agents/luma_accessibility_reviewer.toml',
    '.codex/agents/luma_docs_reviewer.toml',
    '.codex/agents/luma_release_reviewer.toml',
    'docs/codex/LUMA_RULES.md',
    'docs/codex/LUMA_DONE_ITEMS_PROTECTED.md',
    'docs/codex/LUMA_REGRESSION_CHECKLIST.md',
    'docs/codex/LUMA_MVP_SCOPE.md',
    'docs/codex/LUMA_AI_BEHAVIOR_CONTRACT.md',
    'docs/codex/LUMA_SKILL_STACK.md',
]

root = Path.cwd()
missing = [p for p in REQUIRED if not (root / p).exists()]

print('LUMA agent stack validation')
print('Root:', root)
print('Required files:', len(REQUIRED))

if missing:
    print('\nMissing files:')
    for p in missing:
        print(' -', p)
    sys.exit(1)

print('\nAll required LUMA agent stack files are present.')
