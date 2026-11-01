# Project structure

```
rss-reader/
├── .editorconfig                     Editor rules (4 spaces, 120 columns)
├── .gitattributes                    LF everywhere; *.bat → CRLF; keystores/images binary
├── .github/
│   ├── dependabot.yml                Weekly Gradle and GitHub Actions update PRs
│   ├── pull_request_template.md      Plain-language PR description + checklist
│   └── workflows/
│       ├── ci.yml                    Script tests, unit tests, lint, builds, debug APK
│       └── release.yml               Release rules → verify signing key → build APK → publish GitHub Release
├── CLAUDE.md                         Rules for Claude Code sessions
├── DESIGN.md                         Design language shared with the sibling apps
├── PRIVACY.md                        Privacy policy
├── README.md
├── docs/                             This documentation
├── scripts/                          Release helpers, shared with the android-app template
│   ├── release_version.py            Tag parsing, next version, versionCode, "is newer"
│   ├── decode_keystore.py            Decodes the keystore secret tolerantly, explains failures
│   ├── VerifySigningKey.java         Checks the key password before the build
│   └── tests/                        unittest suites for both Python scripts
├── build.gradle.kts                  Root build: plugin declarations
├── settings.gradle.kts               Repositories, single :app module, APK name prefix
├── gradle.properties                 JVM args, build and configuration cache
├── gradle/
│   ├── libs.versions.toml            Version catalog (every dependency version)
│   └── wrapper/                      Gradle wrapper
├── web/                              The web app (static site; see web/README.md)
│   ├── src/lib/                      Feeds, library, sync, account, storage, full articles, embeds, reader style (plain TypeScript, tested)
│   ├── src/components/               Screens: sidebar, article list, reader, dialogs; ui/ = shadcn-style parts
│   └── test/                         Vitest tests
├── worker/                           Feed loader and Reader accounts (Cloudflare Worker; see worker/README.md)
└── app/
    ├── build.gradle.kts              App ID, versioning, signing, languages, lint, dependencies
    ├── proguard-rules.pro            Stack trace settings for release builds
    └── src/
        ├── debug/res/values/strings.xml   "Content Dev" name for debug builds
        ├── main/
        │   ├── AndroidManifest.xml   Internet permission, Instapaper package visibility, activity
        │   ├── kotlin/to/wim/content/
        │   │   ├── RssReaderApp.kt            Application: stores, background scope
        │   │   ├── data/
        │   │   │   ├── Library.kt             Feeds, articles, read state, merge (plain Kotlin)
        │   │   │   ├── Items.kt, LibraryItems.kt  One item per link: Saved's place, folder, tags (plain Kotlin)
        │   │   │   ├── Saved.kt              Saved's tabs, filters, folders, filing (plain Kotlin)
        │   │   │   ├── Topics.kt             Topics: feed folders and saved articles' tags in step (plain Kotlin)
        │   │   │   ├── LibraryRows.kt         Library ⇄ database rows, what a change writes
        │   │   │   ├── db/                    Room: tables, queries, the database
        │   │   │   ├── LibraryRepository.kt   Room, refresh, add feed, OPML
        │   │   │   ├── Settings.kt            Theme, reader style, Save target (plain Kotlin)
        │   │   │   ├── ArticleFilter.kt       Hide articles with words (#277) (plain Kotlin)
        │   │   │   ├── SettingsStore.kt       Preferences DataStore
│   │   │   └── FullArticles.kt        Full articles for summary-only feeds, cached
        │   │   ├── push/                      Notifications when something is shared (#235): Firebase
        │   │   │   ├── PushNotifications.kt   On/off, token to the feed loader, the notification
        │   │   │   └── SharedMessagingService.kt  Firebase's pokes and new tokens
        │   │   ├── sync/
        │   │   │   ├── SyncDocument.kt        The sync file: folders, feeds, read marks (plain Kotlin)
        │   │   │   ├── SyncMerge.kt           Three-way merge with the last synced file (plain Kotlin)
        │   │   │   ├── Json.kt                Small JSON reader and writer (plain Kotlin)
        │   │   │   ├── SyncRound.kt           One sync: read, merge, save with the version (plain Kotlin)
        │   │   │   ├── AccountClient.kt       The Reader account's calls (OkHttp); problems
        │   │   │   ├── LiveUpdates.kt         Live connection: another device saved (WebSocket)
        │   │   │   └── SyncManager.kt         When to sync, base and version, status
        │   │   ├── feed/
        │   │   │   ├── FeedFetcher.kt         HTTPS downloads
        │   │   │   ├── FeedParser.kt          RSS / RDF / Atom parser, safe XML parser
        │   │   │   ├── FeedFinder.kt          Address → feed (with discovery)
        │   │   │   ├── FeedDiscovery.kt       Address normalising, <link rel="alternate">
        │   │   │   ├── FeedDates.kt           RFC 822 and ISO 8601 dates
        │   │   │   ├── ArticleBlocks.kt       Article → text, picture and embed blocks
        │   │   │   ├── Embeds.kt              Known players (YouTube, Spotify, …): the card, the player and its page
│   │   │   ├── ArticleExtractor.kt    Reader view for full articles (plain parts)
│   │   │   ├── WebArticleExtractor.kt Runs the web app's extractor in a hidden WebView
│   │   │   ├── Html.kt                Clean-up, plain text, snippets, entities
        │   │   │   └── Opml.kt                OPML import and export
        │   │   └── ui/
        │   │       ├── MainActivity.kt, Navigation.kt   Entry, back stack, wiring
        │   │       ├── ExternalApps.kt        Browser, share, Instapaper, Play Store
        │   │       ├── DirectShare.kt         The people in other apps' share menus; folders in the launcher
        │   │       ├── home/                  Article list, search bar, chip rows, Add feed dialog, messages, inbox
        │   │       ├── reader/                Reader screen, text settings, reader colours, Save and share, Content's share sheet
        │   │       ├── feeds/                 Manage topics: topic list, topic page, topic and feed dialogs
        │   │       ├── settings/              Settings screen, Save/Share targets, Instapaper
        │   │       └── theme/                 Theme.kt (palette) + shared design kit (incl. EmptyState)
        │   ├── assets/                  material_symbols.txt (folder icons) and its licence
        │   └── res/
        │       ├── drawable/                  Launcher icon layers, Material icons
        │       ├── mipmap-anydpi/             Adaptive icon
        │       ├── values/ values-night/      Strings (English), colours, window theme
        │       ├── values-nl|de|fr|es|it/     Translations
        │       └── xml/                       Backup rules
        └── test/kotlin/to/wim/content/
            ├── data/                          Library, codec and settings tests
            └── feed/                          Parser, dates, HTML, discovery, finder, OPML tests
```

