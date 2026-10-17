# Instapaper

Reader can sign in to the user's own Instapaper account (Instapaper API v2, OAuth 2). Signed in,
**Save** sends the article straight to the account, and a saved-articles screen shows Unread,
Archive, Liked, folders and tags, with archive, like, delete, move to a folder and tags. Not signed
in, Save works as before: Instapaper's app or the share menu (see `usage.md`).

## How sign-in works

1. Settings › Instapaper › **Sign in** opens `https://www.instapaper.com/oauth2/authorize` with the
   client ID, the callback address, `response_type=code` and a random `state` (kept on the device).
2. The user approves Reader on Instapaper's page. Instapaper sends them back to the callback
   address with `?code=...&state=...`.
3. The app checks `state`, then asks the feed loader (`POST /instapaper/token`) to swap the code for
   an access token. That swap needs the **client secret**, which can't live in an app or in the web
   bundle, so only the Worker has it.
4. The token never expires. It is kept on the device and, with Drive sync on, in the sync file
   (`docs/sync.md`), so signing in once signs in every device. Instapaper refusing it (401) signs the
   user out.

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
| Web: screen | `web/src/components/InstapaperDialog.tsx`; Save button in `ArticleView.tsx`; sign-in in Settings |
| Android: client, sign-in helpers, list logic | `app/src/main/kotlin/be/wimvandonck/reader/instapaper/` |
| Android: screens | `ui/instapaper/`, `ui/settings/InstapaperSettings.kt` |
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

- Highlights, and Instapaper's parsed article text (for accounts other than the owner's it needs a
  separate Instaparser key). A saved article opens on its own website.
- Syncing reading progress.
- Signing in from a debug build's App Link: debug APKs are signed with a different key every CI run
  and have another package name (`.debug`), so Android doesn't verify the link for them and opens the
  browser, where the *web app* finishes the sign-in. Try the Android sign-in with a released build.
