# Instapaper

Content can connect to the user's own Instapaper account (Instapaper API v2, OAuth 2), in ☰ ›
Manage feeds and Saved › Saved. That connects **Saved** to Instapaper (#107, #109); it is not a way
to sign in to Content (#215: the Content account signs in with an email link, `docs/account.md`).
There are no options (#251): everything in Saved syncs both ways, highlights included.

## The reading list and Instapaper (Android)

Saved is Content's own (items of the library, `data/Items.kt`): it works without an account.
Connecting asks once, **Import from Instapaper?** (#251): **Import** brings in everything Instapaper
has now, each article where it belongs, as it is; **Don't import** remembers it all as seen, so it
stays there until it changes. The sync waits for the answer (`ServiceStatus.ImportChoice`,
`ServiceManager.chooseImport`), which is kept as a synced record (`data/ServiceImport.kt`,
`services.instapaper.imported`), so another device doesn't ask again. From then on
`services/ServiceManager.kt` keeps Instapaper in step with everything in Saved, both ways, through
`services/ServiceSync.kt` and `instapaper/InstapaperReadingList.kt`, while the app is on screen:
when it comes on screen or connects, two seconds after a change to Saved, on pull-to-refresh and on
Sync now. Never in the background.

- **Reading changes:** `GET /api/2/bookmarks?since=<time>` (500 a page, with `deleted_ids`) returns
  everything changed since the last sync across the whole account; the first sync asks with
  `since=1`, so the device keeps a full copy and Search covers every article. The next `since` is
  the time of the request minus five minutes (clocks differ; taking a change twice is harmless).
- **Merging:** each article is merged three ways with the article as last seen or sent at Instapaper
  (the `service_rows` table): a change on one side wins; both changed, the later change wins
  (Instapaper's only change time is the reading progress's, so otherwise Reader's); an edit beats a
  removal; tags as a set (`ServiceSync.merge`). An article seen there but not imported stays there
  until it changes.
- **Sending changes:** what differs from the last-seen article is sent: new articles
  (`POST /bookmarks` with title and tags, and for one saved in the last week the text Content
  shows, see below; then `/move` to archive), archive (`/move`), like
  (`/like`), tags (`/tags`, removed by id), the title and reading progress (`POST /bookmarks/{id}`
  with `progress`), and removal from the reading list (`DELETE /bookmarks/{id}`). A failure (offline,
  `429`, a server error) stops the sync and keeps everything in Reader; the rest goes next time. A
  refused token (`401`) signs out.
- **Folders** became tags (#187). Content neither reads nor changes Instapaper's folders: an
  article in one there is in To read here, and stays in its folder there. When the update first
  runs, each saved article's folder becomes a tag, which then goes to Instapaper as a tag.
- **Liked** maps to Instapaper's liked (the old "star"), again since #206; **archived** to its archive.
- **The text** is always Reader's own (#134): a saved article is read like a feed article, through
  `ArticleExtractor`. Instapaper's parsed text (`GET /bookmarks/{id}/parse`) is not used: it is only
  given to the account that registered the app, and no Instaparser key is bought (#113). Texts kept
  before are deleted when the app starts.
- **Instapaper gets that text too** (#247): a new article goes to Instapaper with `content`, the
  article's HTML as Content shows it (the one stored text every device shows, `SharedText`; the
  first device to need it extracts and stores it). So Instapaper shows what Content shows, and its
  highlights find the same passages. Only for articles saved in the last week (`CONTENT_DAYS`), so
  connecting doesn't load the whole backlog; older ones, and any whose page can't be read, go as a
  link and Instapaper fetches the page itself, as before. Should Instapaper refuse the text (too large,
  say), the link alone is sent. Only on creation: an article already in Instapaper keeps its text there.
  The web app does the same through the feed loader, which allows up to 1 MiB for saving a bookmark
  (64 KiB for its other calls).
- A link saved without a title (shared or added) takes Instapaper's title when Instapaper knows it.
- **Signing out** forgets what was synced with Instapaper; the reading list stays.


## The web app

Saved looks and works the same signed in or not (the owner's rule: services sync, they don't change
the screens): it is always the library's items, with the Android app's rules (`web/src/lib/saved.ts`,
#147): To read, All, the folders and the Archive as tabs, Liked and the tab's tags as chips, the type
filter, and notes and highlights on the cards of a folder. Signed in, `web/src/lib/serviceSync.ts` keeps
it the same as Instapaper in the background, by the Android app's rules (`ServiceSync.kt`): what
changed there since the last look comes in (`since`), what changed here goes there (title, link,
tags, like, archive, folder, reading progress); the web app asks the same import question
(`app.chooseInstapaperImport`, #251), and Settings › Integrations › Instapaper says how many came in.
`web/src/lib/instapaperSync.ts` is Instapaper's side (`InstapaperReadingList.kt`'s twin), through the
Worker. It runs after every sync with the Reader account (when the page opens, a moment after a
change, and when it comes back into view). This browser's copy of what Instapaper had is kept in
IndexedDB (`instapaperSync`) and forgotten on sign-out; Saved stays.

## How sign-in works

1. Settings › Integrations › Instapaper › **Sign in** opens `https://www.instapaper.com/oauth2/authorize` with the
   client ID, the callback address, `response_type=code` and a random `state` (kept on the device).
2. The user approves Reader on Instapaper's page. Instapaper sends them back to the callback
   address with `?code=...&state=...`.
3. The app checks `state`, then asks the feed loader (`POST /instapaper/token`) to swap the code for
   an access token. That swap needs the **client secret**, which can't live in an app or in the web
   bundle, so only the Worker has it.
4. The token never expires. It is kept on the device and, signed in to a Content account, in the
   account's synced settings (`services.instapaper.token`, `docs/sync.md`), so **Instapaper is
   connected once for every device of the account**, the web app's and the Android app's alike.

## One sign-in for every device

`sync/SharedInstapaper.kt` and `web/src/lib/sharedInstapaper.ts` (the same rules, both tested):

- **Connecting** on one device puts its token (with the Instapaper user's id and name) in the
  file; another device takes it at its next sync and is connected, without the import question
  (its answer is synced too, `services.instapaper.imported`).
- **Disconnecting** on any device, or Instapaper refusing the token (401), writes `""`: every
  device disconnects at its next sync.
- A device that has **never** been connected sends nothing, so it doesn't disconnect the others; a
  device that was connected at its last sync and isn't now sends `""`.
- A new token for the **same Instapaper user** (same user id; two devices that each signed in
  before this) keeps what was synced with Instapaper; another user starts afresh, as signing in
  again always did. Settings follow the sync file's merge (a change on one side wins; both changed,
  this device wins), so after updating, the devices settle on one token.

One callback address serves both apps:
`https://content.wim.to/instapaper/callback`.

- **Web:** the page itself handles it (`AppStore.finishInstapaperSignIn`, `web/src/lib/app.ts`).
- **Android:** it is an Android App Link, so the phone opens the app instead of the browser
  (`MainActivity` → `InstapaperAccount.finish`).

## Where things are

| What | Where |
|---|---|
| Token swap, and the web app's Instapaper calls | `worker/src/instapaper.ts` (tests in `worker/test/instapaper.test.ts`) |
| Web: client, sign-in helpers | `web/src/lib/instapaper.ts` |
| Web: Saved and the reader | `web/src/lib/saved.ts` (tested), `Sidebar.tsx`, `ArticleList.tsx`, `SavedActions.tsx` in the reader; Save in `ArticleView.tsx`; sign-in in Settings |
| Android: client, sign-in, the reading list's service | `app/src/main/kotlin/to/wim/content/instapaper/` (`InstapaperClient`, `InstapaperAuth`, `InstapaperAccount`, `InstapaperReadingList`) and `services/` (`ServiceSync`, `ServiceManager`) |
| Android: the reading list's screens | `ui/home/ReadingListContent.kt`, `FolderTabs.kt` (`ReadingListTabRow`), `FeedDrawer.kt`, `ui/reader/ItemSheets.kt`, `ui/feeds/TagsManage.kt`, `ui/settings/ServicesScreen.kt` |
| Android: App Link and backup exclusion | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |
| App Link proof for the website | `web/public/.well-known/assetlinks.json` |

The web app can't call Instapaper directly (its API sends no CORS headers), so the Worker forwards a
short list of calls: bookmarks (list, save, delete, move, like, tags), folders (list, create,
delete, reorder) and tags (list, create, rename). The Android app calls Instapaper directly.

## One-time setup (owner)

1. **Register the application** at <https://www.instapaper.com/developers/applications> (title
   Reader; application URL the GitHub repository). Callback URIs, one per line:
   `https://content.wim.to/instapaper/callback`. The client ID goes into
   `worker/wrangler.toml` (`INSTAPAPER_CLIENT_ID`), `web/src/lib/config.ts` and `InstapaperAuth.kt`; it
   is public.
2. **Put the client secret in the Worker**, never in the repository: Cloudflare dashboard › Workers
   & Pages › `rss-reader-worker` › Settings › Variables and Secrets › Add, type **Secret**, name
   `INSTAPAPER_CLIENT_SECRET`. (Or `npx wrangler secret put INSTAPAPER_CLIENT_SECRET` in `worker/`.)
   Secrets survive deployments; a plain variable would not.
3. **App Links** need `assetlinks.json` on the callback's website, with the package name
   `to.wim.content` (and, until everyone has moved over, the old `be.wimvandonck.content` and `be.wimvandonck.reader`) and the SHA-256 fingerprint of the release signing key. If the signing key
   ever changes, update the fingerprint here. Check it after each deployment:
   `https://content.wim.to/.well-known/assetlinks.json` must show that file, not
   the web app's page.
4. **Review.** A new Instapaper application is *Owner Only*: it works only for the account that
   registered it. Submit it for review in Instapaper's developer pages before other people use it;
   until then, other accounts see "Instapaper hasn't approved Reader for your account yet" (HTTP 403).

If the callback address ever changes, it must be changed in four places that must match exactly: the
Instapaper application, `INSTAPAPER_REDIRECT_URI` in `worker/wrangler.toml`,
`INSTAPAPER_REDIRECT_URI` in `web/src/lib/config.ts`, and `InstapaperAuth.REDIRECT_URI` with the
intent filter in `AndroidManifest.xml`.

## Syncing highlights (#164)

Always on while Instapaper is connected (#251: there is nothing to choose any more). Free Instapaper
accounts can make five highlights a month; the rest stay in Content and are tried again a day later.

Both ways, with the same rules and tests on Android (`services/HighlightCopy.kt`) and the web
(`web/src/lib/highlightCopy.ts`):

- **When:** after each Instapaper sync, for every article in step with Instapaper that has something
  to send (an article with nothing new costs no call), and when a saved article is opened in the
  reader (its highlights in Instapaper are looked up then, so ones made there come in).
- **Instapaper → Content:** a highlight made in Instapaper's own apps becomes a Content highlight
  (id `instapaper-<id>`), placed by its passage and which occurrence it is. One deleted there is
  deleted here. A note changed there comes in, unless it changed here too (then Content's wins).
- **Content → Instapaper:** new ones are made there; a changed note deletes the copy and makes it
  again (Instapaper can't edit one); one deleted in Content is deleted there.
- **No doubles:** a highlight with the same passage on both sides (typographic differences ignored)
  is the same one, so two devices with the switch on don't make it twice.
- **The limit:** Instapaper answers 403 when the month's highlights are used up. The highlight stays
  in Content, its list shows "Not in Instapaper yet", and it is tried again a day later.
- Which copy belongs to which highlight is kept per device (`<service>-highlights` in the database's
  meta table on Android, `instapaperHighlights` in IndexedDB on the web) and forgotten on disconnect.
- Instapaper places a highlight at the nth occurrence of the passage in *its* text, using Content's
  occurrence number, and the other way round. When a passage appears more than once, the two may mark
  different occurrences.
- The web app goes through the feed loader, which allows only these three highlight calls
  (`GET`/`POST /bookmarks/<id>/highlights`, `DELETE /highlights/<id>`).

## Not supported

- Instapaper's parsed text for accounts other than the owner's (it needs an Instaparser key).
- Signing in from a debug build's App Link: debug APKs are signed with a different key every CI run
  and have another package name (`.debug`), so Android doesn't verify the link for them and opens the
  browser, where the *web app* finishes the sign-in. Try the Android sign-in with a released build.
