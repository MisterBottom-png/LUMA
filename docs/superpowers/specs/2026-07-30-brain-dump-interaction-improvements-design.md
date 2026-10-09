# Brain Dump Interaction Improvements Design

Date: 2026-07-30

## Purpose

Reduce Brain Dump decision density while preserving LUMA's calm, local-first confirmation model and its durable, exactly-once handling of multi-item captures.

The improved experience uses progressive disclosure: the default card shows enough information to make a trustworthy decision, common actions remain immediately available, and uncommon or destructive actions move behind a More menu.

## Goals

- Show the proposed item type, destination Space, and applicable timing before confirmation.
- Keep the one-suggestion-at-a-time interaction model.
- Reduce competing actions on the default card.
- Support Undo for an accidentally skipped thought.
- Advance immediately after successful actions while resetting scroll and accessibility focus.
- Keep failures and reminder warnings visible inside the sheet.
- Make Back, swipe-down, and scrim dismissal behave consistently.
- End a completed session with a calm, non-editable summary.
- Preserve current Room persistence, resume navigation, confirmation, and exactly-once creation guarantees.

## Non-goals

- Do not stage all decisions for a final bulk commit.
- Do not add editable history for already-created items.
- Do not add Undo for note, task, reminder, or Inbox creation.
- Do not change Brain Dump detection, AI routing, Room schema, export format, reminder scheduling, or finalized-item visibility.
- Do not redesign Home, Review, item detail, or the shared modal system beyond the integration needed for this flow.

## Protected behavior

- Raw capture is saved locally before analysis.
- Raw source material and internal processing records remain hidden from normal user-facing collections.
- Every non-blank source line remains represented exactly once and in source order.
- Important AI-proposed item creation remains user-confirmed.
- A failed database write leaves the current item pending.
- Note, task, reminder, Inbox, and skip outcomes remain exactly-once.
- Pending sessions survive ordinary sheet dismissal, navigation, supported recreation, export, and restore.
- Home, Review, and capture detail retain Resume Brain Dump navigation.
- Task timing remains optional.
- Reminder target time remains required and separate from notification delivery.
- Configured 24-hour behavior remains intact.
- Discarding remaining suggestions preserves items already created.

## Core interaction

### Default suggestion card

The default card contains:

1. Screen title: `Sort your thoughts`
2. Progress heading: `Suggestion N of M`
3. Suggested title
4. Compact confirmation metadata:
   - item type;
   - destination Space;
   - applicable date and time.
5. Collapsed `Why this?` disclosure
6. Primary action
7. `Edit details`
8. `Finish later`
9. More menu

Example:

```text
Sort your thoughts                         More
Suggestion 2 of 6

Suggested title

Task / Personal
Tomorrow, 15:00

Why this?

[ Set up task ]
[ Edit details ]
  Finish later
```

The primary action label reflects the proposed type:

- Note: `Save note`
- Task: `Set up task`
- Reminder: `Set up reminder`

The metadata must remain visible before the primary action. Destination Space must never be confirmed only through hidden state.

### Why this disclosure

The collapsed default keeps the card compact. Expanding `Why this?` shows:

- the raw source line;
- the suggestion explanation;
- the small-next-action guidance when available.

The disclosure must not expose internal processing labels or provider-debug information.

### More menu

The More menu contains:

1. `Keep this thought in Inbox`
2. `Skip this thought`
3. Divider
4. `Discard remaining suggestions`

`Discard remaining suggestions` retains error/destructive styling and requires the existing explicit confirmation. Its confirmation explains that the original multi-line source is archived, remaining suggestions are discarded, and previously created items remain.

### Finish later

`Finish later` persists no new outcome for the current item. It flushes any pending skip from the preceding item, then closes the sheet and leaves the current item pending and resumable.

Ordinary root-level sheet dismissal has the same result as `Finish later`, including flushing a pending skip before closing.

## Editing and setup

### Edit details

`Edit details` opens a nested editor in the same modal flow. It contains:

- editable title;
- Note, Task, and Reminder type choices;
- one compact Space selection row showing the current destination;
- schedule summary when applicable;
- raw source and suggestion explanation.

