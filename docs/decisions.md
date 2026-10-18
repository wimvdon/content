# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-18: The navigation bar is back, next to the drawer

The bar (Feeds | Instapaper) was removed earlier today because the drawer changed with the
destination shown. The owner missed it and asked for it back. Both problems are solved together
now: the bar (rail on wide windows) switches between the two destinations as Material 3
recommends for two to five, and the drawer keeps both sections everywhere, with the one you're in
open and the other folded shut. The bar only shows when signed in to Instapaper (one destination
needs no bar). Global search, also asked in the same issue, is a separate change. Rejected: a bar
with a third Search destination (search acts on the content of both, so it belongs in the top bar)
and dropping the drawer (single feeds, Manage and Settings need a place).

## 2026-10-18: Unread | All as one filter chip in the top bar

The connected Unread | All buttons had a row of their own under the title and tabs and drew the
eye more than the articles. The owner asked for chips. Material 3's filter chip is the component
for a filter that is on or off, so Unread | All is now one **Unread** chip at the end of the top
app bar: selected (with a check) for unread only, unselected for all. The row is gone, so the list
starts higher. The web app keeps its Unread | All switch (the issue was about Android). Rejected:
two chips (Unread and All) in their own row (still a row, and two chips for one on/off choice) and
an icon button (its state isn't readable at a glance, as an earlier version showed).



## 2026-10-18: Manage icons: the feed symbol and a bookmark, each with a pen

Manage feeds had a generic icon (lines with a pen on Android, sliders on the web) and Manage
Instapaper a bookmark, so the two didn't read as a pair and the bookmark looked like a list rather
than a page to manage. The owner asked for a feed with a pen and a bookmark with a pen. Neither
Material Symbols nor Lucide has them, so each is drawn from the set's own feed or bookmark shape
(made smaller) and the pen that the set uses in its other "manage" icons (`bookmark_manager`,
`user-pen`). Rejected: `bookmark_manager` (a folder, not a bookmark) and a pen on its own (doesn't
say what it manages).

## 2026-10-18: The drawer opens the section you're in

With Feeds and Instapaper both open, the ☰ drawer grew long (every folder and feed, then every
Instapaper folder and tag). The owner asked for the sections to fold by themselves depending on
where you are. The section being shown is now open and the other folded shut; moving to the other
section turns it round. Folding by hand still works and lasts until you move. The drawer keeps
both sections, so it still looks the same everywhere. Rejected: remembering the folds (the drawer
would stay long once both were opened) and dropping the other section (the drawer would change
with the destination again).

## 2026-10-18: Unread | All is no longer synced

Unread | All was a synced setting, so switching it on one device switched the list on every other
device as soon as it synced. The owner pointed out that it is navigation (where you are in the
list), not a setting. It is still remembered on each device, but no longer in the sync document;
older documents' `unreadOnly` is dropped on the next sync. Rejected: not remembering it at all (the
list would reset to Unread every time the app starts).

## 2026-10-18: Sync through a Reader account, signed in with Instapaper

