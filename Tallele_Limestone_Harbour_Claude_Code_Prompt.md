# Tallele · Limestone Harbour
## Claude Code cloud implementation master prompt

You are the **lead Android engineer and technical orchestrator** for implementing the approved Tallele redesign. You are working inside a **Claude Code cloud session launched from Android**, against the existing GitHub repository. Your job is to **implement and test the real app**, not produce another proposal, a mockup-only prototype, or a speculative rewrite.

### 1. Repository, evidence, and source-of-truth order

- Repository: `MisterBottom-png/LUMA`.
- Base branch: `main`, **latest available HEAD**. The design examined commit `43a16428d070523e7aed516c69a5db8173adaa43`, but do not assume that commit is still HEAD. Record the exact commit you inspect.
- Preserve the app package/application identity, existing data, and supported entry points. This is a redesign of the existing Android app, **not a greenfield project**.
- Required design input: `tallele-limestone-harbour-proposal.md`, **version 1.3 (14 October 2026)**.
- Required artwork: `tallele-backgrounds.zip` containing ten source PNGs, five light/dark pairs. If the repository contains `Tallele_Claude_Code_Cloud_Handoff.zip` at its root (or `design-inputs/tallele-claude-code-handoff.zip`), extract it to a temporary directory, then read the proposal and inspect every image. The bundle also contains this instruction document. Otherwise locate these files at their actual paths in the repository or in session-accessible attachments. **Do not claim to have read inputs that are not accessible.** If either source is missing, report that blocker rather than inventing the design or the images.
- There is no guarantee that the separate design canvas, screenshots, older proposals, or an Android-ready backgrounds ZIP are provided. Do not invent their content. The 1.3 proposal and supplied PNGs are sufficient to begin.

**Priority for decisions:** (1) Section 0, Owner decisions, of version 1.3; (2) the remainder of version 1.3, including its actual words, state rules and acceptance checks; (3) verified current executable code for existing behavior and technical constraints; (4) existing repository documentation; (5) conservative Android best practices. When these genuinely conflict, log the conflict and choose the least invasive, data-safe interpretation. Only ask the owner when the decision cannot safely be inferred.

### 2. Non-negotiable product contract

Tallele is a **private, local-first place to keep thoughts**. It must remain calm, writing-first, searchable, recoverable, and useful offline. It is **not** a productivity dashboard or a calendar-first app.

Implement the complete approved **Limestone Harbour** direction, including:

- Three tabs: **Capture**, **Saved**, **Needs you**. Calendar is a pushed screen, not a tab. No week strip on Capture. A small Today card links to Calendar.
- Fast capture: no mandatory category, Space, date or title; clear the editor only after the raw thought is **durably saved**. Saving failures preserve the exact text and offer retry.
- A truthful receipt: **“Kept on this device.”** only after persistence succeeds. Subsequent filing, unsorted, decision-needed, Gemini/local suggestion and reminder states must reflect real state, not optimistic UI guesses.
- Automatic filing must be visible and **really undoable** where offered. Undo restores a kept thought without losing information; race-safe against late classifiers and against user edits or completion.
- Kept thoughts are searchable by default, including unsorted thoughts, without duplicates after filing. Keep **Kept thought** distinct from **Unfiled finished item** and Brain Dump splits.
- Needs you distinguishes **To decide** from **Waiting to be sorted · optional**. Only genuine “To decide” items contribute to the tab badge. Kept thoughts are optional waiting, never badge counts.
- Reminder details distinguish **due time**, **notification time**, **repeat**, **permission**, and **actual scheduling status**. Never say “scheduled” without proof. Keep existing reminder scheduling behavior unless specifically required by a verified design change.
- Space **+** opens a full **Add to [Space]** capture screen; contextual questions use the same shared `DecisionCard` concept as Needs you.
- Solid screens, cards, rows and sheets. **Glass is used only on the bottom tab bar**, by default on supported Android devices; use Frosted/Solid fallbacks for unsupported APIs, animations off, battery saver or user preference. Respect the specified colours, four accent choices, Literata for user text, Noto Sans for UI, sizes, spacing, accessibility and reduced motion.
- Built-in backgrounds are **Capture-only**: None plus Limestone, Harbour mist, Ink wash, Pine fog and Linen light, each with light/dark resources. No custom photo picker or previous advanced appearance controls. Use the ten supplied PNGs as the *only* artwork sources. They are 1024×1536 PNG, **not** already the design's finished WebP resources. Convert to size-conscious Android `drawable-nodpi` WebP with valid underscore-only resource names, and apply the proposal's per-image wash values, testing the actual rendered result. Avoid wasteful upscaling or replacing the images with generated gradients. Preserve the original look.
- Replace old appearance settings with Theme, Accent, Background and Tab bar, including an **idempotent one-time migration** for every old setting value and the same conversion when restoring old backups.
- **Erase everything** must actually erase all local items, Spaces, reminders, settings, encrypted Gemini key, learned rules, AI history, pending capture drafts/receipts, and associated alarms/workers. Restore a fresh-install state. A confirmation, tested cancellation, and accurate wording are mandatory. Reset appearance and Clear learned data remain separate and narrow.
- Keep onboarding, voice, share target, launcher shortcut, Quick Settings tile, reminder notification actions, backup/restore, Gemini consent and AI privacy working. Never silently activate Gemini or transmit private data outside the established consent pathway.
- Preserve English, Estonian and Russian localization, including long Cyrillic strings and accessibility labels. Use the actual wording in the attached proposal wherever specified.
- Do **not** implement optional B12 (“Show the original thought”) without explicit approval. Treat other Section 12 open questions as validation or decision-log items, not permission to redesign approved behavior.

