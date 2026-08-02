# Numbered small-change workflow

Use only when the user requests `implement small change <number>`.

1. Read `docs/planning/LUMA_UI_UX_Small_Change_Steps.md` and the matching numbered section in `docs/planning/LUMA_ChatGPT_Project_Small_Change_Prompts.md`.
2. Inspect the current implementation before treating the catalog entry as unfinished work.
3. Define the exact included behavior and exclusions from that one numbered entry.
4. Implement exactly one numbered change and preserve all excluded behavior.
5. Ignore planning instructions about Google Drive, ZIP creation, publication, or unrelated follow-on work unless the user explicitly requests those effects.
6. Run the narrowest relevant validation, inspect the final diff, and report current evidence.

Done means the requested numbered change is either verified complete or blocked by a specifically reported decision or environment limitation; no adjacent catalog item was implemented.
