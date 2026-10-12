# 06 · Final handoff

## Verdict

**Usable with stated limitations.**

Every finding that changes what Tallele does with your data is fixed and covered by tests on the JVM: filing, reminders, restore, reset, type changes and Undo. The limits are below. The biggest is that nothing was run on a real phone or emulator in this pass. Notification delivery, reboot, daylight-saving changes, permission changes, TalkBack and real-device rendering are traced in code and tested in Robolectric, but they are **not verified on a device**.

## What changed (branch `ccr-eed43729-xsml4c`)

| Batch | Commit | What you'll notice |
| --- | --- | --- |
| 1a | `f7d3c47` | Kept thoughts show up in Search. Receipts say what really happened. A thought can't be filed twice. A Calendar-day thought keeps its day. |
| 1b | `e0cdfd3` | The new Space "+" flow from the design. "Not sorted yet" in each Space. To sort is visible in Needs you. |
| 1c | `59d8fbd` | Shared text never closes an item you are editing. A Brain Dump started in a Space stays in that Space. |
| 2 | `03c1384` | Automatic filing skips negations, conditions and quotes in EN/ET/RU. Prices are not read as times. The consent screen says what is filed, and the pause is under Settings › Saving. |
| 3a | `6af1cc7` | Repeating reminders keep ringing after you ignore, snooze or restore one. |
| 3b | `a8ccd9e` | A time that has passed is refused with the reason. Reminders re-arm when you turn notifications back on. "Retry reminder setup" is honest. |
| 3c | `6d0b3b0` | Ask and Situation AI count tasks that only have a date. Needs you and Calendar follow the real clock and the phone's current time zone. Notes can be given a date. |
| 4 | `980c945` | Reset really starts fresh. A type change never fails on a clash. Restore is one safe step. Undo keeps later edits. |
| 5 | `7978dda` | The Needs you one-item sheet from the design. Answered cards say what happened. Wording is consistent ("No Space", "To sort"). |
| 6 | `b5b08b6` | Layout fixes for long Estonian/Russian words and large text. TalkBack names and states. One-by-one is modal. |
| 7 | `052284d` | Calm solid tab bar. Violet for new installs. Glass/Solid only. Gemini and key hardening. Release signing guard. |
| Final | handoff commit | Calendar no longer ends a full day with "Nothing planned" (B13). Audit docs updated. |

Status for every finding, with its commit and tests, is in `02-findings-register.md`.

## Evidence

- 784 unit and Robolectric tests, 0 failures, on both debug and release (the baseline was 726).
- `lintDebug`: 0 errors and 48 warnings, the same as the baseline.
- Light and dark screen tours pass, including the new sheets. The renders were compared with the "Add to a Space" design.
- CI "Screens" is green on every batch from 1c on.
- No on-device evidence: the API 36 emulator job does not run on this branch.

## Decisions only you can make

| ID | Decision | Current state |
| --- | --- | --- |
| D1 | Reword the permanent rule in `AGENTS.md` ("AI must not silently … schedule …") to match the approved automatic-filing boundary. | Not edited, because `AGENTS.md` needs your explicit approval. The code and the other docs follow the approved boundary. |
| D2 | Distribution channel: Google Play or private install (N010). | `USE_EXACT_ALARM` and the direct battery request stay. The release checklist says what to remove for Play. |

## Known limitations (not fixed)

| ID | What it means day to day |
| --- | --- |
| N014 | "Monday 18:00" typed on a Monday morning gives next Monday. Check the time on the receipt. |
| N048 | If you archive a Space in the few seconds before a new thought in it is analysed, the note goes to No Space. |
| N051 | Inflected words ("машину", "dogs") don't match a Space name. You choose the Space yourself. |
| N013 (part) | A bare "9:30" is read as 09:30, on the 24-hour clock. This is intended. |
| N042 | The tab dot reads all thoughts. It was not measured and is fine at normal sizes. |
| N024 | The launcher shortcut behaviour needs a device. Not verified. |
| N050 | Dead code is left for a separate cleanup run, per the repository's cleanup rule. |
| L2 | Already handled. No change needed. |

## Manual checks before relying on it

1. On your phone: set a repeating reminder, ignore one occurrence, and confirm that the next one rings. Do the same after a restart.
2. Turn notifications off, then on again in Android settings, and come back to Tallele. Reminders should show as set again.
3. Run Reset, then confirm the app opens like a new install (first-time guide, starter Spaces, no Gemini key).
4. Open a pull request (or run the release workflow) so the API 36 emulator job runs `connectedDebugAndroidTest`.
5. Look at Calendar, Settings and the Space editor in Estonian and Russian at your largest font size.
