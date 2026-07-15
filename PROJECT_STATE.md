# LUMA project state

## Source authority

- Authoritative repository: `MisterBottom-png/LUMA`
- Authoritative branch: `main`
- Authority rule: the latest merged commit on `main` is the sole source of truth for code, project state, planning, and implementation history.
- Drive files, ZIP archives, local folders, previous chat artifacts, generated packages, and unmerged task branches are non-authoritative references or outputs.
- Every source-changing task must begin by re-reading the latest `main` and the root continuity files.

## Continuity

- Latest completed product change: 14
- Latest completed product task: Support IME Send
- Current repository migration: Change 15, LDS V2 workflow adoption
- Change 15 is documentation and repository-process work only; it does not change application source or behavior.

## Current product state

LUMA is a complete local-first Android application source tree with Gradle configuration, application code, tests, repository rules, verification records, and supporting scripts.

The current Ask LUMA composer:

- exposes the keyboard IME Send action;
- routes keyboard Send and the embedded send icon through the same guarded submission helper;
- blocks blank, too-short, and in-flight duplicate requests;
- preserves the existing loading state, answer rendering, source rows, and visual layout;
- includes focused unit coverage for rejected and accepted shared submission paths.

## Current task

Change 15 migrates repository authority and development workflow to LDS V2. The bounded scope is agent guidance, workflow documentation, status vocabulary, pull request structure, and continuity metadata. Application source and behavior are out of scope.

## Next likely work

After Change 15 is merged, begin the next explicitly requested task from the new latest `main` and assign the next unpublished continuity number.

## Known risks and verification limitations

- Previous focused unit-test and debug-build execution could not complete because Gradle 8.9 was not cached and the execution environment could not reach `services.gradle.org`.
- IME Send behavior has not been physically exercised on an Android device or emulator.
- The loading animation, failure recovery, focus restoration, composer interaction, and accessibility semantics retain earlier device-verification limitations where no device or emulator was attached.
- Documentation-only LDS V2 migration does not remove or satisfy those product verification gaps.
- GitHub Actions is authoritative for repository unit tests, lint, and Android builds; device, visual, and accessibility checks remain manual where automation cannot provide equivalent evidence.