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
└── app/
    ├── build.gradle.kts              App ID, versioning, signing, languages, lint, dependencies
    ├── proguard-rules.pro            Stack trace settings for release builds
    └── src/
        ├── debug/res/values/strings.xml   "RSS Reader Dev" name for debug builds
        ├── main/
        │   ├── AndroidManifest.xml   Internet permission, Instapaper package visibility, activity
        │   ├── kotlin/io/github/impca201/rssreader/
        │   │   ├── RssReaderApp.kt            Application: stores, background scope
        │   │   ├── data/
        │   │   │   ├── Library.kt             Feeds, articles, read state, merge (plain Kotlin)
        │   │   │   ├── LibraryCodec.kt        Library ⇄ text for storage
        │   │   │   ├── LibraryRepository.kt   DataStore, refresh, add feed, OPML
        │   │   │   ├── Settings.kt            Theme, reader style, Save target (plain Kotlin)
        │   │   │   ├── SettingsStore.kt       Preferences DataStore
│   │   │   └── FullArticles.kt        Full articles for summary-only feeds, cached
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
        │   │       ├── settings/              Settings screen, folder editor, Save/Share targets
        │   │       └── theme/                 Theme.kt (palette) + shared design kit
        │   └── res/
        │       ├── drawable/                  Launcher icon layers, Material icons
        │       ├── mipmap-anydpi/             Adaptive icon
        │       ├── values/ values-night/      Strings (English), colours, window theme
        │       ├── values-nl|de|fr|es|it/     Translations
        │       └── xml/                       Backup rules
        └── test/kotlin/io/github/impca201/rssreader/
            ├── data/                          Library, codec and settings tests
            └── feed/                          Parser, dates, HTML, discovery, finder, OPML tests
```
