# Privacy policy

_Last updated: 2026-10-16 (sync with Google Drive)_

RSS Reader has no account, analytics, advertising or crash reporting, and no server of its own.
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
  asks whether RSS Reader may use its own hidden folder in your Google Drive (`drive.appdata`).
  RSS Reader can't see your other files. While the app is open (at start, a few seconds after a
  change, and on Sync now) it reads and writes one file there, `rss-reader-sync.json`, with your
  feed list (addresses, names, folders, website and icon addresses), your folders, and which
  articles you read in the last 120 days (feed address, article id and date). Article texts and
  settings are not in it. The file goes to Google (www.googleapis.com) under Google's privacy
  policy and your Google account; Google also sees your IP address. Disconnect stops syncing;
  the file stays in your Drive until you remove the app's data there (Google Drive › Settings ›
  Manage apps) or revoke access in your Google account.
- **Links you open** (Read on website, links in articles) open in your browser, under the
  website's own privacy policy.
- **Save and Share.** When you tap Save or Share, the article's title and link go to the app you
  chose in Settings (Instapaper for Save by default) or picked in the share menu, under that app's
  privacy policy. Nothing is sent otherwise.

## What the app stores on your device

- Your feed list (with each feed's icon address) and folders, the downloaded articles and which ones you read.
- If sync is on: a copy of the sync file as last synced, to merge changes.
- Your settings (theme, text settings, full articles, pictures, Save and Share targets, the Unread filter, mark as read on scroll).
- The text of full articles you opened, and recently shown pictures, in the app's cache (Android
  may clear it).

Android's own backup (to your Google account, if you turned that on) and phone-to-phone transfer
include the feed list, articles, read state and settings, not the cache. Removing a feed deletes its articles; uninstalling the app deletes everything.

## Background activity

None. The app only downloads feeds and articles, and syncs, while you use it.

## Permissions

- **Internet:** to download feeds and articles, and to sync.

The app also declares that it looks for apps that accept shared links, so Settings › Save and
share can list them. This is not a permission and reveals nothing to those apps.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.
