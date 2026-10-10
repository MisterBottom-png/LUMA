from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLANNING = ROOT / "docs" / "planning"


class PlanningCatalogTests(unittest.TestCase):
    def test_numbered_prompts_match_checklist_order(self) -> None:
        checklist = (PLANNING / "LUMA_UI_UX_Small_Change_Steps.md").read_text(encoding="utf-8")
        prompts = (PLANNING / "LUMA_ChatGPT_Project_Small_Change_Prompts.md").read_text(encoding="utf-8")

        checklist_items = re.findall(r"^- \[[ xX]\] (.+)$", checklist, flags=re.MULTILINE)
        prompt_numbers = [
            int(value)
            for value in re.findall(r"^## (\d+)\. .+$", prompts, flags=re.MULTILINE)
        ]

        self.assertEqual(len(checklist_items), 56)
        self.assertEqual(prompt_numbers, list(range(1, len(checklist_items) + 1)))

    def test_autopilot_supports_numbered_planning_request(self) -> None:
        skill = (ROOT / ".agents/skills/luma-autopilot/SKILL.md").read_text(encoding="utf-8")
        workflow = (
            ROOT / ".agents/skills/luma-autopilot/references/numbered-changes.md"
        ).read_text(encoding="utf-8")

        self.assertIn("implement small change <number>", skill)
        self.assertIn("references/numbered-changes.md", skill)
        self.assertIn("Implement exactly one numbered change", workflow)
        self.assertIn("Ignore planning instructions about Google Drive", workflow)


if __name__ == "__main__":
    unittest.main()