Google Drive sync was unreliable: read marks went missing and arrived late. Three causes: Drive has
no "save only if unchanged", so two devices saving at once lost one's changes; a change made while
a sync ran (the web app) was overwritten or waited a minute; and other devices only looked every
minute (and the web app's Google sign-in lasted an hour). The owner chose an account instead, with
Instapaper as the first way to sign in, so there is no Reader password system to build.

- **Identity:** the feed loader already swaps Instapaper's sign-in code, and Instapaper's answer
  names the user. At that moment it gives the device a key to that user's account. The account id is
  an HMAC of the Instapaper user id, so other sign-in methods can map to an account later.
- **Storage:** one Cloudflare Durable Object (SQLite-backed, on the free plan) per account. It runs
  one request at a time, so a save carries the version it was based on and is refused (`409`) when
  another device saved first; the device merges again. It also holds a WebSocket per open device
  (hibernating, so quiet connections cost nothing) and tells them at once when the document changes:
  changes now arrive within a second or two. Nothing to create in the dashboard; only the secret.
  Rejected: D1 (works, but needs a database created by hand and has no live connection), Workers KV
  (changes take up to a minute to reach other locations, so syncs would fight) and polling every few
  seconds (costs requests all day).
- **Encryption:** server-side, AES-256-GCM with a key per account derived from the `ACCOUNT_KEY`
  secret, on top of Cloudflare's own. This is what comparable apps do. End-to-end encryption
  (a passphrase on every device, lost passphrase means lost data) was judged too much for a feed
  list and read marks, also later.
- **Same format and merge:** the document is the sync file of `docs/sync.md` and the three-way merge
  is unchanged; only where it lives changed. The Instapaper token is no longer in it: each device
  signs in itself.
- **Drive removed** (no users yet), with Google Sign-In (`play-services-auth`) and the Google Cloud
  setup. Also fixed: a change made during a sync is synced right after it, and the web app merges
  with the library as it is at that moment. A change syncs after one second instead of two.
- **Privacy:** Reader now keeps data on the owner's server; `PRIVACY.md` says what, how and how to
  delete it (Settings › Account › Delete synced data).

## 2026-10-18: One navigation drawer with Feeds and Instapaper as sections

The navigation bar (Feeds | Instapaper) made the ☰ drawer change with the destination shown, while
the drawer is the app's top-level navigation and should stay the same (owner's review). The bar and
rail are gone; the drawer (and the web sidebar) shows Feeds and Instapaper as sections whose header
folds them open and shut. Managing is split and named after what it manages: Manage feeds ("Feeds and
folders") and Manage Instapaper ("Instapaper folders and tags"), both in the drawer. Settings now
follows the shared order: Account first (sign-in and sync together), then the app's own sections;
the Feeds row in Settings is gone, as managing lives in the drawer. Rejected: keeping the bar and a
fixed drawer (two navigations for the same places).

## 2026-10-17: A new folder while moving an article

Moving a saved article to a folder only offered the folders that already existed; making one meant going to Manage feeds and folders first. Move to folder and the sheet after Save now end with **New folder**: a tap shows a field for the name, and adding makes the folder in Instapaper and moves the article into it in one step. Rejected: a separate dialog for the name (one more step) and creating the folder without moving the article (the point is to file it).

## 2026-10-17: Feeds and Instapaper as two destinations

Instapaper sat in the ☰ drawer under the feeds, with its own title but no tabs, so the two felt neither clearly apart nor alike. The owner asked for better separation and cohesion, Instapaper's lists treated like feed folders, and whether a navigation bar was needed. Material 3 puts two to five top-level destinations in a navigation bar and peer views of one destination in tabs, so: signed in to Instapaper, a navigation bar has Feeds and Instapaper (a navigation rail on windows 600dp and wider); each has tabs for its peer views (Feeds: All and the folders; Instapaper: Unread, Liked, Archive, its folders and Tags, with chips for the tag); and the ☰ drawer lists only the shown destination's places, as Gmail keeps its labels in the drawer next to its bottom bar. The web app's sidebar gets a Feeds | Instapaper switch and shows one destination's places at a time. Rejected: a tab per tag (tags come and go, and a long row of tags hides the folders), dropping the drawer (single feeds and Manage need a place), and a bar without Instapaper (one destination needs no bar).

## 2026-10-17: File an article in Instapaper right after saving it

The owner wanted to tag a feed article or put it in a folder as soon as it is saved, instead of finding it again under Instapaper. Save now opens a sheet (Android) or dialog (web) titled "Saved to Instapaper" with the folders as chips (Unread selected) and the same tag editor as a saved article's Tags. Every tap applies at once, like the reader's own Move and Tags; Done or closing leaves the rest as it is. Rejected: a snackbar with an action (it disappears before you decide) and asking before saving (saving should stay one tap).

## 2026-10-17: The real logo in the drawer and the web app

The ☰ drawer and the web app's sidebar opened with a generated mark (a plain feed symbol on a tinted circle), and the web app's favicon drew the feed symbol with other proportions than the Android icon. The owner asked for the real logo everywhere: both headers now show the launcher icon (its foreground on white, in a circle), and the web favicon uses the same symbol and gradient as the Android icon. Rejected: tinting the logo with the theme (it would no longer be the logo).

## 2026-10-17: One way to reorder, shared with the sibling apps

A design review of the sibling apps found four ways to put a list in order. The rule is now shared (`DESIGN.md`, "Reordering"): a drag handle at the end of each row, from `ui/theme/Reorder.kt`, which every app copies. Reader's feeds and folders already worked that way; the Instapaper folders on Manage feeds and folders had up and down arrows and now have the same handle (screen readers keep Move up and Move down). The article lists and Manage feeds and folders keep a readable width (640dp, `Layout.kt`) on tablets and in landscape, as the shared rule asks. Rejected: keeping the arrows for the short Instapaper list (two ways to do the same thing on one page).

## 2026-10-17: Instapaper lives in the reader's own navigation

The first Instapaper version had its own screen (Android) and dialog (web). The owner wanted the
saved articles, folders and tags next to the feeds and folders, opening like feed articles, and
managed like the feeds. Now the ☰ menu / sidebar has an Instapaper section (Unread, Liked, Archive,
folders, tags), its lists use the article list's rows, a saved article opens in the reader (which
loads the full article from the website, as for feeds) with like, archive, move, tags and delete,
and Manage feeds and folders manages the Instapaper folders and tags. The owner chose: saved
articles have no read state (archiving stays a deliberate action) and stay out of All articles.
The list logic is shared in spirit (`InstapaperLists` on Android, `instapaperLibrary.ts` on the web,
both tested): an article leaves a list when a change means it no longer belongs there (archived
out of Unread, moved out of Archive), and the reader keeps it open. Rejected: marking a saved
article read (or archiving it) by opening it, and mixing saved articles into All articles.
## 2026-10-17: Settings and the Instapaper sign-in are synced

