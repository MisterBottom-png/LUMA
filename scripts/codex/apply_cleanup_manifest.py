#!/usr/bin/env python3
"""Apply a reviewed, content-bound cleanup manifest safely.

The default mode is validation-only. ``--apply`` removes only regular files or
empty directories whose current metadata matches the reviewed manifest.
Recursive directory deletion is intentionally unsupported.
"""
from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import os
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any

SCHEMA_VERSION = 2
READ_CHUNK = 1024 * 1024
PROTECTED_NAMES = {
    ".git", ".env", "local.properties", "gradle.properties", "gradlew",
    "gradlew.bat", "settings.gradle", "settings.gradle.kts", "AGENTS.md",
    "google-services.json", "proguard-rules.pro", "signing.properties",
}
PROTECTED_PARTS = {
    ".git", "gradle", "schemas", "schema", "migration", "migrations",
    "fixtures", "fixture", "testdata", "user-data", "userdata",
}
PROTECTED_GLOBS = {
    ".env*", "*.jks", "*.keystore", "*.p12", "*.pfx", "*.pem", "*.key",
    "*.db", "*.sqlite", "*.sqlite3", "*.realm", "*.properties",
}


class ManifestError(ValueError):
    """Raised when the manifest or a target fails a safety check."""


@dataclass(frozen=True)
class Entry:
    path: str
    kind: str
    reason: str
    sha256: str | None = None
    size: int | None = None


def run_git(root: Path, *args: str) -> tuple[int, str]:
    try:
        proc = subprocess.run(
            ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, timeout=15, check=False,
        )
        return proc.returncode, proc.stdout.strip()
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 1, str(exc)


def repository_root(start: Path) -> Path:
    start = start.resolve()
    code, output = run_git(start, "rev-parse", "--show-toplevel")
    if code != 0 or not output:
        raise ManifestError(f"Not inside a Git repository: {start}")
    root = Path(output).resolve()
    if not root.is_dir():
        raise ManifestError(f"Invalid Git root: {root}")
    return root


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(READ_CHUNK), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_relative_path(value: Any) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ManifestError("entry.path must be a non-empty string")
    if "\\" in value:
        raise ManifestError(f"entry.path must use forward slashes: {value!r}")
    pure = PurePosixPath(value)
    if pure.is_absolute() or value.startswith("~"):
        raise ManifestError(f"entry.path must be repository-relative: {value!r}")
    if any(part in {"", ".", ".."} for part in pure.parts):
        raise ManifestError(f"entry.path is not canonical: {value!r}")
    return pure.as_posix()


def protected_reason(rel: PurePosixPath) -> str | None:
    lowered_parts = tuple(part.lower() for part in rel.parts)
    for part in lowered_parts:
        if part in {item.lower() for item in PROTECTED_PARTS}:
            return f"protected path component: {part}"
    name = rel.name.lower()
    if name in {item.lower() for item in PROTECTED_NAMES}:
        return f"protected file name: {rel.name}"
    for pattern in PROTECTED_GLOBS:
        if fnmatch.fnmatch(name, pattern.lower()):
            return f"protected file pattern: {pattern}"
    return None


def parse_manifest(path: Path) -> tuple[str, list[Entry]]:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise ManifestError(f"Cannot read manifest: {exc}") from exc
    if not isinstance(raw, dict):
        raise ManifestError("Manifest root must be an object")
    if raw.get("schema_version") != SCHEMA_VERSION:
        raise ManifestError(f"schema_version must be {SCHEMA_VERSION}")
    commit = raw.get("repository_commit")
    if not isinstance(commit, str) or not commit.strip():
        raise ManifestError("repository_commit must be a non-empty Git commit")
    items = raw.get("entries")
    if not isinstance(items, list) or not items:
        raise ManifestError("entries must be a non-empty list")

    entries: list[Entry] = []
    seen: set[str] = set()
    for index, item in enumerate(items):
        if not isinstance(item, dict):
            raise ManifestError(f"entries[{index}] must be an object")
        rel = canonical_relative_path(item.get("path"))
        if rel in seen:
            raise ManifestError(f"duplicate manifest path: {rel}")
        seen.add(rel)
        kind = item.get("type")
        if kind not in {"file", "empty-directory"}:
            raise ManifestError(f"{rel}: type must be 'file' or 'empty-directory'")
        reason = item.get("reason")
        if not isinstance(reason, str) or len(reason.strip()) < 8:
            raise ManifestError(f"{rel}: reason must contain meaningful evidence")
        digest = item.get("sha256")
        size = item.get("size")
        if kind == "file":
            if not isinstance(digest, str) or len(digest) != 64:
                raise ManifestError(f"{rel}: file entry requires a 64-character sha256")
            try:
                int(digest, 16)
            except ValueError as exc:
                raise ManifestError(f"{rel}: sha256 is not hexadecimal") from exc
            if not isinstance(size, int) or isinstance(size, bool) or size < 0:
                raise ManifestError(f"{rel}: file entry requires a non-negative integer size")
        else:
            if digest is not None or size not in {None, 0}:
                raise ManifestError(f"{rel}: empty-directory must not carry file metadata")
            digest = None
            size = None
        entries.append(Entry(rel, kind, reason.strip(), digest, size))
    return commit.strip(), entries


