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

- [x] **Application ID and name (decided 2026-10-09).** The product is **Tallele** with application ID `com.tallele.app`; the Kotlin namespace stays `com.orbit.app`. The release verifier pins `com.tallele.app` (`-ExpectedPackageName`) and checks the app's own classes against `-CodeNamespace com.orbit.app`.
- [ ] Register `tallele.com` (the reverse-domain basis of the ID) and check Tallele in the EUIPO, Estonian Patent Office and WIPO trademark registers (Classes 9 and 42) before publishing.
- [ ] Phones with a build installed as `com.orbit.app` get Tallele as a separate app: move data with Settings > Local data > Export, then Restore in Tallele. Uninstall the old build afterwards.
- [ ] Home-screen widget: needs an exported, unprotected `AppWidgetProvider` receiver, which widens the release export allowlist.
- [ ] Encrypted and automatic weekly backups: key handling (passphrase vs. device-bound key) and where automatic copies are written.
- [ ] "Remind again until done": cadence and limits.

## Distribution boundary

The repository can produce a release-ready artifact when the secure signing variables are present. It must not claim a production-signed artifact until an authorized signing environment has completed that step.
