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
| Web app and feed loader | `npm test`, `npm run lint`, `npm run build` in `web/`; `npm test`, `npm run typecheck` in `worker/` | Parser ids that match Android, reading the Android app's sync file, merge (also settings), one sync with the account (conflicts merged again), the account client and live connection, OPML, HTML cleaning, embed cards, Instapaper's lists in the reader, when to load the full article, the full-article cache, reader text settings, mark as read on scroll; the Worker's origin check, address rules, redirects, size and type limits, and its accounts (device keys, versions and conflicts, encryption, sign-out and delete) |

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
| `LibraryCodecTest` | Storage round trip with folders, tabs, line breaks and backslashes; missing folders and unknown icons; damaged input |
| `ScrollReadTest` | Mark as read on scroll: which articles have scrolled past (unread only, not the top one) |
| `JsonTest` | JSON round trip (escapes, emoji, numbers, nesting), whitespace, broken text |
| `SyncDocumentTest` | Sync file round trip (with settings, the same text as the web app), other or newer files left alone, broken entries skipped, taking folders, feeds and read marks from the library, pruning old read marks |
| `SyncMergeTest` | Three-way merge: changes and removals from either side, this device wins but an edit beats a removal, order from the side that reordered, first sync without base, unread again; articles not here yet; applying the result to the library; syncing twice changes nothing; settings per setting, and a device without synced settings taking the file's |
| `InstapaperListsTest` | A saved article as a list/reader article (no read state, safe links), which Instapaper list it belongs in, a change updating the list |
| `InstapaperModelTest` | Lists, paging, archive, like, move, tags, delete, folders and tags management; the article open in the reader following changes |
| `AccountSyncTest` | One sync with the account: the first device's library becomes the document, nothing saved when nothing changed, another device's save in between merged in (with the right base), newer documents left alone; reading the server's answers and problems; the live connection's address, messages and retry delays; the stored base with its version |
| `SyncSettingsTest` | Settings to shared names and back, Save/Share targets not synced, unknown names kept, bad values ignored, the Instapaper sign-in of older versions dropped |
| `SyncTimingTest` | When a sync is due: never synced, or more than ten seconds ago (also when the clock went back) |
| `SiteIconTest` | Choosing a site's icon: largest size, apple-touch-icon, relative and http addresses, skipping SVG and inline icons, home page and favicon.ico fallback |
| `IconCatalogTest` | Reading the icon file, search order (name, words, categories, related words), suggestions, old icon names, and that the bundled file has every suggested and migrated icon |
| `ArticleDaysTest` | Grouping the list by local day, time zones |
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
- [ ] **Save** sends to the app chosen in Settings › Save and share (Instapaper by default);
      **Share** opens the share menu or the chosen app. An uninstalled app falls back to the share menu.
      Signed in to Instapaper, Settings › Save and share has no Save button row (Save goes to the account).
- [ ] **List:** at app start the list shows loading placeholders, never "No feeds yet" first.
      Swipe an article left and right past a third: a tick, it becomes read, then unread; a short
      swipe does nothing; swiping never opens the ☰ menu. The Unread chip at the top right switches unread only (filled, check) / all.
      Scroll down: the title, the Unread chip and the folder tabs slide away together; scroll up a
      little: they come back; pulling down at the very top refreshes, and the spinner stays in place
      until the refresh is done (no jump back up and down again), also in an Instapaper list. Settings › Article list › View: Small,
      Medium and Large each look right, with and without pictures.
- [ ] **Website icons:** after a refresh, feeds show their site's icon in the list, ☰ menu,
      manage page and reader, on a light tile; in dark mode dark logos are still visible; a feed
      without one shows its first letter.
- [ ] **Manage feeds and folders:** drag a feed into another folder and above another feed; drag
      a folder above another; the drawer and folder tabs follow the new order. Tap a feed, rename
      it, refresh: the name stays; clear it: the feed's own name comes back after a refresh.
- [ ] **Navigation bar:** signed in, a bar at the bottom has Feeds and Instapaper (a rail at the
      left in landscape on a tablet); Instapaper opens its last list, Feeds the feed section you had;
      signed out, no bar. The Mark all read button and messages sit above the bar.
- [ ] **Navigation:** the ☰ drawer has the same sections on Feeds and on an Instapaper list; the
      section you're in is open and the other folded shut (on Feeds: Feeds open, Instapaper shut; on
      an Instapaper list the other way round). Tapping Feeds or Instapaper folds that section shut
      and open (folded Feeds shows the unread count).
      In a folder or feed, Back goes to All; on All, Back closes the app.
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

## Instapaper (by hand, on the released app and the web app)

Sign-in needs the real Instapaper, the Worker's secret and a verified App Link, so it can't be tested
in CI. Use the signed release build, not a debug APK (see `instapaper.md`).

- [ ] Settings › Account › **Sign in with Instapaper** opens Instapaper; approving returns to the app (Android) or
      the page (web) with "Signed in to Instapaper", and Settings shows your user name.
- [ ] **Save** in the reader saves the article; it appears in Instapaper's Unread list.
- [ ] The ☰ menu (web: the sidebar) has an **Instapaper** section: Unread, Liked, Archive, your
      folders and tags each show the right saved articles in the normal list (no unread dot, not
      in All articles). Tapping one opens it in the reader, with the full article from its website.
- [ ] In the reader: like, archive (the article leaves Unread but stays open), move back to Unread,
      move to a folder, add and remove a tag, and delete (asks first) each change it in Instapaper.
- [ ] **☰ › Manage Instapaper**: create, drag by the handle to reorder, and delete folders;
      rename tags.
- [ ] **Sign out** returns Settings to "Sign in", and Save goes back to the chosen app.
- [ ] `https://rss-reader-web.wim-d57.workers.dev/.well-known/assetlinks.json` shows the JSON file.
