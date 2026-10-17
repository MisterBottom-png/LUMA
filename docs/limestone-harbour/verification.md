# Limestone Harbour: verification evidence

Result words: **PASS**, **FAIL**, **BLOCKED** (environment prevented the run), **NOT RUN**. Phone-only checks stay `NEEDS OWNER PHONE VERIFICATION` until the owner reports a result. Nothing here is claimed without a command or a person's report.

## P0 baseline (2026-10-17)

Source: `main` at `f2662dd9c7efad43cf657325beb30e6be167b6f7` (the 43a1642 tree the proposal read, plus the handoff ZIP and prompt; no source changes).

### Environment

| Item | Value |
|---|---|
| JDK | OpenJDK 21.0.12 (cloud VM). CI uses Temurin 17. |
| Gradle | Wrapper 8.11.1 (downloaded by `./gradlew`; services.gradle.org reachable) |
| AGP / Kotlin / KSP | 8.10.1 / 2.2.21 / 2.2.21-2.0.5 |
| Android SDK | Not preinstalled. Installed in P0 to `/root/android-sdk` (outside the repo): cmdline-tools 13114758, `platforms;android-36`, `build-tools;35.0.0`, `platform-tools`. Licences accepted by `sdkmanager --licenses`. |
| Emulator / device | None. Instrumentation tests cannot run in the cloud VM. |

### Local results

Run from the repository root with `ANDROID_HOME=/root/android-sdk`, first build (cold Gradle cache), 2026-10-17 10:29–10:42 UTC.

| Command | Result | Detail |
|---|---|---|
| `./gradlew --no-daemon :app:testDebugUnitTest` | **PASS** | 142 suites, 726 tests, 0 failures, 0 errors, 2 skipped (`ScreenTourDarkTest.tour`, `ScreenTourLightTest.tour` — opt-in screen renders that run only when `LUMA_SCREENS_DIR` is set, as in `screens.yml`). 8 m 14 s. |
| `./gradlew --no-daemon :app:lintDebug :app:assembleDebug` | **PASS** | Lint: 0 errors, 48 warnings, 1 hint. Debug APK `app-debug.apk` 23,871,126 bytes (size baseline for P1/P6). 4 m 53 s. |
| `python3 scripts/codex/check_workplace_privacy.py --strict` | **PASS** | heuristic; semantic review of changed text done by the lead |
| `python3 scripts/codex/check_locale_resource_parity.py` | **PASS** | |
| `python3 scripts/codex/validate_luma_agent_stack.py` | **PASS** | unchanged by the new `.claude/agents/` files |
| Instrumentation (`connectedDebugAndroidTest`) | **NOT RUN** locally | no emulator in the cloud VM; runs in CI's API 36 emulator job |

### CI results

Push of `f2662dd` to `main` (GitHub Actions):

| Workflow / job | Run | Result |
|---|---|---|
| Android verification / verify (unit tests, lintDebug, assembleDebug, APK upload) | Actions run 39024798647 | **PASS**; artifact `luma-debug-apk-164` (22,644,561 bytes zipped) |
| Android release verification / Fast release gates (clean debug+release unit tests, lintRelease, assembleDebugAndroidTest, assembleRelease, bundleRelease, release verifier, privacy) | Actions run 39024798643 | **PASS** |
| Android release verification / API 36 emulator verification (instrumentation tests on an emulator) | same run | **PASS** (23 min) |

The local environment and CI agree: the baseline source builds and tests green. There is no build blocker.

P0 branch on PR #14: at `cb514d3`, verify, Fast release gates, Screens render and the API 36 emulator verification (instrumentation tests, Actions run 39028458098) all **PASS**. One earlier run (verify on `74faaa3`, docs-only) failed after 24 s while Gradle downloaded `gson-2.10.1.jar` from Maven Central, before any compile or test; the same configuration passed on `main`, locally and on every later commit, so it was a transient download failure, not a source problem.

## P0 characterization tests