Type controls expose a selected semantic state. The Space row opens a separate selection surface rather than displaying every Space simultaneously.

### Type-specific continuation

- Note saves directly from the editor.
- Task continues to optional scheduling.
- Reminder continues to required date-and-time selection.

Draft title and Space carry into type-specific setup without re-entry.

The Reminder confirmation action stays disabled until both a non-blank title and valid target time exist. The interface must explain the missing time through visible field state, not only a disabled button.

### Draft state

The coordinator tracks:

- initial title, type, Space, and schedule;
- current draft values;
- whether the draft differs from its initial values.

Database failures preserve the current draft. Confirmed discard of edits restores the initial values for the current suggestion.

## Dismissal and Back behavior

Hardware Back, swipe-down, and scrim-tap follow the same state rules:

- At the root suggestion card, save progress and close.
- In a pristine nested editor or setup state, return one level.
- In a modified nested state, ask whether to discard the current edits before returning one level.
- While a committed action is in progress, disable dismissal.

The discard-edits confirmation applies only to the current draft. It does not delete the durable Brain Dump session or any previously created item.

## Action progression

### Immediate committed actions

The following actions use the existing transactional action boundary and advance after success:

- Save Note
- Create Task
- Create Reminder
- Keep this thought in Inbox

After success:

1. Update completion counts.
2. Load the next pending item.
3. Reset modal scroll to the top.
4. Move accessibility focus to `Suggestion N of M`.
5. Show a brief inline success status.

Already-created items are not editable from the Brain Dump flow. They remain available through their normal detail surfaces.

### Skip with Undo

Skip is the only reversible Brain Dump outcome.

When the user selects `Skip this thought`:

1. Advance optimistically to the next pending item.
2. Show `Skipped this thought / Undo` in the inline status area.
3. Keep the skipped source key in a transient pending-skip state.

The pending skip is committed when:

- the Undo window expires;
- another item action begins; or
- the user closes or dismisses the session from its root state.

Undo returns to the skipped item and clears the transient skip state.

If the process stops before the transient skip is committed, the item remains pending and may be presented again. Re-presenting the thought is safer than silently losing it.

Only one pending skip exists at a time. Beginning another action flushes the existing pending skip before applying the new action.

### Final-item skip

If the final pending item is skipped, show the completion summary with the Undo affordance still active.

- Undo returns to the final item.
- Undo-window expiry commits the skip and finalizes the session.
- Selecting Close commits the pending skip before closing.

## Completion summary

After all items are handled, show a non-editable summary:

```text
Thoughts sorted

4 saved
1 kept in Inbox
1 skipped

[ Close ]
```

Counts include:

- finalized notes, tasks, and reminders under `saved`;
- fragment captures under `kept in Inbox`;
- committed skipped items under `skipped`.

The summary does not list titles or raw source text and does not offer editing. This keeps completion calm and avoids turning Brain Dump into a session dashboard.

## Inline status and error handling

A status area remains inside the modal and near the progress heading.

### Success

Show a brief non-blocking confirmation after a committed item action. No extra acknowledgement is required.

### Pending skip

Show the skip confirmation and Undo action until the transient skip is committed or undone.

### Database or transactional failure

- Keep the current item visible.
- Preserve the draft.
- Show a pinned error message inside the sheet.
- Offer Retry for the failed action.
- Do not advance or alter completion counts.

### Reminder notification warning

If the reminder is created but device notification scheduling is unavailable:

- treat item creation as successful;
- advance to the next item;
- show a persistent warning inside the sheet;
- do not retry item creation;
- explain that the reminder exists but notification settings require attention.

### Missing session

If the pending session or source item no longer exists:

- close the sheet;
- clear the resume request;
- show a user-facing message that the Brain Dump is no longer available.

## State and component boundaries

Brain Dump uses an explicit UI state machine:

```text
Suggestion
  -> Edit
  -> Task setup
  -> Reminder setup
  -> Completion
```

A dedicated Brain Dump coordinator owns:

- current item and progress;
- initial and current draft values;
- nested UI state;
- dirty-draft detection;
- pending skip and Undo timing;
- inline status;
- retry intent;
- completion counts;
- dismissal decisions.

