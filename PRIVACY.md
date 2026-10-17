# Privacy policy

_Last updated: 2026-10-17 (optional sign-in with Instapaper)_

Reader has no account, analytics, advertising or crash reporting, and no server of its own.
Sync, if you turn it on, uses your own Google Drive.

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
- **Sync with Google Drive (off unless you connect it).** In Settings › Sync › Connect, Google
  asks whether Reader may use its own hidden folder in your Google Drive (`drive.appdata`).
  Reader can't see your other files. While the app is on screen (when it opens, every minute,
  a few seconds after a change, on refresh, and on Sync now; never in the background) it reads and writes one file there, `rss-reader-sync.json`, with your
  feed list (addresses, names, folders, website and icon addresses), your folders, and which
  articles you read in the last 120 days (feed address, article id and date). Article texts and
  settings are not in it. The file goes to Google (www.googleapis.com) under Google's privacy
  policy and your Google account; Google also sees your IP address. Disconnect stops syncing;
  the file stays in your Drive until you remove the app's data there (Google Drive › Settings ›
  Manage apps) or revoke access in your Google account.
- **Instapaper (off unless you sign in).** Settings › Instapaper › Sign in opens Instapaper's own
  page in your browser, where you approve Reader; Reader never sees your Instapaper password.
  Instapaper then sends back a one-time code, which the app gives to the owner's feed loader (a
  Cloudflare Worker, built from `worker/`). The loader adds Reader's client secret, swaps the code
  for an access token at Instapaper, hands the token back and keeps nothing: it stores and logs
  nothing, and Cloudflare sees your IP address, under Cloudflare's privacy policy. With the token the
  app talks to www.instapaper.com (the Android app directly, the web app through the feed loader,
  because browsers aren't allowed to call Instapaper): it sends the title and link of an article
  you save, and reads and changes your saved list (archive, like, delete, move to a folder, tags,
  folders), under Instapaper's privacy policy. Signing out deletes the token from your device; it
  stays valid at Instapaper until you revoke it there.
- **Links you open** (Read on website, links in articles) open in your browser, under the
  website's own privacy policy.
- **Save and Share.** When you tap Save or Share, the article's title and link go to the app you
  chose in Settings (Instapaper for Save by default) or picked in the share menu, under that app's
  privacy policy. Signed in to Instapaper (see above), Save sends them to your Instapaper account
  instead. Nothing is sent otherwise.

## What the app stores on your device

- Your feed list (with each feed's icon address) and folders, the downloaded articles and which ones you read.
- If sync is on: a copy of the sync file as last synced, to merge changes.
- Your settings (theme, text settings, full articles, pictures, Save and Share targets, the Unread filter, mark as read on scroll).
- The text of full articles you opened, and recently shown pictures, in the app's cache (Android
  may clear it).
- If you signed in to Instapaper: the access token and your Instapaper user name, in a file of
  their own (`datastore/instapaper`) that Android's backup and phone-to-phone transfer leave out.

Android's own backup (to your Google account, if you turned that on) and phone-to-phone transfer
include the feed list, articles, read state and settings, not the cache and not the Instapaper sign-in. Removing a feed deletes its articles; uninstalling the app deletes everything.

## Background activity

None. The app only downloads feeds and articles, and syncs, while you use it.

## Permissions

- **Internet:** to download feeds and articles, and to sync.

The app also declares that it looks for apps that accept shared links, so Settings › Save and
share can list them. This is not a permission and reveals nothing to those apps.

## The web app

The web version (built from `web/`, published by the owner on Cloudflare at
`https://rss-reader-web.wim-d57.workers.dev`) runs in your browser and keeps your feed list, articles, read state and settings in the
browser's storage on that device (IndexedDB). There is no account and no analytics.

- **Feeds and article pages** go through the owner's feed loader (a Cloudflare Worker, built
  from `worker/`), because browsers don't let a web page load most feeds directly. It loads
  only the address the web app asks for and passes it back; it stores nothing, logs nothing and
  is used only by the web app. Cloudflare runs it, so Cloudflare sees which feed or page is
  loaded and your IP address, under Cloudflare's privacy policy. It keeps a copy of a feed for at
  most five minutes in Cloudflare's cache, shared by everyone reading that feed. The Android app
  uses it only to finish signing in to Instapaper (see above).
- **Pictures and website icons** load directly from the websites, without telling them which
  page you came from.
- **Embedded videos and posts** show as a card; nothing plays inside the page. YouTube cards
  show the video's preview picture from i.ytimg.com, which then sees your IP address. Clicking
  a card opens the video or post on its own website in a new tab.
- **Sync** works as described above: the web app reads and writes the same
  `rss-reader-sync.json` in your Google Drive's hidden app folder, after you click Connect
  (Google's sign-in script comes from accounts.google.com). The sign-in lasts about an hour per
  browser tab.
- **Instapaper** (off unless you sign in, in Settings › Instapaper): the page's sign-in leaves for
  Instapaper's own website and returns to the web app, which swaps the one-time code for an access
  token through the feed loader. The token is kept in the browser's storage (IndexedDB), never in the
  sync file. Saving and managing your articles goes through the feed loader to www.instapaper.com;
  the loader passes the token on, and keeps and logs nothing.
- Removing the site's data in your browser deletes everything the web app stored.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.
