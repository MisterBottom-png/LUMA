# 03 · Contract conflicts

This document records where three sources disagree: the owner's decisions (in `Tallele_full_review.md` §11, dated 12 October 2026), the repository's documentation, and the code.

For each conflict it gives the resolution this work applies and whether existing approval covers it. Where approval does not cover it, the item is listed under **Open decisions** at the end.

---

## C1 · Automatic filing vs the older confirmation rules

**Sources**
- Review §11 (approved): Gemini suggestions are filed automatically, using the same act/ask/keep rule as on-phone suggestions. The consent must say so and name the pause switch. There is no action on "don't…" thoughts, and a reminder without a clear future time is asked about.
- Conflicting docs:
  - `docs/codex/LUMA_AI_BEHAVIOR_CONTRACT.md`: "must not silently create important tasks, schedule reminders… mutate user data".
  - `LUMA_PRODUCT_RULES.md` ("AI trust").
  - `docs/cloud-ai-data-flow.md` ("important actions require visible confirmation").
  - `LUMA_RISK_POLICY.md` (hard approval).
  - `AGENTS.md` product invariants.

**Code:**
- `AutoFiler` already files local and Gemini suggestions (shipped in PR #11, kept by the owner).
- The consent text promises confirmation (F11).

**Resolution applied:**

| Category | Behaviour |
| --- | --- |
| **May be automatic** | Filing a *new* thought as **one** note, task or reminder. This applies only at High confidence and only through `decide()`, never for a Brain Dump or failed analysis. Every automatic filing shows a receipt with **Change** and an **Undo** path, and is paused by the "Sort right after saving" switch. |
| **Reminders** | Act only when the request is positive (F03) **and** the time resolved to a single future instant. Otherwise ask (time question) or keep. |
| **Never automatic** | Editing, completing, archiving, deleting, merging or moving existing items; sending anything; changing learned rules; creating more than one item from one thought. |
| **Ambiguity** | Negation, quotes, conditionals, diary-style text, several dates, an ambiguous hour, or a price/number mistaken for a time: Keep or Ask, never Act. |

**Docs updated in the same batch (batch 2):** the AI behaviour contract, the product rules, the cloud-AI data flow and the consent strings (version 2).

**Approval:** covered by the review's decisions for filing.

**Not covered:** the wording of the permanent invariant in `AGENTS.md`. The repository's risk policy says `AGENTS.md` changes need explicit approval, so it is **not edited** (see open decision D1).

## C2 · "A kept thought is findable" vs "raw captures are internal"

**Sources**
- Review (approved): a kept thought is "waiting", shows in To sort, and must be searchable. A Space "+" thought is visible in its Space.
- `LUMA_PRODUCT_RULES.md`: "Do not show the original raw capture as a separate Space card. Do not show `Processed`…".

**Resolution applied:**

| Thought state | How it appears to the user |
| --- | --- |
| **Not sorted yet** (`status = Inbox`) | Shown as a **thought**: one Search result labelled as a thought in To sort, and in its chosen Space under a separate **"Not sorted yet"** section. It opens the thought's own page with its full text. |
| **Filed** (`status = Processed`, with an item) | Hidden. The resulting note, task or reminder represents it, and carries the full text (F05). |
| **Processed with no item** (finished Brain Dump) | Hidden. Each saved Brain Dump item is its own item. |
| **Archived** | Hidden unless "Search archived too" is on. |

A thought is therefore never shown twice: it is either unsorted or represented by its item. The product rules are updated to say so.

**Approval:** covered by the review's decisions.

## C3 · Full Reset vs incomplete export

**Sources**
- Review §11 (approved): Reset deletes everything (items, Spaces, thoughts, AI history, learned rules, memory, settings, the Gemini key) and returns the app to first launch.
- `docs/data-and-backup-policy.md`: export omits settings, the key, AI history, memory, aliases and delivery state.

**Resolution applied:**
- Reset deletes every one of these.
- The Reset dialog says exactly what is deleted, and that an export **does not** contain settings, the Gemini key, AI history or learned memory. "Export before Reset" is therefore not described as a full restore.
- Export scope is **not** widened. That would be a separate, compatibility-relevant proposal.

**Approval:** covered.

## C4 · Repeating delivery vs "remind until done"

**Sources**
- Review §11 (approved): every occurrence of a repeat rings on schedule, even if the previous one was ignored or swiped away. Done marks only this occurrence.
- `docs/release-checklist.md` defers "Remind again until done" ("Each reminder notifies once").

**Resolution applied:**
- A repeating reminder advances to its next occurrence when the current occurrence is **shown**, and the next one is armed. Each occurrence notifies once.
- A one-off reminder still notifies once. Nothing nags until acknowledged.
- The release checklist line is reworded to "each occurrence notifies once".
- Calendar's projected occurrences then match what is armed.

**Approval:** covered.

## C5 · Package migration and Android permissions

**Sources:** the release checklist (application id changed from `com.orbit.app` to `com.tallele.app`; the two install side by side) and the manifest (`USE_EXACT_ALARM`, a direct battery-optimisation request).

**Resolution applied:**
- No package rename.
- Data moves only through Export/Restore; this work states that and does not imply an in-place migration.
- Restored future reminders are re-armed by the restore reconciler. Past repeating reminders are advanced (N044) rather than silenced.

**Open:** whether to remove `USE_EXACT_ALARM` depends on the distribution channel (D2).

## C6 · Space "+" and Calendar "+" vs the persistence model

**Sources:** review §9.2 and §11 (save straight into the Space, keep a draft per Space, Calendar "+" opens the same add sheet with the date) and the existing capture lifecycle.

**Resolution applied:**
- The chosen Space (`captures.suggestedSpaceId`) and date (`captures.contextDateEpochDay`) are stored with the thought at save time. They survive analysis, restart and retry.
- Until the thought is filed, it shows in that Space's "Not sorted yet" section.
- Filing uses the normal exactly-once finalisation. A user-chosen Space counts as Space evidence for a note.
- A reminder without a time is asked about. It is never turned into a plain note.
- The Calendar-day sort trigger is persisted, so it survives process death (N019).

**Delivery:** the multi-step sheet design (screens 1–7) is delivered in stages. Batch 1 keeps the draft, refuses dismissal while saving, shows a confirmation, and shows the thought in the Space. The full redesigned sheet follows in batch 5.

## C7 · Visual decisions vs existing settings

**Source:** review §9.4, §9.5 and §11 (solid calm tab bar; Tallele violet for **new** installs; Glass/Solid only, with Clear and Soft becoming Glass).

**Resolution:**
- **Violet for new installs only.** Existing installs keep their stored accent. An install that never stored an accent, and has already completed first run, gets Ink & Paper written explicitly before the default changes, so its look does not shift.
- **Glass settings.** The presets map Clear and Soft to Glass, and Solid stays Solid. The stored `GlassEffect` value is migrated as Soft → Strong.
- **Tab bar.** Solid in every mode.

Tests and screen-tour shots are updated in the same batch (batch 7, after the reliability work).

## C8 · "Always 24-hour" vs the device time format

**Sources**
- `LUMA_PROTECTED_BEHAVIORS.md` and `docs/reminder-contract.md`: user-facing times keep 24-hour behaviour.
- Code: `timeFormatMode` defaults to **Device**, with explicit 12h and 24h options (tested by `SettingsTimeFormatModeTest`).

**Resolution:**
- No behaviour change: the explicit setting is a later, tested feature.
- The docs are updated to say the default follows the phone and 24-hour can be forced.
- Notification timestamps use the system format, which is documented.
- Separately, care point "never cut a time" (L6) is honoured.

## C9 · Reminder contract statements that are false today

`docs/reminder-contract.md` makes three claims that do not match the code:

| Claim | What the code does | Fixed by |
| --- | --- | --- |
| "A time-zone change does not move them" | Repeats re-anchor to their chosen local time | Doc correction |
| "A long-missed repeat moves to the next future occurrence" | Only Done advances it | F04 fix makes it true |
| "Each reminder notifies once" (release checklist) | – | C4 rewording |

The documents are corrected together with the batch that changes behaviour.

---

## Open decisions

None of these block other work.

| ID | Decision needed | Why it is not already decided | Current handling |
| --- | --- | --- | --- |
| D1 | Update the permanent product invariant in `AGENTS.md` ("AI must not silently create… schedule…") to match the approved automatic-filing boundary in C1. | The repository's risk policy says `AGENTS.md` rules may only be rewritten with explicit approval. | Left unchanged. C1 is implemented as approved, and the other docs are aligned. |
| D2 | Distribution channel: Google Play, or private install. | Play restricts `USE_EXACT_ALARM` and direct battery-optimisation requests. Removing them makes alarms inexact on Android 14+ unless the user grants exact alarms. | Left declared. The release checklist and verifier gain an explicit Play-release item. |
