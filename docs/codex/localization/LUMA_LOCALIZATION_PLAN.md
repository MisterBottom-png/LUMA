# LUMA localization plan

This is the canonical implementation and verification plan for LUMA language support. Use it to resume localization work without repeating the repository audit.

## Status

```text
Updated: 2026-07-26
State: IN PROGRESS
Fallback language: English
Declared application languages: English, Estonian, Russian
Per-app language selector: implemented
Externalized resource parity: complete for the current resource set
Full visible-copy localization: incomplete
Localized local analysis: partially implemented
Device and linguistic acceptance: pending
```

Localization is not a confirmed release blocker for the implemented initial-MVP contract, but the language-switching backlog item is not complete.

## Goal

The selected application language consistently controls LUMA's user-facing interface, accessibility text, locally generated guidance, background notifications, and formatting while preserving user-authored content and stable internal data.

English remains the fallback. Estonian and Russian must provide complete, calm, natural user-facing experiences rather than a translated Settings shell around English workflows.

## Included

- Android per-app language selection and persistence.
- Compose copy, dialogs, empty states, menus, validation, snackbars, and accessibility descriptions.
- Plurals, interpolation, dates, weekdays, month names, numbers, and protected 24-hour time behavior.
- Local capture, reminder, Review, and Situation analysis output.
- Gemini-assisted user-facing output and local fallback behavior.
- Reminder notification channel and notification presentation.
- Linguistic, layout, accessibility, restart, and offline verification.
- Documentation and tests that prevent resource or behavior regressions.

## Excluded

- Translating user-authored captures, titles, notes, tasks, reminders, or custom Space names.
- Translating stored records during migration, export, restore, or ordinary app startup.
- Changing Room schema, export format, or restore behavior.
- Translating stable enum literals, JSON keys, source IDs, route names, database keys, or other protocol identifiers.
- Adding languages beyond English, Estonian, and Russian without a separate product decision.
- Adding a translation service, cloud localization backend, or production dependency.

## Product and engineering boundaries

- Home remains calm and capture-first.
- Raw captures and internal AI processing remain private source material.
- AI suggestions remain suggestions and require confirmation for important mutations.
- Local-first and offline fallback behavior must remain available in every supported language.
- Event time, reminder target time, and notification offset remain separate.
- Preserve 24-hour display and parsing behavior unless the product requirement explicitly changes.
- Preserve categorized Settings, centered bottom navigation, centered Home input, Search, Undo, Brain Dump, Ask LUMA, Waiting For, Someday, and Make Smaller.
- Translate presentation, not persistence. Stable identifiers and user data must not be rewritten to match the current locale.
- Avoid concatenating translated sentence fragments. Use complete resource strings with positional placeholders or plurals.

## Current implementation evidence

### Locale selection

- `app/src/main/res/xml/locales_config.xml` declares `en`, `et`, and `ru`.
- `app/src/main/java/com/orbit/app/ui/localization/AppLanguage.kt` models system default, English, Estonian, and Russian.
- `app/src/main/java/com/orbit/app/MainActivity.kt` reads and applies AppCompat application locales.
- `app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt` exposes the language selector.
- `app/src/test/java/com/orbit/app/ui/localization/AppLanguageTest.kt` covers empty, regional, ordered, and unsupported language tags.

### Resource coverage

The current `values/strings*.xml` files contain 304 entries marked translatable. Both `values-et` and `values-ru` contain all 304 keys, with no missing translatable keys.

This proves key parity only. It does not prove that:

- all visible copy has been externalized;
- translations are natural or contextually correct;
- placeholders and plurals work at runtime;
- text fits at supported font scales;
- background components use the selected locale.

Current string groups:

```text
app/src/main/res/values/strings.xml
app/src/main/res/values/strings_core_ui.xml
app/src/main/res/values/strings_ai_reminders.xml
app/src/main/res/values/strings_release.xml
```

Estonian and Russian counterparts exist under `values-et` and `values-ru`.

