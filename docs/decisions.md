# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-17: Readability4J finds the article (new dependencies: Readability4J, jsoup)

The owner asked for a proven reader-view engine instead of more rules of our own, and for no
rules for single publications. Full articles now go through Readability4J
(`net.dankito.readability4j`, Apache 2.0), the Kotlin port of Mozilla's Readability that Firefox
Reader View uses, on a current jsoup (pinned to the latest release; Readability4J itself asks for
an old one). Readability4J's last release is from 2021, but the algorithm is stable and Android
RSS readers such as Feeder and Read You use it. Around it, a few generic steps make up for what
this older Readability lacks: glossary words in buttons inside text are kept, hidden parts are
removed, a `<div>` holding only text becomes a `<p>` (so its links stay inside the paragraph),
look-alike containers that each hold several paragraphs are joined (split articles), and after
extraction the heading that repeats the page title and short byline and comment-link lines are
dropped; the reader shows a caption written twice in a row once. slf4j, which Readability4J logs
through, has no backend in the app, so `proguard-rules.pro` tells R8 not to look for one. Feed
texts that link to their own article ("Read full article") now count as summaries whatever their
length, so feeds that send a teaser of a few paragraphs (Ars Technica) load the full article.
Rejected: running Mozilla's Readability.js in a WebView or JavaScript engine (heavy, and it brings
a browser engine back into reading), Crux (pulls in an older OkHttp and Klaxon for features we
don't use), and keeping our own scorer (each new site needed new rules).

## 2026-10-17: The web app and its feed loader are paused

The owner put the web version on hold. Its code in `web/` and `worker/` stays, so it can be
picked up again, but its CI job (`.github/workflows/ci.yml`) and its npm updates
(`.github/dependabot.yml`) are commented out; removing the `# ` in front of them turns them back
on. The Cloudflare builds of `rss-reader-web` and `rss-reader-worker` are set up in the
Cloudflare dashboard, not in this repository, and are switched off there. Android doesn't use
either, and the sync format (`docs/sync.md`) doesn't change, so the web app still fits when it
resumes. Rejected: deleting the folders (the work would have to be redone).

## 2026-10-17: The extractor finds the whole article on modern news sites

After the reader got its own text layout, full articles still lost most of their paragraphs on
real sites. Tested on saved pages from VRT NWS, Ars Technica, The Verge and 9to5Google:
paragraph scores only counted one and two levels up, so on sites that wrap every paragraph in
boxes of its own (VRT NWS) no container collected the text, and the 3-line summary, or only the
lead, won; sites that split the text over several identical containers with ads in between
(Ars Technica's `post-content`) kept only one of them. Scores now count five levels up (as
Readability does), list items no longer score as paragraphs (Readability doesn't either), the
extractor moves up from a small winning box to the nearest ancestor holding at least three times
as much paragraph text, and joins look-alike containers. Glossary words that VRT NWS puts in
buttons inside a paragraph are kept. Cached full articles get a version in their file name, so
articles cut off by the old extractor are loaded again after an update. Rejected: a library such
as jsoup or Readability4J (a new dependency; the problem was the scoring, not the HTML reading).

## 2026-10-17: The reader lays out article text itself

Full articles sometimes lost their paragraphs and layout: Compose's HTML conversion puts a single
line break between paragraphs and drops list bullets and quote bars, and the extractor turned a
`<div>` per paragraph into a line break. Article text is now split into blocks first
(`feed/TextLayout.kt`: paragraphs, headings, lists with bullets, numbers and nesting, quotes,
code), and the reader spaces and draws each block itself; only the text inside a block still
goes through Compose's HTML conversion (links, bold, italics). The extractor makes a paragraph of
every container that holds only text, and matches "unlikely" class names as whole name parts, so
utility classes like `overflow-hidden` no longer remove the article. Rejected: a WebView (heavy,
and it would bring the site's scripts back), an HTML library such as jsoup (a new dependency for
what a small splitter does).

## 2026-10-17: Less help text

The owner found the app over-explanatory; the shared rule is in `DESIGN.md` ("Wording"): help text only says what the screen can't. Removed: the paragraph above the Save and Share rows, the sentence above the address field in Add feed, and the subtitle under "Mark as read on scroll". Shortened: the feeds-screen hints (only the non-obvious "drag a feed onto a folder" stays), the OPML hint, the sync, images, auto-dark and Instapaper summaries, the caught-up text and the Dynamic color line. The web app's sync summary matches. Kept: consequences, privacy notes and setup steps. Rejected: removing every hint (the remaining ones say something the screen doesn't).

## 2026-10-16: Shared empty state on Android, same layout on the web

A Material 3 audit of the sibling apps found empty screens drawn differently in each app. Here the empty list's feed icon never showed: it was 48dp tall with 48dp of padding inside, which left nothing to draw. Android now uses the shared `ui/theme/EmptyState.kt` (`DESIGN.md`, "Empty and setup states"): an icon in a circle that fits the state (feed, all read, article), a title, one sentence and buttons. The web app's `Empty` got the same icon circle, and its empty list without the Unread filter now says "No articles yet" instead of "All caught up". "Show all articles" became "Show all", as on the web. Rejected: fixing only the padding (the three empty states would still differ between the apps).

## 2026-10-16: Instapaper: Save to the account with the Simple API

Save can now go straight to the user's Instapaper account instead of the Instapaper app, with
Instapaper's free Simple API (email or username and password, over https; no app key). Android
checks the account when signing in and reports whether each save worked; the account lives in
its own DataStore file that Android backup leaves out. The web app posts to the same API from
the browser; Instapaper's API doesn't allow web pages to read its answer, so the web app says
"Sent" rather than "Saved". The Full API (list, archive, star, folders, highlights) needs an
OAuth consumer key that Instapaper hands out after a manual review, and its bookmark methods
have historically been limited to paying subscribers, so it is not built until the owner has a
key. Rejected: routing the web app's saves through the feed loader (the password would pass our
Worker).

## 2026-10-16: The web app: static React site, feeds through the Worker

The web app is a static site in `web/` (React, TypeScript, Vite, Tailwind with shadcn-style
parts on Radix primitives, as chosen for the web in `DESIGN.md`), hosted on Cloudflare Pages
next to the feed loader Worker in `worker/`. It keeps its library in IndexedDB and syncs through
the same Drive file and three-way merge as Android (ported to TypeScript, with tests that read
the Android app's file), signing in with Google Identity Services. Its feed parser gives the same
article ids as Android so read marks match. Full articles use Mozilla's Readability; article HTML
is cleaned with DOMPurify; folder icons are the Android app's Material Symbols file, copied in at
build time. The Worker passes only feeds and pages (no pictures), only for the web app's origin,
logs nothing and is rate limited. Both live in this repository so the shared sync format changes
in one place, with their own CI job. Rejected: Compose Multiplatform for the web (Android look on
the web), a service worker for offline use (more moving parts; the app opens offline from its
stored library anyway once loaded), our own server.

## 2026-10-16: Sync through the user's own Google Drive, three-way merge, no clocks

Feeds, folders and read marks sync through one JSON file in Google Drive's hidden app folder
(`drive.appdata`: the app sees only its own file, and Google requires no paid security audit for
it). Sign-in uses Google's authorization API from `play-services-auth` (new dependency, approved
by the owner); Drive is called over the existing OkHttp, and the JSON is read and written by a
small plain-Kotlin reader so the logic stays unit-testable without a new library. Changes are
found by comparing with the file as last synced (three-way merge), not by timestamps, so wrong
clocks on a device can't lose changes. Article texts are not synced (each device downloads its
feeds), which keeps the file small. Sync runs only while the app is open. Rejected: Firebase or
our own server (an account and a server to run), kotlinx.serialization (a compiler plugin for one
small file), timestamp "last writer wins" (device clocks differ), full Drive access (sees all
files, needs an audit).

## 2026-10-16: Web app loads feeds through a small Cloudflare Worker; sync is pluggable

Browsers block a web page from loading most feeds and article pages (CORS), so the planned web
app loads them through a small, stateless Cloudflare Worker: it only fetches the feed or page it
is asked for and passes it back, keeps nothing, logs nothing, and refuses anything that isn't a
GET for a feed or page. Pictures load directly (browsers don't block those). It stays small
enough for anyone to run their own copy, and the web app can be pointed at another copy. The
Android app, including a Play Store release, keeps loading feeds directly and never uses it. Sync goes through one interface with Google Drive
(the hidden app folder) as the first provider and room for others (for example Dropbox,
OneDrive or WebDAV/Nextcloud) using the same file format. Rejected: a Google Apps Script proxy
(ties feed loading to Google while sync should not depend on one provider), a browser extension
(too much for the goal, and little use on phones), public CORS proxies and RSS-to-JSON services
(a stranger sees every feed, limits, unreliable).

## 2026-10-16: One brand, each platform's own design language

For a planned web app and a possible iOS app, the owner chose a shared brand with a native design
language per platform, rather than Material 3 everywhere: Material on Android, Apple's guidelines
on iOS, and shadcn/ui (Radix with Tailwind) on the web, themed with the app's seed colour. Shared
code is the logic, not the screens. Rejected: Material 3 on every platform (an iOS app would feel
like a port), Compose Multiplatform for the web UI (it brings the Android look along), Fluent 2
(looks like Microsoft), Carbon and Primer (made for business and developer tools). Recorded in
this app's section of `DESIGN.md`; if other apps from the template get web versions, it moves
to the template's shared rules.

## 2026-10-16: The home header scrolls away as one block

The owner wanted more room for articles and found the header's scrolling wonky: the medium top
app bar only shrank to a small bar, the tabs and Unread | All never moved, and pulling down at the
top went to pull-to-refresh instead of opening the bar again. The app bar, the folder tabs and
Unread | All now form one header that slides away while scrolling down and comes back as soon as
you scroll up, using Material's own "enter always" scroll behaviour (it also snaps the header
fully in or out when you let go). A new section starts with the header in view. The order stays
app bar, tabs, filter: Material 3 attaches primary tabs directly under the top app bar, and a
filter for the content sits below them. Rejected: putting Unread | All in the app bar (its labels
don't fit next to a long title) and above the tabs (it would split the tabs from the app bar).

## 2026-10-16: Material 3 audit: settings list, folder tabs, normal-sized Unread | All

An audit against Material 3 (owner's request) changed three things. Settings are one list with
section headers instead of collapsed cards, from the shared `SettingsKit.kt` (see the template's
decision log). The floating folder bar copied Tracker for Simkl's floating navigation pill, but
Material 3 keeps floating bars for actions and uses tabs for peer views of the same content, so
folders are now scrollable tabs under the top app bar (they fit any number of folders, which a
navigation bar doesn't). Unread | All is back to the normal button height and as wide as its
labels; the earlier "more padding" request meant room around it, so it now has 16dp above it.
Rejected: a docked navigation bar for folders (at most five items, and folders aren't app
sections) and filter chips (they would look like extra filters next to Unread | All).

## 2026-10-15: Site icons on a light tile; pens and an empty "No folder" note on the manage page

Many site icons are dark logos on a transparent background, made for light browser tabs, and
vanished in dark mode. They now sit on a fixed light tile in both modes (as Chrome does), which
also gives every icon the same shape. Rejected: tinting or inverting icons (breaks colourful
logos) and per-icon brightness checks (decoding every icon for little gain). The manage page got
a pen button per row (tapping the row still works) and a note under "No folder" when every feed
is in a folder, which also shows where to drop a feed. Asked for by the owner.

## 2026-10-15: Swipe, folder bar and filter polish after using v0.1.1

The owner found the swipe buggy, the Unread | All buttons jumpy and asked whether the folder bar
followed Tracker for Simkl. Changes: the swipe is our own small `draggable` that only slides the
row and springs back (no dismiss state to get stuck in), with a haptic tick at 30%; the drawer
no longer opens by swiping, which fought with swiping rows (it opens with ☰). The folder bar now
is Tracker for Simkl's floating pill (selected tab expands to show its name, spring motion), the
Unread | All halves have fixed equal widths and spring motion, the FAB is icon-only again, and
the list shows loading placeholders until the stored feeds are read, so "No feeds yet" no longer
flashes at start.

## 2026-10-15: Drag and drop on the manage page; renaming feeds

Asked for by the owner. Feeds and folders are reordered and feeds moved between folders by
dragging a handle, built on `LazyColumn` with our own small drag logic (`FeedArrangement`, unit
tested) instead of a reorder library: no new dependency. A folder is dragged on its own with
the other folders collapsed, so it can't land in the middle of another folder's feeds. Screen
reader users get Move up / Move down actions. Feeds can be renamed; the `renamed` flag keeps a
refresh from overwriting the user's name, and clearing the name returns to the feed's own.
Rows open an edit dialog on tap (name, folder, remove) instead of carrying three icon buttons.

## 2026-10-15: Unread | All as a connected button group; no ⋮ menu on the list

The owner asked for Material 3's connected button group for Unread/All (instead of the icon
toggle), and to drop the list's ⋮ menu: refresh is pull-down, and adding feeds belongs on the
Manage feeds and folders page, which is now the drawer's single entry for feeds. Material 3 1.4
(our Compose BOM) doesn't have the component yet, so `ReadFilter` builds it from `Surface`s
following the spec; replace it with Material's `ToggleButton` group once it is stable. The
folder bar's first tab is now "All" with the feed symbol (was "Home").

## 2026-10-15: Navigation review and a livelier list

The owner found the design boring and asked for a navigation and Material 3 review. Changes:
Back inside a folder or feed returns to Home before leaving the app (Material's "back to the
start destination"); snackbars sit above the folder bar instead of on it; Mark all as read is an
extended FAB with its label, shrinking to the icon when scrolled (clearer than an icon alone);
Home has a collapsing medium top app bar; articles are grouped under sticky day headers; the
drawer starts with the app's mark. Kept: the drawer for all destinations plus the folder bar as
a shortcut (the drawer also holds single feeds, Feeds and folders and Settings, which the bar
can't).

## 2026-10-15: Folder icons from the whole Material Symbols set, with search

The owner asked for many more folder icons ("just use an entire library with keyword search").
The app now bundles the Material Symbols set (4 229 icons, outlined rounded style, Apache 2.0)
as one text file in `assets/` (about 0.9 MB in the APK): name, category and SVG path data per
icon, generated from the `@iconify-json/material-symbols` package. Icons are drawn as vectors
from that path data, and the folder dialog searches names, categories and a few related words.
Folders now store the icon's name; the 19 icons of v0.0.3 are mapped to their Material Symbols
names. Rejected: `material-icons-extended` (a new library, the older icon set, and no search
words) and the Material Symbols font (`.woff2` only, which Android can't load, and no names to
search). Trade-off: search words are English.

## 2026-10-15: Feeds and folders get their own page

Managing feeds and folders was two sections in Settings; with folders, the feeds belong under
their folder, which a settings section can't show well. Asked for by the owner. The new page
(☰ › Feeds and folders, and a link at the top of Settings) lists each folder with its feeds,
then the feeds outside folders, with Add feed, New folder and OPML import/export. Settings keeps
only how the app looks and behaves.

## 2026-10-15: Website icons (favicons) from the sites themselves

The owner asked for favicons so sources are recognisable. The app reads the icon a feed's
website announces on its home page (the largest `apple-touch-icon` or `icon`; SVG skipped,
since that would need another Coil module), or else uses `/favicon.ico`, and downloads it with
Coil like other pictures. Looked up once per feed (after a refresh or when added) and stored with
the feed. Rejected: Google's or DuckDuckGo's favicon services (simpler, but they would learn every
site the user follows, a third party `PRIVACY.md` doesn't allow). Feeds without an icon show
their first letter on a tinted square.

## 2026-10-15: Article list views, swipe, filter in the top bar; refresh merged at once

Asked for by the owner after using v0.0.3:
- Three list views (Small without picture, Medium as before, Large card) in a new Settings ›
  Article list section, which also holds Mark as read on scroll.
- Swiping an article either way toggles read (`SwipeToDismissBox`, springs back; also an
  accessibility action).
- The Unread/All chips became one icon toggle in the top bar, with the unread count under the
  title: chips suit more than two options. Settings is only in the drawer, not also in ⋮.
- The folder bar's tabs have a fixed width (icon with name below) so switching no longer makes
  the bar change size and jump.
- A refresh used to merge each feed as soon as it was downloaded, so articles popped up in the
  middle of the list while reading. Now all feeds are downloaded first and merged in one update;
  dates in the future are clamped to now so they can't stick to the top.

## 2026-10-15: Mark as read on scroll, off by default

Asked for by the owner. An article counts as read once it has scrolled off the top of the list
(not merely shown), and stays in the Unread list until it changes, like opened articles, so
scrolling back still finds it. Off by default so nothing is marked read without the user choosing
it.

## 2026-10-15: Folders, a floating folder bar and a Mark all read button

- Feeds can be put in folders, each with a title and an icon from a fixed set of 19 (drawn in the
  app, no icon library). A feed is in at most one folder. Asked for by the owner.
- Once a folder exists, a floating bar at the bottom of the list switches between Home (all feeds)
  and each folder. The bar only shows the selected tab's title, so many folders still fit; it
  scrolls sideways when they don't. Rejected: Material's `NavigationBar` (fixed, at most five
  items, not floating).
- Mark all read moved from the top bar to a floating action button above the bar. It marks only
  the section shown (Home, a folder, or one feed) and offers Undo.
- Storage: folders are a new record type in `library.txt` and feeds got a folder field; older files
  load unchanged. OPML import and export keep the folders (nested outlines), so moving from
  Inoreader or Feedly keeps them too.

## 2026-10-15: Save and Share buttons send to any app; one OPML import

- The Save and Share buttons each have a target in Settings › Save and share: one app, picked
  from the installed apps that accept shared text (the article goes straight there), or the share
  menu. Save defaults to Instapaper (as before; the old setting carries over), Share to the share
  menu. The manifest's `<queries>` now names the share intent instead of the Instapaper package,
  so the list can be shown (package visibility, not a permission).
- "Import from Inoreader" is gone: Import/Export OPML is universal and the hint names Inoreader
  and Feedly as examples. Asked for by the owner.

## 2026-10-15: Pictures with Coil; embeds as cards

The owner asked for article pictures as list thumbnails and reader hero, plus inline pictures and
embedded content. Added **Coil 3 + OkHttp** (the same versions Tracker for Simkl uses) to load
and cache pictures; writing our own loader (downsampling, memory and disk cache) would be more
code and more bugs. The reader stays native: the article is split into text, picture and embed
blocks (`ArticleBlocks`, tested) so the reader's font, size, spacing and sepia/dark page still
apply. Embedded players (YouTube, Vimeo, posts) are cards with a preview that open the content
in its app or the browser. Chosen by the owner over a WebView reader, which would play embeds
inline but run the sites' and players' scripts. Picture addresses are upgraded to https like
feeds; tracking pixels are skipped. Settings › Reader › Show images (on by default) turns
pictures off. Coil's caches live in the cache folder, so they are never backed up.

## 2026-10-15: Icon thicker, with more padding

After using v0.0.1 the owner found the icon too thin and too tight in the launcher shape. The
symbol is now about 44dp wide instead of 50dp (more white space around it) with thicker waves and
dot (owner's pick "T3" from mockups).

## 2026-10-15: Settings screens make room for the keyboard

The app targets Android 15+ and draws edge to edge, so the system no longer shrinks the screen when the keyboard opens. `SettingsScaffold` (shared design kit, same change in every app) now adds the keyboard to its content insets, so a focused text field scrolls into view instead of hiding behind the keyboard. Rejected: opting out of edge-to-edge (not possible for apps targeting Android 15+).

## 2026-10-15: Import from Inoreader through its OPML export

The owner asked for an Inoreader import. It is a guided dialog: open inoreader.com, export the
feeds as OPML there, pick the file here (the existing OPML import). Chosen by the owner over
signing in with Inoreader's API, which would need a registered Inoreader app (client ID and
secret), an OAuth sign-in screen and inoreader.com as a new network destination. Trade-off:
read and starred states are not imported, only the feeds.

## 2026-10-15: Icon without a box

The owner chose the feed symbol on its own, with the blue gradient, over the first design (a
gradient rounded square with a white symbol inside), after comparing mockups, drawn with the proportions of the
classic feed icon the owner supplied (thick waves, round ends) so it stays clear at small sizes. This departs from the shared icon rule in
`DESIGN.md` ("one bold shape with one white detail inside"); recorded under "This app" there, and
the master copy in the template should mention the exception.

## 2026-10-15: Full articles in the reader, Android backup

- **Full articles:** for feeds that only carry a summary (under 1 500 characters of text), the
  reader downloads the article's page and shows just the article, like a browser's reader view.
  On by default (Settings › Reader › Load full articles), switchable per article. Asked for by the
  owner. The extraction (`ArticleExtractor`) is our own small HTML reader with Readability-style
  scoring, in plain Kotlin and unit tested. Rejected: jsoup + Readability4J (two new libraries for
  one feature) and a WebView (runs the site's scripts, ads and trackers). Trade-off: some unusual
  page layouts extract badly; the feed text and "Read on website" stay one tap away.
- **Where full articles live:** files in the app's cache folder (300 most recent), not DataStore.
  They can be downloaded again, Android may clear the cache when space runs low, and the cache is
  never backed up, which keeps backups small.
- **Backup:** Android's built-in Auto Backup and device-to-device transfer (`allowBackup` with
  `data_extraction_rules.xml`) cover the feed list, articles, read state and settings. Nothing in
  the app is secret, so nothing is excluded. Rejected: a key/value `BackupAgent` (more code, and
  Auto Backup already handles DataStore files).

## 2026-10-15: RSS Reader, built from the template

- **Identity:** app name "RSS Reader", application ID `io.github.impca201.rssreader` (the
  `io.github.impca201.*` scheme of Tracker for Simkl and Snoozer). The ID can still change until
  the first release; after that it never may. Debug builds are called "RSS Reader Dev", like the
  sibling apps.
- **Internet permission:** an RSS reader has to download feeds. The app only contacts the
  addresses the user adds (or types), with no account, cookies, analytics or third-party services.
  `PRIVACY.md` says so. Asked for by the owner ("build me a full RSS reader").
- **HTTPS only:** `http://` and `feed://` addresses are upgraded to `https://`, and Android's default
  block on unencrypted traffic stays on. Rejected: allowing cleartext traffic for all hosts, which
  would let anyone on the same Wi-Fi change articles. Revisit if a feed the owner needs has no
  HTTPS.
- **No new libraries beyond the sibling apps':** DataStore (already in the catalog, as `CLAUDE.md`
  asks) and `lifecycle-runtime-compose` (used by Snoozer and Tracker for Simkl, for
  `collectAsStateWithLifecycle`). Downloads use `HttpURLConnection`, feeds and OPML the platform's
  XML parser, article text Compose's `AnnotatedString.fromHtml`. Rejected: OkHttp, Room,
  kotlinx.serialization, Coil and a WebView. They would add size and setup, and a WebView would
  run the sites' scripts.
- **Storage:** the whole library (feeds, articles, read state) is one immutable `Library` value
  stored with DataStore in a small tab-separated text format (`LibraryCodec`, tested). At most 200
  articles per feed and 200 000 characters per article keep it small. Rejected: Room (a database
  and annotation processor for a few thousand rows) and a JSON library (a new dependency).
- **Reader without images:** article HTML is cleaned (scripts, embeds, images, inline colours and
  fonts removed) and shown as styled text, so the reader's font, size, spacing and sepia or dark
  background always apply, and no tracking pixels load. "Read on website" opens the full page.
  Images would need an image library (Coil); left for a later decision.
- **Save sends to Instapaper:** the reader's Save button sends the link with an `ACTION_SEND`
  intent aimed at the Instapaper app (`com.instapaper.android`), the same as choosing Instapaper in
  the share menu. No Instapaper account or API key in this app. Settings › Save for later can
  switch Save to the share menu; without Instapaper installed, the share menu opens. The manifest
  declares Instapaper in `<queries>` (package visibility, not a permission) so Settings can tell
  whether it is installed.
- **Refresh only while the app is used:** once per app start and on pull-to-refresh. Rejected for
  now: background refresh and new-article notifications (WorkManager, a new dependency, and
  battery use).
- **Languages:** English, Dutch, French, German, Spanish and Italian, like the sibling apps (the
  template has only English and Dutch). French, Spanish and Italian plurals include `many`.
- **Theme:** seed `#0091EA` (the icon's middle stop), tonal palette generated the same way as
  Snoozer's (primary from the seed's chroma, neutrals at chroma 4/8, tertiary from the icon's sky
  blue). Dynamic color off by default, per `DESIGN.md`.

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-13: Template baseline

- **Language/UI:** Kotlin + Jetpack Compose + Material 3, single `app` module. This is Google's
  recommended, mainstream stack with the most documentation and examples.
- **Tool versions (latest stable on this date):** Gradle 9.8.1, Android Gradle Plugin 9.4.1
  (uses its built-in Kotlin support, so no separate `kotlin-android` plugin), Kotlin 2.4.21
  (Compose compiler plugin), Compose BOM 2026.09.00. Dependabot keeps these current.
- **minSdk 31 (Android 12).** The owner installs apps on their own, recent phone, so older
  Android versions aren't worth the extra code and testing. Android 12 brings Material You dynamic
  colour (no fallback code path), the modern splash screen and per-app language settings. It still
  covers the large majority of active devices. Lower it only if someone with an older phone needs
  the app.
- **compileSdk / targetSdk 37 (Android 17)**, the latest stable API level. The minor SDK release
  37.2 exists but isn't needed by anything yet.
- **Java 17 bytecode, JDK 21 to build.** JDK 21 is what Claude Code cloud sessions provide, so local
  checks match CI exactly.
- **Version numbers:** release `X.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, passed in by the release
  workflow. All other builds use `git describe --tags --long` (e.g. `1.2.0-3-gabc1234`), falling back to
  `0.0.0-dev`, so a dev build can never be mistaken for a release. Version logic lives in
  `scripts/release_version.py` (unit tested) and `app/build.gradle.kts`.
- **Debug builds use `applicationIdSuffix = ".debug"`** so a pull request's APK installs next to the
  released app instead of conflicting with it.
- **Releases are GitHub Releases with a signed APK**, published by `gh release create` (preinstalled
  on runners) instead of a third-party action, which means one fewer dependency with write access.
  The R8 mapping file is attached to each release.
- **Signing secrets** are base64 and decoded by a tolerant, tested script (`scripts/decode_keystore.py`)
  that never prints the secret. The key password is checked with a small Java program
  (`scripts/VerifySigningKey.java`), because `keytool` ignores the key password of PKCS12 keystores and
  the build would otherwise fail later with an unclear error.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT licensed). The default
  "enhanced" provider is closed source under separate terms of use.
- **CI artifacts:** the debug APK is uploaded unzipped (`archive: false`) so it can be installed on a
  phone straight from the download.
- **Languages:** English (default) + Dutch, as a working example of translations. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.
