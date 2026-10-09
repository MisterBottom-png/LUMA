# Calm Area Hubs Spaces Design

## Status

Approved in collaborative design review on 2026-08-01. This document defines product and technical intent only. Implementation requires a separate approved plan.

## Goal

Redesign Spaces so they help users understand each chosen life area quickly while preserving reliable filing and retrieval.

The redesign must make three outcomes equally strong:

- opening a Space and understanding what matters within a few seconds;
- filing a new item with almost no effort;
- finding an older item quickly and reliably.

## Product role

Spaces are calm, user-chosen life-area views. They are not productivity dashboards, raw-capture folders, AI-generated taxonomies, or replacements for Review, Search, or Situation AI.

The surrounding surfaces retain distinct responsibilities:

- Home captures input.
- Spaces orient and retrieve by life area.
- Review resolves ambiguity and staleness.
- Search finds a known item directly.
- Situation AI offers broader contextual judgment.

## Core model

Each finalized note, task, or reminder has zero or one canonical Space. An item with no canonical Space is Unfiled. Unfiled is a finalized-item retrieval state, not a visible Space and not the raw-capture Inbox.

Each finalized item may also have zero or more optional labels. Labels represent cross-cutting context without duplicating an item across Spaces. A canonical Space answers where the item belongs; labels add secondary retrieval context.

Space and label names are unique after trimming, case normalization, and internal-whitespace normalization. User-facing casing is preserved.

Raw captures and internal AI records remain outside Space contents. Only finalized notes, tasks, and reminders can appear in Spaces.

## New-user setup

New installations begin without automatically seeded Spaces. During onboarding, LUMA presents a short localized list of common life areas. The user may select any number of suggestions and add custom Spaces. Only selected and explicitly created Spaces are stored.

The user may skip Space setup. Capture, confirmation, Review, Search, and other core functions remain available; finalized items without a Space remain Unfiled.

Existing installations do not repeat onboarding. Existing Spaces and assignments remain unchanged through migration, including existing starter Spaces that the user kept, renamed, hidden, or archived.

## Spaces overview

The Spaces overview displays active Spaces in the user's chosen order. Each card shows only its icon, name, and visual identity.

The overview deliberately excludes:

- attention counts;
- item totals;
- due-date previews;
- progress indicators;
- productivity scores;
- workload summaries.

Unfiled appears only when at least one finalized item has no canonical Space. It uses neutral wording and does not imply failure.

Search and Create Space remain available. Hidden and archived Spaces remain behind secondary disclosures.

## Space detail

Opening a Space shows three predictable sections.

### Needs attention

This section is populated by deterministic, local rules. Eligible items are:

- overdue reminders;
- dated tasks whose actionable date has arrived;
- notes the user explicitly flagged;
- waiting items whose explicit follow-up date has arrived.

Archived and completed items are excluded. Items with missing or ambiguous dates are not guessed into this section.

An optional AI suggestion may appear separately from the deterministic list. It states a short reason, identifies its source items when practical, is dismissible, and never changes the deterministic ordering or mutates data.

### Upcoming

This section contains future reminders and dated tasks. Items are ordered only by their actual target time. Reminder target time, task date, notification offset, item update time, and event time remain distinct concepts.

### Recent and reference

This section contains undated open tasks, notes, someday items, recently changed items, and recently completed tasks. Ordering uses activity time consistently and never compares activity timestamps with target timestamps.

A compact All items action exposes the complete contents of the Space. It supports type, label, and state filters plus explicit sorting. Label filters remain collapsed until requested so the primary view does not become a permanent chip cloud.

Selecting an item opens its established detail destination.

## Filing and suggestions

Capture follows this sequence:

1. Save raw input privately to the local Inbox.
2. Run local analysis to propose an item type, one active Space, and optional labels.
3. When enabled and available, let Gemini improve those suggestions within the current active Space set.
4. Show the proposed Space and labels in the existing confirmation boundary.
5. Create the finalized item and its assignments only after user confirmation.

Suggestions may use active Space names, user-defined aliases, deterministic local language rules, prior confirmed corrections, and optional Gemini analysis. Low-confidence classification resolves to Unfiled instead of guessing. Hidden and archived Spaces are excluded from suggestions.

Renaming a Space preserves assignments through its stable identifier. Local rules, aliases, and learned corrections resolve to identifiers rather than depending on a hardcoded display name.

