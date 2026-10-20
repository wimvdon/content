# Raindrop.io

Saved can be kept in a [Raindrop.io](https://raindrop.io) account too (#112), next to or instead of
Instapaper. Everything below is the Android app; the web app only serves the page Raindrop sends you
back to (see "How connecting works").

## Saved and Raindrop (Android)

- **Connect:** ☰ › Connected services › **Connect Raindrop.io** opens Raindrop's own page in the
  browser. Approve Reader there and you come back to the app connected ("Connected to Raindrop.io").
- **Mapping:** Raindrop's **Unsorted** is To read, its **collections** are Saved's folders (a nested
  collection is "Parent / Child"), a collection called **Archive** is the Archive (made the first time
  you archive something), its **favorite** is Liked, and tags and the **note** are the same on both
  sides. Filing into a folder Raindrop doesn't have yet makes the collection.
- **Syncing:** when the app opens, a moment after a change in Saved, when you pull down on Saved and
  with **Sync now**. Raindrop has no "what changed since", so a sync lists the whole account, 50 at a
  time (`RaindropSaved.changes`); the quick sync after a change does that at most every three minutes,
  and what is gone from the list was deleted in Raindrop. Up to 5000 raindrops are read; with more,
  nothing is taken as deleted.
- **Removing** an article from Saved moves it to Raindrop's trash.
- **Tokens** last two weeks. The app renews them a day before (and when Raindrop refuses one) through
  the Worker; if Raindrop no longer accepts the renewal, the app disconnects and says so in Connected
  services.
- **Disconnect** forgets the tokens on this device and what was synced; Saved stays.
- Not supported: Raindrop's highlights, reading progress (Raindrop doesn't keep it), its own article
  text, and Raindrop on the web app.

## Import, scope, and Instapaper too (#145)

Reader is the library; Raindrop (like Instapaper) is somewhere it is also kept.

- **Import, once:** connecting asks what to bring in: everything Raindrop has, or some collections
  (and the Archive). Each raindrop arrives where it belongs, as it is: a collection becomes the
  matching folder (made if you don't have it, flattened to "Parent / Child" if it was nested), the
  Archive collection the Archive, favorites stay liked, tags and notes come along. **Import again**
  repeats it later. What isn't imported stays in Raindrop until it changes there.
- **Scope:** Connected services › Raindrop.io › **What it syncs**: everything in Saved (the default)
  or only some folders (To read counts as one). The scope is shared both ways: articles in it stay in
  step, field by field, and the later change wins when both sides changed the same field (Raindrop
  says when each raindrop last changed, `lastUpdate`).
- **Leaving the scope:** filing an article out of a synced folder, or unticking a folder, archives it
  in Raindrop (the Archive collection), never deletes it. Unticking says first how many articles that
  is. Removing an article from Reader still removes it (to Raindrop's trash).
- **Where a folder came from:** a nested collection arrives flat, so Reader keeps which collection
  each folder name stands for (`ServiceState.origins`, stored on the device). Filing an article there
  later lands it in that collection, even before Raindrop is listed again, instead of making a new
  top-level one with a slash in its name.
- **With Instapaper too:** a save reaches every service whose scope covers its folder. Put the same
  folder in both scopes and the two stay in step with each other through Reader. Removing an article
  both keep asks: **Remove from both**, or **Only from** one (`ServiceManager.removeFrom`).

This replaced #121's "Articles in both" switches, the one-off copy between services, "New saves go
to" and the small service marks on each article.

## How connecting works

OAuth 2 with an authorization code, through the feed loader Worker (`worker/src/raindrop.ts`), which
keeps the client ID and secret, so the app holds neither:

1. The app remembers a random `state` and opens `https://rss-reader-worker.wim-d57.workers.dev/raindrop/authorize?state=…`.
   The Worker sends the browser on to `https://raindrop.io/oauth/authorize` with its client ID and
   the callback address.
2. Raindrop sends the browser back to `https://rss-reader-web.wim-d57.workers.dev/raindrop/callback?code=…&state=…`.
   On the released app that App Link opens Reader directly (`MainActivity`). Anywhere else the web
   app shows a page with **Open in Reader** and **Open in Reader Dev** buttons
   (`web/src/components/RaindropCallback.tsx`), which hand the same address to the app; debug builds
   can't verify App Links, so they always go this way.
3. The app checks the state and posts the code to the Worker's `/raindrop/token`, which swaps it at
   `https://raindrop.io/oauth/access_token` and returns only the access token, the refresh token and
   how long it lasts. Renewing goes through `/raindrop/refresh` the same way.
4. With the token the app calls `https://api.raindrop.io/rest/v1` directly. The Worker keeps and
   logs nothing.

## Where things are

| File | What |
|---|---|
| `raindrop/RaindropClient.kt` | The REST calls over OkHttp, the token renewed once on 401, pure parsing helpers |
| `raindrop/RaindropSaved.kt` | Raindrop as Saved's service: the mapping above |
| `raindrop/RaindropAuth.kt` | Addresses, callback parsing, the token swap through the Worker |
| `raindrop/RaindropAccount.kt` | The tokens in their own DataStore file (`datastore/raindrop`, left out of backups) |
| `services/ServiceManager.kt` | Both services, the default for new saves, when to sync |
| `services/ServiceSync.kt` | The merge, and which items go to which service |
| `worker/src/raindrop.ts` | `/raindrop/authorize`, `/raindrop/token`, `/raindrop/refresh` |
| `web/src/components/RaindropCallback.tsx`, `web/src/lib/raindrop.ts` | The callback page |

## One-time setup (owner)

1. **Register the app** at <https://app.raindrop.io/settings/integrations> › *For developers* ›
   **Create new app** (name Reader, site the GitHub repository). Redirect URI:
   `https://rss-reader-web.wim-d57.workers.dev/raindrop/callback`.
2. **Put the client ID and secret in the Worker**, never in the repository: Cloudflare dashboard ›
   Workers & Pages › `rss-reader-worker` › Settings › Variables and Secrets › Add, type **Secret**,
   names `RAINDROP_CLIENT_ID` and `RAINDROP_CLIENT_SECRET`. Until both exist the Worker answers
   "Raindrop isn't set up on this server" and connecting fails.
3. **App Links:** the callback is on the same website as Instapaper's, so the existing
   `web/public/.well-known/assetlinks.json` covers it; the manifest lists both paths.

If the callback address ever changes, change it in all of these, exactly the same: the Raindrop app,
`RAINDROP_REDIRECT_URI` in `worker/wrangler.toml`, `RaindropAuth.REDIRECT_URI`, the intent filter in
`AndroidManifest.xml` and the route in `web/src/main.tsx`.