### Local capture and reminder language behavior

- `CaptureAnalyzer.kt` contains English, Estonian, and Russian rule packs.
- `ReminderTimeInterpreter.kt` contains English, Estonian, and Russian date, reminder-intent, and time-context rules.
- Focused tests cover Estonian and Russian capture classification and reminder interpretation.
- Local analyzer presentation values still include English labels, reasons, chips, fallback names, and next-action sentences.
- `OrbitContainer` currently constructs `LocalRulesCaptureAnalyzer()` without explicitly injecting the AppCompat-selected application locale. The analyzer defaults to `Locale.getDefault()`. This must be made explicit or proven correct across live switching, process recreation, and background execution.

### Gemini language behavior

- `GeminiPromptBuilders.kt` instructs Gemini to keep user-facing output in the source language.
- Brain Dump prompts account for mixed-language fragments.
- `SourceLinkedGemini.kt` applies the same source-language rule to Ask LUMA and source-linked summaries.
- JSON keys and required enum literals intentionally remain stable English protocol values.
- Local fallbacks and local Situation/Review output still need full localization, so optional Gemini availability currently changes language completeness.

### Notifications

- Reminder channel name, description, fallback title, and open-LUMA text are resource-backed.
- Runtime verification is still required after language changes, process death, package replacement, and reboot.
- Existing notification channels may retain operating-system-managed state. Verification must distinguish channel metadata behavior from notification content behavior.

### Focused automated evidence

Run on 2026-07-26 with the Android Studio bundled JDK:

```text
.\gradlew.bat --no-daemon :app:testDebugUnitTest \
  --tests "com.orbit.app.ui.localization.AppLanguageTest" \
  --tests "com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzerTest" \
  --tests "com.orbit.app.domain.analyzer.ReminderTimeInterpreterTest"
```

Result: PASS.

The shell default Java was Java 8 and could not configure the current Android build. Set `JAVA_HOME` to a Java 17-or-newer runtime, such as the Android Studio bundled JDK, before running Gradle.

## Known unfinished surfaces

The following files contain the largest confirmed concentrations of user-facing string literals. The counts below are heuristic candidate-line counts, not an exact string inventory:

| Surface | File | Approximate candidate lines | Examples of unfinished presentation |
|---|---|---:|---|
| Capture and Brain Dump confirmation | `ui/screens/home/CaptureSuggestionSheet.kt` | 29 | actions, explanation labels, progress, task/reminder editors, empty and paused states |
| Spaces and Life Feed | `ui/screens/spaces/SpacesScreen.kt` | 29 | headings, menus, counts, archive/hidden states, empty states, dialog copy, accessibility labels |
| Note/task item detail | `ui/screens/item/ItemDetailScreen.kt` | 25 | field labels, actions, dialogs, status, validation, accessibility descriptions |
| Reminder detail | `ui/screens/review/ReminderDetailScreen.kt` | 15 | editor labels, notification controls, actions, validation, navigation descriptions |

Other confirmed or likely English presentation sources:

- `domain/analyzer/CaptureAnalyzer.kt`
- `domain/analyzer/LocalReviewAnalyzer.kt`
- `domain/analyzer/SituationAnalyzer.kt`
- `domain/ai/SourceLinkedAi.kt`
- `domain/usecase/ConfirmCaptureActionUseCase.kt`
- `ui/screens/item/ItemDetailViewModel.kt`
- `ui/screens/review/ReviewViewModel.kt`
- `ui/screens/settings/LocalDataToolsViewModel.kt`

Not every string literal is user-facing. Animation/debug labels, route names, JSON keys, database keys, exception diagnostics, test fixtures, and protocol values must be classified before conversion.

## Implementation plan

### Phase 1: Complete the presentation resource inventory

1. Search all production Kotlin and XML for visible string literals.
2. Classify each result as:
   - visible UI copy;
   - accessibility copy;
   - background notification copy;
   - domain-generated user-facing copy;
   - diagnostic/internal-only text;
   - stable identifier or protocol value.
