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
| Web app and feed loader | `npm test`, `npm run lint`, `npm run build` in `web/`; `npm test`, `npm run typecheck` in `worker/` | Parser ids that match Android, feed links only http and https (no `javascript:`), reading the Android app's sync file, merge (also settings), one sync with the account (conflicts merged again), the account client and live connection, OPML, HTML cleaning (picture sizes, lazy-loaded and tracking pictures, paragraph breaks), embed cards, items (one per link, memberships, tags), the library in IndexedDB (only changes written, older libraries read, Saved from before #106 moved in), Saved (`test/saved.test.ts`: the tabs, Liked, tags and type, folders and tags renamed or removed, saving into a folder with a note, filing and archiving, the card's note, highlight or description, an item shown like its feed article), search over saved items (folders, notes and highlights too), the Search tab's list without words and its filters, per-item sync (the same cases as Android's `ItemSyncTest`, `test/syncRows.test.ts`), Save and share's sending (the same cases as `SendToTest`, `test/sendTo.test.ts`), a saved article's reading progress (where it is, where to scroll back to, what is worth storing: `test/readingProgress.test.ts`), when to load the full article, the full-article cache, reader text settings, mark as read on scroll; the Worker's origin check, address rules, redirects (garbled ones too), answers cut off halfway, size and type limits, and its accounts (device keys, versions and conflicts, encryption, sign-out and delete; item rows: changes since a version, conflicts, deletions, pages, encrypted under ids that don't show the key) |

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
| `ArticleExtractorTest` | The plain parts around the article extractor: handing the page to the WebView as JavaScript strings, reading its result (also no article, errors and unexpected values), charsets, when a feed text counts as a summary (short, or a teaser that links to its own article). Finding the article is the web app's code, tested by `web/test/articleExtractor.test.ts` with the sample pages in `testdata/articles/` for both apps |
| `SharedTextTest` | One text per saved article: the first device's is stored and every device shows it, two at once (the one stored first wins), offline the device's own, nothing stored for articles that aren't saved (the web app's `sharedText.test.ts` checks the same) |
| `TextLayoutTest` | Article text as reader blocks: a block per paragraph (also from divs and double line breaks), headings, bulleted, numbered and nested lists, quotes, code |
| `FullArticlesTest` | Loaded full articles are kept in the cache and not downloaded again; ones kept by an older extractor are extracted again and removed; a page that can't be downloaded fails |
| `ArticleBlocksTest` | Splitting articles into text, pictures (captions, srcset, lazy-loaded, tracking pixels skipped, https) and embeds (YouTube, Vimeo, videos, others); first picture; same picture in other sizes; a caption written twice in a row shows once |
| `OpmlTest` | Import with folders and duplicates, an Inoreader export with folders, export round trip with folders, non-OPML files |
| `LibraryTest` | Merging downloads (read state kept, duplicates, limits), sorting, Unread filter, counts, mark read (and undo), remove feed, rename feed (kept through refreshes), a whole refresh merged at once, future dates; folders: add, edit, remove, move feeds, folder selection, OPML import into folders and export |
| `LibraryItemsTest` | Links with tracking parameters are one item and duplicates from before join with everything of both (#253); a saved feed article tagged with its feed's folder (#120: existing spelling reused, not twice, nothing outside folders); one item per link (#106): links written another way are one item, saving a feed article makes its item without a copy, saving again brings it back to read, archive keeps the bookmark, keep as bookmark keeps the reading list place, remove bookmark keeps the reading list place, read again, tags cleaned and renamed (joined), lists newest first, removing a feed keeps its items |
| `LibraryRowsTest` | The library to database rows and back unchanged (order, tabs, line breaks, tags, highlights), broken rows repaired, only changed articles and items written, an empty database filled |
| `EmailLinksTest` | Email sign-in (#166): the token read from an emailed link (only Content's own address), what a used link gave (a key or an added address), the Gravatar address of an email in one form. The web app's `emailLinks.test.ts` does the same and checks the calls; the Worker's `email.test.ts` checks signed tokens (changed, ended, another key), a link that works once, the same account every time, adding an address to an Instapaper account and refusing one that opens another, and what the email says |
| Web `extension.test.ts` | The browser extension (#168): whether it is installed, asking it for a page and reading its bytes and final address, ignoring messages from other origins, and giving up when it says no or doesn't answer. The extension itself (`extension/`) has no build and is tried by hand (see `extension/README.md`) |

| `ArticleReportTest` | Reporting a badly cleaned-up article (#181): the pre-filled GitHub issue has the `parser` label, a title with the problem and website, the link, problem and app in its body, no empty comment, a long comment cut, spaces kept, and the text Content shows (#251): one paragraph per block, whole when it fits, else cut at a word so the address stays short enough. The web app's `articleReport.test.ts` checks the same report |
| `SendToTest` | Save and share (#256): what looks like an email address, the people offered (those shared with before, then added ones, each once), picking and unpicking (case ignored), the button (Save, Save and send, Send for the share menu's Send with Content), sending only needs someone to send to, one message for who it reached or the first one it didn't |
| `ShareAppsTest` | Content's share sheet (#251): apps picked most first, then well-known messaging and mail apps, then by name; an app's share targets counted apart; the counts stored and read back. `ShareShortcutsTest` also checks the launcher's folders (the first four, and the open request they make) |
| `SiteSignInsTest` | Signing in to sites you pay for (#180): the site of an address (https only, without www.), a site's cookies only to that site and its subdomains (not to look-alike or http addresses), the closest site winning, cookie names, sign-ins stored and read back |
| `SharingTest` | Sharing with other Content users (#167) and the inbox (#246): shared articles land in the inbox, unread, with who shared them, not in Saved; saving one keeps it in the inbox and removing it from Saved too; one already saved arrives in the inbox and keeps its place in Saved; one shared again comes back unread with its latest sender; the inbox newest first, read and unread; a tag's articles newest first; who shared it synced and merged with the item and stored in the database; the account's inbox read. The web app's `sharing.test.ts` runs the same cases, and the Worker's `share.test.ts` checks the account check, the inbox until taken in, and refusing a false sender or an address without an account |
| `HighlightCopyTest` | Highlights with Instapaper, both ways (#164): new ones copied and linked, nothing to send costs no call unless the article is opened, a passage already there is linked instead of copied, one made there comes in (its passage, occurrence and note) and isn't sent back, one deleted there is deleted here, a note changed there comes in unless it changed here too, a changed note made again, one deleted here deleted there, the monthly limit keeps the rest and retries a day later, another problem stops and keeps what got through, a sync's changes land on highlights edited meanwhile, the links stored and read back. The web app's `highlightCopy.test.ts` runs the same cases, and the Worker's test checks the three highlight calls it lets through |
| `AnchorsTest` | Highlights (#102): quote, surroundings and occurrence; found again exactly, with typographic variants, letter case as a last resort, a repeated quote told apart by its surroundings, roughly when the text changed, and kept when not found. The web app's `anchors.test.ts` runs the same cases, and `highlightDom.test.ts` reads and marks a page |
| `ArticleTextTest` | The reader's paragraphs as one text, a selection's place in it, and the marks per paragraph |
| `ScrollReadTest` | Mark as read on scroll: which articles have scrolled past (unread only, not the top one, only ones that were on screen) |
| `JsonTest` | JSON round trip (escapes, emoji, numbers, nesting), whitespace, broken text |
| `SyncDocumentTest` | Sync file round trip (with settings, the same text as the web app), other or newer files left alone, broken entries skipped, taking folders and feeds (not read marks) from the library, a version 2 file's read marks read but never written |
| `ItemSyncTest` | Per-item sync between two devices: items and read marks arrive, **no article text in the payload**, changes to different parts of an item both stay, tags as a set and highlights per highlight, this device wins the same field but an edit beats a removal, removals and unread again arrive, marks of articles not here yet arrive read, the mark of a months-old article arrives (#245), an item from an older app under a key with tracking parameters moves once on every device (#253), old marks leave the account without making anything unread, syncing again saves nothing, pages, rows of unknown kinds left alone, the account's answers |
| `SyncMergeTest` | Three-way merge: changes and removals from either side, this device wins but an edit beats a removal, order from the side that reordered, first sync without base; a version 2 file's read marks kept for articles not here; applying the result to the library; syncing twice changes nothing; settings per setting, and a device without synced settings taking the file's |
| `AccountSyncTest` | One sync with the account: the first device's library becomes the document, nothing saved when nothing changed, another device's save in between merged in (with the right base), newer documents left alone; reading the server's answers and problems; the live connection's address, messages and retry delays; the stored base with its version |
| `SyncSettingsTest` | Every Android setting under `android.` and back, the web app's and older names leaving them alone, the shared Load full articles and Mark as read on scroll from before read once and dropped, Save/Share targets and notifications not synced, unknown names kept, bad values ignored, the Instapaper sign-in in the file only when given, the Instapaper import record carried, the people shared with, and the hidden words (#277) |
| `SharedInstapaperTest` (and the web's `sharedInstapaper.test.ts`) | One Instapaper sign-in for the account: its text and back, damaged values, what a device sends (its sign-in, a disconnect, or nothing when it never had one), connecting and disconnecting after another device did, and the same user's new token keeping what was synced |
| `SyncTimingTest` | When a sync is due: never synced, or more than ten seconds ago (also when the clock went back) |
| `SiteIconTest` | Choosing a site's icon: largest size, apple-touch-icon, relative and http addresses, skipping SVG and inline icons, home page and favicon.ico fallback |
| `IconCatalogTest` | Reading the icon file, search order (name, words, categories, related words), suggestions, old icon names, and that the bundled file has every suggested and migrated icon |
| `ArticleDaysTest` | Grouping the list by local day, time zones |
| `MixedSearchTest` | Search's ranking: the whole query in a title above single words above text-only matches, feed and reading list results in one list (then newest), the All/Feeds/Reading list chips; `SearchResultsTest`: the Search tab without words (everything, newest first) and its filters (feed folder, feed, Saved folder, tag, type); `ItemSearchTest`: the reading list searched by title, website, description, tags, note and highlights, title matches first; the web app's `test/search.test.ts` checks the ranking too |
| `SavedTest` | Saved (#119): each item in one place (To read, a folder, Archive) and All with them all, newest first; Liked and tags (all of them); chips in the topics' order (alphabetical without topics); folders and counts, a filter without a folder or tags that are gone; filing and archiving (out of the folder); renaming (joining) and removing folders; the folder through sync and its merge |
| `TopicsTest` | Topics: every tag gets its topic once (the same id on every device), topics with one name joined with their feeds, no second topic with a taken name, renaming on feeds and saved articles (to another one's name joins the two), removing keeps feeds and articles, counts and the topics' order, a saved feed article gets its feed's topic; Feeds' places and Back going up a level; reordering topics and one topic's feeds; merging; the topic being viewed (Edit topic). The web app's `test/topics.test.ts` checks the same library the same way. |
| `FeedChipsTest` | Feeds' chip rows without ☰: the topics then the feeds without a topic, a chosen topic's feeds in a second row, a feed keeping its topic lit (or lit itself in the first row when it has none), the chosen topic staying a chip, a tagged feed counting as in its topic |
| `ReadingListsTest` | Reading times and minutes left, an item shown like its feed article (or with its description), its source (feed or website with favicon) |
| `ServiceSyncTest` | An article the service has twice, with and without tracking parameters, becomes one here and there, rows from before move to the new key (#253); Saved and a fake service (#145): connecting imports the whole account, or nothing when the user says so (#251: what is there stays until it changes, Saved still goes there), folders in and out (filing, archiving leaves the folder), adding, archiving, liking and tagging sent once, what came in not sent back, removing sent (a bookmark stays), changes on both sides to other fields both kept, the same field: the later change wins (Reader's without a time from the service), deleted at the service leaves Saved unless changed here or another service keeps it in step, removed here but changed there stays, a link the service has joins it, a failure keeps everything and the rest goes next time, progress, notes; a save reaches every service whose scope covers its folder, a scope losing a folder archives its articles there (never deletes) and gaining it brings them back, an import lands each article in its place and leaves the rest there until it changes, another device's import, folder origins kept, entries stored and read back (rows from before #145 in step), a new article sent with Content's text to a service that takes it only when saved in the last week, and added without it when there is none (#247); the web app's `test/serviceSync.test.ts` covers the same rules |
| `InstapaperReadingListTest` | Instapaper as the reading list's service, with recorded answers: `since` page by page with deletions and folders, filing into a folder made there when missing, the first sync asks for everything, adding with tags and archive, adding with Content's text and with the link alone when Instapaper refuses the text (#247), only changes sent (taking the folder's tag off moves it home), problems mapped |
| `PageInfoTest` | A page's title, description and picture (Open Graph first, https pictures only) for links saved without a title |
| `ArticleFilterTest` | Hide articles with words (#277): words split on commas, semicolons and new lines, whole words and phrases only, case ignored, accented letters as letters, hidden articles out of the list and the unread counts |
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
- [ ] **Backup:** with a cable and `adb`, `adb shell bmgr backupnow to.wim.content`,
      uninstall, reinstall, `adb shell bmgr restore to.wim.content` (or set up a
      new phone from backup): feeds and settings come back.
- [ ] **Pictures:** list rows show thumbnails; the reader shows the picture above the title and
      pictures between paragraphs; a YouTube embed shows a card with a preview that opens YouTube.
      Settings › Reader › Show images off hides them all.
- [ ] **Sync** (needs `ACCOUNT_KEY` on the Worker, `docs/account.md`, and the Instapaper sign-in
      below): signed in, Settings › Account says "Last synced just now". Sign in on the web app (or
      a second phone) with the same Instapaper account: the same feeds, folders and read marks
      appear. Read an article on one with both open: within a few seconds it is read on the other,
      without refreshing. Rename a feed: the same. Delete synced data: both say to sign in again.
- [ ] **Save** (a list icon) puts the article in Saved (or sends it to the app chosen in Settings ›
      Save and share); **Share** opens the share menu or the chosen app. An uninstalled app falls back
      to the share menu. Settings › Save and share looks the same signed in to Instapaper or not.
- [ ] **List:** at app start the list shows loading placeholders, never "No feeds yet" first.
      Rows don't swipe (#305); long press one for Mark as read, Save and Share. Save: the sheet opens,
      then "Saved to To read" with **Open**, which opens the article. The Unread chip in its own row under the folder tabs (not in the top bar) switches unread only (filled, check) / all.
      Scroll down: the title, the folder tabs and the Unread chip slide away together; scroll up a
      little: they come back; pulling down at the very top refreshes, and the spinner stays in place
      until the refresh is done (no jump back up and down again), also on the reading list. Settings › Article list › View: Small,
      Medium and Large each look right, with and without pictures.
- [ ] **Website icons:** after a refresh, feeds show their site's icon in the list, their chips,
      manage page and reader, on a light tile; in dark mode dark logos are still visible; a feed
      without one shows its first letter.
- [ ] **Manage feeds and folders:** drag a feed into another folder and above another feed; drag
      a folder above another; the chips follow the new order. Tap a feed, rename
      it, refresh: the name stays; clear it: the feed's own name comes back after a refresh.
- [ ] **Search tab (#171):** the bottom bar has Feeds, Saved and Search; Search shows everything
      (feeds and Saved, newest first) before you type. Feeds › Folder and Feed narrow it to a folder
      or one feed; Saved › Folder, Tag and Type narrow Saved. Saved has no All tab any more. Tapping
      Feeds or Saved goes back there.
- [ ] **Search:** typing
      *cafe* finds articles with "Café" in the title (first) or the text; two words find articles with
      both; nothing found shows "Nothing found"; opening a result and going back shows the same results.
      The magnifier is on the reading list too; a word from a reading list article's title, website,
      tag or note finds it (labelled Reading list, archived ones too), and tapping it opens it with ♥,
      Keep and Archive. A bookmark's collection finds it too (labelled Bookmark; it opens its page);
      the Bookmarks chip shows only bookmarks.
- [ ] **Navigation bar:** a bar at the bottom has Feeds and Saved, and signed in the Inbox with a
      dot while something in it is unread (a rail at the left in landscape on a tablet). The Mark all
      read button and messages sit above the bar. Signed out, there is no Inbox.
- [ ] **Navigation:** there is no ☰. The search bar ends with Settings (your picture signed in,
      else a cogwheel). In Feeds the chips are Unread/All, the topics, then the feeds without a
      topic, with no X and no Manage topics icon at the end (tap a chosen chip again for
      everything); picking a topic shows a second row with its feeds, and
      tapping one shows that feed alone. Settings › Feeds › Manage topics opens the same page. In a
      topic, a feed, Saved or the Inbox, Back goes up a level; on All, Back closes the app.
- [ ] **Archive from the reader:** a saved article in To read has an Archive button next to its
      list at the bottom; tapping it closes the article, shows Archived with Undo, and Undo puts it
      back. An archived article has no Archive button.
- [ ] **Saved:** tabs To read, All, each folder and Archive; the Liked chip and tag chips (several
      at once) filter it. To read,
      All and Archive look like the feed list (Settings › View applies); a folder shows notes in two
      columns (picture, title, note or description, site, tags). Add link asks for an address and a
      folder. In the reader (#139): the top bar has the full-article button (lit while the full article
      shows; tap: the feed's text, tap again: the full article), Aa and ⋮, before and after saving;
      the bar below has Save on the left, or once saved the folder and the tags side by side (both
      icons in the same colour), and Share at the right end; the text ends with Read on website; ⋮
      has Open in browser, Mark as unread and Remove from Saved (asks first, everywhere), no Tags. Web app: the same.
      Nothing says "Finished reading", in any language.
      Leave an article halfway and open it again: it opens where you were. Manage topics
      renames and removes folders (their articles go to the Archive, not back to To read) and tags. An app updated from a
      version with Bookmarks shows them in folders (their collection, or "Bookmarks").
      The Undo message after Mark all as read shows above the folder bar.
- [ ] **Mark as read on scroll** on: scroll down the Unread list; the articles that leave the
      top of the screen are marked read (their dot goes) but stay until you refresh. Scroll down,
      pull to refresh so new articles arrive at the top: they stay unread (#157).
- [ ] **What stays in the Unread list (#157):** open an article and go back, open one from Search,
      long press one › Mark as read: each stays, dimmed. Go to Saved and back, or tap the same feed again: they
      are still there. Pick another feed, switch Unread/All, or pull to refresh: they are gone.
      Mark all read: the list empties ("All caught up"); Undo brings them back unread.
- [ ] Saved rows (also archived ones and Saved hits in Search) are never dimmed and have no dot.
      Opening a saved article saved from a feed marks that feed article read, in both apps.
- [ ] Web app: pointing at an article in the list shows a round button that marks it read or
      unread; it stays in the Unread list after marking it read. **m** does the same for the open
      article; ⋮ in the reader offers Mark as read / Mark as unread.
- [ ] Two devices: let a read article drop out of a busy feed (or remove the feed) on one; on the
      other it stays read. Add the feed again: its read articles come back read.
- [ ] Import and export OPML (for example an Inoreader export); folders come along both ways.
- [ ] **+ in the search bar (#307):** in Feeds it opens the Add feed sheet (address, Topic chips
      starting on the topic shown, Cancel and Add); in Saved the save sheet asking for an address;
      not in the Inbox. Saved has no round button any more.
- [ ] **Sheets alike:** Save on a feed article: the sheet is headed **Save**, Cancel at the top right,
      **Save and archive** (box icon) and **Save** (open book) at the bottom. Organise on a saved
      article: headed **Organise**. Add feed, New topic, a highlight and Report a problem: Cancel at
      the top right, no Cancel at the bottom. New topic and Edit are text buttons, not chips. Web:
      the same, Cancel in words at the top right, + in the search bar, Manage topics in Settings.
- [ ] **Topic chips (#314):** Feeds' and Saved's chip rows show each topic's icon. In the Add feed
      and Organise sheets the topics have their icons too, then **New topic** and **Edit**. New
      topic: name and icon, Save: the new topic is chosen. Edit: each chip gets a grip and a pen and
      Edit says **Done**; drag a chip by its grip onto another place: the order changes there and in
      the chip rows. Tap a chip: rename it (to another topic's name: they join), change its icon, or
      Remove (asks under the chips). Web app: the same, dragging with the mouse or ← → on a chip.
- [ ] **Manage feeds** (Settings › Feeds › Manage topics): create a folder, search an icon (e.g. *soccer*) and pick it, move feeds into it (they show under it); the bottom bar appears, its
      tabs show only that folder's articles; Mark all read (round button) only marks that
      section and Undo restores it; removing the folder keeps its feeds and hides the bar.
- [ ] **Dark mode** and **Settings › Appearance**: switch Theme and Dynamic color. Text stays
      readable, status bar icons stay visible (also on sepia and dark reader pages).
- [ ] **Rotation**: the screen keeps its state.
- [ ] **Language**: Settings › System › Languages › App languages › Reader › each language.
      All text is translated.
- [ ] **Large text**: Settings › Display › Font size at maximum. Nothing is cut off.
- [ ] **Back** gesture/button behaves as expected (also with the Text sheet open).
- [ ] **No connection**: pull to refresh shows how many feeds failed; stored articles still open.
- [ ] After a release: install the release APK over the previous release. It updates without
      losing feeds.

## Reading a crash from a release build
Release builds are minified, so stack traces show short names like `a.b.c`. Download
`content-<version>-mapping.txt` from the matching GitHub Release and decode the trace with R8's
`retrace` (Android SDK `cmdline-tools/latest/bin/retrace`):
```sh
retrace content-<version>-mapping.txt stacktrace.txt
```
Or ask Claude Code to do it: give it the stack trace and the version.

## Instapaper (by hand, on the released app)

Sign-in needs the real Instapaper, the Worker's secret and a verified App Link, so it can't be tested
in CI. Use the signed release build, not a debug APK (see `instapaper.md`).

- [ ] Settings › Connected services › Instapaper › **Sign in with Instapaper** opens Instapaper; approving returns to the app
      with "Signed in to Instapaper", and Settings shows your user name.
- [ ] Right after signing in, everything in the Instapaper account appears in Saved (archived ones
      under Archive, Instapaper's folders as folders), and what was in Saved appears in Instapaper.
      Settings › Connected services says Saved is synced with your account.
- [ ] **Save** on a feed article: it appears in Instapaper's Unread list within seconds.
      Archive, like, tag and remove it in Reader: each change shows in Instapaper. Do the same in
      Instapaper's own app and pull down on the reading list: the change shows in Reader.
- [ ] File an article in a folder (also a new one): it moves to that folder in Instapaper (made
      there when missing); archiving it moves it to Instapaper's archive.
- [ ] An article that came in from Instapaper (or an added link) shows its full text in the reader,
      with nothing above the title saying where the text came from.
- [ ] Read an article halfway, leave it: Instapaper shows the same progress.
- [ ] **Sign out** returns Settings to "Sign in"; Saved stays.
- [ ] Signed out: **Save** keeps the article in Saved; the sheet offers Like and
      tags; the articles are still there after restarting the app.


## Sharing to Reader (#103)

- [ ] In Chrome, Share › **Content Dev**: only the save sheet slides up over Chrome; Reader's own
      screens don't open. **Save** shows where it went and you are back in Chrome.
- [ ] Share plain text without a link: "No link to save", and nothing else opens.
- [ ] Reader's own Share button doesn't list Reader itself.

## Web app: folders without an account (#119)

- [ ] Signed out, save an article, open it, tap the folder button and make a new folder: the article
      moves there, the sidebar lists the folder with its count, and Saved no longer shows it.
- [ ] Archive it: it leaves the folder, and the folder disappears with its last article.
- [ ] Sidebar › **Folders and tags**: delete a folder; its articles are back in Saved.

## Feed to Saved to folder or archive (smooth flow)

- [ ] In an article from a feed, tap **Save**: the sheet's heading says "Saved to To read", then
      **Folder** chips (To read and Archive with their icons, a thin line, then your folders),
      **Tags** with Like in front, and **Note**, then **Remove** (red) and **Save**. Tap a folder chip
      (the heading stays "Saved to To read"), or New folder and type one, and **Save**: it is in
      that folder. Type a tag without tapping + and tap Save: the tag is on it too. Pick another
      folder and swipe the sheet away instead: it stays in To read. Tap **Remove**: "Removed from
      Saved", and it isn't in Saved at all.
- [ ] Saved, filtered to nothing (Liked or a tag with no match): **Show all** brings the tab back.
      Add link from a folder tab starts in that folder. Empty To read on the web offers Add link.
- [ ] Removing a feed, a feed folder, a Saved folder or a tag asks first (both apps); Remove is red.
      Remove from Saved says "Removed from Saved".
- [ ] Organise (#304): Save in the reader, Add link and the share menu's Save all open a sheet
      headed Organise, with no Send with Content in it (both apps). Share a link from another app to
      a person at the top of the share menu: Send with Content opens with them picked, the button
      says Send. The sheet has no Don't save anywhere; the share menu's Save saves; its Send with Content only sends; its Add feed
      opens Add feed with the link. After saving, a tap anywhere but Open goes back to the other app.
- [ ] Send with Content: hold a person, each one shared with before gets an ×; tapping it removes
      them (also on the other device after a sync), Done ends it. Dragging a feed in Manage feeds
      and folders and dropping it: it glides into place, nothing jumps.
- [ ] Notifications on (phone): something shared with you shows its title, intro, picture and
      sender, makes no sound, and opens the inbox.
- [ ] Web app: Escape closes a menu, then the article; shortcuts do nothing behind a dialog. On a
      phone, messages show above the bottom bar, and the round read button shows on every row.
      Moving a feed up or down in Manage feeds always moves it within its folder.
- [ ] Long press a feed article › **Save**: the sheet opens; Save: "Saved to To read".
- [ ] **Highlights (#102):** in an article, press and hold a word, drag over a sentence and tap
      **Highlight** in the menu: it turns yellow. Select another and tap **Note**, type, Save. The
      bottom bar shows a quote mark with 2; tap it: both, with the note. Tap a marker: change its
      note or **Remove highlight**. A feed article you highlight appears in Saved › To read. Switch
      to the feed's text with the page button: the highlights are still there (or say approximate).
      Web app: select text with the mouse, pick Highlight or Note in the bar above it; click a
      marker to edit; a highlight made on the phone shows on the web after sync, and the other way.
- [ ] In a saved article the bottom bar has two labelled buttons (#305): **Archive** (a box) and
      **Organise** (a tag). Tap **Archive**: back in the list with "Archived" and **Undo**; Undo puts
      it back. Open an archived article: the button says **Back to read**; tap it and the article
      stays open, now in To read. **Organise** opens the sheet. Web app: the same two buttons.
- [ ] **Add link** and a link shared from another app: the sheet asks for the address (or shows the
      link), has no To read / Archive chips, **Cancel** at the top right saves nothing, and at the
      bottom **Save and archive** and **Save**. Save and archive: "Saved to Archive"; Save: "Saved to To read".
- [ ] **Organise** a saved article (long press a card in Saved): topics, Like, the note, Remove and
      Save; no Save and archive. **Cancel** changes nothing.
- [ ] Nothing in a sheet asks which service, and no sheet explains an empty field.
- [ ] **Save, the list icon and Open (#142):** nothing says "Read later", in any language; the
      navigation bar's Saved is a list icon, To read keeps the open book. After saving from the
      reader the message has no Open; after a long press › Save, Add link or a share (over Chrome) it has
      **Open**, which lands on that article. Web app: the same icons, and Open after Add link.
- [ ] Web app: **Save** on a feed article and **Organise** on a saved one open the same sheet;
      **Add link** in Saved too, with Cancel.
- [ ] Nothing swipes (#305): not feed rows, the Inbox or Saved. Long press a card in Saved › To
      read: **Archive** first (archived, with Undo), then Organise, Share, Remove. In Archive the first
      entry is **Back to read**. Web app: the same with right-click.

## Web app: Saved like Android's (#147)

- [ ] Saved shows **To read**, **All**, each folder and **Archive** as tabs, with **Liked** and the
      tab's tags as chips under them.
- [ ] A folder shows cards: your note, else the first highlight (with a quote mark), else the
      description (three lines), a glimpse of the note, the tags as chips, then the site, the
      highlight count and a heart when liked.
- [ ] In a saved article, the note button opens the note; write one and close it: the card in its
      folder shows it, it shows above the text, and Search finds it. A note written on the phone
      shows here after a sync, and the other way round.
- [ ] Nothing in Saved says Instapaper: Archive / Back to read, Like / Unlike, Folder, Tags, Note,
      Remove from Saved.

## Web app: the same Saved signed in or not

- [ ] Signed out, save a few articles and put one in a folder. Sign in with Instapaper: Saved looks
      the same (same tabs, same buttons); within a few seconds the articles are in Instapaper too, the
      filed one in its folder.
- [ ] Archive, like and tag an article in the web app: the change shows in Instapaper's own app.
      Change one in Instapaper and reload the page: the change shows in the web app.
- [ ] Sign out: Saved stays exactly as it was.

## Web app: an article whose site blocks loading

- [ ] Open a feed article from a site that blocks the web app (many big news sites do): instead of an
      empty page or a vague error, a box says the website doesn't let the web app load it, with
      **Read on website**. The feed's own text stays below it.

## One text per saved article

- [ ] Signed in on the phone and the web: save an article from a summary-only feed and open it on the
      phone (the full article loads). Open it in the web app: the same text, also when the site blocks
      the web app. A highlight made on one shows in the same place on the other.

## Feeds and Saved header (Android)

- [ ] Scroll the feed list or Saved: the area behind the folder tabs and chips stays the page's colour,
      with no grey band.