New JVM (Robolectric) tests in `app/src/test/java/com/orbit/app/limestone/`. Each **passes today** and pins current behavior; its KDoc names the phase that must flip or extend it. No production code changed.

| Test | Pins | Flips / extends in |
|---|---|---|
| `P0CalendarDateTimeCharacterizationTest` (4 tests) | B7 **unreproduced**: timed note (`scheduledAt`) and timed task (`dueAt`) appear on their day as `Timed` at their instant; date-only stays `DateOnly` (never 00:00); adding a time through `ItemScheduleActions.apply` keeps items on Calendar; repositories refuse "day + time" rows; a DAO-written "both" row is excluded by the projection | P4 (guard only) |
| `P0ItemDetailStaleCharacterizationTest` (2 tests) | B10 **reproduced**: Done from the notification (`handleReminderAction`) is stored but the open reminder screen still shows it open; a task renamed elsewhere keeps its old title on the open screen | P4 (B10) |
| `P0ResetScopeCharacterizationTest` (1 test) | B5 baseline: Reset removes spaces, captures, notes, tasks, reminders and cancels `orbit_reminder_<id>` work; **keeps** learned rules, AI suggestion history (capture link nulled, raw `sourceTextSnippet` survives), person and project memory, and settings | P5 (B5): every KEPT becomes REMOVED |
| `P0AutoFilingCharacterizationTest` (2 tests) | B1: `CaptureResolution.undo` deletes an auto-filed task even after it was completed and renamed; D-09: a Gemini-failed fallback at high confidence is auto-filed because the stored suggestion loses `analyzerFailed` (while `decide()` on the live analysis returns Keep) | P3 (B1, D-09) |

