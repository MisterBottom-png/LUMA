#!/usr/bin/env python3
"""Generate a conservative, non-destructive cleanup inventory for a Git repository."""
from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path
from typing import Iterable

SKIP_TRAVERSAL_DIRS = {
    ".git", ".gradle", ".idea", ".kotlin", "build", "out", "target",
    "node_modules", ".venv", "venv", "__pycache__",
}
GENERATED_DIR_NAMES = {"build", "out", "target", ".gradle", ".kotlin", "captures"}
BACKUP_SUFFIXES = {".bak", ".backup", ".old", ".orig", ".tmp", ".log", ".7z", ".rar"}
ARCHIVE_SUFFIXES = {".zip", ".tar", ".tgz", ".gz", ".bz2", ".xz"}
CODE_SUFFIXES = {".kt", ".kts", ".java"}
TEXT_SUFFIXES = CODE_SUFFIXES | {".xml", ".gradle", ".md", ".properties", ".toml", ".yaml", ".yml"}
DEFAULT_MAX_HASH_BYTES = 256 * 1024 * 1024
LARGE_FILE_BYTES = 5 * 1024 * 1024


def run_git(root: Path, *args: str) -> tuple[int, str]:
    try:
        process = subprocess.run(
            ["git", *args], cwd=root, text=True, stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT, timeout=30, check=False,
        )
        return process.returncode, process.stdout.strip()
    except (OSError, subprocess.TimeoutExpired) as exc:
        return 1, str(exc)


def git_root(start: Path) -> Path:
    start = start.resolve()
    code, output = run_git(start, "rev-parse", "--show-toplevel")
    if code != 0 or not output:
        raise ValueError(f"Not inside a Git repository: {start}")
    return Path(output).resolve()


def safe_output_path(root: Path, requested: str) -> Path:
    raw = Path(requested)
    if raw.is_absolute():
        candidate = raw.resolve()
    else:
        candidate = (root / raw).resolve()
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"Output must remain inside repository: {candidate}") from exc
    if candidate == root:
        raise ValueError("Output cannot be the repository root")
    return candidate


def walk_repository(root: Path) -> tuple[list[Path], list[Path]]:
    files: list[Path] = []
    empty_dirs: list[Path] = []
    for current_raw, dirs, names in os.walk(root, followlinks=False):
        current = Path(current_raw)
        rel_parts = current.relative_to(root).parts
        if any(part in SKIP_TRAVERSAL_DIRS for part in rel_parts):
            dirs[:] = []
            continue
        dirs[:] = sorted(d for d in dirs if d not in SKIP_TRAVERSAL_DIRS)
        names = sorted(names)
        visible_children = dirs + names
        if current != root and not visible_children:
            empty_dirs.append(current)
        for name in names:
            path = current / name
            try:
                if path.is_symlink() or not path.is_file():
                    continue
            except OSError:
                continue
            files.append(path)
    return files, empty_dirs


def sha256(path: Path, max_bytes: int) -> str | None:
    try:
        if path.stat().st_size > max_bytes:
            return None
        digest = hashlib.sha256()
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        return digest.hexdigest()
    except OSError:
        return None


def read_text(path: Path) -> list[str]:
    try:
        return path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeDecodeError):
        return []


def unused_import_candidates(path: Path) -> list[tuple[int, str]]:
    lines = read_text(path)
    body_lines: list[str] = []
    imports: list[tuple[int, str]] = []
    for number, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith("import "):
            imports.append((number, stripped[7:].strip()))
        else:
            body_lines.append(line)
    body = "\n".join(body_lines)
    results: list[tuple[int, str]] = []
    for number, imported in imports:
        if imported.endswith(".*"):
            continue
        symbol = imported.split(" as ")[-1].split(".")[-1]
        if symbol and not re.search(rf"\b{re.escape(symbol)}\b", body):
            results.append((number, imported))
    return results