3. Externalize visible and accessibility copy into the existing resource groups.
4. Use complete strings and placeholders instead of word-by-word composition.
5. Use Android plurals for all quantities, especially item, day, capture, task, reminder, and source counts.
6. Add Estonian and Russian entries in the same change as each new English key.
7. Add an automated resource-parity check so a new English translatable key cannot silently omit either locale.

Recommended UI order:

1. Capture suggestion and Brain Dump confirmation.
2. Item detail and reminder detail.
3. Spaces and Life Feed.
4. Remaining dialogs, snackbars, validation, empty states, and accessibility descriptions.
5. Secondary and preview-only UI after user-reachable production surfaces.

### Phase 2: Separate domain meaning from localized presentation

Domain and persistence layers should not depend directly on Compose `stringResource`.

For enums and bounded states:

- expose stable semantic values;
- map them to resource IDs or localized presentation models at the UI boundary.

For generated sentences:

- prefer structured domain results containing values such as type, count, date, source title, and reason code;
- format complete localized sentences in an Android-aware presentation layer;
- avoid persisting the localized sentence when the underlying semantic value can be persisted instead.

Apply this to:

- capture source, confidence, life-signal, type reason, Space reason, chips, and next actions;
- Review reasons and Make Smaller guidance;
- Situation summaries, overdue/due-soon text, open-loop labels, tiny plans, and noise-clearing guidance;
- untitled-item and no-data fallbacks;
- validation and data-tool status messages emitted by ViewModels.

Where a sentence contains user-authored text, preserve that text exactly and localize only the surrounding presentation.

### Phase 3: Make locale ownership explicit

1. Define one application-language source of truth compatible with AppCompat per-app locales.
2. Pass the effective locale or language tag to local analyzers instead of relying implicitly on `Locale.getDefault()`.
3. Ensure a language change invalidates or recreates any state owner that caches localized presentation.
4. Ensure process recreation restores the selected language before user-facing analysis is produced.
5. Ensure workers, alarm receivers, and notification builders use a localized application context.
6. Decide mixed-language capture behavior through tests:
   - use source-language signals for classification;
   - preserve source text;
   - use the selected UI language for surrounding interface copy;
   - use source language for AI-authored content when the prompt contract requires it.

Do not conflate:

- selected interface language;
- source language of a capture;
- language used by optional AI output;
- device region and date/number formatting preferences.

### Phase 4: Complete language-aware local analysis

The existing Estonian and Russian rule packs are a foundation, not final linguistic acceptance.

1. Replace English-only local labels and generated sentences with structured/localizable presentation.
2. Expand tests for common inflections, capitalization, punctuation, and word boundaries.
3. Cover ambiguous and missing reminder time information without confident guessing.
4. Verify 24-hour parsing for colon, dot, marked-hour, and compact forms where appropriate.
5. Verify mixed-language input and unsupported-language fallback.
6. Localize Situation and Review fallback output so offline behavior is complete.
7. Keep Gemini validation language-neutral and preserve stable JSON schema values.

### Phase 5: Formatting, plurals, and layout

1. Audit every date/time formatter for effective locale and protected 24-hour behavior.
2. Use locale-aware weekday and month names.
3. Use `plurals` for quantity-sensitive copy:
   - Russian requires `one`, `few`, `many`, and `other` behavior where applicable;
   - Estonian generally uses `one` and `other`, but wording still requires linguistic review.
4. Avoid embedded English separators or status words.
5. Verify placeholder ordering independently per language.
6. Test long Russian copy and Estonian compound words at:
   - default font scale;
   - large font scale;
   - narrow supported device width;
   - light, dark, and custom-background appearance.
7. Confirm no important action becomes clipped, unreachable, or ambiguous.

### Phase 6: Notifications and background execution

Verify for each supported language:

1. Notification permission flow.
2. Channel name and description on fresh install.
3. Reminder title and fallback body.
4. Notification offset presentation.
5. Language switch before scheduled delivery.
6. Process death before delivery.
7. Package replacement and reboot reconciliation.
8. Notification tap routing into the correct reminder.