def assert_commit(root: Path, expected: str) -> str:
    code, actual = run_git(root, "rev-parse", "HEAD")
    if code != 0 or not actual:
        raise ManifestError("Cannot determine current Git commit")
    code, resolved = run_git(root, "rev-parse", f"{expected}^{{commit}}")
    if code != 0 or not resolved:
        raise ManifestError(f"repository_commit is not a valid commit: {expected}")
    if actual != resolved:
        raise ManifestError(f"Repository commit changed: expected {resolved}, found {actual}")
    return actual


def path_is_dirty(root: Path, rel: str) -> bool:
    code, output = run_git(root, "status", "--porcelain=v1", "--", rel)
    if code != 0:
        raise ManifestError(f"Cannot inspect Git status for {rel}: {output}")
    return bool(output.strip())


def validate_target(root: Path, entry: Entry, allow_dirty: bool) -> Path:
    rel = PurePosixPath(entry.path)
    reason = protected_reason(rel)
    if reason:
        raise ManifestError(f"{entry.path}: {reason}")

    target = root.joinpath(*rel.parts)
    resolved_parent = target.parent.resolve(strict=True)
    try:
        resolved_parent.relative_to(root)
    except ValueError as exc:
        raise ManifestError(f"{entry.path}: parent resolves outside repository") from exc
    if target.is_symlink():
        raise ManifestError(f"{entry.path}: symlink targets are never deleted")
    if not target.exists():
        raise ManifestError(f"{entry.path}: target does not exist")
    if not allow_dirty and path_is_dirty(root, entry.path):
        raise ManifestError(f"{entry.path}: target has uncommitted Git changes")

    if entry.kind == "file":
        if not target.is_file():
            raise ManifestError(f"{entry.path}: expected a regular file")
        stat = target.stat()
        if stat.st_size != entry.size:
            raise ManifestError(f"{entry.path}: size changed ({entry.size} -> {stat.st_size})")
        actual_hash = sha256_file(target)
        if actual_hash != entry.sha256:
            raise ManifestError(f"{entry.path}: SHA-256 changed")
    else:
        if not target.is_dir():
            raise ManifestError(f"{entry.path}: expected a directory")
        try:
            next(target.iterdir())
        except StopIteration:
            pass
        else:
            raise ManifestError(f"{entry.path}: directory is not empty; recursive deletion is forbidden")
    return target


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--apply", action="store_true", help="Delete validated targets")
    parser.add_argument(
        "--allow-dirty-targets", action="store_true",
        help="Permit uncommitted target paths after hash/size validation (not recommended)",
    )
    args = parser.parse_args(argv)

    try:
        root = repository_root(args.root)
        manifest_path = args.manifest.resolve(strict=True)
        commit, entries = parse_manifest(manifest_path)
        actual_commit = assert_commit(root, commit)
        validated = [(entry, validate_target(root, entry, args.allow_dirty_targets)) for entry in entries]
    except (ManifestError, OSError) as exc:
        print(f"REFUSE: {exc}", file=sys.stderr)
        return 2

    mode = "APPLY" if args.apply else "DRY-RUN"
    print(f"Mode: {mode}")
    print(f"Repository: {root}")
    print(f"Commit: {actual_commit}")
    for entry, target in validated:
        action = "DELETE" if args.apply else "WOULD DELETE"
        print(f"{action} {entry.kind}: {entry.path} | {entry.reason}")
        if args.apply:
            if entry.kind == "file":
                target.unlink()
            else:
                target.rmdir()

    if not args.apply:
        print("No files changed. Review this output, then re-run with --apply at the same commit.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
