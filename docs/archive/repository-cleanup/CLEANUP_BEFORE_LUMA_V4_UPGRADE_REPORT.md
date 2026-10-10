# Cleanup Before LUMA V4 Upgrade Report

Date: 2026-07-09

## Summary

The Android project root was cleaned by moving old package material, duplicate agent/package scaffolding, logs, temporary files, and generated APK output into `_cleanup_before_luma_v4_upgrade/`.

No app source code, Gradle files, Android Studio project files, Room schema files, installed V3 LUMA Codex agent stack files, or active `docs/codex/` files were moved or edited.

## What Was Found

KEEP:
- `app/`
- `gradle/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradlew`
- `gradlew.bat`
- `local.properties`
- `.idea/`
- `.gitignore`
- `publish-debug-apk.ps1`
- `serve-apk.ps1`
- `AGENTS.md`
- `.agents/`
- `.codex/`
- `docs/codex/`

KEEP BUT ORGANIZE:
- Older root-level docs/spec/checklist/tutorial materials were useful historical notes but not part of the installed V3 stack. They were moved into `_cleanup_before_luma_v4_upgrade/old_docs/`.

QUARANTINE:
- Old ZIP package: moved into `_cleanup_before_luma_v4_upgrade/old_zips/`.
- Old extracted package bundle: moved into `_cleanup_before_luma_v4_upgrade/extracted_packages/`.
- Older root-level Orbit agent/prompt/skill scaffolding: moved into `_cleanup_before_luma_v4_upgrade/duplicate_agent_packages/`.
- Runtime logs, temporary HTML files, old debug APK, and Codex remote attachment residue: moved into `_cleanup_before_luma_v4_upgrade/logs_and_temp/`.

IGNORE:
- `.gradle/`
- `.kotlin/`
- `app/build/`

These are generated build/cache folders and were left in place.

DO NOT TOUCH:
- `app/src/`
- `app/src/main/`
- `app/src/main/AndroidManifest.xml`
- `app/schemas/`
- Gradle files and wrapper files
- Android Studio project structure
- `AGENTS.md`
- `.agents/`
- `.codex/`
- `docs/codex/`

## V3 Agent Stack Backup

Created:
- `_cleanup_before_luma_v4_upgrade/v3_agent_stack_backup/`

Copied into backup:
- `AGENTS.md`
- `.agents/`
- `.codex/`
- `docs/codex/`
- `scripts/codex/`

Backup contains:
- `.codex/agents/luma_memory_guardian.toml`
- `.codex/agents/luma_regression_reviewer.toml`
- `.codex/agents/luma_risk_reviewer.toml`
- `.codex/agents/luma_ux_reviewer.toml`
- `.codex/skills/luma-autopilot/SKILL.md`
- `.codex/skills/luma-self-learning/SKILL.md`
- `docs/codex/LUMA_AUTOPILOT_SYSTEM.md`
- `docs/codex/LUMA_DEBUG_BUILD_DELIVERY.md`
- `docs/codex/LUMA_FEATURE_QUESTIONS.md`
- `docs/codex/LUMA_FINAL_REPORT_TEMPLATE.md`
- `docs/codex/LUMA_REGRESSION_CHECKLIST.md`
- `docs/codex/LUMA_REQUEST_ROUTING.md`
- `docs/codex/LUMA_RISK_POLICY.md`
- `docs/codex/LUMA_RULES.md`
- `docs/codex/learning/LUMA_AGENT_MEMORY.md`
- `docs/codex/learning/LUMA_BUG_GRAVEYARD.md`
- `docs/codex/learning/LUMA_DECISIONS.md`
- `docs/codex/learning/LUMA_PATTERN_LIBRARY.md`
- `docs/codex/learning/LUMA_PROMOTION_QUEUE.md`
- `scripts/codex/validate_luma_agent_stack.py`

## Moved Paths

Moved into `_cleanup_before_luma_v4_upgrade/old_zips/`:
- `codex_packages/luma_execute_that_cleaned_codex_package.zip` -> `_cleanup_before_luma_v4_upgrade/old_zips/luma_execute_that_cleaned_codex_package.zip`

Moved into `_cleanup_before_luma_v4_upgrade/extracted_packages/`:
- `codex_packages/` -> `_cleanup_before_luma_v4_upgrade/extracted_packages/codex_packages/`

Moved into `_cleanup_before_luma_v4_upgrade/duplicate_agent_packages/`:
- `codex/` -> `_cleanup_before_luma_v4_upgrade/duplicate_agent_packages/codex/`
- `skills/` -> `_cleanup_before_luma_v4_upgrade/duplicate_agent_packages/skills/`

