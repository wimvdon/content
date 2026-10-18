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
        ├── debug/res/values/strings.xml   "Reader Dev" name for debug builds
        ├── main/
        │   ├── AndroidManifest.xml   Internet permission, Instapaper package visibility, activity
        │   ├── kotlin/be/wimvandonck/reader/
        │   │   ├── RssReaderApp.kt            Application: stores, background scope
        │   │   ├── data/
        │   │   │   ├── Library.kt             Feeds, articles, read state, merge (plain Kotlin)
        │   │   │   ├── LibraryCodec.kt        Library ⇄ text for storage
        │   │   │   ├── LibraryRepository.kt   DataStore, refresh, add feed, OPML
        │   │   │   ├── Settings.kt            Theme, reader style, Save target (plain Kotlin)
        │   │   │   ├── SettingsStore.kt       Preferences DataStore
│   │   │   └── FullArticles.kt        Full articles for summary-only feeds, cached
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
│   │   │   ├── ArticleExtractor.kt    Reader view for full articles
│   │   │   ├── Html.kt                Clean-up, plain text, snippets, entities
        │   │   │   └── Opml.kt                OPML import and export
        │   │   └── ui/
        │   │       ├── MainActivity.kt, Navigation.kt   Entry, back stack, wiring
        │   │       ├── ExternalApps.kt        Browser, share, Instapaper, Play Store
        │   │       ├── home/                  Article list, folder bar, feed drawer, Add feed dialog, messages
        │   │       ├── reader/                Reader screen, text settings, reader colours
        │   │       ├── feeds/                 Feeds and folders page, folder dialogs
        │   │       ├── settings/              Settings screen, Save/Share targets
        │   │       └── theme/                 Theme.kt (palette) + shared design kit (incl. EmptyState)
        │   ├── assets/                  material_symbols.txt (folder icons) and its licence
        │   └── res/
        │       ├── drawable/                  Launcher icon layers, Material icons
        │       ├── mipmap-anydpi/             Adaptive icon
        │       ├── values/ values-night/      Strings (English), colours, window theme
        │       ├── values-nl|de|fr|es|it/     Translations
        │       └── xml/                       Backup rules
        └── test/kotlin/be/wimvandonck/reader/
            ├── data/                          Library, codec and settings tests
            └── feed/                          Parser, dates, HTML, discovery, finder, OPML tests
```

Instapaper: `app/src/main/kotlin/be/wimvandonck/reader/instapaper/` (client, sign-in, list logic,
account), `ui/reader/SavedArticleControls.kt`, `ui/feeds/InstapaperManage.kt`, `ui/home/` (drawer and
list) and `ui/settings/AccountSettings.kt` (screens), `web/src/lib/instapaper.ts`,
`web/src/lib/instapaperLibrary.ts`, `web/src/lib/localSaved.ts` (Saved without an account),
`web/src/components/InstapaperActions.tsx` and
`InstapaperManage.tsx` (web), `worker/src/instapaper.ts` (token swap and the web
app's calls). See `docs/instapaper.md`.

Reader account (sync): `worker/src/account.ts` (accounts, encrypted document, live connection),
`app/src/main/kotlin/be/wimvandonck/reader/sync/`, `web/src/lib/account.ts` and
`web/src/lib/sync.ts`. See `docs/account.md` and `docs/sync.md`.