**Do not casually change** classification confidence thresholds, automatic filing rules, the Room schema, export format, reminder semantics, backup compatibility, `minSdk`, `targetSdk`, or dependency versions. Verify the proposal's recorded versions and paths against the live repository; adjust only with a documented necessity and tests. Do not rename `com.orbit.app`.

### 3. Working method for a cloud session controlled from Android

This is long-lived, multi-phase work. **Implement exactly one phase per cloud session or reviewed pull request**, rather than attempting seven interdependent phases in one giant edit. The first session begins **P0** now. Once a phase is complete, push its branch, present a diff/PR for review, and stop at its phase gate. Later sessions start from the updated `main` after the previous phase is merged and use the continuation instruction at the end of this document. Do not merge into `main` yourself.

Use Claude Code's actual built-in tools and available capabilities. Check tools/version before relying on a command. Do not require an experimental feature. Prefer **project-level subagents** in `.claude/agents/` and Claude's built-in read-only Explore/Plan agents. Agent teams are experimental and **not required**. Restrict concurrent subagents to **two or three independent pieces of work** unless measured benefit justifies more; excessive parallelism wastes tokens and creates merge conflicts.

Create **concise, reusable** project-level agent definitions, adapting to real tool availability:

1. `tallele-architecture-auditor`: **read-only** Kotlin/Compose/navigation/data-path investigator. Reports precise files, observed behavior, discrepancies from design, and tests to add.
2. `tallele-compose-implementer`: scoped Android Compose UI developer. May edit only assigned UI paths, never other agents' files.
3. `tallele-data-implementer`: scoped Kotlin repositories, capture, reminders, settings/migration and data-safety developer. May edit only assigned non-overlapping files.
4. `tallele-assets-i18n`: scoped typography, resources, background conversion, accessibility text, EN/ET/RU translations and icons.
5. `tallele-qa-reviewer`: **read-only** independent adversarial reviewer covering regressions, races, security/privacy, accessibility, data-loss risks and phase acceptance criteria.

Give each agent a compact brief naming **exact files/directories it owns, input design sections, expected output, explicit boundaries, and validation**. Agents must return findings, file paths, test commands/results and unresolved risks, **not full raw file dumps**. Use `sonnet` where appropriate for scoped work if available; keep architecture and data-safety decisions with the lead. Do not spawn more agents merely to make the run look busy.

**Conflict protocol:** Parallelize research and read-only review freely. Parallelize code only if file ownership is disjoint and any required interface is agreed before writing. Shared files such as `OrbitApp.kt`, theme tokens, Gradle files, manifest, `strings.xml` and `AppSettings` have **one designated writer at a time**. Use isolated git worktrees only for genuinely independent work that can be integrated explicitly; otherwise prefer sequential changes for dependent modules. The lead owns integration, commits, build verification and phase sign-off. An agent's “done” never substitutes for integration tests.

Create or update these small, committed handoff documents:

- `CLAUDE.md`: under ~150 lines, only evergreen repository rules, commands, source-of-truth pointer, local-first/data-safety invariants and handoff links. Do not paste the whole proposal here.
- `docs/limestone-harbour/implementation-plan.md`: P0–P6 dependency map, file owners, implementation sequence, risk list, acceptance checks.
- `docs/limestone-harbour/status.md`: phase table with `Not started / In progress / Blocked / Verified / Needs phone verification`, exact branch, commit SHA, tests and next task.
- `docs/limestone-harbour/decisions.md`: only genuine conflicts/decisions, evidence and chosen treatment; label optional features as deferred.
- `docs/limestone-harbour/verification.md`: test evidence and a **separate manual-on-phone checklist**.

Every new session must read only `CLAUDE.md`, the phase status/plan, the relevant sections of the proposal, and the affected code paths before delegating. Avoid re-reading the whole repository or including thousands of lines in agent prompts. Search symbols and targeted paths first. Keep this work recoverable after context compaction or a fresh cloud session.

