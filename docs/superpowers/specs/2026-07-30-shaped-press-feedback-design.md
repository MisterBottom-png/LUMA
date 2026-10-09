# Shaped press feedback

## Goal

Make visual press feedback conform to the rounded or circular control being pressed, without changing the control's layout, navigation callback, accessibility role, or touch target.

## Scope

The patch covers the Settings category rows, Home week-strip day controls, and shared bottom-navigation controls shown to use a full rectangular interaction indication.

## Design

Each existing clickable or selectable keeps its current interaction source and hit bounds. Its ripple/pressed indication is rendered in a shape-clipped visual layer using the same source, so the indication follows the established rounded or circular control shape. Grouped Settings rows retain their shared outer-card appearance and divider layout.

## Non-goals

No theme, typography, navigation, data, or input-area changes. No control loses its current minimum touch target.

## Verification

Add a focused regression test where the Compose test infrastructure can observe the intended interaction structure; run the relevant unit or instrumentation task, compile the app, inspect the final diff, and manually verify press feedback for the three affected surfaces.
