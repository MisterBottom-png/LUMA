# Review Decision Session Design

Date: 2026-07-30

## Summary

Replace the repeated inline Open Loops and Carry Forward decisions with one calm, full-screen Review Session. The existing Review screen remains an overview and provides a single **Start review** entry card with the number of pending decisions.

The session presents one exception at a time, gives it one reason for appearing, and removes it from the current session after one deliberate decision. It must not introduce scoring, pressure, silent AI mutations, duplicate appearances, or false claims that the whole Review is clear.

## Goals

- Give each review-due item one clear decision point.
- Prevent the same item from appearing in both Carry Forward and Open Loops.
- Keep ordinary due-today items informational rather than forcing a decision.
- Make every session-owned mutation reversible through a single-level Undo where the underlying action can be restored safely.
- Preserve local-first operation, confirmation boundaries, reminder integrity, and protected Review behavior.
- Support large text, screen readers, reduced motion, and predictable Back behavior.

## Non-goals

- Adding AI behavior or moving Make Smaller into the Review Session.
- Adding task scores, streaks, productivity metrics, or celebratory pressure.
- Adding bulk decisions.
- Changing Room entities, database versions, migrations, export formats, or restore behavior.
- Redesigning item-detail, capture-confirmation, or Brain Dump flows.
- Providing persistent or multiple-level Undo history.

## User entry and overview

Morning and evening Review remain calm overview surfaces. Weekly Review, Waiting For, Someday, and other supporting sections remain available.

The existing inline Carry Forward decision cards and expandable Open Loops workflow are replaced by one **Start review** card. The card shows the number of currently eligible decisions and never opens the session automatically.

Ordinary due-today items remain in the overview. They do not enter the decision queue solely because they are due today.

The overview must not display a whole-screen completion label such as **Review clear** while eligible Review Session decisions remain. Any completion wording on the overview must be scoped to the section it describes.

## Session route and lifecycle

Start review opens a dedicated full-screen Review Session route.

The session snapshots the eligible typed item keys and their order when it starts. This provides stable progress such as **2 of 6** even if repositories emit updates during the session. New eligible items are not injected into an active session; they appear the next time a session starts.

Before showing a snapshotted item, the session rehydrates it from its repository:

- If it still exists and remains actionable, show it.
- If it was resolved or removed elsewhere, skip it without counting a decision.
- If its data changed but it remains actionable, show the current data.

Back from the focused item exits to the Review overview without changing undecided items. Already committed decisions remain committed. Back from an inline date step returns to the focused item instead of exiting the session.

When no snapshotted items remain, show a quiet completion state:

- **Review complete for today**
- A factual count of decisions made
- A **Done** button

Do not show scores, streaks, performance judgments, or exaggerated celebration.

## Queue eligibility, ownership, and order

The queue contains only exceptions requiring a deliberate decision:

1. Overdue reminders
2. Overdue tasks
3. Unfinished Brain Dumps
4. Unresolved captures
5. Stale tasks

Items are ordered by this category priority, then oldest first within each category.

Each typed item key may appear at most once. When an item qualifies for multiple categories, the earliest category owns it. For example, an overdue task that is also stale appears only as an overdue task.

Items suppressed through the current local day by **Not today** are excluded.

## Universal Not today behavior

Every focused item offers **Not today**.

Not today:

- Does not mutate the underlying task, reminder, capture, or Brain Dump.
- Removes the item from the current session.
- Suppresses it from newly started Review Sessions for the rest of the current local day.
- Makes it eligible again on the next local day if it still qualifies.
- Supports Undo during the current Undo window.

Suppression survives process death and reboot. Store typed item keys with their next eligible local date in a preferences-style local store rather than adding fields to Room entities. Expired entries are ignored and pruned.

## Item decision sets

### Overdue task

Visible top-level choices:

- **Not today**
- **Keep active**
- **Complete**

Keep active opens an inline date step:

- **Tomorrow**
- **Choose date**
- **Back**

Confirming a date keeps the task open and applies a date-only schedule. The picker disables past local dates, and the executor validates the selection again before writing. Cancelling the date picker or choosing Back leaves the item unchanged and does not advance the session.

