from __future__ import annotations

import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / "scripts" / "codex" / "check_locale_resource_parity.py"


class LocalizationResourceParityTests(unittest.TestCase):
    def test_accepts_matching_supported_locale_resources(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            resource_root = Path(temporary_directory)
            self._write_resources(
                resource_root,
                english='<resources><string name="message">Hello, %1$s</string></resources>',
                estonian='<resources><string name="message">Tere, %1$s</string></resources>',
                russian='<resources><string name="message">Привет, %1$s</string></resources>',
            )

            result = self._run_checker(resource_root)

            self.assertEqual(0, result.returncode, result.stderr)

    def test_rejects_missing_keys_and_incompatible_placeholders(self) -> None:
        with tempfile.TemporaryDirectory() as temporary_directory:
            resource_root = Path(temporary_directory)
            self._write_resources(
                resource_root,
                english=(
                    '<resources>'
                    '<string name="message">Hello, %1$s</string>'
                    '<string name="secondary">Later</string>'
                    '</resources>'
                ),
                estonian='<resources><string name="message">Tere, %1$d</string></resources>',
                russian=(
                    '<resources>'
                    '<string name="message">Привет, %1$s</string>'
                    '<string name="secondary">Позже</string>'
                    '</resources>'
                ),
            )

            result = self._run_checker(resource_root)

            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing key", result.stderr)
            self.assertIn("placeholder", result.stderr)

    def _run_checker(self, resource_root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [sys.executable, str(CHECKER), "--resource-root", str(resource_root)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
        )

    @staticmethod
    def _write_resources(
        resource_root: Path,
        english: str,
        estonian: str,
        russian: str,
    ) -> None:
        for directory_name, contents in {
            "values": english,
            "values-et": estonian,
            "values-ru": russian,
        }.items():
            directory = resource_root / directory_name
            directory.mkdir()
            (directory / "strings.xml").write_text(contents, encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
