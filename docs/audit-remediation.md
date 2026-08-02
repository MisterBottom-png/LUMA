# Audit remediation record

This record maps the July 2026 technical audit to the release-hardening work.

| Finding | Remediation | Evidence |
| --- | --- | --- |
| API and release hardening | `compileSdk` and `targetSdk` are 36; release is non-debuggable, minified, resource-shrunk, and uses optimized ProGuard rules. | `:app:assembleRelease`, `:app:bundleRelease`, lint |
| Release signing | Release signing is supplied only by `LUMA_RELEASE_*` environment variables; no key material is tracked. | `app/build.gradle.kts`, `.gitignore` |
| Backup exposure | Android cloud backup is excluded. A user-initiated local SAF export remains available. | backup rules and export flow |
| Restore safety | Restore input has a 10 MiB limit and JSON depth limit; restore remains preview/confirm based. | restore unit tests |
| AI control | Gemini use requires current consent; per-surface controls and local-learning erasure are available. | settings tests and implementation |
| Portrait and adaptive layout | Main activity is portrait-only; content width is bounded on larger portrait widths. | manifest and layout test |
| Performance | Baseline-profile producer and macrobenchmark module are enabled. | API 36 profile generation |

Open release conditions are documented in `release-checklist.md`; an actual production signing key and distributable signature remain operator-owned.
