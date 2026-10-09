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

## Owner decisions open on this branch

- [ ] **Application ID.** The package is still `com.orbit.app`. It must not be changed silently: a new ID is a new app on every phone, with no upgrade path for existing data, alarms or Play listing. Decide before the first public release; the release verifier pins `com.orbit.app` until then.
- [ ] Home-screen widget: needs an exported, unprotected `AppWidgetProvider` receiver, which widens the release export allowlist.
- [ ] Encrypted and automatic weekly backups: key handling (passphrase vs. device-bound key) and where automatic copies are written.
- [ ] "Remind again until done": cadence and limits.

## Distribution boundary

The repository can produce a release-ready artifact when the secure signing variables are present. It must not claim a production-signed artifact until an authorized signing environment has completed that step.
