# Data and backup policy

LUMA is local-first. Room data and preference state remain on-device unless the user explicitly exports a local backup through the Android Storage Access Framework.

Android cloud backup is excluded through `data_extraction_rules.xml`; device-to-device transfer permits the minimum app state required for a user-owned device transfer and excludes the secure AI store. The legacy full-backup rules also exclude all app data.

Exports are user-chosen JSON files. The UI states that exports are plaintext and may contain sensitive information. Restore validates a bounded input size and structure before parsing, previews changes, and requires user confirmation. Restore must never silently overwrite user data.
