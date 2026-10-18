# Architecture

## Overview

Reader is a single-activity Jetpack Compose app with no server and no account. It downloads
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
| `feed/ArticleExtractor.kt` | "Reader view": reads a web page with jsoup, prepares it (glossary words in buttons, hidden parts, a div per paragraph, articles split over look-alike containers), lets Readability4J (Firefox Reader View's algorithm) find the article, drops the repeated title, byline and comments link, and returns the article as simple HTML. |
| `feed/ArticleBlocks.kt` | Splits article HTML into text, picture and embed blocks; finds article pictures; turns iframes into embed cards (YouTube, Vimeo, video, other). |
| `ui/reader/ArticleBlocksView.kt` | Hero picture, text blocks, inline pictures and embed cards. Pictures load with Coil (`RssReaderApp` is its `SingletonImageLoader.Factory`). |
| `data/FullArticles.kt` | Loads full articles for summary-only feeds and keeps them in the cache folder (300 most recent). The file names carry a version (`PREFIX`), raised when the extractor changes, so articles kept from before are extracted again. |
| `data/ScrollRead.kt` | Mark as read on scroll: which list articles have scrolled off the top. |
| `feed/SiteIcon.kt` | Picks a website's icon from its home page (`<link rel="icon">`, `apple-touch-icon`, largest first; no SVG), or `/favicon.ico`. The repository looks it up once per feed (after a refresh or adding a feed) and stores it with the feed. `ui/home/FeedIcon.kt` shows it with a letter fallback. |
| `data/IconCatalog.kt` | Folder icons: the Material Symbols set in `assets/material_symbols.txt` (name, category, path data per icon; Apache 2.0, licence next to it), with search and the names of the icons folders had before. `RssReaderApp.iconCatalog` reads it once in the background; `ui/home/FolderIcons.kt` turns an icon into a vector. |
| `feed/FeedDates.kt`, `Html.kt`, `Opml.kt` | Date formats of real feeds; HTML clean-up, plain text and snippets; OPML read and write. |
| `ui/Navigation.kt` | Back stack, wiring between screens and stores, Add feed dialog, OPML file pickers, Instapaper and share hand-offs. |
| `ui/home/` | Article list with Unread/All filter, pull-to-refresh, loading placeholders, empty states, Mark all read button (for the section shown: All, a folder or a feed); folder tabs (`FolderTabs`); a header (top app bar, tabs, Unread | All) that slides away on scroll (`HomeHeader`); the drawer (`FeedDrawer`) with Feeds and Instapaper as sections that fold open and shut; Add feed dialog. |
| `ui/reader/` | Reader screen, Text sheet, reader colours (theme, sepia, dark). `ReaderStyleControls` is shared with Settings. |
| `web/` | The web app (see `web/README.md`): `src/lib/` holds the logic (feed parser with Android-compatible ids, library, sync merge and the Reader account with its live connection, IndexedDB storage, feed loading through the Worker, Readability with a full-article cache, DOMPurify with embed cards, reader text settings, mark as read on scroll) and `src/lib/app.ts` the state the screens subscribe to; `src/components/` the screens. |
| `worker/` | The Cloudflare Worker the web app loads feeds and pages through (CORS): one address per request, https and public hosts only, feeds and pages only, the web app's origin only, rate limited, nothing stored or logged. It also swaps the Instapaper sign-in code and keeps the Reader accounts: one Durable Object per account with the encrypted sync document and a live connection per open device (`src/account.ts`, `docs/account.md`). |
| `sync/` | Sync with the user's Reader account (see `docs/sync.md`, `docs/account.md`): `SyncDocument` (the shared JSON document), `SyncMerge` (three-way merge with the last synced document, applied to the `Library` in one DataStore update), `SyncRound` (read, merge, save with the version read, merge again after another device's save), `AccountClient` (the account's calls over OkHttp, with the device key from the Instapaper sign-in), `LiveUpdates` (the WebSocket that says when another device saved), and `SyncManager` (syncs while the app is on screen: on coming back, live, every minute only while the live connection is down (`whileVisible`), a second after a change, on refresh and on request, with the timing in `SyncTiming`; keeps the base and its version; tells `LibraryRepository` which new articles were already read elsewhere). |
| `data/FeedArrangement.kt` | The manage page as a flat list of folder headers and feeds: which moves are allowed while dragging, and turning the dragged order back into folder order, feed order and each feed's folder. |
| `ui/feeds/` | Manage feeds (feeds and folders) and Manage Instapaper (its folders and tags, `InstapaperManage.kt`). Manage feeds: drag and drop (own `LazyColumn` with a drag handle per row), feed dialog (rename, folder, remove) and folder dialog (`FolderDialogs.kt`), OPML import/export. |
| `ui/settings/` | Settings screen built from the shared `SettingsKit`: Account (Instapaper sign-in and sync, `AccountSettings.kt`), Article list, Reader, Save and Share targets, Appearance. |
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
4. Opening an article marks it read. If its feed text looks like a summary (under 1 500
   characters, or a teaser with a link to the article itself, such as "Read full article") and
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

## Instapaper

`instapaper/` holds everything about the user's Instapaper account, in plain Kotlin where possible:
`InstapaperClient` (API v2 over OkHttp, with pure parsing helpers), `InstapaperAuth` (sign-in address,
callback parsing, token swap through the Worker), `InstapaperModel` (one list at a time, the folders and
tags, the article open in the reader and every change, tested on the JVM), `InstapaperLists` (a
saved article as a list/reader `Article`, which list it belongs in after a change) and
`InstapaperAccount` (the token in its own DataStore file). The navigation keeps one model per
sign-in: the drawer (`FeedDrawer`) and the home list show its lists, the reader its open article
with `ui/reader/SavedArticleControls.kt`, and Manage feeds and folders its folders and tags
(`ui/feeds/InstapaperManage.kt`). `ui/settings/InstapaperSettings.kt` signs in and out. See
`instapaper.md`.