The owner asked for settings, including everything Instapaper, to sync through Google Drive. The
sync file (version 2) has a `settings` table of shared names and text values (`docs/sync.md`),
merged per setting like the rest: a change on one side wins, this device wins a clash. A device
whose base has no settings yet takes the file's, so a new phone's defaults don't overwrite the
user's choices. Each app keeps names it doesn't know, so Android-only settings survive the web app.
The Instapaper token is in the table too, so signing in once signs in every device and signing
out signs out everywhere; it stays out of Android's backups as before, and only reaches devices
through the user's own Drive folder that only Reader can open (`PRIVACY.md` says so). The
version goes to 2 so that an older app asks to be updated instead of writing the file back
without settings. Not synced: the Save and Share targets (they name apps on one phone).
Rejected: per-setting timestamps (the base already tells which side changed), syncing the
Save/Share targets, and keeping the token out of the file (the owner wants one sign-in).

## 2026-10-17: Web: Google's popup only on a click

The web app showed Google's sign-in popup every time it was opened. Its Drive sign-in was kept only
for the open tab, and every sync without one asked Google for a new one; in a browser Google can
only hand one out through its popup, even when nothing has to be approved. The sign-in (about an
hour) is now kept in the browser's local storage, and only a click on Connect asks Google; a sync
without a valid sign-in stops with "needs your permission again". Rejected: a long-lived sign-in
kept by the feed loader Worker (no popup at all, but our server would hold a key to the user's
Drive app folder and need a client secret).

## 2026-10-17: Sign in with Instapaper (OAuth 2), through the feed loader Worker

Reader can now sign in to the user's Instapaper account (API v2): Save goes straight to the account, and a screen manages Unread, Archive, Liked, folders and tags. Details: `docs/instapaper.md`. Choices:

- **The Worker swaps the code for a token.** Instapaper's token request needs the client secret, and its docs offer no PKCE. A secret can't live in a public repository, an APK or a web bundle, so `worker/src/instapaper.ts` holds it as a Worker secret (`POST /instapaper/token`). This bends two earlier rules: the Android app now uses the Worker (for that swap only), and "no Reader server" now has one exception, a stateless pass-through that stores and logs nothing. `PRIVACY.md` says so. The redirect address is fixed in the Worker, so the endpoint can't be pointed at another address.
- **The web app's Instapaper calls go through the Worker** because Instapaper's API sends no CORS headers (checked with a preflight request). The Worker forwards an allowlist of calls only (bookmarks, folders, tags), refuses anything else, and never follows a redirect with the token. Android calls Instapaper directly.
- **One callback address for both apps**, an Android App Link on the web app's own address, because Instapaper accepts only http(s) callbacks (no custom URL scheme). The cost: debug builds can't verify the link (different key and package), so sign-in is tried with a released build.
- **The token never expires**, so there is no refresh code. It is stored in its own DataStore file on Android, excluded from backups and phone transfers, and in the browser's IndexedDB on the web. It is never in the Drive sync file.
- **Folders and tags** were added after checking Instapaper's TypeScript SDK and the documentation: the published OpenAPI file listed bookmarks only, but the documentation and the live API have folders and tags.

Rejected: a personal access token pasted into the app (works only for the account that owns the Instapaper application, so useless to other people), a custom URL scheme callback (not allowed), the client secret in the app or the web bundle (extractable from a public build), an encrypted token store (needs a new dependency; the backup exclusion keeps the token on the phone), Instapaper's parsed article text (needs a separate Instaparser key for other accounts; Reader's own full-article reader opens the saved link instead), and highlights (a separate feature).

## 2026-10-17: A refresh always syncs

A refresh skipped its sync when the last one was under ten seconds ago. Pulling down right after switching devices could therefore miss a read mark made a few seconds earlier elsewhere, and the next automatic sync was up to a minute away. Refresh now always syncs first (Android `SyncManager.sync()`, web `AppStore.sync()`); coming back to the app and the one-minute timer keep the ten-second gap so they don't sync twice in a row. Google Drive is not the limit here: reading the file by its id returns what the last upload wrote. Rejected: dropping the gap everywhere (starting the app would sync twice), remembering the Drive file id between syncs (two devices that create the file at the same moment would each keep their own copy; looking it up every time makes them settle on the newest one), and checking the file's version every few seconds (more code and Drive traffic than a refresh that always syncs needs; it can be added if waiting for the minute timer still feels too slow).

