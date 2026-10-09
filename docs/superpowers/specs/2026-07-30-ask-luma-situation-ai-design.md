# Ask LUMA and Situation AI design

## Goal

Add a real optional Gemini synthesis layer to Ask LUMA and Situation AI without weakening LUMA's local-first behavior, source grounding, privacy boundaries, or user control.

## Scope

Included:

- Source-linked Gemini synthesis for Ask LUMA and Situation AI.
- Local retrieval and compact context building for focused, weekly, and broader questions.
- Ephemeral in-sheet Ask LUMA conversation state.
- Foreground-only cloud requests, local fallbacks, origin labels, and source rows.

Excluded:

- Automatic mutations, action proposals, tool or function calling, notifications, and background Gemini calls.
- Persistence of cloud answers or Ask LUMA conversation history.
- Raw-capture inclusion unless local retrieval finds it directly relevant to the question.

## Architecture

`LocalAiRetriever` remains the sole selector of LUMA data. A new context builder receives retrieved items and produces either a focused source list or a compact current-week or broader-item summary. It excludes raw captures by default and includes them only when directly relevant.

A small source-linked synthesis interface is shared by Ask LUMA and Situation AI. It accepts the bounded context, returns structured text plus source IDs, and has no access to persistence, scheduling, notifications, or mutation use cases.

The app validates every response before display. Required fields must be present, cited IDs must exist in the supplied source set, and unsupported action intent is rejected. Failure, malformed output, missing connectivity, missing key, or safety blocking returns the local result.

## Situation AI

The existing local analyzer remains the baseline and refreshes whenever relevant local data changes. It is shown only in the Situation AI sheet, not on Home.

When the app is foregrounded and Gemini plus the Situation feature toggle are enabled, LUMA may request an improved source-linked explanation. Gemini returns `rightNow`, `whatMatters`, `stuck`, `nextTinyStep`, and source IDs. A valid cloud result replaces only the presentation layer; the local analysis remains the fallback.

The sheet displays a freshness timestamp, a concise origin label ("Updated with Gemini" or "Local summary"), and tappable source rows. It exposes no internal IDs, prompt text, provider diagnostics, or confidence scores.

## Ask LUMA

Ask LUMA maintains an in-memory conversation only while its sheet is open. The user question is handled by local retrieval first. Focused questions receive relevant items; broad questions receive a compact current-week or broader-items summary. Raw captures remain excluded unless directly relevant.

Gemini may synthesize a concise response grounded in those sources. If evidence is insufficient, it asks one focused follow-up instead of guessing. Each answer shows whether it uses only local items or Gemini synthesis and displays source rows. Closing the sheet discards the entire thread. Copying is allowed; saving requires a future explicit user action.

## Privacy and resilience

Cloud requests are allowed only while the application is in the foreground, Gemini mode and the applicable feature toggle are enabled, and a key is available. The context is bounded and source-linked. Local answers remain usable without Gemini, connectivity, or a valid provider response.

AI is advisory only. It cannot create, edit, complete, archive, delete, schedule, notify, or send data.

## Verification

- Unit tests cover context minimization, raw-capture exclusion and direct-relevance inclusion, citation validation, clarifying-question responses, and local fallback.
- ViewModel tests cover foreground-only cloud calls, stale-result rejection, thread clearing on sheet close, and Situation AI refresh after source changes.
- UI tests cover origin labels, source rows, local and offline behavior, and absence of AI action controls.
- Manual device verification covers focused and broad questions, a real Gemini request, offline fallback, and blocked or malformed provider responses.
