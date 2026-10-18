# Sync

Reader keeps feeds, folders, read articles and settings the same on every device and in the web
app through the user's **Reader account**, which signing in with Instapaper opens
(`docs/account.md`). The account's feed loader stores one sync document per account, encrypted.

## When it syncs

Only while the app (or the web page) is on screen, never in the background:

- when it comes on screen;
- **at once when another device saves**: while on screen, the app keeps a live connection to the
  account (a WebSocket), which says when the document changed;
- about a second after a change (a feed added, renamed, moved or removed, a folder changed, an
  article read or unread, a synced setting; a row of changes, such as marking while scrolling, goes
  as one). A change made while a sync runs is synced right after it;
- on refresh (pull down, or the refresh button), before the feeds are downloaded, so articles read
  elsewhere arrive already read;
- every minute, but only while the live connection is down;
- with **Sync now** in Settings › Account.

Coming back and the minute skip a sync when the last one was less than ten seconds ago
(`SyncTiming` on Android, the same constants in `web/src/lib/sync.ts`). A refresh never skips. A
sync that finds nothing new costs one small request (`304 Not Modified`).

## The sync file

The account's document (it was `rss-reader-sync.json` in Google Drive before), UTF-8 JSON:

```json
{
  "format": "rss-reader-sync",
  "version": 2,
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
  "read": { "https://example.com/feed.xml <article id>": 1760000000000 },
  "settings": { "theme": "dark", "reader.textSize": "20" }
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
- `settings` (added in version 2) maps shared names to text values (`SyncSettings` on Android,
  `web/src/lib/syncSettings.ts`). Each app reads the names it knows and keeps the others, so
  Android-only settings survive a sync from the web:

  | Name | Values | Apps |
  |---|---|---|
  | `theme` | `system`, `light`, `dark` | both |
  | `dynamicColor` | `true`, `false` | Android |
  | `reader.font` | `sans`, `serif` | both |
  | `reader.textSize` | `14` to `28` | both |
  | `reader.spacing` | `compact`, `normal`, `relaxed` | both |
  | `reader.background` | `default`, `sepia`, `dark` | both |
  | `reader.autoDark` | `true`, `false` | both |
  | `fullArticles` | `true`, `false` | both |
  | `showImages` | `true`, `false` | Android |
  | `markReadOnScroll` | `true`, `false` | both |
  | `listView` | `small`, `medium`, `large` | Android |

  Not synced: the Save and Share targets (each phone has its own apps), the Instapaper sign-in
  (each device signs in itself, and that sign-in is the account) and Unread | All (it is where you
  are in the list, not a setting; each device remembers its own). Older versions synced the sign-in
  as `instapaper.token`, `instapaper.username` and `instapaper.userId`, and the filter as
  `unreadOnly`; those names are dropped.
- A reader that finds a higher `version` leaves the file alone and asks to update the app.

## Merging

Each device keeps the document as it last saved or read it (the *base*), with the account's version
of it. On sync it reads the account's document and merges three ways (`sync/SyncMerge.kt`), then
saves the result with the version it read (`sync/SyncRound.kt`, `syncRound` in
`web/src/lib/sync.ts`). When another device saved in between, the account refuses (`409`) and
returns that device's document; the device merges again, with what it already merged as the base,
and saves again (at most four tries per sync). So two devices never overwrite each other's changes.

- Whatever differs from the base on one side is a change on that side, and is kept.
- When both sides changed the same feed or folder, this device wins, except that an edit beats a
  removal.
- The order comes from the side that reordered.
- Read marks: an article read or unread on one side follows that side. Articles a device doesn't
  have yet count as unchanged; when they arrive they arrive already read.
- Settings: per name, a change on one side wins; both changed, this device wins.
- Without a document (the first device), the device's library becomes the document. Without a
  base (a device signing in), both sides are combined. A device that has no synced
  settings in its base (new, or last synced before settings were in the file) takes the file's
  settings, keeping its own only for names the file doesn't have, so its defaults never overwrite
  the user's choices.
