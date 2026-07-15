# LUMA Protected Done Items

These are user-confirmed or strongly protected items. Future Codex work must not break or reintroduce problems here.

## Protected items

```text
- hidden Processed items
- 24-hour time
- categorized Settings
- centered bottom nav
- centered Home input
- undo
- search
- export/restore
- Reset Mode
- Waiting For / Someday
- Make Smaller
- Brain Dump
- Ask LUMA
```

## Regression rule

Any change touching related UI, data, settings, navigation, AI processing, Review, Spaces, reminders, export/restore, or item actions must check this list.

## Required final-report line

Every meaningful task must include:

```text
Done items protected:
- <items checked>
- <items not affected>
- <items needing manual verification>
```

## Special protection notes

### Processed items

Do not expose internal AI processing records as normal user-facing cards.

### 24-hour time

Preserve 24-hour display/parsing behavior unless the user explicitly asks for another format.

### Settings categories

Do not flatten Settings back into an uncategorized long list.

### Centered Home input and bottom navigation

Do not break the current centered layout when changing Home, nav, insets, edge-to-edge, keyboard behavior, or theming.

### Export/restore and Reset Mode

Treat these as data-risk areas. Any changes require data-flow caution and manual test steps.

### Ask LUMA

Do not remove, hide, or turn Ask LUMA into an internal debug console.
