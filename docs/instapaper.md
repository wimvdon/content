# Instapaper

Reader can sign in to the user's own Instapaper account (Instapaper API v2, OAuth 2). Signed in,
**Save** sends the article straight to the account, and the account's Saved (Instapaper's Unread), Liked,
folders, tags and Archive appear in the ☰ menu (web: the sidebar) next to the feeds. Their saved articles
show in the same list and reader as feed articles (without read state; archiving is an action), and
☰ › Manage Instapaper (web: Manage Instapaper in the sidebar) manages its folders and tags.
Signing in also opens the user's Reader account, which syncs the library (`docs/account.md`). Not signed
in, the same **Saved** section works on the phone or in the browser itself (`LocalSaved`, see below).

Saved articles show in lists, Search and the reader like the feed article with the same address when
the library still has it (`InstapaperLists.article`/`source`, web `bookmarkArticle`/`sourceOf`): the
feed's text, picture, name and icon, and the same choice between that text and the full article.
Otherwise they show Instapaper's description and picture under their website's icon.

In the app this section is called **Saved**, with or without an account, and its first list (Instapaper's
Unread, "home") is called **Saved** too, so it isn't mixed up with unread feed articles.

## Saved without an account

Signed out, the Saved screen runs on `instapaper/LocalSaved.kt` instead of `InstapaperClient`: the
same `InstapaperService`, so the lists, Search, the reader's buttons and the sheet after Save behave
the same. Its articles are the library's reading list (#106): items in the library database with
their address, title, time, liked, archived and tags (`ItemsSavedStorage`, `SavedItems`), part of
Android's backup, and not synced. Saving a feed article makes the item of its link, so it isn't
copied. Versions before #106 kept them in `datastore/saved`; they move into the database once. There are no folders:
the folder parts of the screens are hidden, and a move to a folder is refused. Tags are created when
first added and disappear when no article has them any more; renaming a tag to an existing name joins
the two.

The web app does the same with `web/src/lib/localSaved.ts` (`LocalSaved`, behind the same
`SavedService` as `InstapaperApi`), kept in the browser's IndexedDB under `saved`.

**Signing in copies them to Instapaper** (`LocalSaved.uploadTo`): oldest first (so the newest ends on
top), each with its title, like, tags and archive, so it lands in the same list. Each article is
removed from the phone or browser once it is in the account, so nothing is copied twice. If
Instapaper stops answering halfway (offline, too many requests), the rest stays saved locally and the
copy goes on the next time the app opens signed in. Afterwards a message says how many were copied
and the list is read again. Android starts the copy with each new Saved model for the account
(`InstapaperAccount.newModel`), the web app after signing in and when it opens
(`AppStore.copyLocalSaved`).

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
| Web: lists and reader | `web/src/lib/instapaperLibrary.ts` (tested), `Sidebar.tsx`, `ArticleList.tsx`, `InstapaperActions.tsx` in the reader, `InstapaperManage.tsx` in Manage; Save in `ArticleView.tsx`; sign-in in Settings |
| Android: client, sign-in helpers, list logic | `app/src/main/kotlin/be/wimvandonck/reader/instapaper/` |
| Android: lists and reader | `instapaper/InstapaperLists.kt` (tested), `ui/home/` (drawer and list), `ui/reader/SavedArticleControls.kt`, `ui/feeds/InstapaperManage.kt`, `ui/settings/InstapaperSettings.kt` |
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

- Searching the full text of saved articles. Instapaper's API has no search, so Search reads the
  saved articles (the newest 100 of Unread, each folder and Archive) and searches their title,
  website, description and tags on the phone (`instapaper/SavedSearch.kt`).
- Highlights, and Instapaper's parsed article text (for accounts other than the owner's it needs a
  separate Instaparser key). A saved article opens on its own website.
- Syncing reading progress.
- Signing in from a debug build's App Link: debug APKs are signed with a different key every CI run
  and have another package name (`.debug`), so Android doesn't verify the link for them and opens the
  browser, where the *web app* finishes the sign-in. Try the Android sign-in with a released build.
