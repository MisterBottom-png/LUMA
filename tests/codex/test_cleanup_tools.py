from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPTS = ROOT / "scripts" / "codex"
APPLY = SCRIPTS / "apply_cleanup_manifest.py"
INVENTORY = SCRIPTS / "repo_cleanup_inventory.py"


def run(*args: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, *args], cwd=cwd, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False,
    )


def git(cwd: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", *args], cwd=cwd, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=True,
    )
    return proc.stdout.strip()


class CleanupToolTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.repo = Path(self.temp.name)
        git(self.repo, "init", "-q")
        git(self.repo, "config", "user.email", "test@example.invalid")
        git(self.repo, "config", "user.name", "Test")
        (self.repo / "keep.txt").write_text("keep\n", encoding="utf-8")
        git(self.repo, "add", ".")
        git(self.repo, "commit", "-qm", "initial")

    def tearDown(self) -> None:
        self.temp.cleanup()

    def commit_all(self, message: str = "fixture") -> str:
        git(self.repo, "add", ".")
        git(self.repo, "commit", "-qm", message)
        return git(self.repo, "rev-parse", "HEAD")

    def manifest_for(self, path: str, reason: str = "Reviewed obsolete fixture") -> Path:
        target = self.repo / path
        payload = {
            "schema_version": 2,
            "repository_commit": git(self.repo, "rev-parse", "HEAD"),
            "entries": [{
                "path": path,
                "type": "file",
                "size": target.stat().st_size,
                "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
                "reason": reason,
            }],
        }
        manifest = self.repo / "manifest.json"
        manifest.write_text(json.dumps(payload), encoding="utf-8")
        return manifest

    def test_dry_run_does_not_delete(self) -> None:
        target = self.repo / "obsolete.txt"
        target.write_text("old\n", encoding="utf-8")
        self.commit_all()
        manifest = self.manifest_for("obsolete.txt")
        proc = run(str(APPLY), str(manifest), "--root", str(self.repo), cwd=self.repo)
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertTrue(target.exists())
        self.assertIn("WOULD DELETE file: obsolete.txt", proc.stdout)

    def test_apply_deletes_exact_committed_file(self) -> None:
        target = self.repo / "obsolete.txt"
        target.write_text("old\n", encoding="utf-8")
        self.commit_all()
        manifest = self.manifest_for("obsolete.txt")
        proc = run(str(APPLY), str(manifest), "--root", str(self.repo), "--apply", cwd=self.repo)
        self.assertEqual(proc.returncode, 0, proc.stdout)
        self.assertFalse(target.exists())

    def test_changed_content_is_refused(self) -> None:
        target = self.repo / "obsolete.txt"
        target.write_text("old\n", encoding="utf-8")
        self.commit_all()
        manifest = self.manifest_for("obsolete.txt")
        target.write_text("changed\n", encoding="utf-8")
        proc = run(str(APPLY), str(manifest), "--root", str(self.repo), cwd=self.repo)
        self.assertEqual(proc.returncode, 2, proc.stdout)
        self.assertTrue(target.exists())

    def test_protected_patterns_are_refused(self) -> None:
        for name in (".env", "local.properties", "release.jks", "app.db"):
            target = self.repo / name
            target.write_text("secret\n", encoding="utf-8")
            self.commit_all(name)
            manifest = self.manifest_for(name)
            proc = run(str(APPLY), str(manifest), "--root", str(self.repo), cwd=self.repo)
            self.assertEqual(proc.returncode, 2, f"{name}: {proc.stdout}")
            self.assertTrue(target.exists())

    def test_non_empty_directory_is_refused(self) -> None:
        directory = self.repo / "old-dir"
        directory.mkdir()
        (directory / "child.txt").write_text("x", encoding="utf-8")
        commit = self.commit_all()
        manifest = self.repo / "manifest.json"
        manifest.write_text(json.dumps({
            "schema_version": 2,
            "repository_commit": commit,
            "entries": [{"path": "old-dir", "type": "empty-directory", "reason": "Reviewed empty directory"}],
        }), encoding="utf-8")
        proc = run(str(APPLY), str(manifest), "--root", str(self.repo), cwd=self.repo)
        self.assertEqual(proc.returncode, 2, proc.stdout)
        self.assertTrue(directory.exists())

    def test_output_outside_repository_is_refused(self) -> None:
        outside = self.repo.parent / "escaped.md"
        proc = run(str(INVENTORY), "--root", str(self.repo), "--output", str(outside), cwd=self.repo)
        self.assertEqual(proc.returncode, 2, proc.stdout)
        self.assertFalse(outside.exists())

    def test_inventory_reports_empty_directory_and_debug_output(self) -> None:
        (self.repo / "empty").mkdir()
        src = self.repo / "Main.kt"
        src.write_text("fun main() { println(\"debug\") }\n", encoding="utf-8")
        output = "docs/codex/cleanup/report.md"
        proc = run(str(INVENTORY), "--root", str(self.repo), "--output", output, cwd=self.repo)
        self.assertEqual(proc.returncode, 0, proc.stdout)
        report = (self.repo / output).read_text(encoding="utf-8")
        self.assertIn("`empty`", report)
        self.assertIn("println", report)


if __name__ == "__main__":
    unittest.main()
