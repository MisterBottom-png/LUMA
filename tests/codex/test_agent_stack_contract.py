from __future__ import annotations

import tomllib
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SKILLS_ROOT = ROOT / ".agents" / "skills"
REVIEWERS_ROOT = ROOT / ".codex" / "agents"

EXPECTED_SKILLS = {
    "luma-ai-reminder-guardian",
    "luma-android-developer",
    "luma-autopilot",
    "luma-compose-ui",
    "luma-glass-haze-guardian",
    "luma-mvp-release-manager",
    "luma-project-cleanup",
    "luma-regression-qa",
    "luma-room-data-guardian",
}
EXPECTED_REVIEWERS = {
    "luma_code_data_reviewer",
    "luma_release_regression_reviewer",
    "luma_ui_accessibility_reviewer",
}
REQUIRED_SKILL_SECTIONS = {
    "## When to use",
    "## Do not use",
    "## Workflow",
    "## Verification",
    "## Workplace privacy",
}


class AgentStackContractTests(unittest.TestCase):
    def test_every_skill_has_consistent_operational_contract(self) -> None:
        found = {path.parent.name for path in SKILLS_ROOT.glob("*/SKILL.md")}
        self.assertEqual(found, EXPECTED_SKILLS)

        for name in sorted(EXPECTED_SKILLS):
            text = (SKILLS_ROOT / name / "SKILL.md").read_text(encoding="utf-8")
            with self.subTest(skill=name):
                for section in REQUIRED_SKILL_SECTIONS:
                    self.assertIn(section, text)
                self.assertIn("docs/codex/WORKPLACE_PRIVACY_POLICY.md", text)

    def test_every_skill_has_discovery_metadata(self) -> None:
        for name in sorted(EXPECTED_SKILLS):
            metadata_path = SKILLS_ROOT / name / "agents" / "openai.yaml"
            with self.subTest(skill=name):
                text = metadata_path.read_text(encoding="utf-8")
                self.assertIn("display_name:", text)
                self.assertIn("short_description:", text)
                self.assertIn(f"${name}", text)

    def test_root_routing_names_every_specialist(self) -> None:
        agents_text = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        for name in sorted(EXPECTED_SKILLS - {"luma-autopilot"}):
            with self.subTest(skill=name):
                self.assertIn(name, agents_text)

    def test_reviewers_are_read_only_and_have_bounded_output_contracts(self) -> None:
        found = {path.stem for path in REVIEWERS_ROOT.glob("*.toml")}
        self.assertEqual(found, EXPECTED_REVIEWERS)

        for name in sorted(EXPECTED_REVIEWERS):
            data = tomllib.loads((REVIEWERS_ROOT / f"{name}.toml").read_text(encoding="utf-8"))
            instructions = data["developer_instructions"].lower()
            with self.subTest(reviewer=name):
                self.assertEqual(data["sandbox_mode"], "read-only")
                self.assertIn("do not edit", instructions)
                self.assertIn("supplied diff", instructions)
                self.assertIn("evidence gap", instructions)
                self.assertIn("untrusted", instructions)
                self.assertIn("never mention any coworker", instructions)


if __name__ == "__main__":
    unittest.main()