### Overdue reminder

Visible top-level choices:

- **Not today**
- **Keep active**
- **Complete**

Keep active opens the same Tomorrow or Choose date step. The picker disables past local dates, and the executor validates the selection again before writing. Rescheduling preserves the reminder's existing local time and notification offset. Cancelling leaves it unchanged.

### Stale task

Visible choices:

- **Not today**
- **Keep active**
- **Complete**
- **More**

More contains:

- **Move to Someday**
- **Archive**

Keep active acknowledges the task so it leaves Review until it becomes stale again. This differs from Not today, which leaves the task untouched and guarantees only same-day suppression.

The implementation may continue using the existing task recency behavior if the stale-loop query treats that acknowledgement consistently. It must not require a Room schema change for this feature.

### Unresolved capture

Visible choices:

- **Not today**
- **Review capture**
- **Dismiss**
- **More**

More contains **Archive source**.

Review capture opens the existing clarification and confirmation flow. Returning to the Review Session:

- Advances and counts a decision if the capture was resolved.
- Shows the same card if the flow was cancelled and the capture remains unresolved.
- Skips without counting if the capture was removed elsewhere.

Dismiss creates no finalized item. Archive source remains distinct from Dismiss in both wording and behavior.

### Unfinished Brain Dump

Visible choices:

- **Not today**
- **Resume Brain Dump**
- **Dismiss**
- **More**

More contains **Archive source**.

Resume uses the existing interruption/resume pathway and preserves exactly-once handling of already accepted suggestions. Return behavior matches Review capture: advance only when the source is resolved.

## Focused screen presentation

The screen uses the existing LUMA shell, design tokens, and calm visual language.

Top area:

- Back
- **Review**
- Subtle progress, such as **2 of 6**

Focused card:

- Item type and user-facing reason, such as **Overdue reminder**
- Full item title
- Relevant timing or context
- **View details** when the item has a detail destination
- One visually primary contextual action
- Quieter visible alternatives
- More only where specified

For overdue and stale tasks, Keep active is primary. For captures and Brain Dumps, Review capture or Resume Brain Dump is primary. Not today remains easy to find but visually quiet. Complete, Dismiss, Someday, and Archive use explicit text labels rather than relying on icons.

Make Smaller is not shown in the Review Session. It remains available in task details.

## Accessibility and motion

- Treat the focused card as one named semantic group.
- Announce its reason and queue position.
- Place focus on the new card's title or heading after a successful decision.
- Announce Undo availability without unexpectedly stealing focus.
- Preserve a logical order from context to primary action to alternatives.
- Allow large text to turn horizontal actions into a vertical stack.
- Do not clip titles, reasons, action labels, or progress.
- Distinguish Dismiss from Archive in screen-reader labels.
- Keep practical touch targets for every action.
- Replace card transitions with immediate state changes when reduced motion is enabled.
- Back from the date step returns to the item; Back from the item exits the session.

## Components and responsibilities

### ReviewQueueBuilder

A pure component that:

- Accepts current tasks, reminders, captures, Brain Dump state, settings, local time, and same-day suppression.
- Produces typed review candidates with one owning reason and category priority.
- Excludes ordinary due-today items and suppressed keys.
- Deduplicates by typed item key.
- Sorts by category priority and age.

### ReviewSessionViewModel

Owns:

- The snapshotted candidate keys and initial total.
- Current position and current rehydrated candidate.
- Date-step state.
- Action-in-progress state.
- Successful decision count.
- The latest Undo token.
- Completion state.

It advances only after successful persistence or an intentional Not today decision.

### ReviewSuppressionStore

Persists typed item keys with their next eligible local date. It:

- Survives process death and reboot.
- Uses local-day boundaries.
- Removes or ignores expired entries.
- Supports clearing one suppression during Undo.
- Does not mutate the underlying item.

### ReviewDecisionExecutor

Maps typed session decisions onto existing domain actions. It:

