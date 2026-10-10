# AI settings appearance parity design

## Goal

Make the AI settings experience use the same menu-and-subsection structure as Appearance, without changing AI behavior, persistence, consent, or privacy boundaries.

## Scope

The existing AI form becomes an AI overview card with four navigable rows:

1. **AI mode**: local-only and Gemini mode selection, including the existing consent confirmation.
2. **Gemini setup**: API-key save/remove, connection test, result message, and the fast and reasoning model fields.
3. **AI features**: the existing per-feature Gemini toggles.
4. **Local learning**: local learning controls, learned-rule management, sharing control, and clear-learning confirmation.

Each row presents a concise live status. The overview and every subsection reuse the Appearance card, row, divider, title, back navigation, animation, and scrolling patterns.

## Behavior preservation

- Local-only selection continues to clear Gemini consent and feature toggles.
- Gemini selection continues to require the current consent version.
- Gemini-only feature controls remain enabled only when mode, consent, and key are valid.
- Key handling, connection testing, learned-rule editing, deletion, sharing, and clear-learning confirmation retain their current callbacks and state.
- The existing privacy explanation remains visible in the relevant AI settings flow.
- The AI overview remains under System settings; AI subsections retain System as their back-navigation parent.

## Architecture

Add AI subsection state alongside the existing Appearance and System subsection state. Selecting AI from System opens the AI overview; selecting an AI row opens its focused page. Back first returns from an AI page to the AI overview, then from the overview to System settings. The existing header and bottom-navigation visibility logic treats any AI subsection as an open settings subsection.

## Verification

Extend focused navigation coverage for the extra settings depth. Build and run the affected JVM tests. Manually check the four AI pages in light and dark appearance modes, with no key, a saved key, local-only mode, and Gemini mode.
