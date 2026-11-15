# Content

Feeds and saved articles in one app, on Android and the web. Formerly called Reader. Its app ID changed from `be.wimvandonck.reader` to `be.wimvandonck.content` (2026-10-23) and then to `to.wim.content` (2026-10-31): each time the new app installs next to the old one, which you then uninstall.

## Documentation
| | |
|---|---|
| [Architecture](docs/architecture.md) | System design, components and flow |
| [Structure](docs/structure.md) | Project organization and responsibilities |
| [Installation](docs/installation.md) | Building, signing key, releases, GitHub settings |
| [Technical decisions](docs/decisions.md) | Trade-offs and design justifications |
| [Usage guide](docs/usage.md) | Feeds, reading, text settings, Instapaper, settings |
| [Content account](docs/account.md) | Signing in with an email link, device keys, the account's calls |
| [Sync](docs/sync.md) | What syncs between the phone and the web app, and how |
| [Newsletters](docs/newsletters.md) | Each tag's email address and its Newsletters feed |
| [Instapaper](docs/instapaper.md) | Connecting, saving, the saved list, and the one-time setup |
| [Testing](docs/testing.md) | How to run tests and what they cover |

---

## Description

Content follows the websites and newsletters you choose and keeps the articles you want to read
later, on an Android phone and in the browser.

- **Feeds:** add a website (`theverge.com`) or a feed address, or import an OPML file from another
  reader. New articles show in **Feed**, sorted by tag, and open in a clean reader with your choice
  of font (sans or serif), text size, line spacing and background (theme, sepia or dark).
- **Saved:** **Save** keeps an article to read later, also one you add by its address or share to
  Content from another app. Instapaper can be connected to keep Saved and Instapaper in step
  ([docs/instapaper.md](docs/instapaper.md)).
- **Newsletters:** signed in, every tag gets its own email address. Newsletters sent to it arrive
  as articles under that tag ([docs/newsletters.md](docs/newsletters.md)).
- **Share:** send an article to another Content user, who gets it in their Inbox, or to any app.
- **Real use case:** add `theverge.com` and subscribe to a newsletter with your Tech tag's address,
  read both in the morning in sepia with a serif font, and tap **Save** on a long article to finish
  it on your laptop in the evening.

### Content account and sync

Sign in with your email address: the app emails a link, and opening it signs you in (no
password). Your feeds, tags, read articles, Saved and settings then stay the same on every phone
and in the web app. Without an account, everything stays on the device. How it works:
[docs/account.md](docs/account.md) and [docs/sync.md](docs/sync.md).

### Web app and browser extension

- **Web app** (`web/`, at [content.wim.to](https://content.wim.to)): does the same as the Android
  app, in the browser. Publishing it: [web/README.md](web/README.md).
- **Feed loader** (`worker/`): a Cloudflare Worker that loads feeds for the web app, keeps the
  Content accounts and receives the newsletters. See [worker/README.md](worker/README.md).
- **Browser extension** (`extension/`, Chrome, Edge and Firefox): loads article pages for the web
  app from your own browser, so sites that block the feed loader and sites you pay for open in
  full, and adds **Save to Content** to the toolbar and the right-click menu. See
  [extension/README.md](extension/README.md).

English, Dutch, French, German, Spanish and Italian. Android 12+. No ads, analytics or tracking:
see the [privacy policy](PRIVACY.md).

## Quick start

Install the latest `content-x.y.z.apk` from
[Releases](https://github.com/wimvdon/content/releases) and open it. Sign in with your email
address, or tap **Or start on this device only**. Then tap **+** › **RSS feed** and type the
address of a website you like, or import your feeds from another reader as OPML. In the browser,
open [content.wim.to](https://content.wim.to) and sign in with the same address.

## Technologies used

| Area | Technology |
|---|---|
| Language | Kotlin 2.4, coroutines, `StateFlow` |
| UI | Jetpack Compose, Material 3 (dynamic colour optional) |
| Networking | `HttpURLConnection` (HTTPS only), the platform XML parser for RSS, Atom and OPML |
| Pictures | Coil 3 with OkHttp (thumbnails, hero and inline pictures, video previews) |
| Storage | Jetpack DataStore (settings; feeds and articles in a small text file) |
| Notifications | Firebase Cloud Messaging (opt-in, for articles shared with you) |
| Build | Gradle 9.8 (Kotlin DSL, version catalog), AGP 9.5, R8 |
| CI/CD | GitHub Actions: tests, lint, the web build and a debug APK on every pull request; signed releases; Dependabot |
| Web app | React, TypeScript, Vite, Tailwind, shadcn/ui-style parts (Radix) |
| Feed loader | Cloudflare Worker with Durable Objects, Email Routing for newsletters |
| Tests | JUnit 4 (Android), Vitest (web app and feed loader) |

## Quick installation

1. Install [Android Studio](https://developer.android.com/studio) (brings the JDK and Android SDK).
2. Clone the repo and run `./gradlew installDebug` with a phone connected.

Full steps: [docs/installation.md](docs/installation.md).

## Architecture (summary)

A single-activity Compose app. `RssReaderApp` owns a `SettingsStore` and a `LibraryRepository`.
The repository keeps all feeds and articles as one immutable `Library` value in DataStore and
merges freshly downloaded feeds into it; screens render that state and send changes back through
it. Feed parsing, dates, HTML clean-up, OPML and the library logic are plain Kotlin and unit
tested. A small back stack replaces a navigation library. When signed in, `sync/` exchanges the
library with the Content account on the feed loader, in the shared format of
[docs/sync.md](docs/sync.md); the web app keeps the same library in the browser and syncs the same
way. Details:
[docs/architecture.md](docs/architecture.md).

## Project structure

```
content/
├── .github/            CI and release workflows, PR template, Dependabot
├── CLAUDE.md           Rules for Claude Code sessions
├── DESIGN.md           Design language shared with the sibling apps
├── PRIVACY.md          Privacy policy
├── docs/               Documentation
├── scripts/            Release helpers (versioning, keystore secret) with tests
├── app/src/main/kotlin/to/wim/content/
│   ├── RssReaderApp.kt Application: settings, library, background scope
│   ├── data/           Library (feeds, articles, read state, Saved, tags, newsletters), settings, storage
│   ├── feed/           Download, RSS/Atom parsing, dates, HTML, feed discovery, OPML
│   ├── sync/           Content account, sync with the feed loader, live updates
│   ├── instapaper/     Instapaper connection
│   ├── services/       Read-it-later services behind Saved
│   ├── push/           Notifications for shared articles (opt-in)
│   └── ui/             Compose screens, navigation, theme
├── app/src/test/       JVM unit tests
├── web/                The web app (see web/README.md)
├── worker/             Feed loader and Content accounts, Cloudflare Worker (see worker/README.md)
└── extension/          Browser extension for the web app (see extension/README.md)
```

Full tree: [docs/structure.md](docs/structure.md).
