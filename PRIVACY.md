# Privacy policy

_Last updated: 2026-10-18 (sync through a Reader account instead of Google Drive)_

Reader has no analytics, advertising or crash reporting. It works without an account. If you sign
in with Instapaper, that sign-in is also your Reader account: Reader then keeps your feed list,
read articles and settings on its own server (the owner's Cloudflare Worker), encrypted, so your
devices stay the same.

## What the app sends, and to whom

- **The feeds you add.** To show new articles, the app downloads each feed you subscribed to
  from its own website: when the app starts, when you pull down to refresh, and when you add or
  import a feed. When you add a website, the app also loads that page and, if needed, a few common
  feed addresses on the same site to find its feed. These websites see your IP address and the
  app's name and version (the "user agent"), like any visit. No cookies or identifiers are sent.
- **Article pages.** When you open an article whose feed has only a summary, the app downloads
  that article's web page to show the whole article (turn this off in Settings › Reader › Load
  full articles; you can also switch it per article). Only the article text is kept; the page's
  scripts, images and trackers are not loaded. The website sees the same as for its feed.
- **Pictures.** Article pictures, list thumbnails and video previews are downloaded from where
  the feed or article says they are: usually the website itself or its image server (CDN), and
  for YouTube videos i.ytimg.com. Those servers see your IP address, like any visit. Turn this off
  in Settings › Reader › Show images. Embedded videos and posts are not played inside the app;
  tapping one opens it in its app or your browser.
- **Website icons.** Once per feed, the app loads the home page of the feed's website to find
  the site's small icon (favicon), and then downloads that icon (from the site, or wherever the
  site says it is, often its image server). If the site names none, its `/favicon.ico` is tried.
  The icon is shown next to the feed's name. These servers see the same as for the feed.
- **Sync with your Reader account (off unless you sign in with Instapaper).** While the app is
  on screen (when it opens, a second after a change, on refresh, on Sync now, and at once when
  another of your devices changed something; never in the background) it reads and saves one
  document in your account on the owner's feed loader (a Cloudflare Worker, built from `worker/`,
  at rss-reader-worker.wim-d57.workers.dev). The document holds your feed list (addresses, names,
  folders, website and icon addresses), your folders, which articles you read in the last 120 days
  (feed address, article id and date) and your settings (theme, text settings, full articles,
  pictures, list view, the Unread filter, mark as read on scroll; not the Save and Share targets).
  It does not hold article texts, your Instapaper token or your Instapaper password. While the app
  is on screen it also keeps a connection open to the feed loader that only says when the document
  changed. The document is stored compressed and encrypted (AES-256-GCM, with a key for your account
  derived from a secret that only the feed loader has) in Cloudflare's storage (Durable Objects).
  The feed loader can decrypt it to send it to your devices; the owner could too, but doesn't look
  at it. Your account is found by a code derived from your Instapaper user number; each device gets
  its own key, of which only a scrambled form (hash) is stored. Nothing is logged. Cloudflare sees
  your IP address, under Cloudflare's privacy policy. **Sign out** (Settings › Account) forgets this
  device's key; **Delete synced data** deletes the document and every device's key from the server
  at once (your devices keep their own copy of the library).