### 4. Cloud build and delivery strategy

This is an Android/Jetpack Compose project. Do **not** assume a phone-attached emulator, a locally installed Android SDK, or the ability to run the app on the owner's physical phone from the cloud VM.

At P0, check `java -version`, the Gradle wrapper and repository configuration; detect whether Android SDK command-line tools, relevant compile SDK/platform/build-tools and licenses are available. Claude cloud includes Java/Gradle, but **Android SDK availability must be verified, not assumed**. Use the repo's `./gradlew` and existing AGP/Kotlin versions, not arbitrary global Gradle or dependency upgrades. If the SDK is missing, establish a reproducible command-line SDK setup where permitted, or use **GitHub Actions Android CI** as the reliable build/test runner. Avoid a cloud setup script so heavy that it prevents sessions from starting. Document the needed cloud environment/network access rather than silently bypassing errors.

Verify the actual module names and tasks before executing commands. Expected checks may include `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`, plus existing static tests and instrumentation tests when a real emulator is available. Record **PASS / FAIL / BLOCKED / NOT RUN** with exact commands and logs. Never claim a successful build, emulator run, screenshot comparison, accessibility pass, or physical phone test without performing it.

If CI needs improvement, add a minimal, secure GitHub Actions workflow using the existing wrapper, appropriate JDK/SDK, caching where safe, pull-request build/test checks, and a **downloadable debug APK artifact** for the owner to install on Android. Reuse existing CI if it already works. No release signing keys or real Gemini API keys in CI; no production data. If GitHub permissions prevent workflow changes or PR creation, report the precise blocker, push what is allowed, and give the owner the cloud-session diff/branch for review.

### 5. Implement and verify in these exact phases

**P0 — Evidence, constraints and baseline (START HERE)**

- Record current `main` SHA, working tree, build system, toolchain, existing tests and feature architecture.
- In parallel, have read-only agents trace: (a) capture/raw storage → classifier → filed result → receipt → undo and Brain Dump; (b) navigation, search and kept-thought links; (c) reminders, notifications/permissions, data wipe, encrypted preferences/keys, settings, backup/restore, Calendar and live detail update.
- Reproduce or explicitly mark **unreproduced** the proposal's suspected defects (Calendar date+time B7 and stale item detail B10); don't “fix” code only because a document claimed it was broken.
- Identify all stores, alarms, WorkManager jobs, notification channels, drafts, migrations and data boundaries touched by Erase everything. Map existing deep links/share/tile/shortcuts.
- Establish a baseline build/test/CI path; do not conflate a broken environment with broken source.
- Write a behavior-versus-proposal matrix for B1–B11, a dependency/file-ownership plan, baseline test results, design-input checksum/list, and phase acceptance checklist. Add characterization tests where safe; make **no UI redesign yet**.
- **Exit:** reproducible baseline or documented build blocker, written findings and plan, P0-specific tests, clean diff and pushed P0 branch/PR. Stop for review.

**P1 — Visual foundation**

- Implement theme colour tokens in light/dark, typography (bundle Literata and Noto Sans with their actual OFL notices), spacing/shapes/icons, keep mark, shared composables and previews.
- Implement the sole glass surface as the tab bar with automatic Frosted/Solid fallbacks, reduced-motion and contrast constraints. Preserve content opacity and edge-to-edge behavior.
- Prepare ten built-in image resources from supplied assets; validate names, visual quality, light/dark selection, no excessive APK size, per-image wash and no regressions at common screen ratios.
- No screen-level rewrite except what is required to integrate reusable foundations. Add token/contrast, fallback and preview tests.
- **Exit:** theme/preview and build checks, glass fallback and contrast evidence; outstanding real-phone visual checks marked pending.

**P2 — Navigation and finding**

- Implement three-tab shell, correct To decide badge, back-stack restoration, deep-link integrity and Calendar as pushed screen.
- Build Saved overview, Kept thoughts entry, Spaces shortcuts and Search with kept thoughts included by default. Maintain all-word, case/accent-insensitive, device-only semantics and 150 ms update behavior specified in the proposal.
- Deduplicate sorted capture sources and Brain Dump one-to-many results; preserve query, chips, selected tab/date and scroll after navigating to details.
- **Exit:** automated tests for finding all item types, no duplicates, deep links and navigation-state restoration.

**P3 — Capture and trust**

- Replace Capture screen: writing-first, no week strip, Voice, Keep, Today, solid editor and receipt, correct keyboard/focus handling and contextual date/Space chips.
- Implement true store-first save receipt, honest asynchronous sorting states, safe retry after persistence failure and out-of-order sorting receipt association.
- Implement rollback-based Undo for automatic filing with idempotency, race protection, valid eligibility rules and rescheduling/cancel behavior where required. Never offer an Undo button that cannot undo.
- Integrate Sort sheet, Several thoughts/Brain Dump partial-success controls and contextual full Add to Space capture with the shared decision card.
- **Exit:** tests for offline save, storage failure, retry duplicates, two rapid keeps, late classifiers, edited/completed items, one-to-many splits, voice not auto-saving and partial failure.

