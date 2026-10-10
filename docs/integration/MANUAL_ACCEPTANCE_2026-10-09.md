# Manual acceptance checklist — integration branch 2026-10-09

Run on a phone (or API 36 emulator) with a debug build. Repeat the starred
items in Estonian and Russian. Mark each PASS / FAIL with a note.

## Upgrade safety (do first, on a phone that has the previous build with data)

- [ ] Install this build over the previous one: every note, task, reminder, Space and label is still there; no old reminder rings.
- [ ] Future reminders still ring at their time after the upgrade.
- [ ] Export a backup from the previous build; restore it on this build: preview counts match, data matches.

## Capture (the core promise)

- [ ] * Type a thought, send: "Saved" appears at once, one soft vibration; nothing else opens.
- [ ] The thought appears under Review > To sort with a suggestion within a few seconds (airplane mode: still saved, suggestion appears later or "Sort" offered).
- [ ] Force-stop right after sending, reopen: the thought is there.
- [ ] Type a draft, rotate / switch apps / let Android kill LUMA: the draft is still there.
- [ ] "Call the dentist tomorrow at 10" asks once "Remind you …?"; "Not now" leaves it in To sort.
- [ ] Share text from a browser to LUMA: it appears in the Home draft, not saved until sent.
- [ ] Launcher long-press > "New thought" and the Quick Settings tile open Home with the keyboard up.
- [ ] Empty box shows a microphone; dictation fills the draft.

## Review and To sort

- [ ] * Accept a suggestion in one tap; Undo restores the thought.
- [ ] "Change" opens the sorting sheet; "Let go" and "Hide suggestion" each offer Undo.
- [ ] "All sorted" appears only when nothing waits.
- [ ] * Ask LUMA questions answer from your own items; "nothing needed" answers are calm.

## Reminders

- [ ] Reminder at a time 2 minutes ahead rings once (not twice) and shows Done / Snooze.
- [ ] Snooze rings again 15 minutes later; Done removes it.
- [ ] Phone off across a reminder time, then on: it is shown once as missed.
- [ ] Repeating weekly reminder: Done moves it to next week, same time; it rings then.
- [ ] Notifications off in Android settings: Settings > Capture & reminders says so and links there.
- [ ] Exact alarms not allowed (Android 12+): status says reminders may be late, with a link.

## Navigation and screens

- [ ] * Bar shows Home · Spaces · Calendar · Review with labels; Settings is the top-right button on Home and has a back arrow.
- [ ] Calendar: no back arrow; "Today" appears only when away from today; last row not hidden by the bar.
- [ ] Spaces: cards show item count and next item; "To sort" card opens Review.
- [ ] Creating a Space with an existing name (also archived) explains why and offers Restore.
- [ ] Glass effect Strong / Soft / Off visibly changes surfaces; Off is solid.
- [ ] Settings slider drags feel immediate and do not jump back.

## Backup

- [ ] Export, reset local data, restore: everything is back, including learned rules and repeat rules; past reminders do not ring.
- [ ] Restoring a corrupted file changes nothing and says so.

## Accessibility and languages

- [ ] TalkBack: tabs announce their names and selection; capture box, send/microphone and settings buttons are labelled.
- [ ] Largest font size on a small phone: Home, Review and Settings scroll, nothing clipped.
- [ ] * No English text left in Estonian or Russian on the screens above.
