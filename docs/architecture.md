# Architecture

## Overview

RSS Reader is a single-activity Jetpack Compose app with no server and no account. It downloads
the feeds the user subscribes to, keeps them on the phone, and shows them in a list and a reader.

```
MainActivity ─ RssReaderAppTheme ─ RssReaderNavigation (back stack: Home › Reader / Settings)
                                        │ state ▲          │ clicks
                                        ▼       │          ▼
RssReaderApp ── SettingsStore ── DataStore "settings" (Preferences)
             └─ LibraryRepository ── DataStore "library.txt" (Library ⇄ LibraryCodec)
                        │
                        └─ FeedFetcher (HTTPS) → FeedParser / FeedFinder / Opml (plain Kotlin)
```

## Components

| Component | Responsibility |
|---|---|
| `RssReaderApp` | Application object: owns the stores and a process-wide coroutine scope, so a refresh survives leaving a screen. |
| `data/Library.kt` | The whole state of the user's reading: `Feed`s and `Article`s with read state. Immutable; every change (merge a download, mark read, remove a feed) returns a new `Library`. Plain Kotlin, unit tested. |
| `data/LibraryCodec.kt` | Turns a `Library` into a small tab-separated text format and back, tolerant of damaged lines. |
| `data/LibraryRepository.kt` | Stores the `Library` with DataStore, refreshes all feeds (four at a time), adds feeds, imports and exports OPML, and remembers which feeds failed. |
| `data/SettingsStore.kt`, `Settings.kt` | Theme, dynamic colour, reader style, Save target and the Unread filter, in Preferences DataStore. |
| `feed/FeedFetcher.kt` | The only network code: HTTPS GET with timeouts, a 10 MB limit and at most 5 redirects. |
| `feed/FeedParser.kt` | RSS 2.0/0.9x, RSS 1.0 (RDF) and Atom into `ParsedFeed`, with the platform XML parser (external entities off). Retries with HTML entities fixed. |
| `feed/FeedFinder.kt`, `FeedDiscovery.kt` | From what the user typed to a feed: normalise the address, try it as a feed, then feeds the page links to, then common paths. |
| `feed/ArticleExtractor.kt` | "Reader view": reads a web page with its own forgiving HTML reader, scores containers by their paragraphs (Readability-style) and returns the article as simple HTML. |
| `data/FullArticles.kt` | Loads full articles for summary-only feeds and keeps them in the cache folder (300 most recent). |
| `feed/FeedDates.kt`, `Html.kt`, `Opml.kt` | Date formats of real feeds; HTML clean-up, plain text and snippets; OPML read and write. |
| `ui/Navigation.kt` | Back stack, wiring between screens and stores, Add feed dialog, OPML file pickers, Instapaper and share hand-offs. |
| `ui/home/` | Article list with Unread/All filter, pull-to-refresh, loading placeholders, empty states; feed drawer; Add feed dialog. |
| `ui/reader/` | Reader screen, Text sheet, reader colours (theme, sepia, dark). `ReaderStyleControls` is shared with Settings. |
| `ui/settings/` | Settings screen built from the shared `SettingsKit`. |
| `ui/theme/` | The app's palette and the shared design kit (see `DESIGN.md`). |
| `ui/ExternalApps.kt` | Browser, share sheet, Instapaper and Play Store intents. |

## Data flow

1. On start the repository reads `library.txt`; the list shows stored articles at once.
2. The first time the list appears after the app starts (and on pull-to-refresh), every feed is
   downloaded, parsed and merged: new articles are added unread, known ones keep their read state,
   and each feed keeps its newest 200 articles.
3. Screens observe `StateFlow`s (`library`, `settings`, `refreshing`, `problems`) and call
   repository functions on the app scope; the new state flows back to the screens.
4. Opening an article marks it read. If its feed text is short (under 1 500 characters) and
   Load full articles is on, the reader fetches the article's page and shows the extracted article.
5. Save hands the link to Instapaper with an `ACTION_SEND`
   intent aimed at its package; the share menu is the fallback.

## Patterns

- **Logic in plain Kotlin** (no Android types) so it runs in JVM unit tests: `Library`,
  `LibraryCodec`, `Settings`, everything in `feed/` except `FeedFetcher`.
- **Composables only display state and forward clicks**; screens get a state class and an actions
  class (`HomeState`/`HomeActions`, `SettingsState`/`SettingsActions`, `ReaderActions`).
- **One source of truth** per kind of data: DataStore, exposed as `StateFlow`.
- **No navigation library:** a `Screen` back stack with a shared-axis transition, and a
  `SaveableStateHolder` so the list keeps its scroll position while an article is open.

## Why this design

See [decisions.md](decisions.md): no database or JSON library is needed for a few thousand
articles; no image or HTTP library keeps the app small and the privacy story simple; no background
work means no battery use when the app is closed.
