# Rules for Claude Code sessions in this repository

Content (formerly Reader), an Android app built from the `android-app` template (a private repository). The
owner is **not a developer and does not review code**. CI, tests, lint and these rules
are the safety net. Follow them every time.

## Workflow
1. **One focused change per branch and pull request.** Start a new branch from an up-to-date
   `main` (`git fetch origin main && git checkout -b <branch> origin/main`). Never reuse an old
   or merged branch for a new change.
2. **Before finishing, run and fix everything:**
   ```
   ./gradlew testDebugUnitTest lintDebug
   ```
   CI builds the debug and release APKs on every push, so don't build them here unless a step below asks for it.
   If you touched `scripts/` or the workflows, also run `python3 -m unittest discover -s scripts/tests`.
   Then push and make sure CI on the pull request is green.
3. **Write the pull request description in plain language** (fill in `.github/pull_request_template.md`):
   what changed and why, in words a non-developer understands, and **exactly what to tap on the
   phone to test it** (step by step, with what they should see). The owner tests by installing
   the debug APK from the pull request's CI run (Checks → CI → Artifacts). It installs as a
   separate app, "Content Dev" (`to.wim.content.debug`), next to the released one.
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
  `feed/FeedFetcher.kt`, for pictures through Coil (`RssReaderApp.newImageLoader`), and for sync in
  `sync/AccountClient.kt` and `sync/LiveUpdates.kt` (the Reader account on the feed loader, see
  `docs/account.md` and `docs/sync.md`), for Instapaper in `instapaper/` (see `docs/instapaper.md`),
  and Firebase Cloud Messaging's own connection for notifications in `push/` (opt-in, #235).
- **All user-visible text goes in `app/src/main/res/values/strings.xml`**, never hard-coded. In
  Compose, read strings with `stringResource` or `LocalResources`, not `LocalContext`.
  Add the translation to **every** `values-xx/strings.xml` (currently: `nl`, `fr`, `de`, `es`, `it`),
  using the shared wording in `DESIGN.md`.
- Dependency versions live only in `gradle/libs.versions.toml`.
- Versions are automatic (git tags / release workflow). Don't edit `versionName`/`versionCode`.

## Web app and feed loader
- **Feature parity: the phone and the web app do the same things.** Every new feature or behaviour
  change goes into both apps in the same pull request, each in its own design system (Material 3
  on Android, shadcn/ui on the web: see "Design" below; parity is about behaviour, never looks).
  Never build something for one app only. The only exceptions are
  things one platform can't do (Android's share menu, Direct Share, the browser's keyboard
  shortcuts); name the exception in the pull request and in `docs/decisions.md`. If an existing
  feature turns out to be missing in one app, say so and add it.
- `web/` is the web app (React, TypeScript, Vite, Tailwind, shadcn-style parts in
  `src/components/ui/`), `worker/` the Cloudflare Worker that loads feeds for it. The Android
  rules above apply in spirit: logic in `web/src/lib/` with Vitest tests, all text in
  `web/src/lib/i18n.ts` in every language, no analytics or tracking, new npm packages only after
  asking. The sync file format is shared with Android (`docs/sync.md`): change both together.
- Before finishing a change there, run in that folder: `npm ci`, `npm test`, `npm run build`
  (web, also `npm run lint`) or `npm run typecheck` (worker).

## Design
- **Two design languages, never mixed.** Android is **Material 3**; the web app is **shadcn/ui**
  (Radix with Tailwind, in `web/src/components/ui/`). The apps share what they *do* and the brand
  (icon, seed blue, wording), never what they *look like*. "As on Android" in a web change means the
  same behaviour, not the same widget.
- **Android UI work** (new screens, changes, reviews): use the `material-design-3-ui` skill in
  `.claude/skills/` together with `DESIGN.md`; where they differ, `DESIGN.md` (the owner's
  choices) wins.
- **Web UI work: no Material on the web.** Build from the parts in `web/src/components/ui/` and
  shadcn/ui's look. Never use the Material skill there, and never bring in Material's components,
  shapes, colour roles or motion: no filled tonal buttons, pill-shaped (`rounded-full`) buttons or
  fields, segmented buttons with a check, filter chips with a check, a pill behind the selected
  navigation item, a round 56 px search bar, snackbars, floating action buttons or floating
  toolbars, text buttons in `primary` for Cancel or Done, `primary` section headings, a tinted
  `accent`, 12–28 px corners, Expressive shapes or bouncy springs. Their shadcn counterparts are
  listed in `DESIGN.md`, "Other platforms". A feature that needs a new kind of part on the web takes
  shadcn/ui's version of it (or its nearest look), and says so in the pull request.

## Keep docs in sync
- Behaviour change → update `docs/` and, if it touches data or permissions, `PRIVACY.md`.
- Notable decision (new library, architecture choice, minSdk change, …) → add a dated entry to
  `docs/decisions.md`.
- Structure: `docs/structure.md`. Design and flow: `docs/architecture.md`. What is tested and how:
  `docs/testing.md`. User guide: `docs/usage.md`. Building and releasing: `docs/installation.md`.
- `DESIGN.md`: only its "This app" section belongs to this repository. The other sections are
  copied from the master copy in the android-app template (`wimvdon/android-app`): change a
  shared rule there first, then copy it unchanged to every app (Tracker for Simkl, Time Tracker
  for Notion, Snoozer, Reader). The design kit in `ui/theme/` (all files except `Theme.kt`)
  is identical in every app apart from the package line.

## Releasing (only when the owner asks)
Actions → Release → Run workflow on `main` → choose `patch`, `minor` or `major`.
See `docs/installation.md`, "Making a release".