Saved and Instapaper (#107, #109, #119): `data/Saved.kt` (tabs, filters, folders),
`data/ReadingLists.kt` (reading times, items as articles), `services/` (`ReadingListService`, `ServiceSync`, `ServiceManager`),
`instapaper/` (client, sign-in, account, `InstapaperReadingList`), `ui/home/SavedContent.kt`, `ui/reader/ItemSheets.kt`, `ui/reader/SavedBar.kt` (the reader's folder button, which opens Organise, #251), `ui/reader/HighlightSheets.kt` (a highlight and the list, #102),
`ui/settings/InstapaperSettings.kt` (Instapaper in Settings, and the import question), `data/ServiceImport.kt` (the answer to it). The web app: `web/src/lib/instapaper.ts`,
`web/src/lib/saved.ts` (Saved: tabs, filters, folders, cards, like Android's `Saved.kt`), `web/src/lib/serviceSync.ts` and `instapaperSync.ts` (Instapaper in the background), `web/src/lib/articleExtractor.ts` (reader view with Defuddle, which the Android app runs too, bundled from `web/src/android/`; sample pages in `testdata/articles/`),
`web/src/components/SavedActions.tsx`, `worker/src/instapaper.ts`
(token swap and the web app's calls). See `docs/instapaper.md`.

Reader account (sync): `worker/src/account.ts` (accounts, encrypted document, live connection),
`app/src/main/kotlin/to/wim/content/sync/`, `web/src/lib/account.ts` and
`web/src/lib/sync.ts`. See `docs/account.md` and `docs/sync.md`.

## `extension/`

The optional browser extension for the web app (#168): it loads pages from the user's own browser and saves the page being viewed. See `extension/README.md`.
