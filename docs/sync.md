# Sync

Reader can keep feeds, folders and read articles the same on every device and in the web
app. The data lives in the user's own storage; there is no Reader server.

## Services

| Service | Where the file is | Status |
|---|---|---|
| Google Drive | The hidden app folder (`appDataFolder`), scope `drive.appdata`: only Reader can see the file, and Reader can see nothing else in the Drive | Android and web |
| Others (Dropbox, OneDrive, WebDAV/Nextcloud) | One file in the service's app folder | Not built yet: implement `SyncProvider` (read and write one text file) |

## When it syncs

Only while the app (or the web page) is on screen, never in the background:

- when it comes on screen, and then every minute while it stays there, so read marks and feeds
  from other devices come in;
- about two seconds after a change (a feed added, renamed, moved or removed, a folder changed,
  an article read or unread; a row of changes, such as marking while scrolling, goes as one);
- on refresh (pull down, or the refresh button), before the feeds are downloaded, so articles
  read elsewhere arrive already read;
- with **Sync now** in Settings.

Coming back and the minute skip a sync when the last one was less than ten seconds ago
(`SyncTiming` on Android, the same constants in `web/src/lib/sync.ts`). A refresh never skips: it
always syncs first, so a read mark made a few seconds ago on another device is there when you pull
down. One sync is two or three requests to Drive, far below Google's limits.

In the web app, Google's sign-in lasts about an hour and is kept in the browser, so reopening the
page within that hour syncs straight away. A browser can only get a new sign-in through Google's
popup, so the web app never asks for one by itself: when it has expired, sync waits and Settings ›
Sync says to click **Connect**.

## The sync file

`rss-reader-sync.json`, UTF-8 JSON:

```json
{
  "format": "rss-reader-sync",
  "version": 1,
  "folders": [{ "id": "…uuid…", "title": "News", "icon": "newspaper" }],
  "feeds": [
    {
      "url": "https://example.com/feed.xml",
      "title": "Example",
      "renamed": false,
      "folderId": "…uuid… or null",
      "siteUrl": "https://example.com or null",
      "iconUrl": "https://example.com/icon.png or null"
    }
  ],
  "read": { "https://example.com/feed.xml <article id>": 1760000000000 }
}
```

- `folders` and `feeds` are in the user's order.
- `icon` is a Material Symbols name from the app's icon catalog.
- `read` lists read articles: key `<feed url> <article id>` (one space), value the article's date
  in milliseconds (publication date, or when it was first downloaded). Marks older than 120 days
  and those of removed feeds are dropped.
- The article id is the item's `guid` (RSS) or `id` (Atom), else its link, else
  `hash:` + the hex of Java's `String.hashCode` of `title|date|first 500 characters of the text`
  (see `FeedParser.fallbackId`). The web app computes the same ids.
- Articles themselves are not synced: every device downloads them from the feeds.
- A reader that finds a higher `version` leaves the file alone and asks to update the app.

## Merging

Each device keeps the file as it last wrote or read it (the *base*). On sync it reads the
current file and merges three ways (`sync/SyncMerge.kt`):

- Whatever differs from the base on one side is a change on that side, and is kept.
- When both sides changed the same feed or folder, this device wins, except that an edit beats a
  removal.
- The order comes from the side that reordered.
- Read marks: an article read or unread on one side follows that side. Articles a device doesn't
  have yet count as unchanged; when they arrive they arrive already read.
- Without a file (the first device), the device's library becomes the file. Without a base (a
  device connecting for the first time), both sides are combined.

## Google Cloud setup (owner)

Google only lets the app and the web app use Drive once they are registered in a Google Cloud
project. Everything below is in the [Google Cloud console](https://console.cloud.google.com),
signed in with your Google account. It is free.

1. **Project.** At the top, open the project picker. Use the existing project if you made one
   before (for example "RSS Reader"); otherwise New project, name "Reader", Create, and select it.
2. **Drive API.** Menu › APIs & Services › Library, search "Google Drive API", open it, Enable.
3. **Consent screen.** Menu › Google Auth Platform (or APIs & Services › OAuth consent screen):
   - Branding: app name "Reader", your email as support and developer contact. App home page and
     privacy policy: `https://github.com/wimvdon/rss-reader` and
     `https://github.com/wimvdon/rss-reader/blob/main/PRIVACY.md`.
   - Audience: External. While the app is in "Testing", only the Google accounts listed under
     **Test users** can connect: add your own (and anyone else who uses it). Publishing to
     everyone needs Google's (free) verification of this screen.
   - Data access: Add or remove scopes, find `.../auth/drive.appdata` ("See, create, and delete
     its own configuration data in your Google Drive"), tick it, Update, Save.
4. **Android app.** Google Auth Platform › Clients › Create client › Application type
   **Android**:
   - Package name: `be.wimvandonck.reader`
   - SHA-1 certificate fingerprint: the one at the top of the summary page of the latest run of
     **Actions › Release** ("Signing key SHA-1", it looks like `AB:CD:…`).
   - Create. Nothing has to be copied back: Google recognises the app by these two.
   Clients made for the old package `io.github.impca201.rssreader` can be deleted.
5. **Web app.** Clients › Create client › Application type **Web application**, name "Reader
   web". Under Authorized JavaScript origins, Add URI:
   `https://rss-reader-web.wim-d57.workers.dev` (no slash at the end). Leave Authorized redirect
   URIs empty. Create, and copy the **Client ID** (ending in `.apps.googleusercontent.com`; not
   the client secret, which isn't needed) into `web/src/lib/config.ts` as `GOOGLE_CLIENT_ID`.

Changes in Google Cloud can take a few minutes, sometimes longer, to take effect.

**Test builds don't sync.** The debug APKs from pull requests are signed with a key CI makes up
on every run, so Google can't recognise them: sync shows "Google doesn't recognise this version
of the app yet". Try sync with the released app (from Releases), whose key stays the same.

No secret goes into the Android app: Google recognises it by package name and signing key.