def limited(items: Iterable[str], limit: int = 500) -> list[str]:
    materialized = list(items)
    if len(materialized) <= limit:
        return materialized
    return materialized[:limit] + [f"- Truncated: {len(materialized) - limit} additional candidates"]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", default="docs/codex/cleanup/LUMA_CLEANUP_INVENTORY.md")
    parser.add_argument("--max-hash-mib", type=int, default=DEFAULT_MAX_HASH_BYTES // (1024 * 1024))
    args = parser.parse_args(argv)

    try:
        root = git_root(Path(args.root))
        output = safe_output_path(root, args.output)
    except ValueError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    if args.max_hash_mib < 1:
        print("ERROR: --max-hash-mib must be at least 1", file=sys.stderr)
        return 2
    max_hash_bytes = args.max_hash_mib * 1024 * 1024

    files, empty_dirs = walk_repository(root)
    duplicate_index: dict[tuple[str, int], list[Path]] = defaultdict(list)
    generated_dirs: set[Path] = set()
    backups: list[Path] = []
    archives: list[Path] = []
    large: list[tuple[int, Path, bool]] = []
    todo_hits: list[tuple[Path, int, str]] = []
    debug_hits: list[tuple[Path, int, str]] = []
    commented_code: list[tuple[Path, int, str]] = []
    wildcard_imports: list[tuple[Path, int, str]] = []
    unused_imports: list[tuple[Path, int, str]] = []
    skipped_hashes: list[tuple[int, Path]] = []

    for path in files:
        rel = path.relative_to(root)
        try:
            size = path.stat().st_size
        except OSError:
            continue
        tracked_code, tracked_output = run_git(root, "ls-files", "--error-unmatch", "--", rel.as_posix())
        tracked = tracked_code == 0
        if size >= LARGE_FILE_BYTES:
            large.append((size, rel, tracked))
        suffix = path.suffix.lower()
        if suffix in BACKUP_SUFFIXES or path.name.endswith("~"):
            backups.append(rel)
        if suffix in ARCHIVE_SUFFIXES:
            archives.append(rel)
        digest = sha256(path, max_hash_bytes)
        if digest is None and size > max_hash_bytes:
            skipped_hashes.append((size, rel))
        elif digest is not None and size > 0:
            duplicate_index[(digest, size)].append(rel)

        if suffix in TEXT_SUFFIXES:
            lines = read_text(path)
            for number, line in enumerate(lines, 1):
                clipped = line.strip()[:180]
                if re.search(r"\b(TODO|FIXME|HACK|XXX)\b", line):
                    todo_hits.append((rel, number, clipped))
                if re.search(r"\b(println|System\.out\.print|Log\.[vdiew]|Timber\.[vdiew])\s*\(", line):
                    debug_hits.append((rel, number, clipped))
                if suffix in CODE_SUFFIXES and re.match(
                    r"\s*//\s*(if|for|while|when|return|val|var|fun|class|object|[A-Za-z_][\w.]*\()",
                    line,
                ):
                    commented_code.append((rel, number, clipped))
                if suffix in CODE_SUFFIXES and re.match(r"\s*import\s+.+\.\*\s*$", line):
                    wildcard_imports.append((rel, number, clipped))
            if suffix in CODE_SUFFIXES:
                for number, imported in unused_import_candidates(path):
                    unused_imports.append((rel, number, imported))

    for current_raw, dirs, _ in os.walk(root, followlinks=False):
        current = Path(current_raw)
        rel_parts = current.relative_to(root).parts
        if ".git" in rel_parts:
            dirs[:] = []
            continue
        for directory in dirs:
            if directory in GENERATED_DIR_NAMES:
                generated_dirs.add((current / directory).relative_to(root))

    duplicate_groups = [paths for paths in duplicate_index.values() if len(paths) > 1]
    duplicate_groups.sort(key=lambda paths: (-len(paths), paths[0].as_posix()))
    large.sort(key=lambda item: (-item[0], item[1].as_posix()))
    skipped_hashes.sort(reverse=True)

    _, status = run_git(root, "status", "--short")
    _, branch = run_git(root, "branch", "--show-current")
    _, commit = run_git(root, "rev-parse", "HEAD")

    out: list[str] = [
        "# LUMA cleanup inventory",
        "",
        "> Generated, non-destructive report. Every item is a candidate requiring evidence, not an instruction to delete.",
        "",
        "## Repository",
        "",
        f"- Root: `{root}`",
        f"- Branch: `{branch or 'unknown'}`",
        f"- Commit: `{commit or 'unknown'}`",
        f"- Scanned regular files: {len(files)}",
        f"- Duplicate hash ceiling: {args.max_hash_mib} MiB per file",
        "",
        "### Git status",
        "",
        "```text",
        status or "clean",
        "```",
        "",
        "## Empty directories",
        "",
    ]
    out.extend(limited(f"- `{path.relative_to(root)}`" for path in sorted(empty_dirs)) or ["- None"])
    out.extend(["", "## Generated/cache directories", ""])
    out.extend(limited(f"- `{path}`" for path in sorted(generated_dirs)) or ["- None found outside skipped traversal"])
    out.extend(["", "## Backup/log candidates", ""])
    out.extend(limited(f"- `{path}`" for path in sorted(backups)) or ["- None"])
    out.extend(["", "## Archive candidates", "", "> Archives may be legitimate fixtures or deliverables. Classification is mandatory.", ""])
    out.extend(limited(f"- `{path}`" for path in sorted(archives)) or ["- None"])
    out.extend(["", "## Exact duplicate groups", ""])
    if duplicate_groups:
        for index, group in enumerate(duplicate_groups, 1):
            out.append(f"### Group {index}")
            out.extend(f"- `{path}`" for path in group)
            out.append("")
    else:
        out.append("- None")
    out.extend(["", "## Files not hashed because of the configured ceiling", ""])
    out.extend(limited(f"- `{path}`: {size / (1024 * 1024):.1f} MiB" for size, path in skipped_hashes) or ["- None"])
    out.extend(["", "## Large files (5 MiB or more)", ""])
    out.extend(limited(f"- `{path}`: {size / (1024 * 1024):.1f} MiB; tracked={str(tracked).lower()}" for size, path, tracked in large) or ["- None"])
    out.extend(["", "## Unused import candidates", "", "> Heuristic only. Compiler or IDE confirmation is required.", ""])
    out.extend(limited(f"- `{path}:{number}` `{imported}`" for path, number, imported in unused_imports) or ["- None"])
    out.extend(["", "## Wildcard import candidates", ""])
    out.extend(limited(f"- `{path}:{number}` {line}" for path, number, line in wildcard_imports) or ["- None"])
    out.extend(["", "## TODO/FIXME/HACK candidates", ""])
    out.extend(limited(f"- `{path}:{number}` {line}" for path, number, line in todo_hits) or ["- None"])
    out.extend(["", "## Debug-output candidates", "", "> Logging may be intentional. Verify build type, privacy, and observability requirements.", ""])
    out.extend(limited(f"- `{path}:{number}` {line}" for path, number, line in debug_hits) or ["- None"])
    out.extend(["", "## Commented-out code candidates", "", "> Heuristic only. Comments may be documentation or examples.", ""])
    out.extend(limited(f"- `{path}:{number}` {line}" for path, number, line in commented_code) or ["- None"])
    out.extend([
        "", "## Required next step", "",
        "Classify candidates under `LUMA_CLEANUP_POLICY.md`, establish a build/test baseline, and create a content-bound manifest only for reviewed regular files or empty directories.",
        "",
    ])

    try:
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text("\n".join(out), encoding="utf-8", newline="\n")
    except OSError as exc:
        print(f"ERROR: cannot write {output}: {exc}", file=sys.stderr)
        return 2
    print(f"Wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
