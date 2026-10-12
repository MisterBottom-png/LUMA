# 04 · Remediation plan

The work is split into batches ordered by risk to trust and data (brief §4, Phase C). Each batch:

- adds failing tests first, or a written reproduction where a test isn't practical;
- makes the smallest change that is coherent;
- runs the focused tests, then the full JVM suite and lint;
- updates the findings register and any contract document affected;
- is committed on its own.

Visual work comes last. That follows the review's own care point: "Don't fix the looks first."

Legend:
- **Kind**:
  - **Bug**: a true defect.
  - **Req**: an existing requirement that isn't met.
  - **Dec**: an approved decision or redesign.
  - **Sugg**: a new suggestion. Not implemented unless it is low-risk and clearly in scope.
- **Acceptance**: what the tests (or the manual list) must show.

---

## Batch 1 · Saved means findable, and the screens tell the truth

| Item | Kind | Change | Acceptance |
| --- | --- | --- | --- |
| F05 | Bug | `createReminder` copies the full thought into the reminder's notes when it differs from the title. This covers every capture path. | A reminder made from a long thought keeps its full text, and Search finds the last word. |
| F01, B2, N018 | Bug | **Search:** unsorted (Inbox) thoughts become one Search result each ("Thought · To sort", opens the thought), including in Recent. Processed and archived thoughts are excluded unless "Search archived too" is on. **Receipt:** says "Kept in To sort." for a kept thought, and the correct line for a finished Brain Dump. | Each thought appears exactly once, before and after filing. The receipt never claims a note that doesn't exist. `LocalSearchTest` is updated to reflect the decision. |
| B8, F13, N021, N023, N048 | Bug / Dec | **Space detail:** gets a "Not sorted yet" section listing Inbox thoughts whose `suggestedSpaceId` is that Space. **Space "+" sheet:** keeps its draft per Space until the thought is saved or the draft is discarded. It can't be dismissed while saving, its text field is disabled while saving, and late results only affect the sheet that sent them. A snackbar confirms the save. **Filing:** a note is filed only into an active Space. | Saving from Space "+" shows the thought in that Space at once. A dismissed draft returns when the sheet reopens. |
| N003 | Bug | "Keep in To sort" in the sheet stamps the decision through `CaptureResolution.keep`, so AutoFiler can never file that thought later. Closing the sheet (B16, batch 2) does **not** count as a decision. | Keep, then turn "Sort right after saving" off: the thought stays in To sort. |
| N006 | Bug | `CaptureResolution.undo` deletes only an item the thought still links to. If the item changed type, the item is left alone and the thought is not reopened. | After a type change, Undo of the original sort creates no duplicate. |
| B3 | Bug | New string "Type change undone." in EN/ET/RU. | Shown after a type-change Undo. |
| B4, N026 | Dec | The Needs you summary counts To sort ("N to sort"). The empty card says what is true: "No questions right now", plus the To sort count with an action that expands it. The Spaces "To sort" tile opens Needs you with To sort expanded. | With kept thoughts, Needs you never says "Nothing is waiting". |
| N001, N019 | Bug | The Calendar date is consumed only when a send succeeds (in the ViewModel), not on recomposition. Pending Calendar-day sort requests are persisted in saved state and re-checked on start. | Save a thought, then use Calendar "+": the date stays. After a process restart the sheet still opens. |
| B1, N020 | Bug | Share, shortcut or tile while an item has unsaved edits: the incoming text is held and delivered to Home only after the edit is saved or discarded. Text that arrives while a save is in progress is queued, not dropped. | Edit, then share: the discard dialog appears and nothing is lost. |
| B17 | Bug | The sort sheet refuses to hide while saving or while a nested setup is open. | Swiping during a save does nothing. |
| N017 | Bug | Brain Dump items default to the thought's chosen Space. "Keep for later" creates a decided thought (never auto-filed) and analyses it now. | Covered by a test. |
| N022 | Bug | The Home draft is capped at the same 20 000 characters as shared text. | – |

## Batch 2 · Automatic actions you can trust

| Item | Kind | Change | Acceptance |
| --- | --- | --- | --- |
| F03 | Bug | A shared guard for negation, conditionals, quotes and past tense in EN/ET/RU ("don't/do not/no longer/never/if/unless", "ära/ärge/ei/kui", "не/если/нельзя"). A guarded thought can't reach High confidence. | `CaptureDecisionTest`: "don't remind me…", "if it rains remind me…", "I said 'remind me…'" are not Act, in all three languages. |
| F02 | Bug | Language parity: an explicit reminder phrase counts as two signals in every language, so ET matches EN and RU. A user-chosen Space counts as Space evidence, so a clear note with a chosen Space can be filed into it. | ET, EN and RU "remind me tomorrow at 9" all set a reminder. The "known gap" assertion is removed. |
| N002, N013, N014 | Bug | **Numbers:** a compact number or HH.MM is a time only right after a time marker ("at/kell/в") and not before a currency or unit. Years such as 2027 are never times. **Ambiguous hours:** a bare hour 1–11 asks even when a day is given. **Weekdays:** a weekday that is today, with a time still ahead, means today. | Parser tests with the cases from the register. |
| F08 | Bug | One shared mapping from suggestion life signal to task status (Someday, Waiting for), used by auto-filing, one-tap accept and the sheet. | Accepting a "someday" suggestion creates a Someday task. |
| F11, N046 | Dec | New consent text (EN/ET/RU): Gemini suggestions are filed automatically using the same rules, and "Sort right after saving" pauses that. Consent version goes to 2, so it is asked again. The user's text is delimited in the prompt. A Gemini confidence above the local evidence ceiling is capped, so injected text can't force filing. | Existing consent (v1) reads as not given. |
| N012 | Bug | When Gemini fails, the decision uses the local analysis, marked as a local suggestion, instead of keeping silently. | Gemini offline still files a clear local reminder. |
| B16 | Bug | Closing the sheet records nothing. Only "Keep in To sort" records a rejection. | – |
| Pause discoverability | Dec | "Sort right after saving" moves from Reminders › Advanced into a "Saving" group, with text saying it pauses automatic filing. | Settings tests. |
| N051 | Sugg | Small inflection additions (RU "машину", EN plurals for Space words). | Analyzer tests. |

