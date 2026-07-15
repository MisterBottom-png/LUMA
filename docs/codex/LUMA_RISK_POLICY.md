# LUMA Risk Policy

Use this when deciding whether to act autonomously.

## Low risk

Examples:

- visual alignment
- spacing
- text style
- dark/light color fix
- small composable layout adjustment
- simple wording change

Allowed behavior:

```text
Fix directly.
```

## Medium risk

Examples:

- new UI component
- new screen
- navigation route
- settings option
- local sorting/grouping
- UI state change

Allowed behavior:

```text
State assumptions, implement carefully, run validation, regression check.
```

## High risk

Examples:

- Room schema change
- migrations
- reminder scheduler
- notifications
- date/time parser
- AI analyzer schema/prompt
- Gemini integration
- user data mutation
- backend/cloud/account/API integration
- destructive actions

Allowed behavior:

```text
Ask questions, plan first, wait for approval when needed.
```

## Hard approval required

Never do these without explicit approval:

- delete or wipe user data
- add backend/cloud/auth
- add Google Calendar sync
- add paid services
- store API keys insecurely
- perform destructive database migration
- let AI silently create/edit/delete/complete/schedule/send important data
- rewrite permanent rules in AGENTS.md

## AI behavior safety

AI can suggest.

AI cannot silently act.

If AI output is low-confidence:

```text
Ask user for missing information or route item to Review/Inbox.
```

If AI suggests an important action:

```text
Show confirmation before saving/scheduling/sending/changing anything.
```