## 2026-10-17: The web app is not paused

The pause recorded further down ("The web app and its feed loader are paused") no longer applies. The owner is working on the web app again, and the repository already reflects it: the `web` job in
`.github/workflows/ci.yml` runs the tests, lint and build of the web app and the feed loader on every change, and Dependabot checks the npm packages in `web/` and `worker/` again. The README no longer calls the web app paused. The Cloudflare builds of `rss-reader-web` and `rss-reader-worker` are set up in the Cloudflare dashboard, not in this repository, so this entry says nothing about them. The sync format (`docs/sync.md`) is shared with Android and did not change. Rejected: deleting the earlier entry (the log keeps its history, and this entry replaces it).

## 2026-10-17: Sync every minute while the app is on screen

The owner asked for sync as often as Google allows, at least on marking as read and on refresh.
Marking as read already synced (five seconds after the last change), but other devices' changes
only came in when the app started. Now both apps sync when they come on screen and every minute
while they stay there, on refresh (before downloading, so articles read elsewhere arrive read),
and two seconds after a change. A sync within the last ten seconds is not repeated. One sync is
two or three Drive requests, so a minute is far below Google's per-user limits. Rejected: syncing
on every single read mark (the two-second wait already sends a row of them as one), syncing in
the background (no background work, see `CLAUDE.md`), and Drive's push notifications (they need a
server to receive them).
## 2026-10-17: The web app's addresses are built in

The feed loader's address and the Google client ID were settings in the web app (filled in from
the build), with a Connections section that only made sense to a developer. Both are public, so
they are now constants in `web/src/lib/config.ts` and Settings no longer shows them. Rejected:
keeping them as build variables (`.env.production`), which added a step without a benefit for a
single published site.

## 2026-10-17: New name "Reader" and package `be.wimvandonck.reader`

The owner renamed the app to Reader and asked for a package name under `be.wimvandonck`. The
application ID and the Kotlin package are now `be.wimvandonck.reader` (debug builds
`be.wimvandonck.reader.debug`, "Reader Dev"). Android treats a new application ID as a different
app: phones with the old RSS Reader (up to v0.3.4) don't update to it but install Reader next to
it, with an empty library (sync or OPML brings the feeds over). Google Cloud's Android client
had to be made again for the new package anyway. The repository, the APK file names
(`rss-reader-…`), the sync file (`rss-reader-sync.json`) and the Cloudflare names stay, so
nothing that stores or links them breaks. The shared design table in `DESIGN.md` still says
"RSS Reader" until it changes in the template. Rejected: keeping the old ID (the owner's
explicit choice) and moving data over from the old app (it can't read another app's storage).

## 2026-10-17: Sync says when Google doesn't know this build

Drive sync didn't work and the app said "Couldn't reach Google Drive", whatever Google's reason.
Google refuses an app whose package name and signing key it doesn't know (DEVELOPER_ERROR), which
is the usual setup problem; that now shows as its own message. The release workflow prints the
signing key's SHA-1 on its summary page, so the owner can register it without `keytool`
(`docs/sync.md`, step by step). Debug APKs from CI get a new key on every run and can't be
registered. Rejected: signing debug builds with the release key, or a fixed debug key as a
secret (changes the signing setup, for test builds only).

## 2026-10-17: No Instapaper account sign-in

The owner didn't ask for the Instapaper login of 2026-10-16 and wants saving tackled later, so
it is removed again (Android and web): Save goes to the chosen app (Instapaper by default) or the
share menu, as before. On the web there is no Save button for now; Share stays.

## 2026-10-17: The web app is back and finished

The owner asked to finish the web app, so the pause below is lifted the same day: its CI job and
its npm updates run again. It now matches the Android app: teasers that link to their article
load the full article (same rule and threshold), the reader switches between feed text and full
article and keeps the last 100 full articles in IndexedDB, Aa text settings (font, size,
spacing, background, Auto dark), mark as read on scroll, and embedded videos and posts as a card
that opens them in a new tab. The cards replace the YouTube and Vimeo players that played inside
the page, so the web app no longer loads those players at all. The Google client ID is built in
(`web/.env.production`). The Cloudflare builds were never switched off, as the pause entry
assumed: they fail within a second on every push, with no log on GitHub, which points to their
settings in the Cloudflare dashboard, not to the code (the same build passes in CI). Rejected:
playing videos inside the page (third-party players, unlike Android), a Show images setting and
list sizes (the owner doesn't want them on the web).

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
