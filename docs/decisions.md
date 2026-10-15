# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-15: Article list views, swipe, filter in the top bar; refresh merged at once

Asked for by the owner after using v0.0.3:
- Three list views (Small without picture, Medium as before, Large card) in a new Settings ›
  Article list section, which also holds Mark as read on scroll.
- Swiping an article either way toggles read (`SwipeToDismissBox`, springs back; also an
  accessibility action).
- The Unread/All chips became one icon toggle in the top bar, with the unread count under the
  title: chips suit more than two options. Settings is only in the drawer, not also in ⋮.
- The folder bar's tabs have a fixed width (icon with name below) so switching no longer makes
  the bar change size and jump.
- A refresh used to merge each feed as soon as it was downloaded, so articles popped up in the
  middle of the list while reading. Now all feeds are downloaded first and merged in one update;
  dates in the future are clamped to now so they can't stick to the top.

## 2026-10-15: Mark as read on scroll, off by default

Asked for by the owner. An article counts as read once it has scrolled off the top of the list
(not merely shown), and stays in the Unread list until it changes, like opened articles, so
scrolling back still finds it. Off by default so nothing is marked read without the user choosing
it.

## 2026-10-15: Folders, a floating folder bar and a Mark all read button

- Feeds can be put in folders, each with a title and an icon from a fixed set of 19 (drawn in the
  app, no icon library). A feed is in at most one folder. Asked for by the owner.
- Once a folder exists, a floating bar at the bottom of the list switches between Home (all feeds)
  and each folder. The bar only shows the selected tab's title, so many folders still fit; it
  scrolls sideways when they don't. Rejected: Material's `NavigationBar` (fixed, at most five
  items, not floating).
- Mark all read moved from the top bar to a floating action button above the bar. It marks only
  the section shown (Home, a folder, or one feed) and offers Undo.
- Storage: folders are a new record type in `library.txt` and feeds got a folder field; older files
  load unchanged. OPML import and export keep the folders (nested outlines), so moving from
  Inoreader or Feedly keeps them too.

## 2026-10-15: Save and Share buttons send to any app; one OPML import

- The Save and Share buttons each have a target in Settings › Save and share: one app, picked
  from the installed apps that accept shared text (the article goes straight there), or the share
  menu. Save defaults to Instapaper (as before; the old setting carries over), Share to the share
  menu. The manifest's `<queries>` now names the share intent instead of the Instapaper package,
  so the list can be shown (package visibility, not a permission).
- "Import from Inoreader" is gone: Import/Export OPML is universal and the hint names Inoreader
  and Feedly as examples. Asked for by the owner.

## 2026-10-15: Pictures with Coil; embeds as cards

The owner asked for article pictures as list thumbnails and reader hero, plus inline pictures and
embedded content. Added **Coil 3 + OkHttp** (the same versions Tracker for Simkl uses) to load
and cache pictures; writing our own loader (downsampling, memory and disk cache) would be more
code and more bugs. The reader stays native: the article is split into text, picture and embed
blocks (`ArticleBlocks`, tested) so the reader's font, size, spacing and sepia/dark page still
apply. Embedded players (YouTube, Vimeo, posts) are cards with a preview that open the content
in its app or the browser. Chosen by the owner over a WebView reader, which would play embeds
inline but run the sites' and players' scripts. Picture addresses are upgraded to https like
feeds; tracking pixels are skipped. Settings › Reader › Show images (on by default) turns
pictures off. Coil's caches live in the cache folder, so they are never backed up.

## 2026-10-15: Icon thicker, with more padding