Moved into `_cleanup_before_luma_v4_upgrade/old_docs/`:
- `checklists/` -> `_cleanup_before_luma_v4_upgrade/old_docs/checklists/`
- `specs/` -> `_cleanup_before_luma_v4_upgrade/old_docs/specs/`
- `workflow/` -> `_cleanup_before_luma_v4_upgrade/old_docs/workflow/`
- `FULL_BEGINNER_TUTORIAL.md` -> `_cleanup_before_luma_v4_upgrade/old_docs/FULL_BEGINNER_TUTORIAL.md`
- `PACKAGE_CONTENTS.md` -> `_cleanup_before_luma_v4_upgrade/old_docs/PACKAGE_CONTENTS.md`

Moved into `_cleanup_before_luma_v4_upgrade/logs_and_temp/`:
- `apk-server.log` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/apk-server.log`
- `apk-server.err.log` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/apk-server.err.log`
- `apk-server.out.log` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/apk-server.out.log`
- `apk-server-8766.err` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/apk-server-8766.err`
- `apk-server-8766.out` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/apk-server-8766.out`
- `tmpfiles-page.html` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/tmpfiles-page.html`
- `tmpfiles-page-current.html` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/tmpfiles-page-current.html`
- `tmpfiles-page-latest.html` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/tmpfiles-page-latest.html`
- `orbit-debug.apk` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/orbit-debug.apk`
- `.codex-remote-attachments/` -> `_cleanup_before_luma_v4_upgrade/logs_and_temp/.codex-remote-attachments/`

No files were moved into:
- `_cleanup_before_luma_v4_upgrade/unknown_review_needed/`

## Files Not Touched

Android project health files left in place:
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/`
- `app/src/main/`
- `app/src/main/AndroidManifest.xml`

Installed V3 stack files left in place:
- `AGENTS.md`
- `.agents/`
- `.codex/skills/luma-autopilot/`
- `.codex/skills/luma-self-learning/`
- `.codex/agents/`
- `docs/codex/`
- `scripts/codex/`

## Android Project Structure Check

Still exists:
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/`
- `app/src/main/`
- `app/src/main/AndroidManifest.xml`

Expected missing alternates:
- `settings.gradle`
- `build.gradle`
- `app/build.gradle`

The project uses Kotlin DSL Gradle files, so the missing non-KTS alternates are expected.

## V3 Stack Health Check

Still exists:
- `AGENTS.md`
- `.agents/`
- `.codex/skills/`
- `.codex/agents/`
- `docs/codex/`
- `scripts/codex/`

V3 appears internally consistent:
- `luma-autopilot` exists.
- `luma-self-learning` exists.
- Reviewer agents exist:
  - `luma_ux_reviewer.toml`
  - `luma_risk_reviewer.toml`
  - `luma_regression_reviewer.toml`
  - `luma_memory_guardian.toml`
- Routing/risk/regression/learning docs exist under `docs/codex/`.

No V3 repair was performed.

## Gradle Checks Run

Command:
- `.\gradlew.bat tasks`

First sandboxed result:
- Failed because the sandbox blocked Gradle wrapper network download.
- Exact error: `java.net.SocketException: Permission denied: getsockopt`
- Gradle was trying to download `https://services.gradle.org/distributions/gradle-8.9-bin.zip`.

Approved validation result:
- `.\gradlew.bat tasks` succeeded.
- Result: `BUILD SUCCESSFUL in 22s`

Command:
- `.\gradlew.bat assembleDebug`

First sandboxed result:
- Failed for the same sandbox network reason.
- Exact error: `java.net.SocketException: Permission denied: getsockopt`
- Gradle was trying to download `https://services.gradle.org/distributions/gradle-8.9-bin.zip`.

Approved validation result:
- `.\gradlew.bat assembleDebug` succeeded.
- Result: `BUILD SUCCESSFUL in 9s`
- Debug APK path: `app/build/outputs/apk/debug/app-debug.apk`

Both Gradle commands were run with:
- `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`

## Current Cleanup Folder Counts

- `duplicate_agent_packages/`: 27 entries
- `extracted_packages/`: 301 entries
- `logs_and_temp/`: 33 entries
- `old_docs/`: 14 entries
- `old_zips/`: 1 entry
- `unknown_review_needed/`: 0 entries
- `v3_agent_stack_backup/`: 32 entries

## Uncertain Files

None required `unknown_review_needed/`.

Notes:
- `.codex-remote-attachments/` was treated as runtime attachment residue and quarantined under `logs_and_temp/`.
- `.gradle/`, `.kotlin/`, and `app/build/` were recognized as generated caches/build outputs and left in place.

## Safety Assessment

Android project structure appears valid after cleanup.

V3 agent stack appears valid after cleanup.

It is safe to proceed with the V4 main package install next, provided the installer targets the active V3 locations and does not use the quarantined old package folders as source of truth.

Recommended next step:
- Install the LUMA V4 main package next.
- Do not install the MVP auto-verify add-on until after the V4 package is installed and validated.