- **Instapaper (off unless you sign in).** Settings › Instapaper › Sign in opens Instapaper's own
  page in your browser, where you approve Reader; Reader never sees your Instapaper password.
  Instapaper then sends back a one-time code, which the app gives to the owner's feed loader (a
  Cloudflare Worker, built from `worker/`). The loader adds Reader's client secret, swaps the code
  for an access token at Instapaper and hands the token back without keeping it. From Instapaper's
  answer it also takes your Instapaper user number to find your Reader account and give this device
  its key (see Sync above); nothing is logged, and Cloudflare sees your IP address, under
  Cloudflare's privacy policy. With the token the
  app talks to www.instapaper.com (the Android app directly, the web app through the feed loader,
  because browsers aren't allowed to call Instapaper): it sends the title and link of an article
  you save, and reads and changes your saved list (archive, like, delete, move to a folder, tags,
  folders), under Instapaper's privacy policy. Signing out deletes the token and the account key from
  your device; the token stays valid at Instapaper until you revoke it there.
- **Links you open** (Read on website, links in articles) open in your browser, under the
  website's own privacy policy.
- **Save and Share.** By default Save keeps the article's title and link in the app's own Saved list
  on your phone; nothing is sent. When you set Save or Share to an app in Settings, or pick one in the
  share menu, the title and link go to that app, under its privacy policy. Signed in to Instapaper
  (see above), Save sends them to your Instapaper account instead, and signing in sends the ones saved
  on the device before (see below). Nothing is sent otherwise.

## What the app stores on your device

- Your feed list (with each feed's icon address) and folders, the downloaded articles and which ones you read.
- If you are signed in: a copy of the synced document as last synced, to merge changes.
- Your settings (theme, text settings, full articles, pictures, Save and Share targets, the Unread filter, mark as read on scroll).
- Articles you saved without an Instapaper account: their address, title, when you saved them,
  whether you liked or archived them, and their tags (`datastore/saved`; on the web, the browser's
  storage). They are not sent anywhere until you sign in to Instapaper: then they are copied to your
  Instapaper account and removed from the device.
- The text of full articles you opened, and recently shown pictures, in the app's cache (Android
  may clear it).
- If you signed in to Instapaper: the access token, your Instapaper user name and this device's
  Reader account key, in a file of their own (`datastore/instapaper`) that Android's backup and
  phone-to-phone transfer leave out.

Android's own backup (to your Google account, if you turned that on) and phone-to-phone transfer
include the feed list, articles, read state, settings and the articles saved without an account, not the cache and not the Instapaper sign-in. Removing a feed deletes its articles; uninstalling the app deletes everything.

## Background activity

None. The app only downloads feeds and articles, and syncs, while you use it.

## Permissions

- **Internet:** to download feeds and articles, and to sync.

The app also declares that it looks for apps that accept shared links, so Settings › Save and
share can list them. This is not a permission and reveals nothing to those apps.

## The web app

The web version (built from `web/`, published by the owner on Cloudflare at
`https://rss-reader-web.wim-d57.workers.dev`) runs in your browser and keeps your feed list, articles, read state and settings in the
browser's storage on that device (IndexedDB). There are no analytics; the only account is the
Reader account that signing in with Instapaper opens.

- **Feeds and article pages** go through the owner's feed loader (a Cloudflare Worker, built
  from `worker/`), because browsers don't let a web page load most feeds directly. It loads
  only the address the web app asks for and passes it back; it stores none of it and logs
  nothing, and only the web app loads feeds through it. Cloudflare runs it, so Cloudflare sees which feed or page is
  loaded and your IP address, under Cloudflare's privacy policy. It keeps a copy of a feed for at
  most five minutes in Cloudflare's cache, shared by everyone reading that feed. The Android app
  uses it only to finish signing in to Instapaper and for sync (see above).
- **Pictures and website icons** load directly from the websites, without telling them which
  page you came from.
- **Embedded videos and posts** show as a card; nothing plays inside the page. YouTube cards
  show the video's preview picture from i.ytimg.com, which then sees your IP address. Clicking
  a card opens the video or post on its own website in a new tab.
- **Sync** works as described above, through the same feed loader: the web app reads and saves
  the same document in your Reader account and keeps the same live connection while the page is
  visible. Whether a sidebar section is folded shut is remembered in the browser's local storage.
- **Instapaper** (off unless you sign in, in Settings › Account): the page's sign-in leaves for
  Instapaper's own website and returns to the web app, which swaps the one-time code for an access
  token (and this browser's Reader account key) through the feed loader. Both are kept in the
  browser's storage (IndexedDB) only. Saving and managing your articles goes through the feed loader to www.instapaper.com;
  the loader passes the token on, and keeps and logs nothing.
- Removing the site's data in your browser deletes everything the web app stored.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.
