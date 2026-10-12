# AI data flow and consent

The app can operate locally when Gemini is unavailable or disabled. Gemini access is opt-in: the current consent version must be accepted before a Gemini mode can run. Choosing local-only revokes Gemini use for the relevant surfaces.

Raw captures are private source material. User-facing Spaces and Life Feed contain finalized items, not internal processing records. AI may draft, classify, summarize, and explain; important actions require visible confirmation, with one approved exception: a newly saved, clear thought may be filed by itself (local or Gemini suggestion alike) under the boundary in `codex/LUMA_AI_BEHAVIOR_CONTRACT.md`. The consent (version 2) says so and names "Sort right after saving" as the pause; consent given to version 1 is asked again.

While Gemini is on for capture, every saved thought is sent (in full) with the names of the active Spaces and the current date and time; the thought is delimited in the prompt as data, and Gemini's output is validated (type, Space from the list, confidence 0..1, time window) before use.

Optional local learning is separate from Gemini consent. When disabled it contributes no new learning records. The settings flow can clear local learning data transactionally. No secret or API key is stored in source control.
