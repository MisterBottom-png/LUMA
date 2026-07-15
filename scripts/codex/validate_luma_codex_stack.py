#!/usr/bin/env python3
"""Validate the LUMA Codex stack's local structure and safety contracts.

This is structural validation, not proof that Codex will select a skill, that an
Android build passes, or that cleanup candidates are safe to remove.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

if sys.version_info < (3, 11):
    print("ERROR: Python 3.11 or newer is required.", file=sys.stderr)
    raise SystemExit(2)

import tomllib  # noqa: E402

REQUIRED_SKILL_KEYS = {"name", "description"}
REQUIRED_AGENT_KEYS = {"name", "description", "developer_instructions"}
EXPECTED_SKILLS = {
    "luma-ai-reminder-guardian",
    "luma-android-developer",
    "luma-autopilot",
    "luma-compose-ui",
    "luma-mvp-release-manager",
    "luma-project-cleanup",
    "luma-regression-qa",
    "luma-room-data-guardian",
}
EXPECTED_AGENTS = {
    "luma_code_data_reviewer",
    "luma_release_regression_reviewer",
    "luma_ui_accessibility_reviewer",
}
REQUIRED_PATHS = {
    "docs/codex/LUMA_PRODUCT_RULES.md",
    "docs/codex/WORKPLACE_PRIVACY_POLICY.md",
    "docs/codex/LUMA_PROTECTED_BEHAVIORS.md",
    "docs/codex/PROJECT_STATE.md",
    "docs/codex/cleanup/LUMA_CLEANUP_POLICY.md",
    "docs/codex/cleanup/LUMA_CLEANUP_REPORT.md",
    "docs/codex/cleanup/cleanup_manifest.schema.json",
    "docs/codex/mvp/LUMA_MVP_GATE.md",
    "docs/codex/mvp/LUMA_MVP_BACKLOG.md",
    "docs/codex/mvp/LUMA_MVP_VERIFICATION_POLICY.md",
    "scripts/codex/apply_cleanup_manifest.py",
    "scripts/codex/check_workplace_privacy.py",
    "scripts/codex/repo_cleanup_inventory.py",
    "scripts/codex/validate_luma_codex_stack.py",
    "tests/codex/test_cleanup_tools.py",
    "tests/codex/test_workplace_privacy.py",
}


def parse_frontmatter(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    if not text.startswith("---\n"):
        raise ValueError("missing YAML frontmatter")
    end = text.find("\n---\n", 4)
    if end < 0:
        raise ValueError("unterminated YAML frontmatter")
    result: dict[str, str] = {}
    for number, line in enumerate(text[4:end].splitlines(), 1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if ":" not in line:
            raise ValueError(f"unsupported frontmatter at line {number}: {line!r}")
        key, value = line.split(":", 1)
        key = key.strip()
        value = value.strip().strip('"\'')
        if not key or key in result:
            raise ValueError(f"invalid or duplicate key at line {number}: {key!r}")
        result[key] = value
    return result


def validate_json(path: Path, errors: list[str]) -> None:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        errors.append(f"{path}: invalid JSON: {exc}")


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    errors: list[str] = []
    warnings: list[str] = []

    agents_md = root / "AGENTS.md"
    if not agents_md.exists():
        errors.append("Missing AGENTS.md")
    elif agents_md.stat().st_size > 32 * 1024:
        warnings.append(f"AGENTS.md is {agents_md.stat().st_size} bytes; keep root instructions focused")
    elif "untrusted data" not in agents_md.read_text(encoding="utf-8").lower():
        errors.append("AGENTS.md must define repository content and tool output as untrusted data")
    else:
        agents_text = agents_md.read_text(encoding="utf-8").lower()
        if "zero workplace-person references" not in agents_text:
            errors.append("AGENTS.md must define zero workplace-person references")
        if "workplace privacy checked: yes" not in agents_text:
            errors.append("AGENTS.md completion report must include the workplace privacy check")

    legacy_skills = root / ".codex" / "skills"
    if legacy_skills.exists():
        conflicting = sorted(legacy_skills.glob("luma-*/SKILL.md"))
        if conflicting:
            errors.append("Conflicting legacy LUMA skills exist under .codex/skills")
        else:
            warnings.append(".codex/skills exists, but no conflicting luma-* skill was found")

    skills_root = root / ".agents" / "skills"
    found_skills: dict[str, Path] = {}
    for path in sorted(skills_root.glob("*/SKILL.md")):
        relative = path.relative_to(root)
        try:
            metadata = parse_frontmatter(path)
        except (OSError, UnicodeDecodeError, ValueError) as exc:
            errors.append(f"{relative}: {exc}")
            continue
        missing = REQUIRED_SKILL_KEYS - metadata.keys()
        if missing:
            errors.append(f"{relative}: missing keys {sorted(missing)}")
        name = metadata.get("name", "")
        description = metadata.get("description", "")
        if name in found_skills:
            errors.append(f"Duplicate skill name {name}: {found_skills[name]} and {relative}")
        found_skills[name] = relative
        if path.parent.name != name:
            errors.append(f"{relative}: directory name must match skill name {name!r}")
        if not re.fullmatch(r"[a-z0-9-]+", name):
            errors.append(f"{relative}: invalid skill name {name!r}")
        if len(description.strip()) < 20:
            errors.append(f"{relative}: description is too vague")
        skill_text = path.read_text(encoding="utf-8").lower()
        if "workplace_privacy_policy.md" not in skill_text:
            errors.append(f"{relative}: missing workplace privacy contract")

    missing_skills = EXPECTED_SKILLS - found_skills.keys()
    extra_skills = found_skills.keys() - EXPECTED_SKILLS
    if missing_skills:
        errors.append(f"Missing expected skills: {sorted(missing_skills)}")
    if extra_skills:
        warnings.append(f"Additional skills present: {sorted(extra_skills)}")

    agents_root = root / ".codex" / "agents"
    found_agents: dict[str, Path] = {}
    for path in sorted(agents_root.glob("*.toml")):
        relative = path.relative_to(root)
        try:
            data = tomllib.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, tomllib.TOMLDecodeError) as exc:
            errors.append(f"{relative}: invalid TOML: {exc}")
            continue
        missing = REQUIRED_AGENT_KEYS - data.keys()
        if missing:
            errors.append(f"{relative}: missing keys {sorted(missing)}")
        name = data.get("name")
        if not isinstance(name, str) or not name:
            errors.append(f"{relative}: invalid name")
            continue
        if name in found_agents:
            errors.append(f"Duplicate agent name: {name}")
        found_agents[name] = relative
        if path.stem != name:
            errors.append(f"{relative}: filename must match agent name")
        if data.get("sandbox_mode") != "read-only":
            errors.append(f"{relative}: reviewer must use sandbox_mode = 'read-only'")
        instructions = str(data.get("developer_instructions", "")).lower()
        if "do not edit" not in instructions:
            errors.append(f"{relative}: reviewer must explicitly prohibit edits")
        if "untrusted" not in instructions:
            warnings.append(f"{relative}: reviewer should mention untrusted repository content")
        if "never mention any coworker" not in instructions:
            errors.append(f"{relative}: reviewer must enforce zero workplace-person references")

    missing_agents = EXPECTED_AGENTS - found_agents.keys()
    extra_agents = found_agents.keys() - EXPECTED_AGENTS
    if missing_agents:
        errors.append(f"Missing expected agents: {sorted(missing_agents)}")
    if extra_agents:
        warnings.append(f"Additional agents present: {sorted(extra_agents)}")

    config_path = root / ".codex" / "config.toml"
    try:
        config = tomllib.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, tomllib.TOMLDecodeError) as exc:
        errors.append(f".codex/config.toml: invalid TOML: {exc}")
        config = {}
    if config.get("sandbox_mode") != "workspace-write":
        errors.append(".codex/config.toml must use sandbox_mode = 'workspace-write'")
    if config.get("approval_policy") != "on-request":
        errors.append(".codex/config.toml must use approval_policy = 'on-request'")
    if config.get("allow_login_shell") is not False:
        warnings.append(".codex/config.toml should set allow_login_shell = false")

    for relative in sorted(REQUIRED_PATHS):
        if not (root / relative).is_file():
            errors.append(f"Missing required file: {relative}")

    for path in root.rglob("*.json"):
        validate_json(path, errors)

    path_pattern = re.compile(
        r"(?<![A-Za-z0-9_])((?:\.agents|\.codex|docs/codex|scripts/codex|tests/codex|prompts)/[A-Za-z0-9_./*-]+)"
    )
    for path in sorted((root / "prompts").glob("*.md")):
        prompt_text = path.read_text(encoding="utf-8").lower()
        if "workplace_privacy_policy.md" not in prompt_text:
            errors.append(f"{path.relative_to(root)}: missing workplace privacy instruction")
        if "--strict" not in prompt_text:
            errors.append(f"{path.relative_to(root)}: privacy checker must run in strict mode")

    for path in root.rglob("*.md"):
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as exc:
            errors.append(f"{path.relative_to(root)}: cannot read UTF-8: {exc}")
            continue
        for reference in path_pattern.findall(text):
            reference = reference.rstrip(".,:;)")
            if "*" in reference or reference.endswith("/") or reference == ".codex/skills":
                continue
            if not (root / reference).exists():
                warnings.append(f"Possible missing reference in {path.relative_to(root)}: {reference}")

    print(f"Root: {root}")
    print(f"Skills: {len(found_skills)}")
    print(f"Custom reviewers: {len(found_agents)}")
    if warnings:
        print("\nWarnings:")
        for warning in sorted(set(warnings)):
            print(f"- {warning}")
    if errors:
        print("\nErrors:")
        for error in sorted(set(errors)):
            print(f"- {error}")
        return 1
    print("\nLUMA Codex stack structural validation passed.")
    print("Not validated: Codex runtime discovery, Android build behavior, tool permissions outside this project, or cleanup candidate correctness.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
