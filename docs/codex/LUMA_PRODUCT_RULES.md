# LUMA product rules

## Identity

LUMA is a calm, private, local-first AI life inbox. It helps the user capture messy life input, clarify it, remember it, and act later.

## Home

Home is capture-first and minimal. It may contain identity/greeting, compact date awareness, the main input, primary actions, navigation, and a light Situation AI entry point.

Do not turn Home into a dense dashboard, analytics view, productivity score, large calendar, or long task feed.

## Raw input and final items

- Raw thoughts are private source material.
- AI processing records remain internal.
- Spaces and Life Feed show clean final user-facing items.
- Do not show the original raw capture as a separate Space card.
- Do not show `Processed` as a user-facing category or card.
- One capture should not produce duplicate visible items.

Final user-facing types may include tasks, reminders, notes, ideas, someday items, waiting-for items, saved items, and finalized thoughts.

## AI trust

AI may suggest, classify, summarize, explain, and draft.

AI must not silently create, edit, delete, archive, complete, schedule, or send important user data. Important proposed actions require confirmation. Low-confidence output should ask for missing information or route to Review rather than inventing details.

AI suggestions should be dismissible and should explain their source or reason when practical.

## Review

Review helps resolve unclear, stale, or incomplete items calmly.

Preferred sections:

1. Needs your attention
2. Suggested changes
3. Missing information
4. Completed items, only when useful

Use user-facing wording. Avoid internal labels such as `Processed`, `AI result`, or states that imply the whole review is complete when only an item is complete.

## Situation AI

Situation AI answers what deserves attention now and why. It should be grounded in local LUMA items, link to source items when practical, and remain actionable but not pushy.

It must not become a permanent control-room dashboard.

## Ask LUMA

Ask LUMA is a user-facing assistant, not a debug console. It may use local LUMA context but must not mutate data without confirmation.

## Tone

Avoid guilt, shame, hustle, judgment, medical diagnosis, or productivity-bro language. The app should protect attention and reduce cognitive load.

## Local-first boundary

Core capture, storage, review, and retrieval remain useful without Gemini, accounts, cloud sync, or external services.
