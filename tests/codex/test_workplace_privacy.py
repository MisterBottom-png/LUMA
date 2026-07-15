from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / "scripts" / "codex" / "check_workplace_privacy.py"


def run_checker(repo: Path, strict: bool = True) -> subprocess.CompletedProcess[str]:
    command = [sys.executable, str(CHECKER), "--root", str(repo)]
    if strict:
        command.append("--strict")
    return subprocess.run(command, cwd=repo, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, check=False)


class WorkplacePrivacyTests(unittest.TestCase):
    def test_generic_roles_pass(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            (repo / "notes.md").write_text("Owner: manager\nReviewed by: reviewer\n", encoding="utf-8")
            proc = run_checker(repo)
            self.assertEqual(proc.returncode, 0, proc.stdout)

    def test_private_email_is_reported_without_echo(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            address = "manager" + "@" + "invalid.invalid"
            (repo / "notes.md").write_text("Contact: " + address + "\n", encoding="utf-8")
            proc = run_checker(repo)
            self.assertEqual(proc.returncode, 1, proc.stdout)
            self.assertIn("[email-address]", proc.stdout)
            self.assertNotIn(address, proc.stdout)

    def test_personal_attribution_is_reported_without_echo(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            candidate = "Rolecandidate"
            (repo / "notes.md").write_text("Reviewed by: " + candidate + "\n", encoding="utf-8")
            proc = run_checker(repo)
            self.assertEqual(proc.returncode, 1, proc.stdout)
            self.assertIn("[personal-attribution]", proc.stdout)
            self.assertNotIn(candidate, proc.stdout)

    def test_named_todo_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            (repo / "Main.kt").write_text("// TODO(identity): remove workaround\n", encoding="utf-8")
            proc = run_checker(repo)
            self.assertEqual(proc.returncode, 1, proc.stdout)
            self.assertIn("[named-todo-attribution]", proc.stdout)

    def test_contextual_plain_name_is_reported_in_strict_mode(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            candidate = "Rolecandidate"
            (repo / "notes.md").write_text("Ask " + candidate + " before release.\n", encoding="utf-8")
            proc = run_checker(repo, strict=True)
            self.assertEqual(proc.returncode, 1, proc.stdout)
            self.assertIn("[contextual-person-reference]", proc.stdout)
            self.assertNotIn(candidate, proc.stdout)

    def test_assigned_person_like_string_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as raw:
            repo = Path(raw)
            candidate = "Rolecandidate"
            (repo / "Main.kt").write_text('val approver = "' + candidate + '"\n', encoding="utf-8")
            proc = run_checker(repo, strict=True)
            self.assertEqual(proc.returncode, 1, proc.stdout)
            self.assertIn("[person-like-assigned-string]", proc.stdout)
            self.assertNotIn(candidate, proc.stdout)


if __name__ == "__main__":
    unittest.main()