Not covered by tests (by design or tooling): the Gemini key store (no AndroidKeyStore in Robolectric; Reset doesn't touch it by reading), `ai_correction_history`, `space_alias_memory`, last-export and blocked-delivery prefs; insert-failure on Keep (needs a repository seam, P3).

| Command | Result |
|---|---|
| `./gradlew --no-daemon :app:testDebugUnitTest --tests 'com.orbit.app.limestone.*'` (test writer) | **PASS** — 9 tests, 0 failures |
| `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug` (lead, whole suite with the new tests) | **PASS** — 146 suites, 735 tests (726 baseline + 9 new), 0 failures, 0 errors, 2 skipped (same opt-in tours); lint 0 errors, 48 warnings, 1 hint (unchanged). 2 m 27 s, warm cache. |

## P1 visual foundation (2026-10-17)

Branch `ccr-f940f7dc-udg2hj`, from `main` at `d4c24cc` (P0 merged). Same environment as P0 (SDK reinstalled per session to `/root/android-sdk`).

### Local results (lead, on the committed head `e610815`)

| Command | Result | Detail |
|---|---|---|
| `./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` | **PASS** | 158 suites, 837 tests (735 after P0, net +102 in P1), 0 failures, 0 errors, 2 skipped (the same opt-in `ScreenTourDarkTest.tour` / `ScreenTourLightTest.tour`). Lint: 0 errors, 48 warnings, 1 hint (unchanged from baseline; none in P1 files). |
| `./gradlew --no-daemon clean :app:assembleDebug` (for size) | **PASS** | `app-debug.apk` **25,247,348 bytes** vs P0 baseline 23,871,126: **+1,376,222 bytes (+5.8 %)**. Inside: fonts 665,340 B (Google Sans Flex 282,632 B removed, net +382,708 B), backgrounds 965,842 B. An incremental (non-clean) build measured 26,362,081 B; the clean figure is the comparable one. |
| `python3 scripts/codex/check_workplace_privacy.py --strict` | **PASS** | heuristic; semantic review of changed text done by the lead |
| `python3 scripts/codex/check_locale_resource_parity.py` | **PASS** | no strings added in P1 (D-25) |
| `python3 -I scripts/limestone/verify_fonts.py …` | **PASS** | Estonian and Russian alphabets, tnum/kern/liga/locl, weights; see `p1-assets.md` |
| `python3 -I scripts/limestone/measure_background_contrast.py app/src/main/res/drawable-nodpi scripts/limestone/background_contrast_results.json` | **PASS** | shipped washes (D-23); every text-2 and accent ≥ 4.5, main text ≥ 10.05; table in `p1-assets.md` §5 |
| Instrumentation (`connectedDebugAndroidTest`) | **NOT RUN** locally | no emulator in the cloud VM; runs in CI's API 36 emulator job on the PR |

Intermediate runs during the phase (agents and lead) are in their reports; the only failures were in new tests during development (fixed) and one Robolectric artifact download on a first run (passed on rerun once the jar was cached).

### What the P1 tests prove

| Area | Tests | Proves |
|---|---|---|
| Appearance model | `domain/model/LimestoneAppearanceTest` (8) | §7.17 conversion for every old accent, glass effect, background preset (with and without own photo) and theme; dropped settings do not change the result; defaults map to defaults |
| Colour tokens | `ui/theme/LimestoneColorsTest` | exact §6.1 / §7.17 values; every text/background pair in both themes and all four accents ≥ 4.5 (UI edges ≥ 3); Material role mapping (no ochre in any role, flat surfaces); outline on well documented at 2.90 (D-29) |
| Type, shapes, motion | `TalleleTypographyTest`, `DesignTokensTest` | Literata vs Noto Sans per §6.2 role, sizes/weights, tabular times, Russian uses the same family; §6.3 corners, §6.5 durations, 20/16 dp margins |
| Tab bar fallback | `TabBarLookTest` (10) | Glass only on API ≥ 33 with battery saver off, animations on, a blur source and a live route; otherwise Frosted; Solid/Frosted stay as chosen |
| Glass only on the bar | `GlassRolePolicyTest`, `SolidSurfaceJvmTest`, `GlassContrastTest` | `hazeEffect` only in `TalleleTabBar.kt`; Haze sources only the page and the shell NavHost (D-27); every other surface opaque; taps never pass through; tab labels ≥ 4.5 in Glass/Frosted/Solid, both themes, all accents (Glass 5.19/5.34, Frosted 6.39/7.27, Solid 7.16/8.41 for Ink) |
| Lifecycle | `ReducedMotionJvmTest` | Remove animations re-read on resume; battery-saver broadcast turns the fallback on and off |
| Tab bar a11y | `TalleleTabBarJvmTest`, `TalleleTabBarLongLabelsJvmTest` | Role.Tab + selected state, ≥ 48 dp, badge announced; the real EN/ET/RU labels at 200 % text in 320 and 360 dp are fully shown (a mutation back to ellipsis fails 4 of 7 cases); measured bar height published (D-28) |
| Backgrounds | `CaptureBackgroundTest` (6) | wash table (D-23), distinct light/dark resources, None draws nothing, light/dark selection |
| Shared parts | `parts/PartsJvmTest`, `KeepMarkGeometryTest`, `SourceRowJvmTest` | every §10.3 part (except `TalleleTabBar`, above) rendered at 200 % in 320 dp: text shown, ≥ 48 dp targets, semantics (radio, heading, live region), KeepButton blocks taps while busy, ReminderStatus keeps Due/Notify/Repeat separate, CaptureBox exposes typed text to TalkBack |

Previews at 100 % and 200 % text, light and dark, exist for every shared part (`parts/PartsPreviews.kt`, `SourceRow.kt`, `TalleleTabBar.kt`, `CaptureBackground.kt`). They were not rendered to images in the cloud VM (**NOT RUN**).

### CI

Recorded on the PR once its workflows finish (see `status.md`).

## Manual checks on a phone (owner)

Every item is `NEEDS OWNER PHONE VERIFICATION`. Run in light and dark, and in English, Estonian and Russian where text is involved. Install the debug APK from the CI run's `luma-debug-apk-*` artifact.

### Baseline (before P1) — optional, for comparison later

- [ ] Note current cold start feel and Home/Capture scroll smoothness on your phone (used to judge "no worse than today" in P6).
- [ ] Note what Reset in Settings → data currently removes (items/Spaces/reminders) and that settings, Gemini key and learned rules survive (expected today; P5 changes it).
- [ ] Create a note with a date **and** a time; check whether it appears on Calendar (B7).
- [ ] Open a reminder's detail screen, mark it Done from its notification, and check whether the open screen updates (B10).

### Capture and trust (after P3)
- [ ] Airplane mode on: keep a thought and find it again in Search.
- [ ] "Kept on this device." appears only after saving; text is never lost when saving fails.
- [ ] Two quick keeps: each receipt describes the right thought.
- [ ] Undo after automatic filing returns a kept thought; it is not re-filed later.
- [ ] Voice never keeps by itself; mic permission denial leaves typing usable.
- [ ] Keep stays visible above the keyboard; Enter makes a new line.

### Finding (after P2)
- [ ] A kept thought, a note, a task and a reminder can each be found by a remembered word.
- [ ] A sorted thought never appears twice; Brain Dump results show correctly.
- [ ] Query, filters and scroll come back after opening a result and pressing Back (gesture and three-button).

### Reminders (after P4)
- [ ] A missing or past time offers only valid choices.
- [ ] Due, notification time, repeat and scheduling state show separately.
- [ ] With notifications blocked, nothing says "scheduled".
- [ ] Done and Snooze 15 min from the notification; reboot; manual clock/time-zone change: reminders still ring.

### Navigation (after P2)
- [ ] Share text from another app → lands in Capture, nothing saved without Keep.
- [ ] Launcher shortcut and Quick Settings tile open Capture with focus.
- [ ] Notification tap opens the right reminder.
- [ ] Calendar opens from Today and from Saved and returns to where it came from.
- [ ] Needs you badge counts only To decide; TalkBack reads "Needs you, N to decide".

### Glass and look (after P1)
- [ ] Android 13+: tab bar is Glass (content shows through, blurred); with battery saver or "Remove animations" it becomes Frosted (re-check after switching either while the app is in the background); on Android 12 or older it is Frosted.
- [ ] Tab labels readable over the darkest content in Glass, Frosted and Solid (Settings → Appearance → Glass effect: Strong = Glass, Soft = Frosted, Off = Solid until P5).
- [ ] Lists, cards, sheets, dialogs and menus are never see-through; no gradient or photo shows behind screens.
- [ ] Light and dark: page, cards and text look like warm paper and blue-black ink; no lavender flash when the app opens in dark mode.
- [ ] Accent choices map to Ink / Harbour / Moss / Plum (Ink & Paper or Amber → Ink, Ocean → Harbour, Sage → Moss, Violet or Rose → Plum).
- [ ] English, Estonian and Russian: app text in Noto Sans, no missing letters (õ ä ö ü š ž, ё); Russian looks like English.
- [ ] Largest font size: tab labels wrap instead of being cut off; note any screen whose last row hides under the taller bar (expected until P2, D-28).
- [ ] Scrolling under the glass tab bar stays smooth on a mid-range phone (D-27; if not, report it).
- [ ] Built-in backgrounds (after P3 places them on Capture): each, light and dark, looks like the source (no banding, blur or stretching) and Capture text is readable.

### Data (after P5)
- [ ] After updating from the current version, appearance converts without a crash and items/Spaces/reminders are intact.
- [ ] An old backup file restores; reminders work afterwards.
- [ ] Export warns that the file is readable and confirms the write.
- [ ] Erase everything: Keep my data cancels; Erase removes everything; no old reminder rings afterwards; app opens like a new install.
- [ ] Reset appearance and Clear learned data only do their own job.

### Small screens, large text, TalkBack (P6)
- [ ] Widths 320/360/390/412 dp (display size settings) and font 100–200 %: nothing clips; Keep above the keyboard.
- [ ] TalkBack: capture, search, reminder repair, Space change, backup, restore, erase — in EN, ET, RU.
- [ ] Every swipe or long-press has a visible button or menu item.
- [ ] "Remove animations" on: nothing moves; Take one calm breath shows steps as text.