- Reuses existing scheduling, completion, archive, capture, and Brain Dump pathways.
- Preserves confirmation and exactly-once behavior.
- Returns an Undo token with the previous state required to reverse the latest committed decision.
- Does not duplicate repository business rules inside Composables.

## Mutation, Undo, and errors

Actions commit immediately. While an action is running, all decision controls for the current card are disabled to prevent repeated taps.

Advance only after the action succeeds. On failure:

- Keep the same item visible.
- Restore enabled controls.
- Show calm, specific feedback.
- Offer Retry when retrying is safe.
- Do not optimistically remove the item.

Only the latest committed decision is undoable. Undo:

- Restores the stored item state when a mutation occurred.
- Clears associated Not today suppression when applicable.
- Returns to the restored item.
- Adjusts position and decision count.

This session-level Undo applies to decisions executed by the Review Session. Review capture and Resume Brain Dump navigate into existing confirmation flows; returning from a successfully resolved flow advances the session but does not create a second Review Session Undo for changes already governed by that flow.

An immediate session mutation must not be offered unless the executor can produce a valid Undo token. If an existing domain pathway cannot be reversed safely, require explicit confirmation before committing it, state that it cannot be undone, and preserve all existing confirmation and exactly-once guarantees.

The Undo window does not survive app closure. Committed mutations and persisted Not today suppression do survive.

## Protected behavior

The design must preserve:

- Brain Dump resume and exactly-once acceptance.
- Waiting For and Someday classification and visibility.
- Make Smaller in task details.
- Reminder target time, local time, and notification offset separation.
- 24-hour behavior.
- User confirmation for important AI-proposed mutations.
- Local-first operation.
- Finalized-item visibility boundaries.
- User-facing language without internal processing labels.

## Testing and verification

### Queue unit tests

- Category priority and oldest-first ordering.
- Deduplication when a task is both overdue and stale.
- Exclusion of ordinary due-today items.
- Exclusion and expiry of same-day suppression.
- Next-local-day eligibility.
- Stable typed keys across item types.

### Session ViewModel tests

- Stable snapshot and progress.
- Repository updates do not inject new candidates.
- Resolved or deleted candidates are skipped without counting.
- Not today persists and advances.
- Keep active, Complete, Someday, Dismiss, and Archive transitions.
- Inline date step, cancellation, and Back.
- Successful navigation return versus cancelled return.
- Repeated-tap protection.
- Failure retains the current card.
- Single-level Undo restores state, position, suppression, and count.
- Quiet completion state.

### Domain and integration tests

- Reminder rescheduling preserves local time and notification offset.
- Task rescheduling uses date-only scheduling.
- Past-date selections are rejected.
- Capture decisions create no duplicate final item.
- Brain Dump resume preserves exactly-once behavior.
- Session-owned Dismiss, Archive, completion, Someday, reschedule, and Keep active Undo restore the previous state.
- Same-day suppression survives process recreation.

### Compose and manual checks

- Every item type and action state.
- Narrow width and large text.
- Screen-reader group label, action order, progress, and focus movement.
- Reduced-motion behavior.
- Date picker cancellation.
- Back from date step and session.
- Undo announcement and restoration.
- Light, dark, Auto, preset, and custom-background readability.

Run focused JVM tests during development, then the relevant debug build, lint, strict workplace-privacy scan, and a manual assistive-technology traversal before completion.

## Acceptance criteria

- Start review opens a dedicated full-screen session.
- Each eligible item appears no more than once per session.
- Ordinary due-today items do not enter the queue.
- Every card offers Not today.
- Same-day suppression survives restart and expires on the next local day.
- Overdue Keep active requires Tomorrow or a confirmed chosen date.
- Overdue rescheduling cannot select a past local date.
- Stale Keep active removes the item until it becomes stale again.
- Mutations advance only after successful persistence.
- The latest session-owned decision can be undone or, when safe reversal is impossible, is explicitly confirmed before commitment.
- Make Smaller remains available outside the session.
- Completion wording is calm, scoped, and accurate.
- Protected Review, reminder, capture, Brain Dump, and privacy behavior remains intact.
