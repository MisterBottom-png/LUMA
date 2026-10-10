# Data and backup policy

LUMA is local-first. Room data and preference state remain on-device unless the user explicitly exports a local backup through the Android Storage Access Framework.

Android cloud backup is excluded through `data_extraction_rules.xml`; device-to-device transfer copies the whole database folder (including the write-ahead log), the settings DataStore file and shared preferences, and excludes the secure AI store (`luma_secure_ai.xml`, which holds the Gemini key). The legacy full-backup rules also exclude all app data.

Exports are user-chosen JSON files. The UI states that exports are plaintext and may contain sensitive information. Restore validates a bounded input size and structure before parsing, previews changes, and requires user confirmation. Restore must never silently overwrite user data.

## Versions

| Store | Current | Accepted on restore / upgrade |
|---|---|---|
| Room database | version 9 | migrations 1→2 … 8→9, all explicit; no destructive fallback |
| Export (backup) format | version 5 | versions 1–5 |

Schema history relevant to this branch: v7 adds device-local delivery state on reminders (`deliveredNotificationAt`, `snoozedUntil`; the migration marks past reminders as delivered so an upgrade never rings old reminders), v8 adds `capture_suggestions` (LUMA's stored suggestion for an unsorted thought; for a task suggestion `contextDateEpochDay` is the task's day, named in the thought or picked in Calendar, and tasks are never given a 23:59 time), v9 adds `reminders.repeatRule`. Every migration has a JVM test (Robolectric) and an instrumented test that open the real schema files from `app/schemas/`.

## What a backup contains (format 5)

Included: Spaces, source captures, notes, tasks, reminders (with repeat rule), Brain Dump sessions that are still in progress and their items, labels and label links, capture suggestions for unsorted thoughts, and learned rules (optional array; links to AI history are dropped).

Not included: app settings and appearance, the Gemini key, AI suggestion/correction history, person and project memory, Space aliases, device-local reminder delivery state and alarm tokens.

Backups carry `"product": "LUMA"` in their metadata. That marker predates the Tallele name and is kept unchanged, so every existing backup restores.

Export writes the file only after the encoded payload decodes again with the restore validator; legacy rows restore would reject (for example a blank title) are repaired or left out first.

## Restore behaviour

- Notes, tasks, reminders, captures, Spaces, labels, Brain Dump progress and capture suggestions are **replaced** in one database transaction; an invalid file changes nothing.
- Learned rules are **merged**: rules already on the phone stay, and backup rules are added unless the same rule (category and normalised text) exists.
- Restored reminders whose time has passed are marked as handled; only future reminders are armed, so a restore never sets off old reminders.
- A newer optional field this version does not understand (for example an unknown repeat token) is kept where it is stored as text, and otherwise ignored.

## Retention

AI suggestion and correction history keeps short snippets of past thoughts; entries older than 90 days are removed when LUMA starts. Learned rules are kept.
