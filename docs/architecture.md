# Architecture

## Overview

Reader is a single-activity Jetpack Compose app with no server and no account. It downloads
the feeds the user subscribes to, keeps them on the phone, and shows them in a list and a reader.

```
MainActivity ─ RssReaderAppTheme ─ RssReaderNavigation (back stack: Home › Reader / Settings / Feeds)
                                        │ state ▲          │ clicks
                                        ▼       │          ▼
RssReaderApp ── SettingsStore ── DataStore "settings" (Preferences)
             └─ LibraryRepository ── Room "library.db" (Library ⇄ LibraryRows, data/db)
                        │
                        └─ FeedFetcher (HTTPS) → FeedParser / FeedFinder / Opml (plain Kotlin)
```

## Components

| Component | Responsibility |
|---|---|
| `RssReaderApp` | Application object: owns the stores and a process-wide coroutine scope, so a refresh survives leaving a screen. |
| `data/Library.kt` | The whole state of the user's reading: `Folder`s (title and icon), `Feed`s (each in at most one folder), `Article`s with read state and `Item`s (Saved). Immutable; every change (merge a download, mark read, remove a feed) returns a new `Library`. Plain Kotlin, unit tested. |
| `data/Items.kt`, `LibraryItems.kt` | One item per link (#106): Saved's place (to read, archived, a folder), liked, tags, note, highlights and reading progress. `ItemKeys` normalises links, so a feed article and its saved item share one key. The changes (save, archive, keep as bookmark, tags) are functions on `Library`. |
| `data/db/`, `data/LibraryRows.kt` | The Room database (`library.db`): tables for folders, feeds, articles, items, item texts (per source, on the device only) and a small `meta` table. `LibraryRows` turns rows into the `Library` and back, and `LibraryChanges` works out what a change needs to write. |
| `data/LibraryRepository.kt` | Holds the `Library` in memory and in the database (one change at a time, `update`), refreshes all feeds (four at a time), adds feeds, imports and exports OPML, and remembers which feeds failed. On first start it moves in the files of older versions (`LegacyLibrary`: `library.txt` through `LibraryCodec`, and Saved without an account). |
| `data/SettingsStore.kt`, `Settings.kt` | Theme, dynamic colour, reader style, Save and Share targets, list options (Unread filter, Mark as read on scroll), in Preferences DataStore. |
| `feed/FeedFetcher.kt` | The only network code: HTTPS GET with timeouts, a 10 MB limit and at most 5 redirects. |
| `feed/FeedParser.kt` | RSS 2.0/0.9x, RSS 1.0 (RDF) and Atom into `ParsedFeed`, with the platform XML parser (external entities off). Retries with HTML entities fixed. |
| `feed/FeedFinder.kt`, `FeedDiscovery.kt` | From what the user typed to a feed: normalise the address, try it as a feed, then feeds the page links to, then common paths. |
| `feed/ArticleExtractor.kt` | "Reader view": reads a web page with jsoup, lets Readability4J (Firefox Reader View's algorithm) find the article as it is (#236: no changes before or after it), drops the heading that repeats the title, and returns the article as simple HTML. The web app's `web/src/lib/articleExtractor.ts` takes the same steps (with Mozilla's Readability.js); the sample pages in `testdata/articles/` keep the two the same. |
| `feed/ArticleBlocks.kt` | Splits article HTML into text, picture and embed blocks; finds article pictures; turns iframes into embed cards (YouTube, Vimeo, video, other). |
| `ui/reader/ArticleBlocksView.kt` | Hero picture, text blocks, inline pictures and embed cards. Pictures load with Coil (`RssReaderApp` is its `SingletonImageLoader.Factory`). |
| `data/FullArticles.kt` | Loads full articles for summary-only feeds and keeps them in the cache folder (300 most recent). The file names carry a version (`PREFIX`), raised when the extractor changes, so articles kept from before are extracted again. |
| `data/Anchors.kt` | Highlights anchored by quote (#102): making one from a selection, and finding it again in a text, exactly, roughly or not at all; typographic variants fold. The web app's `lib/anchors.ts` is the same. |
| `data/ArticleText.kt` | The reader's paragraphs as one text to anchor highlights in, and each highlight's marks per paragraph. The web app's `lib/highlightDom.ts` reads the page the same way. |
| `data/ScrollRead.kt` | Mark as read on scroll: which list articles that were on screen have scrolled off the top. |
| `data/MixedSearch.kt` | Search's one list: feed and Saved matches ranked together (whole query in the title, then words in the title, then newest), filtered by the All/Feeds/Saved chips; `SearchResults` is the Search tab's list (everything, newest first, without words) with its filters (`SearchFilters`: feed folder or feed; Saved folder, tag, type); `ItemSearch` searches Saved (title, website, description, folder, tags, note, highlights). The web app does the same in `web/src/lib/search.ts`. |
| `data/ArticleSearch.kt` | Search: the stored articles whose title, feed name, author or text hold every word of the query (case and accents ignored), title matches first. |
| `feed/SiteIcon.kt` | Picks a website's icon from its home page (`<link rel="icon">`, `apple-touch-icon`, largest first; no SVG), or `/favicon.ico`. The repository looks it up once per feed (after a refresh or adding a feed) and stores it with the feed. `ui/home/FeedIcon.kt` shows it with a letter fallback. |
| `data/IconCatalog.kt` | Folder icons: the Material Symbols set in `assets/material_symbols.txt` (name, category, path data per icon; Apache 2.0, licence next to it), with search and the names of the icons folders had before. `RssReaderApp.iconCatalog` reads it once in the background; `ui/home/FolderIcons.kt` turns an icon into a vector. |
| `feed/FeedDates.kt`, `Html.kt`, `Opml.kt` | Date formats of real feeds; HTML clean-up, plain text and snippets; OPML read and write. |
| `ui/Navigation.kt` | Back stack, wiring between screens and stores, Saved's actions, the Add feed dialog and the save sheet, OPML file pickers, share hand-offs. |
| `ui/home/` | Article list with the Unread and All filter chips, pull-to-refresh, loading placeholders, empty states, Mark all read button (for the section shown: All, a folder or a feed); folder tabs (`FolderTabs`); a header (top app bar, tabs, the chip row with the Unread and All chips) that slides away on scroll (`HomeHeader`); the navigation bar or rail (Feeds, Saved, Search; `MainNavigation`, shared by Home and Search); the drawer (`FeedDrawer`) with Feeds and Saved as sections that fold open and shut; Saved (`SavedContent`, `SavedTabRow`); swiping a feed article left saves it to To read; Add feed dialog; the search page (`SearchScreen`: feed articles and Saved in one list ranked by `data/MixedSearch.kt`, labelled Feed or Saved, with All/Feeds/Saved chips and that kind's filters; a tab of its own, #171). |
| `ui/reader/` | Reader screen, Text sheet, reader colours (theme, sepia, dark). `ReaderStyleControls` is shared with Settings. |
| `web/` | The web app (see `web/README.md`): `src/lib/` holds the logic (feed parser with Android-compatible ids, library with its items (`items.ts`, one per link, like Android's), search (`search.ts`, like Android's), Saved (`saved.ts`, the Android app's rules: tabs, filters, folders, cards), sync merge and the Reader account with its live connection, IndexedDB storage (`libraryStore.ts`: an article and an item per record), feed loading through the Worker, Readability with a full-article cache, DOMPurify with embed cards, reader text settings, mark as read on scroll) and `src/lib/app.ts` the state the screens subscribe to; `src/components/` the screens. |
| `worker/` | The Cloudflare Worker the web app loads feeds and pages through (CORS): one address per request, https and public hosts only, feeds and pages only, the web app's origin only, rate limited, nothing stored or logged. It also swaps the Instapaper sign-in code and keeps the Reader accounts: one Durable Object per account with the encrypted sync document and a live connection per open device (`src/account.ts`, `docs/account.md`).. Article pages (`?kind=page`) are asked for as a browser asks (its Accept header and the reader's languages); a site that refuses (401, 403, 429, 451, 503 or a Cloudflare bot check) gets a 403 "blocked" answer, which the web reader turns into "this site doesn't let the web app load it" with Read on website. |
| `sync/` | Sync with the user's Reader account (see `docs/sync.md`, `docs/account.md`): `SyncRows` and `ItemSync` (per-item sync of Saved and read marks: the rows, their three-way merge, and one round of reading changes and saving this device's), `SyncDocument` (the shared JSON document: folders, feeds, settings), `SyncMerge` (three-way merge with the last synced document, applied to the `Library` in one update), `SyncRound` (read, merge, save with the version read, merge again after another device's save), `AccountClient` (the account's calls over OkHttp, with the device key from the Instapaper sign-in), `LiveUpdates` (the WebSocket that says when another device saved), and `SyncManager` (syncs while the app is on screen: on coming back, live, every minute only while the live connection is down (`whileVisible`), a second after a change, on refresh and on request, with the timing in `SyncTiming`; keeps the base and its version; keeps what it last synced of the items in the database). |
| `data/FeedArrangement.kt` | The manage page as a flat list of folder headers and feeds: which moves are allowed while dragging, and turning the dragged order back into folder order, feed order and each feed's folder. |
| `ui/feeds/` | Manage feeds (feeds and folders) and Folders and tags (`TagsManage.kt`: rename and remove Saved's folders and tags). Manage feeds: drag and drop (own `LazyColumn` with a drag handle per row), feed dialog (rename, folder, remove) and folder dialog (`FolderDialogs.kt`), OPML import/export. |
| `ui/settings/` | Settings screen built from the shared `SettingsKit`: Account (Instapaper sign-in and sync, `AccountSettings.kt`), Article list, Reader, Save and Share targets, Appearance. |
| `ui/theme/` | The app's palette and the shared design kit (see `DESIGN.md`). |
| `ui/ExternalApps.kt` | Browser, share sheet, Instapaper and Play Store intents. |

## Data flow

1. On start the repository reads the database (`library.db`); the list shows stored articles at once.
2. The first time the list appears after the app starts (and on pull-to-refresh), every feed is
   downloaded and parsed, and then all results are merged in one update (`Library.mergeAll`), so
   the list changes once, in date order. Dates in the future count as now. In the merge new articles are added unread, known ones keep their read state,
   and each feed keeps its newest 200 articles.
3. Screens observe `StateFlow`s (`library`, `settings`, `refreshing`, `problems`) and call
   repository functions on the app scope; the new state flows back to the screens.
4. Opening an article marks it read. If its feed text looks like a summary (under 1 500
   characters, or a teaser with a link to the article itself, such as "Read full article") and
   Load full articles is on, the reader fetches the article's page and shows the extracted article.
5. Read later (the reader's Save) saves the link to To read (`addToReadingList`), the same
   item as the feed article, and opens the sheet to like and tag it (`AddedSheet`); set to another
   app, it hands the link over with an `ACTION_SEND` intent instead. A link without a title gets
   the page's own (`LibraryRepository.describe`, `feed/PageInfo.kt`).
6. Reader also takes shares (#97, #103): the exported `ui.ShareActivity` alias of
   `SaveFromTabActivity` accepts `ACTION_SEND` with `text/plain`, reads the first web link and a
   title (`instapaper/SharedLink.kt`) and shows only the save sheet (`SaveSheet`: folder, note,
   tags) on a see-through screen over the app that shared it; the rest of Reader doesn't open.
   Reader leaves the alias out of its own share menu (`EXTRA_EXCLUDE_COMPONENTS`).
7. Web links open in a custom tab (#95, `ExternalApps.openInTab`, AndroidX Browser) with a Save
   action button. Its `PendingIntent` (mutable, so the browser can add the page shown as the data)
   opens `SaveFromTabActivity` with the link and title Reader opened; `SharedLink.fromTab` keeps
   that title only while the tab still shows that page. That activity is see-through
   (`Theme.RssReader.Translucent`, not exported) and lands on top of the tab in Reader's task: it
   shows the save sheet over it for the page; saving or closing the sheet finishes it,
   back on the tab.

## Patterns

- **Logic in plain Kotlin** (no Android types) so it runs in JVM unit tests: `Library`,
  `LibraryCodec`, `Settings`, everything in `feed/` except `FeedFetcher`.
- **Composables only display state and forward clicks**; screens get a state class and an actions
  class (`HomeState`/`HomeActions`, `SettingsState`/`SettingsActions`, `ReaderActions`).
- **One source of truth** per kind of data: the library in Room, settings and the sign-in in DataStore, exposed as `StateFlow`.
- **No navigation library:** a `Screen` back stack with a shared-axis transition, and a
  `SaveableStateHolder` so the list keeps its scroll position while an article is open.

## Why this design

See [decisions.md](decisions.md): a database (Room) since the library holds saved articles with notes and highlights (#106), kept in memory as one value so the logic stays plain
Kotlin; no JSON library; no background work means no battery use when the app is closed.

## Saved and connected services

Saved (#107, #119) is the library's items with a reading list place (`data/Items.kt`,
`LibraryItems.kt`): each is in one place, To read, one folder (`Item.folder`, only while to read)
or the archive. `data/Saved.kt` gives its tabs and filters (`SavedTab`, `SavedFilter`: Liked, tags
that must all match, the type read from the link), folders and their counts, chips, saving from the
save sheet, filing, removing, renaming and removing folders, and `settle`, which moves bookmarks from
before #119 into folders when the library is read or a row syncs in. `data/ReadingLists.kt` gives
reading times and an item as a list/reader `Article` (with the feed article's text when the library
still has it). `ui/home/SavedContent.kt` shows it: To read, All and Archive with the feed list's
`ArticleRow`, a folder as notes (`NoteCard`, after Noter) in a staggered grid; `ui/home/FolderTabs.kt`
(`SavedTabRow`) its old tabs, `HomeBar.kt` the chip row; `ui/reader/ItemSheets.kt`
the sheets (added, tags, Finished reading with Archive or File, the save sheet with `FolderField`),
and `ReaderScreen` its buttons (`ReaderItem`) and reading progress.

`services/` connects a section to a service (#109): `ReadingListService` (the core every
reading-list service shares, the features it supports), `ServiceSync` (a three-way merge with the
service's articles as last seen, then sending what changed; failures keep everything and are tried
again), `ServiceManager` (when to sync, while the app is on screen; the service's own text). Only
`instapaper/InstapaperReadingList.kt` knows Instapaper (API v2 `since`, folders as tags, parsed text,
progress). `instapaper/` also holds `InstapaperClient` (API v2 over OkHttp, with pure parsing
helpers), `InstapaperAuth` (sign-in address, callback parsing, token swap through the Worker) and
`InstapaperAccount` (the token in its own DataStore file). Raindrop.io was a second
service until #179 removed it. Reader is the library and a service is somewhere it is also kept (#145): each
service has a scope (`data/ServiceScope.kt`: everything in Saved, or some folders, a synced
setting), and an item reaches every service whose scope covers it. Connecting waits for an import
(`ServiceSync.import`, once: everything or some folders, each article where it belongs); then
`ServiceSync.run` keeps the scope in step per field (rows are what was last seen or sent, so a value
is never sent back right after it came in; both sides changed, the later change wins). Leaving the
scope archives an article there, never deletes it. `ServiceManager` runs the import, the syncs, the count before a scope
change (`leaving`) and removing from one service only (`removeFrom`). `ui/settings/ServicesScreen.kt`
lists the services; `ServiceScreen` is one service's account, import and scope. See
`instapaper.md`.
