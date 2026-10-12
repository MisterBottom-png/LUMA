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

| Batch | SHA | Local result (`testDebugUnitTest`, `lintDebug`, screen tours) | CI "Screens" run |
| --- | --- | --- | --- |
| 1a | `f7d3c47` | Unit tests 0 failures; lint 0 errors | Failure (tour steps; fixed in the next batches) |
| 1b | `e0cdfd3` | Unit tests 0 failures; light and dark tours pass | Cancelled (superseded by 1c) |
| 1c | `59d8fbd` | Unit tests 0 failures | Success |
| 2 | `03c1384` | 748 tests, 0 failures; lint 0 errors | Success |
| 3a | `6af1cc7` | Reminder and data packages pass | Success |
| 3b | `a8ccd9e` | Reminder, item, settings, capture and data packages pass | Success |
| 3c | `6d0b3b0` | 765 tests, 0 failures, 2 skipped; lint 0 errors, 48 warnings; both tours pass | Success |
| 4 | `980c945` | 771 tests, 0 failures; lint 0 errors, 48 warnings; both tours pass | Success |
| 5 | `7978dda` | 778 tests, 0 failures; lint 0 errors, 48 warnings; both tours pass, including the §9.3 sheet | Success |
| 6 | `b5b08b6` | 780 tests, 0 failures; lint 0 errors, 48 warnings; both tours pass | Success |
| 7 | `052284d` | 784 tests, 0 failures; lint 0 errors, 48 warnings; both tours pass | Success |
| Final | handoff commit | `testDebugUnitTest` and `testReleaseUnitTest`: 784 tests each, 0 failures, 0 errors, 2 skipped (the tour classes, run separately) | Recorded after push |

The "API 36 emulator verification" job is not triggered on this branch, so there is **no on-device result** for any batch. It runs when a pull request is opened.

Screen-tour renders were compared by eye with the "Add to a Space" design (screens 1–11): the layout matches. Colours matched only after batch 7 (violet for new installs).

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
