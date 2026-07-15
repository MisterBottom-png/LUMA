# LUMA protected behaviors

These behaviors were previously confirmed or are strongly protected. Protection means do not casually regress or rebuild them. It does not replace current verification.

| Behavior | Protection |
|---|---|
| Internal/Processed items hidden | Do not expose AI processing records as normal cards. |
| 24-hour time | Preserve display and parsing unless explicitly changed. |
| Categorized Settings | Do not flatten Settings into an unstructured list. |
| Centered bottom navigation | Preserve alignment when changing insets, navigation, or theme. |
| Centered Home input | Preserve layout and keyboard/inset behavior. |
| Undo | Check when item mutation flows change. |
| Search | Check when storage, queries, navigation, or item models change. |
| Export/restore | Treat as data-risk behavior. |
| Reset Mode | Never broaden deletion scope accidentally. |
| Waiting For and Someday | Preserve classification and visibility. |
| Make Smaller | Preserve when editing item actions or AI prompts. |
| Brain Dump | Preserve capture behavior and navigation. |
| Ask LUMA | Keep user-facing and available. |

## Verification status

A protected item may be:

```text
verified
unverified in current revision
partially verified
regressed
not affected
```

Do not claim it works solely because it appears in this document.
