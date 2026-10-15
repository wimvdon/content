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
| `ArticleExtractorTest` | Finding the article in pages (with and without `<article>`, split sections), dropping menus, sidebars, comments, scripts and figures, unclosed paragraphs, entities, unsafe links, charsets, when a feed text counts as a summary |
| `ArticleBlocksTest` | Splitting articles into text, pictures (captions, srcset, lazy-loaded, tracking pixels skipped, https) and embeds (YouTube, Vimeo, videos, others); first picture; same picture in other sizes |
| `OpmlTest` | Import with folders and duplicates, an Inoreader export with folders, export round trip with folders, non-OPML files |
| `LibraryTest` | Merging downloads (read state kept, duplicates, limits), sorting, Unread filter, counts, mark read (and undo), remove feed; folders: add, edit, remove, move feeds, folder selection, OPML import into folders and export |
| `LibraryCodecTest` | Storage round trip with folders, tabs, line breaks and backslashes; missing folders and unknown icons; damaged input |
| `SettingsTest` | Text size limits, reader and theme darkness, Save/Share targets (storage and the old Save setting), defaults |

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
- [ ] Open an article from a summary-only feed (e.g. `theverge.com`): the full article loads.
      ⋮ › Show feed text / Show full article switches; Settings › Reader › Load full articles off
      stops it.
- [ ] **Backup:** with a cable and `adb`, `adb shell bmgr backupnow io.github.impca201.rssreader`,
      uninstall, reinstall, `adb shell bmgr restore io.github.impca201.rssreader` (or set up a
      new phone from backup): feeds and settings come back.
- [ ] **Pictures:** list rows show thumbnails; the reader shows the picture above the title and
      pictures between paragraphs; a YouTube embed shows a card with a preview that opens YouTube.
      Settings › Reader › Show images off hides them all.
- [ ] **Save** sends to the app chosen in Settings › Save and share (Instapaper by default);
      **Share** opens the share menu or the chosen app. An uninstalled app falls back to the share menu.
- [ ] Import and export OPML (for example an Inoreader export); folders come along both ways.
- [ ] **Folders:** create a folder with an icon, move feeds into it; the bottom bar appears, its
      tabs show only that folder's articles; Mark all read (round button) only marks that
      section and Undo restores it; removing the folder keeps its feeds and hides the bar.
- [ ] **Dark mode** and **Settings › Appearance**: switch Theme and Dynamic color. Text stays
      readable, status bar icons stay visible (also on sepia and dark reader pages).
- [ ] **Rotation**: the screen keeps its state.
- [ ] **Language**: Settings › System › Languages › App languages › RSS Reader › each language.
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
