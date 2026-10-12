# 05 · Test evidence

## Environment

| Item | Value |
| --- | --- |
| Host | Linux cloud container, JDK 21 (Gradle toolchain), Android SDK platforms and build-tools only |
| Emulator / device | **None.** No `/dev/kvm`, no emulator, no system images. Instrumented tests, notification delivery, reboot, permission transitions, TalkBack and real renders **cannot run here**. |
| On-device evidence available | The GitHub Actions `API 36 emulator verification` job (swiftshader, API 36 only). It runs `:app:connectedDebugAndroidTest` and `:app:generateBaselineProfile` on each PR. Results are recorded below per pushed commit. |
| Gradle invocation | `./gradlew --no-daemon --max-workers=3 …`, with automatic retry on Maven download throttling |

## Baseline (before any change)

| When (UTC) | SHA | Command | Result |
| --- | --- | --- | --- |
| 2026-10-12 16:30–16:55 | `43a1642` | `:app:testDebugUnitTest :app:testReleaseUnitTest :app:lintDebug` | **testDebugUnitTest:** 726 tests, 0 failures, 0 errors, 2 skipped. **testReleaseUnitTest:** 726 tests, 0 failures, 0 errors, 2 skipped. **lintDebug:** 0 errors, 48 warnings, 1 hint. |

The 2 skipped tests are the Robolectric screen-tour classes, which only run when `LUMA_SCREENS_DIR` is set. The baseline matches the CI figures quoted in the review (726 unit tests, 0 failed).

CI for the same commit, as reported in the review: 58 emulator tests passed. This audit did not rerun CI for `43a1642`.

## Per-batch results

Each batch appends its rows here: commands, SHA, counts, and the CI run for the pushed commit.

## Not run / blocked

| Check | Status |
| --- | --- |
| `:app:connectedDebugAndroidTest` | Blocked locally (no emulator). CI only. |
| `:app:assembleRelease :app:bundleRelease :app:lintRelease` | Run in CI (`Fast release gates`). The release artefact is unsigned in CI, because no signing secrets are configured. |
| `:app:generateBaselineProfile`, macrobenchmarks | CI emulator (profile) and weekly schedule (benchmarks). Not measured in this audit. |
| `scripts/verify-release.ps1 -RequireBaselineProfiles` | CI `Fast release gates`. |
| Real notification delivery: 2nd and 3rd repeat, reboot, DST, exact-alarm denial, channel blocked | **Not verified.** Needs a device. |
| Renders at 320/360 dp, 130/200 % font, ET/RU; TalkBack | **Not verified** on a device. Robolectric captures, where added, are listed per batch. |
| Performance with large data | **Not measured.** |