After using v0.0.1 the owner found the icon too thin and too tight in the launcher shape. The
symbol is now about 44dp wide instead of 50dp (more white space around it) with thicker waves and
dot (owner's pick "T3" from mockups).

## 2026-10-15: Settings screens make room for the keyboard

The app targets Android 15+ and draws edge to edge, so the system no longer shrinks the screen when the keyboard opens. `SettingsScaffold` (shared design kit, same change in every app) now adds the keyboard to its content insets, so a focused text field scrolls into view instead of hiding behind the keyboard. Rejected: opting out of edge-to-edge (not possible for apps targeting Android 15+).

## 2026-10-15: Import from Inoreader through its OPML export

The owner asked for an Inoreader import. It is a guided dialog: open inoreader.com, export the
feeds as OPML there, pick the file here (the existing OPML import). Chosen by the owner over
signing in with Inoreader's API, which would need a registered Inoreader app (client ID and
secret), an OAuth sign-in screen and inoreader.com as a new network destination. Trade-off:
read and starred states are not imported, only the feeds.

## 2026-10-15: Icon without a box

The owner chose the feed symbol on its own, with the blue gradient, over the first design (a
gradient rounded square with a white symbol inside), after comparing mockups, drawn with the proportions of the
classic feed icon the owner supplied (thick waves, round ends) so it stays clear at small sizes. This departs from the shared icon rule in
`DESIGN.md` ("one bold shape with one white detail inside"); recorded under "This app" there, and
the master copy in the template should mention the exception.

## 2026-10-15: Full articles in the reader, Android backup

- **Full articles:** for feeds that only carry a summary (under 1 500 characters of text), the
  reader downloads the article's page and shows just the article, like a browser's reader view.
  On by default (Settings › Reader › Load full articles), switchable per article. Asked for by the
  owner. The extraction (`ArticleExtractor`) is our own small HTML reader with Readability-style
  scoring, in plain Kotlin and unit tested. Rejected: jsoup + Readability4J (two new libraries for
  one feature) and a WebView (runs the site's scripts, ads and trackers). Trade-off: some unusual
  page layouts extract badly; the feed text and "Read on website" stay one tap away.
- **Where full articles live:** files in the app's cache folder (300 most recent), not DataStore.
  They can be downloaded again, Android may clear the cache when space runs low, and the cache is
  never backed up, which keeps backups small.
- **Backup:** Android's built-in Auto Backup and device-to-device transfer (`allowBackup` with
  `data_extraction_rules.xml`) cover the feed list, articles, read state and settings. Nothing in
  the app is secret, so nothing is excluded. Rejected: a key/value `BackupAgent` (more code, and
  Auto Backup already handles DataStore files).

## 2026-10-15: RSS Reader, built from the template

- **Identity:** app name "RSS Reader", application ID `io.github.impca201.rssreader` (the
  `io.github.impca201.*` scheme of Tracker for Simkl and Snoozer). The ID can still change until
  the first release; after that it never may. Debug builds are called "RSS Reader Dev", like the
  sibling apps.
- **Internet permission:** an RSS reader has to download feeds. The app only contacts the
  addresses the user adds (or types), with no account, cookies, analytics or third-party services.
  `PRIVACY.md` says so. Asked for by the owner ("build me a full RSS reader").
- **HTTPS only:** `http://` and `feed://` addresses are upgraded to `https://`, and Android's default
  block on unencrypted traffic stays on. Rejected: allowing cleartext traffic for all hosts, which
  would let anyone on the same Wi-Fi change articles. Revisit if a feed the owner needs has no
  HTTPS.
- **No new libraries beyond the sibling apps':** DataStore (already in the catalog, as `CLAUDE.md`
  asks) and `lifecycle-runtime-compose` (used by Snoozer and Tracker for Simkl, for
  `collectAsStateWithLifecycle`). Downloads use `HttpURLConnection`, feeds and OPML the platform's
  XML parser, article text Compose's `AnnotatedString.fromHtml`. Rejected: OkHttp, Room,
  kotlinx.serialization, Coil and a WebView. They would add size and setup, and a WebView would
  run the sites' scripts.
- **Storage:** the whole library (feeds, articles, read state) is one immutable `Library` value
  stored with DataStore in a small tab-separated text format (`LibraryCodec`, tested). At most 200
  articles per feed and 200 000 characters per article keep it small. Rejected: Room (a database
  and annotation processor for a few thousand rows) and a JSON library (a new dependency).
