# Testing strategy

Use the smallest focused check during development, then collect broader release evidence once per release candidate.

| Scope | Check |
| --- | --- |
| Kotlin and JVM behavior | `:app:testDebugUnitTest :app:testReleaseUnitTest` |
| UI and persistence integration | `:app:connectedDebugAndroidTest` on the named API 36 emulator |
| Release packaging | `:app:assembleRelease :app:bundleRelease` |
| Static analysis | `:app:lintDebug :app:lintRelease` |
| Baseline profile | `:app:generateBaselineProfile` |
| Release structure | `scripts/verify-release.ps1 -RequireBaselineProfiles` |

Tests protect migration preservation, restore bounds, Brain Dump idempotency, AI consent/learning controls, reminder identity, portrait layout, and key Compose semantics. Manual release checks remain necessary for notification permission transitions, system picker behavior, visual glass contrast, and signed-artifact distribution.