## Batch 3 · Reminders you can rely on

| Item | Kind | Change | Acceptance |
| --- | --- | --- | --- |
| F04, N044 | Dec | When a repeating reminder's occurrence is shown, or found missed during reconcile or restore, the reminder advances to its next occurrence and re-arms. Done and Snooze carry the occurrence time, so a stale action doesn't skip a newer occurrence. | Shown → next occurrence armed. A second and third occurrence fire in the delivery test (JVM). The device check stays open. |
| N004 | Bug | The reconcile worker uses KEEP + APPEND semantics (`APPEND_OR_REPLACE`), and the delivery claim is released if the notify step is cancelled. | – |
| B5, N038 | Bug | The item page, sort sheet and type change validate the **alert** time (target − offset) and refuse a past one with a specific message. Pickers get a minimum date. Reopening or re-enabling a reminder whose time has passed says it won't ring. | Past time: error, nothing stored. Future time with past alert: explained. |
| B6 | Bug | On resume, if notification capability changed, reconcile runs and `LinkedCaptureOutcomes.armed` clears only reminders that are future and armed. | – |
| RS1, N035 | Bug | Retry counts a null token for an active future reminder as a failure, checks notifications, passes linked thoughts, and keeps the button after "still failing". | – |
| F06, N015 | Bug | `AiSourceItem` gains the date-only day. Ask's today, overdue and soon, and Situation AI, use a shared "due on" rule. "Today" includes earlier today. No midnight is invented. | Ask lists a date-only task for today and an item from earlier today. |
| F12, B9, N005 | Bug | A shared minute and resume clock feeds the Review state and the Calendar's "today". Quick times are re-evaluated on each tick. | Unit tests with a fake clock. |
| F14 | Bug | A note gets "Add date" (date only, optional time). | `ItemDetailFieldsTest` updated. |
| N039 | Bug | Calendar reads the zone at use time. | – |

## Batch 4 · Safe recovery and editing

| Item | Kind | Change | Acceptance |
| --- | --- | --- | --- |
| F07, N008, N045 | Bug | Type change inserts with a new id. In the same transaction it moves labels, rewrites `captures.linkedItemId`/`suggestedType`, keeps `reminders.linkedCaptureId`, repoints `linkedTaskId`, and drops the archived-status memory. The old alarm is cancelled and the new one armed. The ViewModel navigates to the new id. Undo reverses this exactly. Offset and snooze loss are named in the warning. | Converting note 5 when task 5 exists succeeds, and Undo restores it. |
| F09 | Bug | Restore compares and replaces in one transaction. `CaptureInbox.store` drops a result whose capture text or creation time differs from what was analysed. | A test where the capture is replaced during analysis. |
| F10, B7, N036 | Dec | Reset deletes everything: the data tables, AI history and corrections, learned rules, memory, all settings (DataStore cleared), the Gemini key, the last-export and delivery prefs, every alarm, job and notification, and in-memory undo handovers. The app then returns to first launch. The dialog lists exactly this and states what an export does not contain. | Room + settings test: nothing remains after reset. |
| N007, N031, N032, N033, N034, N037 | Bug | **Restore:** no longer re-links device-only AI history or aliases to different items; ignores delivery fields when comparing; can't overlap a reset or be dismissed while running. **Export:** failure deletes only a file it created; the size-limit failure is explained. **Archive Undo:** refuses if the item changed since. | Focused tests. |
| B10 | Bug | The weekly summary is reloaded when it is cleared while visible. | – |

## Batch 5 · Consistency and words

- B11, B12, B14, B15, N016, N025, N027, N049.
- Review §7 wording, limited to where it changes no behaviour: "To sort", "No Space", the time question and developer words.
- The Needs you one-item sheet (review §9.3) and the remaining Space "+" screens (review §9.2).

## Batch 6 · Layout and accessibility

- L1–L8, B18, N028, N029, N053.
- Renders at 320, 360 and 411 dp in EN, ET and RU at 100 %, 130 % and 200 %, through Robolectric screen-tour captures where feasible. Manual TalkBack traversal stays open.

## Batch 7 · Looks, security and release

- **Approved looks:** the calm solid tab bar (§9.4), Tallele violet for new installs (§11), Glass and Solid only (§11), handled per the migration rule in C7.
- **Hardening:** N009, N011, N040, N041, N042, N047.
- **Release items:** N010 and N043, documented and added to the verifier/checklist pending decision D2.

## Not planned in this pass

| Item | Reason |
| --- | --- |
| N024 | Platform behaviour of `singleTop` and the static shortcut. Needs a device. |
| N050 | Dead code. Cleanup is kept separate from behaviour work (AGENTS.md cleanup rule). |
| Encrypted or automatic backups; "Remind again until done" | Owner decided "not now". |
