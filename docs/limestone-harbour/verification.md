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

BASELINE_RESULTS_PLACEHOLDER

### CI results

CI_RESULTS_PLACEHOLDER

## P0 characterization tests

P0_TESTS_PLACEHOLDER

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
- [ ] Android 13+: tab bar is Glass; with battery saver or "Remove animations" it becomes Frosted; on Android 12 or older it is Frosted.
- [ ] Tab labels readable over the darkest content in Glass, Frosted and Solid.
- [ ] Lists, cards, sheets and dialogs are never see-through.
- [ ] Each built-in background, light and dark, looks like the source image (no banding, no blur, no stretching) and text on Capture is readable.
- [ ] Scrolling under the glass tab bar stays smooth on a mid-range phone.

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
