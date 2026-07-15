# LUMA Agent Memory

This file stores useful lessons for future Codex runs.

Do not store one-time task noise here.

## Memory format

```md
## Lesson: <short name>

Source:
<task, bug, or review that revealed this>

Learning:
<what future Codex should know>

Future behavior:
<how future Codex should act>

Related areas:
- ...
```

## Starter lessons

## Lesson: Home stays calm

Source:
LUMA product direction.

Learning:
Home is a calm capture surface, not a dashboard.

Future behavior:
When adding new Home features, prefer compact entry points that open deeper secondary surfaces instead of dense permanent cards.

Related areas:
- Home
- mini calendar
- Situation AI
- Review
- bottom navigation

## Lesson: Simple visual fixes should not become discovery projects

Source:
Autopilot design.

Learning:
When the user provides a screenshot/appshot and a clear visual request, Codex should fix it directly instead of asking product questions.

Future behavior:
Classify screenshot alignment/spacing/contrast issues as Visual Fix unless the visual reference is unclear.

Related areas:
- UI polish
- dark mode
- layout fixes

## Lesson: Use Android Studio JBR for Gradle validation on this machine

Source:
Screenshot follow-up after the Settings categorization task.

Learning:
The default shell `java` on this Windows environment is Java 8, which is too old for the Android Gradle plugin used by this project. Android Studio includes a bundled JBR at `C:\Program Files\Android\Android Studio\jbr` that successfully runs Gradle validation.

Future behavior:
When running Gradle commands, prefer setting `JAVA_HOME` to `C:\Program Files\Android\Android Studio\jbr` for the command invocation instead of changing global environment settings.

Related areas:
- Gradle validation
- Android builds
- Windows environment

## Lesson: Reminder display text is not temporal source data

Source:
Parsed reminder time propagation bug fixed on 2026-07-14.

Learning:
An AI reminder phrase and its structured epoch can disagree even when both fields pass schema validation. The UI must render and confirm one canonical locally resolved timestamp for explicit supported times instead of trusting display text and epoch independently.

Future behavior:
Resolve explicit local date/time phrases once at the interpretation boundary, propagate the resulting epoch unchanged through confirmation and Room, and derive notification fire time only by applying the separately stored offset. Invalid explicit times must clear any conflicting AI timestamp and require review.

Related areas:
- capture analysis
- Gemini validation
- reminder confirmation
- timezone conversion
- notification scheduling

## Lesson: Separate internal processing state from user-facing feed/review labels

Source:
Space Life Feed and Review status clarity fix.

Learning:
Spaces should render finalized user-facing entities such as notes, tasks, and reminders, not raw captures or processing states. Review wording should keep item state language such as "Done" separate from review workflow language such as "Review not finished".

Future behavior:
When editing Spaces or Review, check whether labels come from item type, item status, capture processing state, or review workflow state before exposing them in UI.

Related areas:
- Spaces
- Life Feed
- Review
- captures
- tasks

## Lesson: AI learning starts as additive local storage

Source:
Phase 13.1 local AI learning memory data model.

Learning:
Editable AI learning memory needs local Room tables and repositories before UI or Gemini prompt behavior changes. The first safe slice should be additive: suggestion history, correction history, learned rules, and memory concepts with enabled/reset paths.

Future behavior:
When extending LUMA learning, preserve existing captures/items, add explicit Room migrations, keep raw AI JSON out of normal storage/UI surfaces, and route new behavior through repositories before wiring prompts or screens.

Related areas:
- Room
- migrations
- AI learning memory
- Gemini prompts
- Settings AI

## Lesson: Back up installed agent stacks before upgrade cleanup

Source:
LUMA V3-to-V4 project cleanup preparation.

Learning:
When preparing for an agent-stack upgrade, the installed stack locations are the source of truth. Old zips, extracted packages, root-level legacy agent folders, logs, and temporary artifacts should be quarantined only after copying the active stack to a dedicated backup.

Future behavior:
Before moving cleanup clutter, preserve `AGENTS.md`, `.agents/`, `.codex/`, `docs/codex/`, and `scripts/codex/` if present. Do not merge, simplify, or repair an installed stack during cleanup unless the user explicitly asks for that repair.

Related areas:
- LUMA agent stack
- project cleanup
- upgrade preparation
- Codex skills