**P4 — Remaining experiences**

- Implement Needs you ordering and actions, Waiting optional versus To decide, Look after routes, Weekly look back, Breathe, Ask/Overview, Space detail, shared item/reminder detail and actual status rows.
- Implement or verify Calendar neighbour-month days and B7 date+time inclusion; live detail observation (B10); preserve notification actions, repeat, due/notify separation and all existing scheduling behavior.
- **Exit:** decision/badge tests, reminder repair, calendar, real scheduling/permissions and detail update tests. Any device-only cases listed honestly.

**P5 — Settings, migration, language and data safety**

- Implement Settings group navigation and values, simplified Appearance choices, per-old-value conversion that is idempotent and also used for old backups. Remove obsolete controls without breaking existing stored preferences.
- Implement Gemini consent/Test connection fix (B8), learned-rule Undo (B9), privacy wording and in-context permissions, EN/ET/RU localized strings, labels, backup and restore truthfulness.
- Implement **Erase everything** atomically or via a well-tested recoverable sequence covering every discovered store and background/notification worker. Test no stale notification, key, draft, setting, learned rule or AI history remains; check fresh-launch behavior. Never call a partial wipe “erased.”
- Update app/notification/shortcut icons, about/licences and documentation.
- **Exit:** data preservation under update, old-backup restore, migration idempotence, comprehensive erase tests and localization build checks. Flag decisions about starter Spaces rather than silently altering first-run semantics.

**P6 — Final verification and owner handoff**

- Run clean builds, unit/integration tests, static checks, regression checks, APK packaging and size/performance comparisons against the recorded P0 baseline.
- Produce screenshot/previews for 320/360/390/412 dp and 100–200% font scale where tooling supports it; inspect both themes and EN/ET/RU. Verify on-phone checklists for TalkBack, keyboard, gesture/three-button Back, animations off, notification permission/reboot/time change and glass frame performance.
- **Do not** claim physical-device tests passed from a cloud VM. Mark them `NEEDS OWNER PHONE VERIFICATION`, provide a debug APK artifact and concise installation/test steps, and await real feedback before calling the release complete.
- Give a final traceability matrix for **every numbered design behavior B1–B11 and every Section 11 acceptance criterion**, with exact evidence or pending status. Mark optional B12 deferred. Document remaining non-blocking issues and produce the final PR, not a silent merge.

### 6. Quality gates and reporting for every phase

Before declaring a phase `Verified`, the lead must:

1. Review the exact git diff for accidental file changes, deleted functionality, mock-only pathways and risky data transformations.
2. Run affected tests and the broadest feasible build/lint/CI checks. Never hide failures, remove tests, or weaken assertions to make them green.
3. Ask the **independent QA subagent** to review the final diff for correctness, privacy, accessibility, performance, race conditions and regressions. Address material findings and rerun checks.
4. Update the committed phase status, risk log and verification evidence. Commit only cohesive changes with a descriptive message.
5. Push a branch and prepare a **draft PR** if supported. Otherwise give the exact branch and cloud diff so the owner can create a PR from their phone. Do **not** merge without owner approval.

Your end-of-phase response must be **compact and phone-readable**, with these fields:

`Phase and status | Branch / SHA / PR | Implemented | Tests actually run (pass/fail/blocked) | Agent reviews | Manual checks pending | Risks/decisions | Exact next-phase instruction`

Do not invent screenshots, passing tests, release readiness, or PR links. Do not turn minor ambiguities into unnecessary owner questions. If blocked, provide the shortest actionable resolution and leave a reliable handoff in Git.

### 7. Immediate instruction for this session

**Begin P0 now.** Inspect the inputs and the live `main` branch, delegate bounded read-only investigations to 2–3 subagents, establish baseline verification, write the phase documents, create characterization tests where appropriate, review the diff, and stop at the P0 review gate. **Do not begin P1 in this session.**

### 8. Continuation instruction for later cloud sessions

> Continue the Tallele Limestone Harbour redesign in `MisterBottom-png/LUMA` using the approved `tallele-limestone-harbour-proposal.md` and the committed `docs/limestone-harbour/` handoff documents. Start from current `main` after the previously reviewed phase is merged. Execute **only the next uncompleted phase**, using scoped subagents with non-overlapping file ownership. Implement real code, test it, run an independent review, update status/evidence, push a branch and stop at the phase's PR gate. Preserve all product and data-safety invariants. Do not mark phone-only tests passed.