New labels suggested by AI are visibly marked and are created only when accepted during confirmation. AI never silently creates a Space, label, assignment, or priority state.

## Moving and organizing items

The user may move a finalized item to another active Space or to Unfiled. A successful move offers Undo. A failed move leaves the original assignment intact and presents an unobtrusive retry message.

Space reordering is one transactional operation that preserves a unique stable order.

## Space lifecycle

- Hide removes a Space from normal browsing and future suggestions.
- Archive retires a Space while preserving all items and assignments.
- Restore returns a hidden or archived Space to active browsing.
- Items in hidden or archived Spaces remain available through Search.
- No lifecycle operation silently reassigns contained items.

Space detail is addressed by a navigation route containing the stable Space identifier. Back navigation, process restoration, and direct internal navigation therefore follow the app's normal navigation model. A missing identifier returns to the Spaces overview with a neutral message.

## Local persistence

Room remains the source of truth. The implementation adds a label entity and type-safe note-to-label, task-to-label, and reminder-to-label relationships with foreign-key integrity. Labels do not replace the canonical nullable Space foreign key.

The Room schema change requires an explicit migration. Existing Spaces, items, assignments, hidden/archive state, and ordering must survive byte-for-byte where the schema does not require representation changes.

The local backup format adds labels and item-label relationships in a versioned format. Restore continues validating references before transactional replacement. Older supported backup versions decode with no labels.

Label deletion removes label relationships only and never deletes or changes the underlying items.

## Graceful behavior

Spaces remain fully useful without Gemini or network access.

- Unavailable analysis defaults to Unfiled with no labels.
- A Space hidden or archived before confirmation clears the stale suggestion rather than redirecting it.
- No configured Spaces produces an optional empty state with Create Space; it does not block capture.
- Room updates recalculate section membership without duplicating an item.
- Incomplete dates remain in Recent and reference or Review.
- AI suggestions are dismissible and leave deterministic sections unchanged.

## Protected behavior

The implementation must preserve:

- finalized-only visibility in Spaces and Life Feed successors;
- raw-capture and internal-processing privacy;
- explicit confirmation for important AI-proposed changes;
- local-first capture, storage, review, and retrieval;
- 24-hour time behavior;
- Waiting For and Someday classification;
- Search and item-detail routing;
- reminder target time and notification-offset separation;
- Undo for affected mutation flows;
- export/restore integrity;
- Reset Mode deletion boundaries;
- calm language without guilt, scoring, or productivity pressure.

## Verification

Automated coverage must demonstrate:

- onboarding creates exactly the selected and custom Spaces;
- skipping Space setup remains functional;
- existing databases preserve Spaces and assignments through migration;
- each finalized item has zero or one canonical Space;
- labels overlap without duplicating items;
- raw captures and internal AI records never appear in Spaces;
- deterministic rules assign every eligible state to the correct section;
- target timestamps and activity timestamps are never mixed in one ordering;
- low-confidence and unavailable-AI cases remain Unfiled;
- confirmation controls every Space and label assignment;
- hidden and archived Spaces are excluded from suggestions but remain searchable;
- moves to another Space and Unfiled support Undo;
- normalized duplicate names are rejected;
- Space routes restore after process recreation;
- Space reordering is transactional;
- labels and relationships survive export and restore;
- Search, Review, Calendar, item detail, reminders, Reset Mode, and finalized-only visibility do not regress.

Verification includes focused domain and ViewModel tests, Room migration and export/restore instrumentation, Compose interaction and accessibility tests, relevant JVM suites, build and lint checks, strict workplace-privacy scanning, and physical-device checks for onboarding, navigation restoration, capture confirmation, and Undo.

## Scope boundaries

Included:

- user-selected onboarding for Spaces;
- one canonical Space plus optional labels;
- calm Space overview and structured Space detail;
- deterministic attention and upcoming rules;
- optional explained AI suggestion;
- Unfiled retrieval and move target;
- stable navigation, transactional ordering, migration, and backup support.

Excluded:

- multiple canonical Spaces per item;
- automatically created Spaces or labels;
- cloud sync or accounts;
- external calendar integration;
- productivity scoring, progress dashboards, or workload summaries;
- replacing Review, Search, Calendar, or Situation AI;
- unrelated architecture or visual-system refactoring.

## Completion criteria

The redesign is complete when the three user outcomes in the Goal are supported, all specified protected behaviors remain intact, automated verification passes, and the required physical-device checks have current evidence.
