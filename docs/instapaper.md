# Instapaper

Reader can sign in to the user's own Instapaper account (Instapaper API v2, OAuth 2). Signing in
also opens the user's Reader account, which syncs the library (`docs/account.md`), and connects the
**reading list** to Instapaper (#107, #109).

## The reading list and Instapaper (Android)

The reading list is Reader's own (items of the library, `data/Items.kt`): it works without an
account. Signed in, `services/ServiceManager.kt` keeps it and the Instapaper account the same through
`services/ServiceSync.kt` and `instapaper/InstapaperReadingList.kt`, while the app is on screen: when
it comes on screen or signs in, two seconds after a change to the reading list, on pull-to-refresh
and on Sync now. Never in the background.

- **Reading changes:** `GET /api/2/bookmarks?since=<time>` (500 a page, with `deleted_ids`) returns
  everything changed since the last sync across the whole account; the first sync asks with
  `since=1`, so the device keeps a full copy and Search covers every article. The next `since` is
  the time of the request minus five minutes (clocks differ; taking a change twice is harmless).
- **Merging:** each article is merged three ways with the article as last seen at Instapaper (the
  `service_rows` table): a change on one side wins; both changed, Reader's; an edit beats a removal;
  tags as a set (`ServiceSync.merge`).
- **Sending changes:** what differs from the last-seen article is sent: new articles
  (`POST /bookmarks` with title and tags, then `/move` to archive), archive (`/move`), like
  (`/like`), tags (`/tags`, removed by id), the title and reading progress (`POST /bookmarks/{id}`
  with `progress`), and removal from the reading list (`DELETE /bookmarks/{id}`). A failure (offline,
  `429`, a server error) stops the sync and keeps everything in Reader; the rest goes next time. A
  refused token (`401`) signs out.
- **Folders** are Saved's folders (#119): home is To read, a folder is a folder (`/move` with the
  folder's id), the archive is the archive. Filing into a folder Instapaper doesn't have makes it
  there (`POST /folders`). Before #119 folders showed as tags; the first sync after the update takes
  that tag off and files the article in its folder instead.
- **Liked** maps to Instapaper's liked (the old "star"); **archived** to its archive.
- **The text** is always Reader's own (#134): a saved article is read like a feed article, through
  `ArticleExtractor`. Instapaper's parsed text (`GET /bookmarks/{id}/parse`) is not used: it is only
  given to the account that registered the app, and no Instaparser key is bought (#113). Texts kept
  before are deleted when the app starts.
- A link saved without a title (shared or added) takes Instapaper's title when Instapaper knows it.
- **Signing out** forgets what was synced with Instapaper; the reading list stays.

Before #107 the Android app showed Instapaper's lists directly and kept articles saved without an
account apart (`LocalSaved`); those now live on the reading list, moved in once (#106).

## The web app

Saved looks and works the same signed in or not (the owner's rule: services sync, they don't change
the screens): it is always the library's items, with the Android app's rules (`web/src/lib/saved.ts`,
#147): To read, All, the folders and the Archive as tabs, Liked and the tab's tags as chips, the type
filter, and notes and highlights on the cards of a folder. Signed in, `web/src/lib/serviceSync.ts` keeps
it the same as Instapaper in the background, by the Android app's rules (`ServiceSync.kt`): what
changed there since the last look comes in (`since`), what changed here goes there (title, link,
tags, like, archive, folder, reading progress), and an item kept in Raindrop.io isn't sent (#121).
`web/src/lib/instapaperSync.ts` is Instapaper's side (`InstapaperReadingList.kt`'s twin), through the
Worker. It runs after every sync with the Reader account (when the page opens, a moment after a
change, and when it comes back into view). This browser's copy of what Instapaper had is kept in
IndexedDB (`instapaperSync`) and forgotten on sign-out; Saved stays. Raindrop.io isn't in the web
app yet.

## How sign-in works

1. Settings › Instapaper › **Sign in** opens `https://www.instapaper.com/oauth2/authorize` with the
   client ID, the callback address, `response_type=code` and a random `state` (kept on the device).
2. The user approves Reader on Instapaper's page. Instapaper sends them back to the callback
   address with `?code=...&state=...`.
3. The app checks `state`, then asks the feed loader (`POST /instapaper/token`) to swap the code for
   an access token. That swap needs the **client secret**, which can't live in an app or in the web
   bundle, so only the Worker has it.
4. The token never expires. It is kept on the device only; each device signs in itself. The
   Worker's answer also holds this device's key to the user's Reader account (`docs/account.md`),
   kept next to the token. Instapaper refusing the token (401) signs the user out of both.

One callback address serves both apps:
`https://rss-reader-web.wim-d57.workers.dev/instapaper/callback`.

- **Web:** the page itself handles it (`AppStore.finishInstapaperSignIn`, `web/src/lib/app.ts`).
- **Android:** it is an Android App Link, so the phone opens the app instead of the browser
  (`MainActivity` → `InstapaperAccount.finish`).

## Where things are

| What | Where |
|---|---|
| Token swap, and the web app's Instapaper calls | `worker/src/instapaper.ts` (tests in `worker/test/instapaper.test.ts`) |
| Web: client, sign-in helpers | `web/src/lib/instapaper.ts` |
| Web: Saved and the reader | `web/src/lib/saved.ts` (tested), `Sidebar.tsx`, `ArticleList.tsx`, `SavedActions.tsx` in the reader, `SavedManage.tsx` in Folders and tags; Save in `ArticleView.tsx`; sign-in in Settings |
| Android: client, sign-in, the reading list's service | `app/src/main/kotlin/be/wimvandonck/reader/instapaper/` (`InstapaperClient`, `InstapaperAuth`, `InstapaperAccount`, `InstapaperReadingList`) and `services/` (`ServiceSync`, `ServiceManager`) |
| Android: the reading list's screens | `ui/home/ReadingListContent.kt`, `FolderTabs.kt` (`ReadingListTabRow`), `FeedDrawer.kt`, `ui/reader/ItemSheets.kt`, `ui/feeds/TagsManage.kt`, `ui/settings/ServicesScreen.kt` |
| Android: App Link and backup exclusion | `AndroidManifest.xml`, `res/xml/data_extraction_rules.xml` |
| App Link proof for the website | `web/public/.well-known/assetlinks.json` |

The web app can't call Instapaper directly (its API sends no CORS headers), so the Worker forwards a
short list of calls: bookmarks (list, save, delete, move, like, tags), folders (list, create,
delete, reorder) and tags (list, create, rename). The Android app calls Instapaper directly.

## One-time setup (owner)

1. **Register the application** at <https://www.instapaper.com/developers/applications> (title
   Reader; application URL the GitHub repository). Callback URIs, one per line:
   `https://rss-reader-web.wim-d57.workers.dev/instapaper/callback`. The client ID goes into
   `worker/wrangler.toml` (`INSTAPAPER_CLIENT_ID`), `web/src/lib/config.ts` and `InstapaperAuth.kt`; it
   is public.
2. **Put the client secret in the Worker**, never in the repository: Cloudflare dashboard › Workers
   & Pages › `rss-reader-worker` › Settings › Variables and Secrets › Add, type **Secret**, name
   `INSTAPAPER_CLIENT_SECRET`. (Or `npx wrangler secret put INSTAPAPER_CLIENT_SECRET` in `worker/`.)
   Secrets survive deployments; a plain variable would not.
3. **App Links** need `assetlinks.json` on the callback's website, with the package name
   `be.wimvandonck.reader` and the SHA-256 fingerprint of the release signing key. If the signing key
   ever changes, update the fingerprint here. Check it after each deployment:
   `https://rss-reader-web.wim-d57.workers.dev/.well-known/assetlinks.json` must show that file, not
   the web app's page.
4. **Review.** A new Instapaper application is *Owner Only*: it works only for the account that
   registered it. Submit it for review in Instapaper's developer pages before other people use it;
   until then, other accounts see "Instapaper hasn't approved Reader for your account yet" (HTTP 403).

If the callback address ever changes, it must be changed in four places that must match exactly: the
Instapaper application, `INSTAPAPER_REDIRECT_URI` in `worker/wrangler.toml`,
`INSTAPAPER_REDIRECT_URI` in `web/src/lib/config.ts`, and `InstapaperAuth.REDIRECT_URI` with the
intent filter in `AndroidManifest.xml`.

## Not supported

- Highlights in Instapaper: Reader's own highlights come with #102, copying them to Instapaper is
  optional there.
- Instapaper's parsed text for accounts other than the owner's (it needs an Instaparser key).
- Signing in from a debug build's App Link: debug APKs are signed with a different key every CI run
  and have another package name (`.debug`), so Android doesn't verify the link for them and opens the
  browser, where the *web app* finishes the sign-in. Try the Android sign-in with a released build.
