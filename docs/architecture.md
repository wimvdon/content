# Architecture

> Template skeleton. Fill in the "This app" section for each app and keep it up to date.

## This app
_What the app does, its screens, and where its data lives. (Placeholder: one home screen with a tap counter. No stored data.)_

## Structure
```
app/src/main/kotlin/<package>/
  MainActivity.kt          Entry point. Only sets up the theme and the first screen.
  ui/theme/Theme.kt        Material 3 theme (light/dark, dynamic colour).
  ui/<feature>/            One folder per screen/feature:
    <Feature>Screen.kt       Composables: display state, forward clicks. No logic.
    <Feature>….kt            Plain Kotlin logic (no Android types) → unit tested.
app/src/main/res/
  values/strings.xml       All user-visible text (English, the default).
  values-nl/strings.xml    Dutch translation. Every string must exist in every language.
app/src/test/kotlin/       JVM unit tests (mirrors the main package structure).
scripts/                   Release helpers (Python/Java) with their own tests.
.github/workflows/         ci.yml (every PR), release.yml (signed releases).
```

## Conventions
- **Single `app` module.** Split into modules only if the app grows large; record it in `decisions.md`.
- **Logic in plain Kotlin classes** with no `Context`, `View` or Compose types, so it can be
  tested on the JVM. Example: `ui/home/TapCounter.kt` (immutable; methods return a new value).
- **UI state**: small screens use `rememberSaveable`. When a screen gets real logic or data,
  add a `ViewModel` (`androidx.lifecycle:lifecycle-viewmodel-compose`) that exposes a
  `StateFlow<UiState>` and calls into the plain-Kotlin logic. Record it in `decisions.md`.
- **Navigation**: one screen today. With several screens, use Navigation Compose.
- **Persistence**: Jetpack DataStore (version already in the catalog). Wrap it behind a small
  repository interface so logic tests can use a fake.
- **No network, analytics, crash reporting or ads** unless the owner agreed (see `CLAUDE.md`),
  in which case update `PRIVACY.md`.
- **Strings** in resources only; plurals via `<plurals>`.
- **Versions** of libraries only in `gradle/libs.versions.toml`.
- **App identity** (`applicationId`/`namespace`) only in `app/build.gradle.kts` (`appId`).
