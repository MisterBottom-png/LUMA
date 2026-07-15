# LUMA Rules

## Product identity

LUMA is a calm local-first AI life inbox.

It helps the user capture messy life input, clarify it, remember it, and act on it later.

## Product laws

- Home is calm capture-first space.
- Home must not become a dashboard.
- LUMA should feel personal, quiet, and premium.
- LUMA should reduce cognitive load.
- LUMA should help with ambiguity through missing-information prompts.
- LUMA should avoid guilt, shame, hustle, and productivity-bro language.
- AI should feel optional, not bossy.

## AI laws

- AI may suggest.
- AI may summarize.
- AI may classify.
- AI may explain.
- AI may prepare drafts.
- AI must not silently perform important actions.
- AI must not silently create, edit, delete, archive, complete, schedule, or send important user data.
- User confirmation is required for important actions.
- Low-confidence AI output should ask the user or route to Inbox/Review.

## Engineering laws

- Preserve local-first behavior.
- Do not add backend/cloud/auth unless explicitly requested.
- Do not hardcode API keys.
- Do not introduce paid APIs unless explicitly requested.
- Do not change Room schema without migration review.
- Follow existing architecture unless there is a clear reason not to.
- Prefer small safe changes.
- Run validation after meaningful code changes.

## Home rules

Allowed on Home:

- greeting / identity
- compact date/week strip
- main capture input
- microphone / send actions
- bottom navigation
- Situation AI entry point
- light context-aware hints when appropriate

Avoid on Home unless explicitly approved:

- dense dashboards
- long task lists
- analytics cards
- productivity scores
- large calendar grids
- heavy settings controls
- noisy feeds

## Calendar/date rules

- Compact calendar/date awareness can live on Home.
- Detailed calendar interaction should live in a secondary full-screen or modal surface.
- Do not add Google Calendar sync unless explicitly requested.
- Do not add recurring event engine unless explicitly requested.
- Date/time behavior must respect local timezone and selected time format.

## Review/Situation AI rules

- Review should help the user resolve unclear or stale things calmly.
- Situation AI should explain what matters now and suggest next steps.
- Situation AI should not become a permanent dashboard.
- Suggestions should be helpful and dismissible.
- Missing-information prompts are preferred over guessing.
