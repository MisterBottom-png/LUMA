# LDS V2 status model

LDS V2 uses a deliberately small verification vocabulary. Status words are evidence claims, not decorative confidence labels.

## PASSED

Use `PASSED` only when the named check actually ran and completed successfully.

Examples:

```text
PASSED: strict workplace-privacy checker
PASSED: Android unit tests
```

Do not use `PASSED` for static inspection, expected behavior, or a check that could not execute.

## FAILED

Use `FAILED` when the named check ran and returned a failure.

Record enough context to identify the failing check and whether the failure appears implementation-caused. Do not disguise a failure as a limitation or omit it because the patch is documentation-only.

## PENDING_CI

Use `PENDING_CI` when GitHub Actions has not completed or no conclusive workflow result is available yet.

GitHub Actions is authoritative for repository unit tests, lint, and Android builds. Local or static checks do not replace pending CI.

## MANUAL_CHECK_REQUIRED

Use `MANUAL_CHECK_REQUIRED` when verification requires a physical device, emulator interaction, visual review, accessibility review, binary inspection, or another human-observed check unavailable to automation.

This status does not imply failure. It identifies evidence that still must be gathered before making the corresponding claim.

## BLOCKED

Use `BLOCKED` when required access, permission, source authority, or file capability is missing and the requested action cannot be performed safely.

A blocked report must identify the exact missing capability or permission and the required fallback. Do not continue by guessing, using stale source, or substituting an unofficial archive.

## Reporting rules

- Name the check or verification target beside its status.
- Never claim a check ran when it did not.
- Keep CI status separate from manual verification status.
- A task can be ready for review with `PENDING_CI` or `MANUAL_CHECK_REQUIRED` only when the remaining evidence is stated clearly and no implementation-caused failure is being ignored.
- Application behavior is preserved by evidence from the diff and relevant checks, not by merely writing the phrase “no behavior change,” a ritual humans remain strangely fond of.