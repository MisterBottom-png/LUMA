# Capture Label Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show optional label suggestions during capture confirmation and create their item-label relationships only when the user confirms the finalized item.

**Architecture:** `CaptureAnalysis` carries a bounded, normalized label suggestion list from local or validated Gemini analysis. The confirmation sheet lets the user remove suggestions before saving. The finalization transaction creates or reuses labels and inserts their type-specific relationships in the same Room transaction as the finalized item and capture state update.

**Tech Stack:** Kotlin, Room, Jetpack Compose, ViewModel state, JUnit 4.

## Global Constraints

- AI and local rules may suggest labels only; they never create a label or relationship before confirmation.
- Unavailable, malformed, or low-confidence analysis produces no labels and keeps the item Unfiled when no active Space applies.
- Raw captures and internal records never receive item labels or appear in Spaces.
- Preserve reminder target time, notification offset, scheduling, and 24-hour behavior.
- Include label creation and finalized-item assignment in the existing Room finalization transaction.

---

### Task 1: Carry bounded suggestions through analysis

**Files:**
- Modify: `app/src/main/java/com/orbit/app/domain/analyzer/CaptureAnalyzer.kt`
- Modify: `app/src/main/java/com/orbit/app/integrations/gemini/GeminiJsonValidator.kt`
- Test: `app/src/test/java/com/orbit/app/domain/analyzer/LocalRulesCaptureAnalyzerTest.kt`
- Test: `app/src/test/java/com/orbit/app/integrations/gemini/GeminiJsonValidatorTest.kt`

- [ ] Add `suggestedLabels: List<String> = emptyList()` to `CaptureAnalysis` and normalize/deduplicate labels by trimmed collapsed whitespace and case-insensitive matching.
- [ ] Add tests that local topic suggestions become labels without copying the primary Space, and malformed or oversized Gemini labels are discarded.
- [ ] Extend the Gemini validator to accept an optional bounded `suggestedLabels` array while retaining the existing fallback behavior.
- [ ] Run the focused analyzer and validator tests.

### Task 2: Confirm and persist labels atomically

**Files:**
- Modify: `app/src/main/java/com/orbit/app/domain/usecase/ConfirmCaptureActionUseCase.kt`
- Modify: `app/src/main/java/com/orbit/app/OrbitApplication.kt`
- Test: `app/src/test/java/com/orbit/app/domain/usecase/ConfirmCaptureActionUseCaseTest.kt`
- Test: `app/src/androidTest/java/com/orbit/app/data/repository/LabelRoomRepositoryTest.kt`

- [ ] Write tests proving accepted note, task, and reminder labels are persisted only after their corresponding finalization method succeeds; a failure leaves no new relationship.
- [ ] Inject `LabelRepository` into the use case and add optional `labelNames: List<String>` parameters to the three finalized-item methods.
- [ ] Inside the existing transaction, find/create normalized labels and replace the just-created item’s type-specific relationships before marking its capture processed.
- [ ] Run focused use-case and Room tests.

### Task 3: Render editable label suggestions in confirmation

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt`
- Modify: English, Estonian, and Russian resource files used by the confirmation sheet.
- Test: `app/src/androidTest/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheetTest.kt`

- [ ] Add a label chip row only when analysis carries suggestions; each chip can be removed before confirmation.
- [ ] Pass the selected label names through the existing note/task/reminder callbacks; do not expose labels for Brain Dump or Inbox-only choices in this slice.
- [ ] Add focused Compose semantics coverage for visible suggestions and removal.
- [ ] Run unit tests, instrumentation assembly, debug lint, and the strict privacy check.
