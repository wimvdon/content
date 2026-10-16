# Architecture

## Overview

RSS Reader is a single-activity Jetpack Compose app with no server and no account. It downloads
the feeds the user subscribes to, keeps them on the phone, and shows them in a list and a reader.

```
MainActivity ─ RssReaderAppTheme ─ RssReaderNavigation (back stack: Home › Reader / Settings / Feeds)
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
| `data/Library.kt` | The whole state of the user's reading: `Folder`s (title and icon), `Feed`s (each in at most one folder) and `Article`s with read state. Immutable; every change (merge a download, mark read, remove a feed) returns a new `Library`. Plain Kotlin, unit tested. |
| `data/LibraryCodec.kt` | Turns a `Library` into a small tab-separated text format and back, tolerant of damaged lines. |
| `data/LibraryRepository.kt` | Stores the `Library` with DataStore, refreshes all feeds (four at a time), adds feeds, imports and exports OPML, and remembers which feeds failed. |
| `data/SettingsStore.kt`, `Settings.kt` | Theme, dynamic colour, reader style, Save and Share targets, list options (Unread filter, Mark as read on scroll), in Preferences DataStore. |
| `feed/FeedFetcher.kt` | The only network code: HTTPS GET with timeouts, a 10 MB limit and at most 5 redirects. |
| `feed/FeedParser.kt` | RSS 2.0/0.9x, RSS 1.0 (RDF) and Atom into `ParsedFeed`, with the platform XML parser (external entities off). Retries with HTML entities fixed. |
| `feed/FeedFinder.kt`, `FeedDiscovery.kt` | From what the user typed to a feed: normalise the address, try it as a feed, then feeds the page links to, then common paths. |
| `feed/ArticleExtractor.kt` | "Reader view": reads a web page with its own forgiving HTML reader, scores containers by their paragraphs (Readability-style) and returns the article as simple HTML. |
| `feed/ArticleBlocks.kt` | Splits article HTML into text, picture and embed blocks; finds article pictures; turns iframes into embed cards (YouTube, Vimeo, video, other). |
| `ui/reader/ArticleBlocksView.kt` | Hero picture, text blocks, inline pictures and embed cards. Pictures load with Coil (`RssReaderApp` is its `SingletonImageLoader.Factory`). |
| `data/FullArticles.kt` | Loads full articles for summary-only feeds and keeps them in the cache folder (300 most recent). |
| `data/ScrollRead.kt` | Mark as read on scroll: which list articles have scrolled off the top. |
| `feed/SiteIcon.kt` | Picks a website's icon from its home page (`<link rel="icon">`, `apple-touch-icon`, largest first; no SVG), or `/favicon.ico`. The repository looks it up once per feed (after a refresh or adding a feed) and stores it with the feed. `ui/home/FeedIcon.kt` shows it with a letter fallback. |
| `data/IconCatalog.kt` | Folder icons: the Material Symbols set in `assets/material_symbols.txt` (name, category, path data per icon; Apache 2.0, licence next to it), with search and the names of the icons folders had before. `RssReaderApp.iconCatalog` reads it once in the background; `ui/home/FolderIcons.kt` turns an icon into a vector. |
| `feed/FeedDates.kt`, `Html.kt`, `Opml.kt` | Date formats of real feeds; HTML clean-up, plain text and snippets; OPML read and write. |
| `ui/Navigation.kt` | Back stack, wiring between screens and stores, Add feed dialog, OPML file pickers, Instapaper and share hand-offs. |
| `ui/home/` | Article list with Unread/All filter, pull-to-refresh, loading placeholders, empty states, Mark all read button (for the section shown: All, a folder or a feed); folder tabs (`FolderTabs`); feed drawer grouped by folder; Add feed dialog. |
| `ui/reader/` | Reader screen, Text sheet, reader colours (theme, sepia, dark). `ReaderStyleControls` is shared with Settings. |
| `data/FeedArrangement.kt` | The manage page as a flat list of folder headers and feeds: which moves are allowed while dragging, and turning the dragged order back into folder order, feed order and each feed's folder. |
| `ui/feeds/` | Manage feeds and folders page: drag and drop (own `LazyColumn` with a drag handle per row), feed dialog (rename, folder, remove) and folder dialog (`FolderDialogs.kt`), OPML import/export. |
| `ui/settings/` | Settings screen built from the shared `SettingsKit`: a link to Manage feeds and folders, Article list, Reader, Save and Share targets, Appearance. |
| `ui/theme/` | The app's palette and the shared design kit (see `DESIGN.md`). |
| `ui/ExternalApps.kt` | Browser, share sheet, Instapaper and Play Store intents. |

## Data flow

1. On start the repository reads `library.txt`; the list shows stored articles at once.
2. The first time the list appears after the app starts (and on pull-to-refresh), every feed is
   downloaded and parsed, and then all results are merged in one update (`Library.mergeAll`), so
   the list changes once, in date order. Dates in the future count as now. In the merge new articles are added unread, known ones keep their read state,
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
