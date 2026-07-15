#!/usr/bin/env python3
"""Report likely workplace-person references without echoing matched values.

The strict mode broadens contextual and string-literal checks. It remains a
heuristic and must be paired with semantic review of repository-controlled text.
"""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

TEXT_SUFFIXES = {
    ".c", ".cc", ".cpp", ".css", ".gradle", ".groovy", ".h", ".hpp",
    ".html", ".java", ".js", ".json", ".kt", ".kts", ".md", ".properties",
    ".py", ".sh", ".toml", ".ts", ".tsx", ".txt", ".xml", ".yaml", ".yml",
}
SKIP_PARTS = {".git", ".gradle", ".idea", "build", "dist", "node_modules", "out", "target", "venv", ".venv", "__pycache__"}
PLACEHOLDER_DOMAINS = {"example.com", "example.org", "example.net", "example.invalid", "localhost"}
GENERIC_VALUES = {
    "manager", "reviewer", "stakeholder", "team member", "operator", "administrator",
    "customer", "test user", "user", "team", "anonymous", "redacted", "unknown", "maintainer",
    "phase", "gate",
}
TECHNICAL_PROPER_NOUNS = {
    "android", "codex", "compose", "gemini", "git", "gradle", "java", "json", "kotlin", "luma",
    "material", "mvp", "orbit", "python", "room", "sql", "theme", "toml", "type", "ui", "xml", "yaml",
}
EXEMPT_RELATIVE_PATHS = {Path("tests/codex/test_workplace_privacy.py")}
EMAIL_RE = re.compile(r"(?i)\b[A-Z0-9._%+-]+@([A-Z0-9.-]+\.[A-Z]{2,}|localhost)\b")
NAMED_TODO_RE = re.compile(r"(?i)\b(?:TODO|FIXME)\s*\(\s*[^)\s][^)]*\)")
ATTRIBUTION_RE = re.compile(
    r"(?i)\b(?:author|reviewed by|approved by|assigned to|assignee|owner|contact|created by|requested by|reported by)"
    r"\s*[:=]\s*[\"']?([^\"'\n,#]{2,100})"
)
CONTEXT_NAME_RE = re.compile(
    r"(?i:\b(?:ask|tell|told|said|contact|email|call|approved|reviewed|assigned|owner|manager|coworker|colleague)\b)"
    r"\s*[:=,-]?\s*[\"']?([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,})?)\b"
)
ASSIGNED_STRING_RE = re.compile(
    r"(?i:\b(?:(?:val|var|const\s+val)\s+)?(?:user_?name|person_?name|display_?name|owner|reviewer|approver|assignee|contact)\b)"
    r"[^\n=]{0,50}=\s*[\"']"
    r"([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,})?)[\"']"
)
XML_NAME_VALUE_RE = re.compile(
    r"(?i:<(?:string|item)[^>]*(?:name|key)=[\"'][^\"']*(?:owner|reviewer|approver|contact|person|name)[^\"']*[\"'][^>]*>\s*)"
    r"([A-Z][a-z]{2,}(?:\s+[A-Z][a-z]{2,})?)\s*</"
)
PERSON_LIKE_FILENAME_RE = re.compile(r"^[A-Z][a-z]{2,}(?:[_ -][A-Z][a-z]{2,})?$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=None, help="Repository root")
    parser.add_argument("--changed-only", action="store_true", help="Scan Git-changed files only")
    parser.add_argument("--strict", action="store_true", help="Enable broader contextual person-reference heuristics")
    return parser.parse_args()


def git_root(start: Path) -> Path:
    proc = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=start, text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
    return Path(proc.stdout.strip()).resolve() if proc.returncode == 0 else start.resolve()


def git_paths(root: Path, changed_only: bool) -> list[Path]:
    commands = ([
        ["git", "diff", "--name-only", "--diff-filter=ACMR"],
        ["git", "diff", "--cached", "--name-only", "--diff-filter=ACMR"],
        ["git", "ls-files", "--others", "--exclude-standard"],
    ] if changed_only else [["git", "ls-files", "-co", "--exclude-standard"]])
    found: set[Path] = set()
    for command in commands:
        proc = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False)
        if proc.returncode != 0:
            continue
        for raw in proc.stdout.splitlines():
            path = (root / raw).resolve()
            try:
                path.relative_to(root)
            except ValueError:
                continue
            if path.is_file():
                found.add(path)
    if found:
        return sorted(found)
    return sorted(path for path in root.rglob("*") if path.is_file())


def eligible(path: Path, root: Path) -> bool:
    try:
        rel = path.relative_to(root)
    except ValueError:
        return False
    if any(part in SKIP_PARTS for part in rel.parts):
        return False
    if rel in EXEMPT_RELATIVE_PATHS:
        return False
    return path.suffix.lower() in TEXT_SUFFIXES or path.name in {"AGENTS.md", "Dockerfile", "Makefile"}


def generic(value: str) -> bool:
    cleaned = re.sub(r"[`*_]", "", value).strip().rstrip(".;").lower()
    if cleaned in GENERIC_VALUES or cleaned in TECHNICAL_PROPER_NOUNS or cleaned.startswith(("${", "{{", "<")):
        return True
    tokens = re.findall(r"[a-z0-9]+", cleaned)
    return bool(tokens) and all(token in TECHNICAL_PROPER_NOUNS for token in tokens)


def scan(path: Path, strict: bool) -> list[tuple[int, str]]:
    try:
        text = path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return []
    findings: set[tuple[int, str]] = set()
    for number, line in enumerate(text.splitlines(), 1):
        for match in EMAIL_RE.finditer(line):
            if match.group(1).lower() not in PLACEHOLDER_DOMAINS:
                findings.add((number, "email-address"))
        if NAMED_TODO_RE.search(line):
            findings.add((number, "named-todo-attribution"))
        for match in ATTRIBUTION_RE.finditer(line):
            if not generic(match.group(1)):
                findings.add((number, "personal-attribution"))
        if strict:
            for pattern, category in ((CONTEXT_NAME_RE, "contextual-person-reference"), (ASSIGNED_STRING_RE, "person-like-assigned-string"), (XML_NAME_VALUE_RE, "person-like-resource-value")):
                for match in pattern.finditer(line):
                    if not generic(match.group(1)):
                        findings.add((number, category))
    if strict and PERSON_LIKE_FILENAME_RE.fullmatch(path.stem) and path.stem.lower() not in TECHNICAL_PROPER_NOUNS:
        findings.add((0, "person-like-filename"))
    return sorted(findings)


def main() -> int:
    args = parse_args()
    start = (args.root or Path.cwd()).resolve()
    root = git_root(start)
    findings: list[tuple[Path, int, str]] = []
    for path in git_paths(root, args.changed_only):
        if not eligible(path, root):
            continue
        for line, category in scan(path, args.strict):
            findings.append((path.relative_to(root), line, category))
    if not findings:
        print("Workplace privacy heuristic check passed.")
        print("Semantic review is still required; absence of findings is not proof of absence.")
        return 0
    print("Potential workplace-person reference findings:")
    for path, line, category in findings:
        location = f"{path}:{line}" if line else str(path)
        print(f"- {location} [{category}]")
    print("Matched values are intentionally suppressed. Replace repository-controlled references with generic role labels.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
