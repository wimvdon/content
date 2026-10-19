# Testing

## Running the tests

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 -m unittest discover -s scripts/tests
```

## What CI runs

On every pull request and push to `main`:

| Check | Command | Catches |
|---|---|---|
| Release script tests | `python3 -m unittest discover -s scripts/tests` | Version maths, keystore-secret decoding |
| Unit tests | `./gradlew testDebugUnitTest` | Logic bugs in plain-Kotlin classes (`app/src/test/`) |
| Lint | `./gradlew lintDebug` | Android API misuse, missing translations, accessibility, hard-coded text, … (errors fail the build) |
| Debug build | `./gradlew assembleDebug` | Compile errors; produces the APK to test on the phone |
| Release build | `./gradlew assembleRelease` | Problems caused by R8 minification / resource shrinking |
| Web app and feed loader | `npm test`, `npm run lint`, `npm run build` in `web/`; `npm test`, `npm run typecheck` in `worker/` | Parser ids that match Android, reading the Android app's sync file, merge (also settings), one sync with the account (conflicts merged again), the account client and live connection, OPML, HTML cleaning (picture sizes, lazy-loaded and tracking pictures, paragraph breaks), embed cards, Instapaper's lists in the reader, items (one per link, memberships, tags), the library in IndexedDB (only changes written, older libraries read) and Saved without an account on the items (a migrated sample, save, like, archive, tags, delete), per-item sync (the same cases as Android's `ItemSyncTest`, `test/syncRows.test.ts`), when to load the full article, the full-article cache, reader text settings, mark as read on scroll; the Worker's origin check, address rules, redirects, size and type limits, and its accounts (device keys, versions and conflicts, encryption, sign-out and delete; item rows: changes since a version, conflicts, deletions, pages, encrypted under ids that don't show the key) |

Reports (test results, lint HTML) are attached to every CI run as the `reports` artifact, also
when the run fails.

## What is covered

| Test | Covers |
|---|---|
| `FeedParserTest` | RSS 2.0, RSS 1.0, Atom (relative links, xml:base, xhtml/text content), namespaced look-alike elements, HTML entities, ids for items without guid or link, rejecting web pages, no external entities |
| `FeedDatesTest` | RFC 822 and ISO 8601 variants, time zones, invalid dates |
| `HtmlTest` | Clean-up for the reader, plain text, snippets, entities, escaping |
| `FeedDiscoveryTest` | Address normalising (https, feed://), feed links in a page, common paths |
| `FeedFinderTest` | Address → feed with a fake web: direct feed, linked feed, common path, no feed, errors |
| `ArticleExtractorTest` | Finding the article in pages through Readability4J (with and without `<article>`, split sections, a box per paragraph as on VRT NWS, look-alike containers with ads in between as on Ars Technica), dropping menus, sidebars, comments, scripts, hidden parts, the repeated title, the byline and the comments link, unclosed paragraphs, glossary words in buttons, a div per paragraph with its links, entities, unsafe links, charsets, when a feed text counts as a summary (short, or a teaser that links to its own article) |
| `TextLayoutTest` | Article text as reader blocks: a block per paragraph (also from divs and double line breaks), headings, bulleted, numbered and nested lists, quotes, code |
| `FullArticlesTest` | Loaded full articles are kept in the cache and not downloaded again; ones kept by an older extractor are extracted again and removed; a page that can't be downloaded fails |
| `ArticleBlocksTest` | Splitting articles into text, pictures (captions, srcset, lazy-loaded, tracking pixels skipped, https) and embeds (YouTube, Vimeo, videos, others); first picture; same picture in other sizes; a caption written twice in a row shows once |
| `OpmlTest` | Import with folders and duplicates, an Inoreader export with folders, export round trip with folders, non-OPML files |
| `LibraryTest` | Merging downloads (read state kept, duplicates, limits), sorting, Unread filter, counts, mark read (and undo), remove feed, rename feed (kept through refreshes), a whole refresh merged at once, future dates; folders: add, edit, remove, move feeds, folder selection, OPML import into folders and export |
| `LibraryItemsTest` | Highlighting a feed article puts it on the reading list, notes change and highlights go (#102); a saved feed article tagged with its feed's folder (#120: existing spelling reused, not twice, nothing outside folders); one item per link (#106): links written another way are one item, saving a feed article makes its item without a copy, saving again brings it back to read, archive keeps the bookmark, keep as bookmark keeps the reading list place, remove bookmark keeps the reading list place, read again, tags cleaned and renamed (joined), lists newest first, removing a feed keeps its items |
| `LibraryRowsTest` | The library to database rows and back unchanged (order, tabs, line breaks, tags, highlights), broken rows repaired, only changed articles and items written, an empty database filled |
| `SavedItemsTest` | Moving a sample of the old files into the database: feeds, read marks and saved articles kept, links written another way joined |
| `LibraryCodecTest` | Storage round trip with folders, tabs, line breaks and backslashes; missing folders and unknown icons; damaged input |
| `ScrollReadTest` | Mark as read on scroll: which articles have scrolled past (unread only, not the top one) |
| `JsonTest` | JSON round trip (escapes, emoji, numbers, nesting), whitespace, broken text |
| `SyncDocumentTest` | Sync file round trip (with settings, the same text as the web app), other or newer files left alone, broken entries skipped, taking folders and feeds (not read marks) from the library, a version 2 file's read marks read but never written |
| `ItemSyncTest` | Per-item sync between two devices: items and read marks arrive, **no article text in the payload**, changes to different parts of an item both stay, tags as a set and highlights per highlight, this device wins the same field but an edit beats a removal, removals and unread again arrive, marks of articles not here yet arrive read, old marks leave the account without making anything unread, syncing again saves nothing, pages, rows of unknown kinds left alone, the account's answers |
| `SyncMergeTest` | Three-way merge: changes and removals from either side, this device wins but an edit beats a removal, order from the side that reordered, first sync without base; a version 2 file's read marks kept for articles not here; applying the result to the library; syncing twice changes nothing; settings per setting, and a device without synced settings taking the file's |
| `LocalSavedTest` | The file of Saved without an account from before #106 read back (moved in once), unreadable text starts empty |
| `AccountSyncTest` | One sync with the account: the first device's library becomes the document, nothing saved when nothing changed, another device's save in between merged in (with the right base), newer documents left alone; reading the server's answers and problems; the live connection's address, messages and retry delays; the stored base with its version |
| `SyncSettingsTest` | Settings to shared names and back, Save/Share targets not synced, unknown names kept, bad values ignored, the Instapaper sign-in of older versions dropped |
| `SyncTimingTest` | When a sync is due: never synced, or more than ten seconds ago (also when the clock went back) |
| `SiteIconTest` | Choosing a site's icon: largest size, apple-touch-icon, relative and http addresses, skipping SVG and inline icons, home page and favicon.ico fallback |
| `IconCatalogTest` | Reading the icon file, search order (name, words, categories, related words), suggestions, old icon names, and that the bundled file has every suggested and migrated icon |
| `ArticleDaysTest` | Grouping the list by local day, time zones |
| `MixedSearchTest` | Search's ranking: the whole query in a title above single words above text-only matches, feed and reading list results in one list (then newest), the All/Feeds/Reading list chips; `ItemSearchTest`: the reading list searched by title, website, description, tags, note and highlights, title matches first; the web app's `test/search.test.ts` checks the ranking too |
| `AnchorsTest` | Highlights anchored by quote (#102): quote, surroundings and occurrence kept; found again in the same text; typographic variants (curly quotes, dashes, ellipsis, ligatures, soft hyphens, no-break spaces) and, last, letter case don't matter; repeated quotes told apart by their surroundings even when text was added before, else by occurrence; a changed quote found roughly between its surroundings or by its first and last words; not found is reported, never dropped; the match rate |
| `ArticleTextTest` | The reader's paragraphs as one text: a selection found in its own paragraph; highlights drawn in each paragraph they touch, approximate ones marked |
| `HighlightSyncTest` | Highlights and a fake service: made once with their position, a changed note made again, a deleted one deleted there, past the plan's limit kept and marked not copied and retried a day later, another failure keeps what was done; taking in made-there highlights (new, linked to a same passage, deleted there); links stored with the service rows |
| `MatchTestTest` | The #102 matching report counts per text source and sums over articles; the Markdown table |
| `BookmarksTest` | Bookmarks (#108): a link's type from its address (videos, repositories but not a code host's own pages, a site's home page as a tool), articles suggested for the reading list and other links for Bookmarks, adding with collection, note and tags (keeping the reading list place and the first date), the collection and type filter (newest first) and a collection that is gone let go, renaming (joining) and removing collections, the save sheet to either place, Read again from the start (it stays a bookmark), removing (an item on the reading list stays; otherwise all of it goes), editing; `BookmarkSearchTest`: the Bookmarks chip and collections in search |
| `ReadingListsTest` | The reading list's lists, the Liked chip and tags, a tag the list no longer has let go, reading times and minutes left, an item shown like its feed article (or with its description), its source (feed or website with favicon) |
| `ServiceSyncTest` | The reading list and a fake service: the whole account on the first sync (folders as tags), adding, archiving, liking and tagging sent once, removing sent (a bookmark stays), changes on both sides to other fields both kept, Reader wins the same field, deleted at the service leaves the reading list unless changed here, removed here but changed there stays, a link the service has joins it, a failure keeps everything and the rest goes next time, progress, entries stored and read back |
| `InstapaperReadingListTest` | Instapaper as the reading list's service, with recorded answers: `since` page by page with deletions and folders as tags, the first sync asks for everything, adding with tags and archive, only changes sent (taking the folder's tag off moves it home), parsed text only for the developer's own account (asked once), problems mapped |
| `PageInfoTest` | A page's title, description and picture (Open Graph first, https pictures only) for links saved without a title |
| `ArticleSearchTest` | Search: every word, case and accents ignored, title, text, feed name and author, title matches first, removed feeds left out |
| `FeedArrangementTest` | Manage page order: rows per folder, dragging feeds between folders and out of them, forbidden moves, reordering folders |
| `SettingsTest` | Text size limits, reader and theme darkness (and Auto dark), Save/Share targets (storage and the old Save setting), defaults |

## Strategy

Everything that decides something is plain Kotlin and tested on the JVM. Network code
(`FeedFetcher`), DataStore wiring and Compose screens are thin and checked by hand on the phone.
No device tests (instrumented/UI tests) are set up: they need an emulator, which makes CI slow and
flaky. Add them only if a decision in `decisions.md` says so.

### Writing tests
- Every logic change gets a test, in `app/src/test/kotlin/`, mirroring the package.
- Test names describe behaviour in words: `` fun `merge keeps read state of known articles`() ``.
- Network-dependent logic takes the download as a function (see `FeedFinder`), so tests pass a
  fake.

## Manual checks on the phone

For each pull request (before or after it is merged: merging only waits for green CI), install the
debug APK (see [installation.md](installation.md), "Testing a pull request") and follow the steps in the pull request description. In addition, check every now
and then (always before a release):

- [ ] Add a feed by website address and by feed address; articles appear.
- [ ] Open an article, change every text setting (font, size, spacing, background), go back: the
      list keeps its scroll position and the article shows as read.
- [ ] In an article, tap the title: the page opens in the browser. Aa › Auto dark on, background
      Sepia, phone in dark mode: the page is dark; in light mode: sepia.
- [ ] Open an article from a summary-only feed (e.g. `theverge.com`): the full article loads.
      ⋮ › Show feed text / Show full article switches; Settings › Reader › Load full articles off
      stops it.
- [ ] **Backup:** with a cable and `adb`, `adb shell bmgr backupnow be.wimvandonck.reader`,
      uninstall, reinstall, `adb shell bmgr restore be.wimvandonck.reader` (or set up a
      new phone from backup): feeds and settings come back.
- [ ] **Pictures:** list rows show thumbnails; the reader shows the picture above the title and
      pictures between paragraphs; a YouTube embed shows a card with a preview that opens YouTube.
      Settings › Reader › Show images off hides them all.
- [ ] **Sync** (needs `ACCOUNT_KEY` on the Worker, `docs/account.md`, and the Instapaper sign-in
      below): signed in, Settings › Account says "Last synced just now". Sign in on the web app (or
      a second phone) with the same Instapaper account: the same feeds, folders and read marks
      appear. Read an article on one with both open: within a few seconds it is read on the other,
      without refreshing. Rename a feed: the same. Delete synced data: both say to sign in again.
- [ ] **Read later** puts the article on the reading list (or sends it to the app chosen in Settings ›
      Save and share); **Share** opens the share menu or the chosen app. An uninstalled app falls back
      to the share menu. Signed in to Instapaper, Settings › Save and share has no Save button row.
- [ ] **List:** at app start the list shows loading placeholders, never "No feeds yet" first.
      Swipe an article right past a third: a tick, it becomes read, then unread; swipe one left: "Added
      to reading list" and it is on the reading list; a short swipe does nothing; swiping never opens the ☰ menu. The Unread chip in its own row under the folder tabs (not in the top bar) switches unread only (filled, check) / all.
      Scroll down: the title, the folder tabs and the Unread chip slide away together; scroll up a
      little: they come back; pulling down at the very top refreshes, and the spinner stays in place
      until the refresh is done (no jump back up and down again), also on the reading list. Settings › Article list › View: Small,
      Medium and Large each look right, with and without pictures.
- [ ] **Website icons:** after a refresh, feeds show their site's icon in the list, ☰ menu,
      manage page and reader, on a light tile; in dark mode dark logos are still visible; a feed
      without one shows its first letter.
- [ ] **Manage feeds and folders:** drag a feed into another folder and above another feed; drag
      a folder above another; the drawer and folder tabs follow the new order. Tap a feed, rename
      it, refresh: the name stays; clear it: the feed's own name comes back after a refresh.
- [ ] **Search:** the magnifier at the top right of Feeds opens Search with the keyboard up; typing
      *cafe* finds articles with "Café" in the title (first) or the text; two words find articles with
      both; nothing found shows "Nothing found"; opening a result and going back shows the same results.
      The magnifier is on the reading list too; a word from a reading list article's title, website,
      tag or note finds it (labelled Reading list, archived ones too), and tapping it opens it with ♥,
      Keep and Archive. A bookmark's collection finds it too (labelled Bookmark; it opens its page);
      the Bookmarks chip shows only bookmarks.
- [ ] **Navigation bar:** a bar at the bottom has Feeds, Reading list and Bookmarks (a rail at the left in
      landscape on a tablet). The Mark all read and Add link buttons and messages sit above the bar.
- [ ] **Navigation:** the ☰ drawer has the same sections on Feeds and on the reading list; the
      section you're in is open and the other folded shut. Tapping a section's name folds it shut and
      open (folded, it shows its count).
      In a folder, a feed or the reading list, Back goes to All; on All, Back closes the app.
- [ ] **Reading list:** To read and Archive tabs, the Liked chip and a chip per tag filter it; the
      count and "about … min" sit above; the newest article with a picture is a large card. Add link
      (the button) adds an address and the page's title appears. In the reader: ♥ fills; Keep opens
      the sheet with Keep as bookmark chosen (collection, note, tags); Archive opens it with Archive
      chosen; Finished reading at the end of the text opens it too; ⋮ › Tags and Remove from reading
      list (asks first). Leave an article halfway and open it again: it opens where you were, and the
      list shows a bar and the minutes left. ☰ › Collections and tags renames and removes tags.
- [ ] **Bookmarks:** keep an article (Keep, a collection "Research", a note); in Bookmarks it shows
      as a card with the note, under All and the Research tab, and in the drawer with its count. The
      type chips (Articles, Tools, Repos, Videos) filter; the button at the top switches to a list.
      Add bookmark with `github.com/owner/repo` (Bookmark chosen, a Repo card); share a YouTube link
      to Reader (Bookmark chosen) and an article (Reading list chosen). ⋮ › Keep as bookmark on a feed
      article. Tap a bookmark: Open opens the tab, Read again opens it in the reader and it is To
      read again, the pen edits, ⋮ › Remove bookmark asks first. ☰ › Collections and tags renames and
      removes a collection (its bookmarks stay).
      The Undo message after Mark all as read shows above the folder bar.
- [ ] **Mark as read on scroll** on: scroll down the Unread list; the articles that leave the
      top of the screen are marked read (their dot goes) but stay until you refresh.
- [ ] Import and export OPML (for example an Inoreader export); folders come along both ways.
- [ ] **Manage feeds** (☰): create a folder, search an icon (e.g. *soccer*) and pick it, move feeds into it (they show under it); the bottom bar appears, its
      tabs show only that folder's articles; Mark all read (round button) only marks that
      section and Undo restores it; removing the folder keeps its feeds and hides the bar.
- [ ] **Dark mode** and **Settings › Appearance**: switch Theme and Dynamic color. Text stays
      readable, status bar icons stay visible (also on sepia and dark reader pages).
- [ ] **Rotation**: the screen keeps its state.
- [ ] **Language**: Settings › System › Languages › App languages › Reader › each language.
      All text is translated.
- [ ] **Large text**: Settings › Display › Font size at maximum. Nothing is cut off.
- [ ] **Back** gesture/button behaves as expected (also with the drawer and Text sheet open).
- [ ] **No connection**: pull to refresh shows how many feeds failed; stored articles still open.
- [ ] After a release: install the release APK over the previous release. It updates without
      losing feeds.

## Reading a crash from a release build
Release builds are minified, so stack traces show short names like `a.b.c`. Download
`rss-reader-<version>-mapping.txt` from the matching GitHub Release and decode the trace with R8's
`retrace` (Android SDK `cmdline-tools/latest/bin/retrace`):
```sh
retrace rss-reader-<version>-mapping.txt stacktrace.txt
```
Or ask Claude Code to do it: give it the stack trace and the version.

## Instapaper (by hand, on the released app)

Sign-in needs the real Instapaper, the Worker's secret and a verified App Link, so it can't be tested
in CI. Use the signed release build, not a debug APK (see `instapaper.md`).

- [ ] Settings › Account › **Sign in with Instapaper** opens Instapaper; approving returns to the app
      with "Signed in to Instapaper", and Settings shows your user name.
- [ ] Right after signing in, everything in the Instapaper account appears on the reading list
      (archived ones under Archive, folders as tags), and what was on the reading list appears in
      Instapaper. ☰ › Connected services says the reading list is synced with your account.
- [ ] **Read later** on a feed article: it appears in Instapaper's Unread list within seconds.
      Archive, like, tag and remove it in Reader: each change shows in Instapaper. Do the same in
      Instapaper's own app and pull down on the reading list: the change shows in Reader.
- [ ] Take a folder's tag off an article: it leaves that folder in Instapaper.
- [ ] On the owner's account an article without feed text shows "Instapaper text" above the title.
- [ ] Read an article halfway, leave it: Instapaper shows the same progress.
- [ ] **Sign out** returns Settings to "Sign in"; the reading list stays.
- [ ] Signed out: **Read later** keeps the article on the reading list; the sheet offers Like and
      tags; the articles are still there after restarting the app.
