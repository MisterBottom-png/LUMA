# Performance budgets

The release target is a responsive capture-first experience on the supported API 36 emulator and representative production hardware. The benchmark module measures cold and warm startup plus representative navigation and scroll paths. It records timing and frame metrics; it does not convert a single emulator run into a hardware-wide performance claim.

Baseline profiles are generated from the Home, Spaces, Review, Settings, and capture journey. The release build consumes the profile with dex-layout optimization enabled. Investigate regressions when startup, navigation, jank, allocation, or binary size materially worsens compared with the most recent approved candidate.

Keep performance work behavior-preserving. Do not weaken privacy, confirmation, or accessibility guarantees merely to improve a benchmark score.