- **Reader without images:** article HTML is cleaned (scripts, embeds, images, inline colours and
  fonts removed) and shown as styled text, so the reader's font, size, spacing and sepia or dark
  background always apply, and no tracking pixels load. "Read on website" opens the full page.
  Images would need an image library (Coil); left for a later decision.
- **Save sends to Instapaper:** the reader's Save button sends the link with an `ACTION_SEND`
  intent aimed at the Instapaper app (`com.instapaper.android`), the same as choosing Instapaper in
  the share menu. No Instapaper account or API key in this app. Settings › Save for later can
  switch Save to the share menu; without Instapaper installed, the share menu opens. The manifest
  declares Instapaper in `<queries>` (package visibility, not a permission) so Settings can tell
  whether it is installed.
- **Refresh only while the app is used:** once per app start and on pull-to-refresh. Rejected for
  now: background refresh and new-article notifications (WorkManager, a new dependency, and
  battery use).
- **Languages:** English, Dutch, French, German, Spanish and Italian, like the sibling apps (the
  template has only English and Dutch). French, Spanish and Italian plurals include `many`.
- **Theme:** seed `#0091EA` (the icon's middle stop), tonal palette generated the same way as
  Snoozer's (primary from the seed's chroma, neutrals at chroma 4/8, tertiary from the icon's sky
  blue). Dynamic color off by default, per `DESIGN.md`.

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-13: Template baseline

- **Language/UI:** Kotlin + Jetpack Compose + Material 3, single `app` module. This is Google's
  recommended, mainstream stack with the most documentation and examples.
- **Tool versions (latest stable on this date):** Gradle 9.8.1, Android Gradle Plugin 9.4.1
  (uses its built-in Kotlin support, so no separate `kotlin-android` plugin), Kotlin 2.4.21
  (Compose compiler plugin), Compose BOM 2026.09.00. Dependabot keeps these current.
- **minSdk 31 (Android 12).** The owner installs apps on their own, recent phone, so older
  Android versions aren't worth the extra code and testing. Android 12 brings Material You dynamic
  colour (no fallback code path), the modern splash screen and per-app language settings. It still
  covers the large majority of active devices. Lower it only if someone with an older phone needs
  the app.
- **compileSdk / targetSdk 37 (Android 17)**, the latest stable API level. The minor SDK release
  37.2 exists but isn't needed by anything yet.
- **Java 17 bytecode, JDK 21 to build.** JDK 21 is what Claude Code cloud sessions provide, so local
  checks match CI exactly.
- **Version numbers:** release `X.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, passed in by the release
  workflow. All other builds use `git describe --tags --long` (e.g. `1.2.0-3-gabc1234`), falling back to
  `0.0.0-dev`, so a dev build can never be mistaken for a release. Version logic lives in
  `scripts/release_version.py` (unit tested) and `app/build.gradle.kts`.
- **Debug builds use `applicationIdSuffix = ".debug"`** so a pull request's APK installs next to the
  released app instead of conflicting with it.
- **Releases are GitHub Releases with a signed APK**, published by `gh release create` (preinstalled
  on runners) instead of a third-party action, which means one fewer dependency with write access.
  The R8 mapping file is attached to each release.
- **Signing secrets** are base64 and decoded by a tolerant, tested script (`scripts/decode_keystore.py`)
  that never prints the secret. The key password is checked with a small Java program
  (`scripts/VerifySigningKey.java`), because `keytool` ignores the key password of PKCS12 keystores and
  the build would otherwise fail later with an unclear error.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT licensed). The default
  "enhanced" provider is closed source under separate terms of use.
- **CI artifacts:** the debug APK is uploaded unzipped (`archive: false`) so it can be installed on a
  phone straight from the download.
- **Languages:** English (default) + Dutch, as a working example of translations. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.
