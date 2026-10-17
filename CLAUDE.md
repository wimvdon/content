# Rules for Claude Code sessions in this repository

RSS Reader, an Android app built from the `android-app` template (a private repository). The
owner is **not a developer and does not review code**. CI, tests, lint and these rules
are the safety net. Follow them every time.

## Workflow
1. **One focused change per branch and pull request.** Start a new branch from an up-to-date
   `main` (`git fetch origin main && git checkout -b <branch> origin/main`). Never reuse an old
   or merged branch for a new change.
2. **Before finishing, run and fix everything:**
   ```
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```
   If you touched `scripts/` or the workflows, also run `python3 -m unittest discover -s scripts/tests`.
   Then push and make sure CI on the pull request is green.
3. **Write the pull request description in plain language** (fill in `.github/pull_request_template.md`):
   what changed and why, in words a non-developer understands, and **exactly what to tap on the
   phone to test it** (step by step, with what they should see). The owner tests by installing
   the debug APK from the pull request's CI run (Checks → CI → Artifacts). It installs as a
   separate app, "RSS Reader Dev" (`io.github.impca201.rssreader.debug`), next to the released one.
4. **Testing on the phone does not block merging.** The owner doesn't review code, so green CI is
   what a pull request needs to be merged. The owner tries the change afterwards with the test steps
   from the description (the debug APK stays on the pull request's CI run, and every push to
   `main` builds a new one) and always before a release. A problem found then is fixed in a new
   pull request.

## Never do these without asking first (and saying why)
- Delete, skip, `@Ignore`, or weaken tests, lint checks, CI steps or workflows to make something
  pass. Don't add a lint baseline or disable a lint check. **Never remove the release workflow.**
- Add Android permissions or network destinations beyond the ones in `PRIVACY.md` (only the feeds
  and websites the user adds, the pages of their articles, and the pictures those contain), analytics, crash reporting, ads or tracking. No background
  downloads. Keep Android's block on unencrypted (`http://`) traffic.
- Add new dependencies (libraries or Gradle plugins). Prefer what is already in
  `gradle/libs.versions.toml`. Use **DataStore** if the app needs to store data.
- Change the `applicationId` (`appId` in `app/build.gradle.kts`) or the signing setup once the
  app is released: phones would refuse to update it.

## Never commit
Secrets, keystores (`*.jks`, `*.keystore`), `keystore.properties`, passwords or API keys.

## Code rules
- Kotlin + Jetpack Compose + Material 3, single `app` module, mainstream libraries only.
- **Every logic change gets new or updated unit tests** (`app/src/test/`). Keep logic in plain
  Kotlin classes without Android types (no `Context`, `View`, Compose) so it runs on the JVM
  (`data/Library.kt`, `feed/`). Composables only display state and forward clicks. HTTP only in
  `feed/FeedFetcher.kt`, for pictures through Coil (`RssReaderApp.newImageLoader`), for sync in
  `sync/GoogleDrive.kt` (Google Drive's hidden app folder, see `docs/sync.md`), and for saving to
  Instapaper in `instapaper/Instapaper.kt`.
- **All user-visible text goes in `app/src/main/res/values/strings.xml`**, never hard-coded. In
  Compose, read strings with `stringResource` or `LocalResources`, not `LocalContext`.
  Add the translation to **every** `values-xx/strings.xml` (currently: `nl`, `fr`, `de`, `es`, `it`),
  using the shared wording in `DESIGN.md`.
- Dependency versions live only in `gradle/libs.versions.toml`.
- Versions are automatic (git tags / release workflow). Don't edit `versionName`/`versionCode`.

## Web app and feed loader
- **Paused since 2026-10-17** (`docs/decisions.md`): their CI job and npm updates are commented
  out and Cloudflare no longer needs to build them. Don't change `web/` or `worker/` unless the
  owner asks to resume; the sync format in `docs/sync.md` stays the same, so they still fit.
- `web/` is the web app (React, TypeScript, Vite, Tailwind, shadcn-style parts in
  `src/components/ui/`), `worker/` the Cloudflare Worker that loads feeds for it. The Android
  rules above apply in spirit: logic in `web/src/lib/` with Vitest tests, all text in
  `web/src/lib/i18n.ts` in every language, no analytics or tracking, new npm packages only after
  asking. The sync file format is shared with Android (`docs/sync.md`): change both together.
- Before finishing a change there, run in that folder: `npm ci`, `npm test`, `npm run build`
  (web, also `npm run lint`) or `npm run typecheck` (worker).

## Keep docs in sync
- Behaviour change → update `docs/` and, if it touches data or permissions, `PRIVACY.md`.
- Notable decision (new library, architecture choice, minSdk change, …) → add a dated entry to
  `docs/decisions.md`.
- Structure: `docs/structure.md`. Design and flow: `docs/architecture.md`. What is tested and how:
  `docs/testing.md`. User guide: `docs/usage.md`. Building and releasing: `docs/installation.md`.
- `DESIGN.md`: only its "This app" section belongs to this repository. The other sections are
  copied from the master copy in the android-app template (`wimvdon/android-app`): change a
  shared rule there first, then copy it unchanged to every app (Tracker for Simkl, Time Tracker
  for Notion, Snoozer, RSS Reader). The design kit in `ui/theme/` (all files except `Theme.kt`)
  is identical in every app apart from the package line.

## Releasing (only when the owner asks)
Actions → Release → Run workflow on `main` → choose `patch`, `minor` or `major`.
See `docs/installation.md`, "Making a release".
