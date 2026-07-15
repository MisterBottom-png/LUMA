# LUMA Android Stack Guidance

## Expected stack

Use existing project conventions. When not contradicted by existing code, prefer:

```text
- Kotlin
- Jetpack Compose
- Material 3
- ViewModel
- Repository pattern
- Room
- DataStore where already used/appropriate
- WorkManager/AlarmManager/notification APIs only when the app already uses or needs them
```

## Android implementation rules

- Inspect existing architecture before adding new patterns.
- Do not introduce a dependency just because it is fashionable this week.
- Prefer small composables with clear state boundaries.
- Keep UI state and business state separate.
- Use existing navigation patterns.
- Avoid broad refactors during feature work unless required.
- Run Gradle validation when practical.

## Official Android skills to install separately

Recommended external official skills:

```text
android-cli
edge-to-edge
navigation-3
testing-setup
r8-analyzer
android-intent-security
```

These are not bundled here. Install them separately from the official Android skills source if your Codex/Android Studio workflow supports it.

## Compose pitfalls

Check for:

```text
- unnecessary recomposition
- missing LazyColumn keys
- unstable item models in long lists
- hardcoded colors that break dark mode
- tiny touch targets
- state stored in composables when it belongs in ViewModel
- duplicated UI logic
- edge-to-edge/inset issues
```