Room remains the durable source for sessions and committed outcomes. The coordinator delays only the skip outcome long enough to support Undo. It must use the existing action boundary when flushing the skip.

The general single-capture confirmation flow remains separate.

Suggested focused composables:

- `BrainDumpSuggestionCard`
- `BrainDumpMetadataSummary`
- `BrainDumpEditor`
- `BrainDumpScheduleSetup`
- `BrainDumpActionMenu`
- `BrainDumpInlineStatus`
- `BrainDumpCompletionSummary`

These components may be grouped in one Brain Dump-specific file initially. The design does not require unrelated refactoring.

## Accessibility

- Treat `Suggestion N of M` as the current item heading.
- Reset scroll and move accessibility focus to that heading after item changes.
- Announce progress once per item transition.
- Use a polite live region for inline success, Undo, warning, and failure status.
- Expose type control role, label, enabled state, and selected state.
- Expose the Space row purpose and selected value.
- Give More and destructive actions unambiguous localized labels.
- Do not rely on color alone for selected, warning, error, or destructive state.
- Preserve logical focus order from progress and content through primary, secondary, session, and overflow actions.
- Keep touch targets at least the app's standard accessible size.
- Keep all controls reachable with large text, the IME, and system bars present.
- Respect reduced-motion settings when replacing items or opening nested states.

## Appearance and localization

- Reuse the existing modal surface, design tokens, and contrast roles.
- Support Light, Dark, and Auto themes.
- Support preset and custom backgrounds without hiding the selected background or weakening text contrast.
- Externalize all visible and semantic strings.
- Provide English, Estonian, and Russian variants in the same change.
- Preserve calm, non-judgmental language.

## Testing

### State and calculation tests

- Metadata summary includes type, Space, and applicable timing.
- Draft dirty-state detection covers title, type, Space, and schedule.
- Root dismissal closes while nested dismissal steps back.
- Dirty nested dismissal requests confirmation.
- Pristine nested dismissal does not request confirmation.
- Item transitions reset the requested scroll/focus target.
- Completion counts classify saved, Inbox, and skipped outcomes correctly.

### Skip Undo tests

- Skip advances optimistically without immediately committing.
- Undo restores the skipped item.
- Undo-window expiry commits exactly once.
- Starting another action flushes the pending skip before applying the next action.
- Finish later and Close flush a pending skip.
- Process loss before flush leaves the item pending.
- Final-item skip keeps Undo available on the completion summary.

### Action and error tests

- Note, task, reminder, and Inbox actions remain exactly-once.
- Failed creation preserves the pending item and draft.
- Retry applies only the intended action.
- Reminder creation with notification-scheduling failure advances once and shows the correct warning.
- Missing session closes cleanly.

### Compose interaction tests

- Default card exposes title, type, Space, and applicable timing.
- More contains Keep in Inbox, Skip, and Discard remaining.
- Discard remaining requires confirmation.
- Type controls expose selected semantics.
- Space selection exposes its current value.
- New items reset scroll and accessibility focus.
- Inline status is announced through a live region.
- Large font and IME states keep actions reachable.
- Back, swipe dismissal, and scrim dismissal follow the same nested-state policy where the test framework permits.

### Regression and device checks

- Multi-item interruption and resume from Home, Review, and capture detail.
- Rotation and process recreation.
- Exactly-once actions under repeated taps.
- Reminder scheduling and permission behavior.
- Calendar-context note and task scheduling.
- Export and restore of pending sessions.
- English, Estonian, and Russian copy.
- Light, Dark, Auto, preset, and custom backgrounds.
- Screen-reader traversal, selected-state announcements, focus movement, and Undo.
- Raw/internal capture visibility remains unchanged.

## Success criteria

The design is successful when:

- a user can confirm the proposed type, Space, and timing without opening Edit;
- the default card exposes only the primary action, Edit details, Finish later, and More;
- an accidental Skip can be undone;
- item changes always begin at the top and are announced accessibly;
- errors remain visible and retryable inside the modal;
- ordinary dismissal never deletes pending progress;
- nested dismissal never silently loses modified fields;
- the final summary communicates completion without exposing raw source text;
- existing persistence, resume, exactly-once creation, 24-hour time, and finalized-only visibility behavior remain protected.
