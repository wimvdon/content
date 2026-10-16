# RSS Reader

## 📚 Documentation
| | |
|---|---|
| [⚙️ Architecture](docs/architecture.md) | System design, components and flow |
| [📁 Structure](docs/structure.md) | Project organization and responsibilities |
| [🚀 Installation](docs/installation.md) | Building, signing key, releases, GitHub settings |
| [🧠 Technical decisions](docs/decisions.md) | Trade-offs and design justifications |
| [📖 Usage guide](docs/usage.md) | Feeds, reading, text settings, Instapaper, settings |
| [🧪 Testing](docs/testing.md) | How to run tests and what they cover |

---

## Description

A simple, private RSS and Atom feed reader for Android.

- **What it does:** follows the websites you choose, lists their new articles, and shows them in a
  clean reader with your choice of font (sans or serif), text size, line spacing and background
  (theme, sepia or dark). The Save button sends an article to Instapaper (or any app through the
  share menu).
- **What problem it solves:** reading the sites you like without an account, ads, tracking or an
  algorithm. Your feed list and reading history stay on your phone.
- **Real use case:** add `theverge.com`, pull down in the morning to get the new articles, read a
  few in sepia with a serif font, and tap **Save** on a long one to read it later in Instapaper.

There is also a **web app** (`web/`) with the same feeds, folders and read articles, synced
with the phone through your own Google Drive (`docs/sync.md`); see `web/README.md` and
`worker/README.md` to publish it.

English, Dutch, French, German, Spanish and Italian. Android 12+. See the
[privacy policy](PRIVACY.md).

## Quick start

Install the latest `rss-reader-x.y.z.apk` from
[Releases](https://github.com/wimvdon/rss-reader/releases), open it, tap **Add feed** and type
the address of a website you like. To bring your feeds from another reader, export them there as
OPML and tap **Import OPML**.

## Technologies used

| Area | Technology |
|---|---|
| Language | Kotlin 2.4, coroutines, `StateFlow` |
| UI | Jetpack Compose, Material 3 (dynamic colour optional) |
| Networking | `HttpURLConnection` (HTTPS only), the platform XML parser for RSS, Atom and OPML |
| Pictures | Coil 3 with OkHttp (thumbnails, hero and inline pictures, video previews) |
| Storage | Jetpack DataStore (settings; feeds and articles in a small text file) |
| Build | Gradle 9.8 (Kotlin DSL, version catalog), AGP 9.4, R8 |
| CI/CD | GitHub Actions: tests, lint and a debug APK on every pull request; signed releases; Dependabot |
| Tests | JUnit 4 |

## Quick installation

1. Install [Android Studio](https://developer.android.com/studio) (brings the JDK and Android SDK).
2. Clone the repo and run `./gradlew installDebug` with a phone connected.

Full steps: [docs/installation.md](docs/installation.md).

## Architecture (summary)

A single-activity Compose app. `RssReaderApp` owns a `SettingsStore` and a `LibraryRepository`.
The repository keeps all feeds and articles as one immutable `Library` value in DataStore and
merges freshly downloaded feeds into it; screens render that state and send changes back through
it. Feed parsing, dates, HTML clean-up, OPML and the library logic are plain Kotlin and unit
tested. A small back stack replaces a navigation library. Details:
[docs/architecture.md](docs/architecture.md).

## Project structure

```
rss-reader/
├── .github/            CI and release workflows, PR template, Dependabot
├── CLAUDE.md           Rules for Claude Code sessions
├── DESIGN.md           Design language shared with the sibling apps
├── scripts/            Release helpers (versioning, keystore secret) with tests
├── app/src/main/kotlin/io/github/impca201/rssreader/
│   ├── RssReaderApp.kt Application: settings, library, background scope
│   ├── data/           Library (feeds, articles, read state), settings, storage
│   ├── feed/           Download, RSS/Atom parsing, dates, HTML, feed discovery, OPML
│   └── ui/             Compose screens (list, reader, settings), navigation, theme
└── app/src/test/       JVM unit tests
```

Full tree: [docs/structure.md](docs/structure.md).

## Before the first release

These steps involve secrets, so only the owner can do them (see
[docs/installation.md](docs/installation.md)):

- [ ] Create a signing key and add the four repository secrets ("Signing key").
- [ ] Back up the keystore and both passwords ("Backing up the key"). If you lose them, the app
      can never be updated again.
- [ ] Apply the GitHub settings ("GitHub settings").
- [ ] Make the first release ("Making a release").
