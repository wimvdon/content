# Sync

RSS Reader can keep feeds, folders and read articles the same on every device and in the web
app. The data lives in the user's own storage; there is no RSS Reader server.

## Services

| Service | Where the file is | Status |
|---|---|---|
| Google Drive | The hidden app folder (`appDataFolder`), scope `drive.appdata`: only RSS Reader can see the file, and RSS Reader can see nothing else in the Drive | Android and web |
| Others (Dropbox, OneDrive, WebDAV/Nextcloud) | One file in the service's app folder | Not built yet: implement `SyncProvider` (read and write one text file) |

## When it syncs

Only while the app is in use: when it starts, about five seconds after a change (a feed added,
renamed, moved or removed, a folder changed, an article read or unread), and with **Sync now** in
Settings. Never in the background.

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

Google only lets the app use Drive once it is registered:

1. In the [Google Cloud console](https://console.cloud.google.com), create a project (for example
   "RSS Reader").
2. APIs & Services › Library: enable the **Google Drive API**.
3. APIs & Services › OAuth consent screen (Google Auth Platform › Branding): app name "RSS
   Reader", your email, the privacy policy link (`PRIVACY.md` on GitHub). Data access: add the
   scope `.../auth/drive.appdata`. Audience: External; while testing, add your Google account as a
   test user. Publishing to everyone needs Google's (free) verification of this screen.
4. Clients › Create client › **Android**, twice:
   - package `io.github.impca201.rssreader` with the SHA-1 of the release signing key (Play
     Console › App integrity, or `keytool -list -v -keystore <release keystore>`);
   - package `io.github.impca201.rssreader.debug` with the SHA-1 of the debug key that built the
     test APK.
5. Clients › Create client › **Web application** for the web app, with its address under
   Authorized JavaScript origins (for example `https://rss-reader.pages.dev`). Its client ID goes
   into the web app's settings (see `web/README.md`).

No secret goes into the Android app: Google recognises it by package name and signing key.
