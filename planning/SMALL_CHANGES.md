# LUMA UI/UX Small Change Steps

These are intentionally tiny implementation tasks that can be completed one by one. Based on the UI/UX Improvement Plan v1.2.

## Phase 1 - Trust & Interaction (P0)

- [x] Remove all internal AI IDs from the UI (`task:5`, `capture:26`).
- [x] Create reusable Source Row component.
- [x] Replace FilterChips with Source Rows.
- [x] Hide non-functional microphone on Home.
- [x] Hide unavailable Monday integration actions.
- [x] Remove every dead/non-working control.
- [x] Add fixed Situation AI header.
- [x] Make Situation AI body independently scrollable.
- [x] Pin Ask LUMA composer above keyboard.
- [x] Fix Android Back behavior for Situation AI.
- [x] Prevent keyboard from covering content.
- [x] Replace Ask button with embedded send icon.
- [x] Add loading state to Ask composer.
- [x] Support IME Send action.
- [ ] Clear stale AI responses after question changes.
- [ ] Separate Review modes from Open Loops workflow.
- [ ] Make Review items tappable.
- [ ] Open correct item when tapped.
- [ ] Add carry-forward actions.
- [ ] Add selected navigation indicators.
- [ ] Label bottom navigation tabs.
- [ ] Ensure every touch target is at least 48dp.
- [ ] Add accessibility semantics.
- [ ] Create contrast regression test baseline.
- [ ] Add AI trust states (Fact / Inference / Suggestion / No Action / Insufficient Context).
- [ ] Require confirmation before AI edits data.

## Phase 2 - Decision Clarity

- [ ] Add Suggested Next Move card.
- [ ] Limit Situation AI to one primary action.
- [ ] Move secondary actions into overflow.
- [ ] Show generated result directly below triggering action.
- [ ] Remove duplicated Review headings.
- [ ] Simplify Open Loop actions.
- [ ] Reorganize Review sections.
- [ ] Build Weekly Review synthesis.
- [ ] Reuse Source Rows in Weekly Review.

## Phase 3 - Home & Spaces

- [ ] Add current month label above weekday strip.
- [ ] Move weekday strip slightly downward.
- [ ] Update month while scrolling weeks.
- [ ] Remove hardcoded profile fallback name.
- [ ] Rename Life Feed.
- [ ] Improve Space row actions.
- [ ] Make Create Space primary.
- [ ] Make Search secondary.
- [ ] Make AI button follow theme colors.
- [ ] Introduce shared spacing tokens.
- [ ] Introduce shared corner radius tokens.

## Phase 4 - QA

- [ ] Test all supported screen sizes.
- [ ] Test light & dark mode.
- [ ] Test every accent theme.
- [ ] Test custom backgrounds.
- [ ] Test 200% text scaling.
- [ ] Test screen reader.
- [ ] Test grayscale.
- [ ] Test keyboard navigation.
- [ ] Test empty, normal and stress data.
- [ ] Verify no AI action mutates data without confirmation.
