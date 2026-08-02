# Release checklist

## Before signing

- [ ] Confirm version code and version name.
- [ ] Supply `LUMA_RELEASE_STORE_FILE`, `LUMA_RELEASE_STORE_PASSWORD`, `LUMA_RELEASE_KEY_ALIAS`, and `LUMA_RELEASE_KEY_PASSWORD` only in the secure signing environment.
- [ ] Run `:app:assembleRelease :app:bundleRelease :app:lintRelease` using JDK 17 or newer.
- [ ] Run `scripts/verify-release.ps1 -RequireBaselineProfiles`.
- [ ] Run strict workplace privacy check.

## Device evidence

- [ ] Verify the named API 36 AVD and run `:app:connectedDebugAndroidTest`.
- [ ] Generate the baseline profile with `:app:generateBaselineProfile`.
- [ ] Install and launch the non-debug benchmark candidate.
- [ ] Confirm portrait remains locked through rotation attempts.
- [ ] Review restore/export, reminder, permission, Gemini consent, and locale flows manually.

## Distribution boundary

The repository can produce a release-ready artifact when the secure signing variables are present. It must not claim a production-signed artifact until an authorized signing environment has completed that step.