Do not change reminder target time, offset, scheduling identity, or reconciliation semantics as part of translation work.

### Phase 7: Automated and manual acceptance

#### Automated checks

- Resource parity for English, Estonian, and Russian.
- Placeholder compatibility across locale variants.
- Plural coverage and representative Russian quantities such as 1, 2, 5, 11, 21, and 22.
- `AppLanguage` tag resolution.
- Effective-locale propagation into local analyzers.
- Estonian and Russian capture classification.
- Estonian and Russian reminder date/time interpretation.
- Local Situation and Review localized fallbacks.
- Notification resource selection under localized contexts.
- Existing protected-flow tests.
- Debug and release JVM tests, APK assembly, instrumentation APK assembly, and lint before completion.

#### Manual language matrix

Run English, Estonian, Russian, and system-default modes through:

- cold launch and warm launch;
- language switching without manual restart;
- process recreation;
- system language change while LUMA uses system default;
- Home capture;
- single-item confirmation;
- Brain Dump review, interruption, and resume;
- Calendar Day and Month;
- note, task, and reminder detail;
- Review, Waiting For, Someday, and Make Smaller;
- Spaces, Life Feed, archive, restore, and Undo;
- Search;
- Situation AI and Ask LUMA;
- Settings and local data confirmation;
- reminder scheduling, delivery, and tap routing;
- offline/local-only behavior;
- optional Gemini success, invalid response, and fallback.

For each flow, check:

- untranslated English;
- incorrect or unnatural translation;
- truncation and overlap;
- placeholder order;
- plural form;
- date/time format;
- accessibility announcement;
- accidental mutation or translation of user content.

Native or fluent linguistic review is required for final Estonian and Russian acceptance.

## Completion criteria

Language switching is complete only when all of the following are true:

- English, Estonian, and Russian are selectable and persist correctly.
- System default follows the operating-system locale with English fallback.
- No user-reachable production surface contains unintended English in Estonian or Russian mode.
- Every translatable English resource has Estonian and Russian counterparts.
- User-facing domain and offline fallback output is localized.
- Selected UI locale and source-language analysis behavior are explicit and tested.
- Notifications use the expected localized context.
- Dates, times, quantities, and placeholders are correct.
- User-authored and stored content is never silently translated.
- Protected behavior remains verified or explicitly reported as a manual gap.
- Focused, full JVM, assembly, and lint checks pass.
- The complete manual matrix has current device evidence.
- Estonian and Russian wording has linguistic approval.
- `PROJECT_STATE.md` and `LUMA_MVP_BACKLOG.md` reflect the verified result.

## Resume protocol

At the start of the next localization task:

1. Read this file, `PROJECT_STATE.md`, `LUMA_MVP_BACKLOG.md`, the workplace privacy policy, and protected behaviors.
2. Inspect the current working tree because the recorded baseline is intentionally dirty.
3. Re-run resource parity; do not assume the recorded count is still 304.
4. Search the targeted surface for visible and accessibility string literals.
5. Choose one coherent phase or surface; do not combine localization with unrelated cleanup.
6. Add English, Estonian, and Russian resources together.
7. Add focused tests for placeholders, plurals, locale propagation, or domain behavior affected.
8. Run the smallest relevant checks during iteration.
9. Before completion, run broader relevant checks, the strict workplace-privacy checker, and review the final diff.
10. Update this document only with current evidence, not optimistic completion claims.

## Recommended next milestone

Complete Phase 1 for the four confirmed high-density surfaces:

```text
CaptureSuggestionSheet.kt
ItemDetailScreen.kt
ReminderDetailScreen.kt
SpacesScreen.kt
```

Done when those user-reachable surfaces have no unintended hardcoded English presentation, all new keys exist in English/Estonian/Russian, quantity copy uses plurals, focused UI/resource tests pass, and the relevant flows have a manual language-switch smoke check.
