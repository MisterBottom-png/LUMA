# CLAUDE.md

Short, evergreen rules for Claude Code sessions in this repository. `AGENTS.md` is the full project policy and wins on any conflict. Do not paste design text here.

## What this is

Tallele: a private, local-first Android place to keep thoughts. Repository codename LUMA.

- Kotlin namespace `com.orbit.app` (never rename it); applicationId `com.tallele.app`.
- Single app module `:app`, plus `:macrobenchmark`. Navigation Compose, Room, DataStore, WorkManager, Haze. Hand-written DI in `OrbitContainer`.
- minSdk 26, targetSdk/compileSdk 36. Versions live in `gradle/libs.versions.toml`; do not bump them casually.

## Active work: Limestone Harbour redesign

- Design authority: `tallele-limestone-harbour-proposal.md` v1.3 inside `Tallele_Claude_Code_Cloud_Handoff.zip` (repo root). Extract to a temp dir; never into `app/src`.
- Master instructions: `Tallele_Limestone_Harbour_Claude_Code_Prompt.md` (repo root).
- Plan, status, decisions, evidence: `docs/limestone-harbour/`. Start every session with `status.md`, then `implementation-plan.md`.
- One phase per session/PR. Stop at the phase gate. Never merge to `main` yourself.
- Subagent definitions: `.claude/agents/tallele-*.md`. Two or three concurrent agents at most; shared files have one writer.

## Commands

```bash
# Android SDK is not preinstalled in cloud sessions. One-time setup (~2 min):
#   cmdline-tools from dl.google.com -> $ANDROID_HOME (outside the repo), then
#   sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
export ANDROID_HOME=/root/android-sdk

./gradlew --no-daemon :app:testDebugUnitTest          # JVM + Robolectric tests
./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.orbit.app.SomeTest'
./gradlew --no-daemon :app:lintDebug
./gradlew --no-daemon :app:assembleDebug              # debug APK
python3 scripts/codex/check_workplace_privacy.py --strict   # after any text change
python3 scripts/codex/check_locale_resource_parity.py       # after string changes
```

Only one Gradle build at a time in a checkout. To wait for another run: `while pgrep -f '[G]radleWrapperMain' >/dev/null; do sleep 10; done` (the brackets stop `pgrep` matching its own shell).

CI on every PR:
- `android-verify.yml`: unit tests, lint, assembleDebug; uploads the `luma-debug-apk-*` artifact the owner installs.
- `android-release.yml`: clean debug+release unit tests, lintRelease, release build/bundle, privacy check, then an API 36 emulator job (`scripts/run-device-verification.ps1`) that runs instrumentation tests. This is the only place instrumentation tests run; the cloud VM has no emulator.

Record results as PASS / FAIL / BLOCKED / NOT RUN with the exact command. Never claim a device, emulator, screenshot or phone check you did not run.

## Invariants (true today; do not break)

- Store first: the editor clears only after the raw thought is saved; on a failed save the exact text stays.
- Never say "scheduled", "saved", "restored" or "erased" without proof from the real state.
- A late sorting result never overrides a decision already made (Undo, user choice).
- Raw captures stay private source material; a sorted thought never shows twice.
- AI suggests; important changes need user confirmation. Gemini only through the existing consent path. Local-first and offline must keep working.
- Due time, notification time and repeat stay separate.
- No Room schema/version, export format, confidence threshold, auto-filing rule or reminder-semantics change without a recorded decision in `docs/limestone-harbour/decisions.md` and tests.
- English, Estonian and Russian for every new string.
- Zero workplace-person references anywhere (see `docs/codex/WORKPLACE_PRIVACY_POLICY.md`). Example names in designs become placeholders.

## Redesign targets (not true yet; land in the named phase)

- Only the bottom tab bar is glass; lists, cards, sheets and dialogs solid (P1; today five more surfaces use glass).
- Receipt says "Kept on this device." and offers Try again after a failed save (P3).
- Undo on automatic filing, refused once the item was edited or completed (P3; today's `CaptureResolution.undo` deletes unconditionally).
- Erase everything clears every store in `docs/limestone-harbour/p0-findings.md` §3 (P5; today Reset is narrow).

Current behavior and evidence: `docs/limestone-harbour/p0-findings.md`.

## Before pushing a phase

1. Review `git diff origin/main...HEAD` for unrelated or deleted behavior.
2. Run affected tests, then the CI trio above.
3. Ask `tallele-qa-reviewer` for an independent review; fix material findings.
4. Update `docs/limestone-harbour/status.md` and `verification.md`.
