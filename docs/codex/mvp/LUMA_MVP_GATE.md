# LUMA initial MVP gate

## Product definition

The initial MVP proves that LUMA is a stable, understandable, local-first personal life inbox. It does not need to become a broad AI operating system.

## Required user outcomes

The user can:

1. launch the app without a core-path crash;
2. capture a raw thought quickly;
3. have the raw input saved locally before optional AI processing;
4. accept, edit, or keep a clear final item;
5. find saved items after restart;
6. use Spaces/Life Feed without raw or internal AI records appearing as separate final cards;
7. use Review without misleading status language;
8. use reminders supported by the app with sane date/time behavior;
9. use Ask LUMA, search, undo, Settings, export/restore, and Reset Mode without critical regression;
10. use the app in light and dark mode;
11. retain core behavior when Gemini is unavailable.

## Completion order

1. Build and launch stability
2. Local storage and persistence
3. Capture reliability
4. Final-item visibility and deduplication
5. Spaces and Review correctness
6. Reminder/date-time reliability
7. Core navigation and protected behavior
8. Theme/accessibility polish
9. Final regression pass

## Status definitions

```text
MVP PASS
- every blocker is verified resolved;
- core-path validation passes or any environment limitation is clearly external and manually covered;
- no unexplained data-loss or privacy risk remains.

MVP PARTIAL
- app is substantially usable;
- one or more non-catastrophic blockers or required manual checks remain;
- the exact gap and next repair batch are known.

MVP BLOCKED
- core build/launch, persistence, visibility, reminder, data safety, or validation is materially broken or unverifiable.
```

## Blockers

- app does not build or launch on the intended configuration;
- captured text can be lost;
- saved items do not persist;
- user cannot find saved items;
- core navigation is broken;
- raw/internal processing records appear as final Space items;
- one capture creates duplicate visible cards;
- Review is materially misleading or unusable;
- reminder scheduling/cancellation is materially incorrect;
- dark mode makes core content unreadable;
- a protected core behavior regresses;
- Gemini failure blocks local capture or retrieval.

## Deferred from initial MVP

Unless required to repair a core flow:

- advanced learning memory;
- backlinks and related-thought graphs;
- automatic duplicate merging;
- people/project profiling;
- broad pattern insights;
- cloud sync/accounts/backend;
- external calendar sync;
- large Situation AI V2 expansion.
