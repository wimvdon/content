# Sync

Reader keeps feeds, folders, read articles, the reading list, Bookmarks (with notes, tags and
highlights) and settings the same on every device and in the web app through the user's **Reader
account**, which signing in with Instapaper opens (`docs/account.md`). The account's feed loader
stores, encrypted, one small sync document per account (folders, feeds, settings) and one row per
item (#106): the reading list and Bookmarks, and every read article.

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
  "version": 3,
  "folders": [{ "id": "…uuid…", "title": "News", "icon": "newspaper" }],
  "feeds": [
    {
      "url": "https://example.com/feed.xml",
      "title": "Example",
      "renamed": false,
      "folderId": "…uuid… or null",
      "siteUrl": "https://example.com or null",
      "iconUrl": "https://example.com/icon.png or null",
      "tags": ["News"]
    }
  ],
  "settings": { "fullArticles": "true", "markReadOnScroll": "false" }
}
```

- `folders` and `feeds` are in the user's order.
- `icon` is a Material Symbols name from the app's icon catalog.
- A feed's `tags` (#187) are left out when it has none, so the file reads the same for older
  apps, which ignore the field. An older app that writes the file back drops them; a feed in a
  folder then gets its folder's name as its tag again.
- Version 2 files also held `read`, the read articles (key `<feed url> <article id>`, value the
  article's date); they are per-item rows now (below). A version 3 app reading a version 2 file keeps
  those marks for the articles it doesn't have and syncs them as rows; it never writes `read` back.
- Articles themselves are not synced: every device downloads them from the feeds.
- `settings` (added in version 2) maps shared names to text values (`SyncSettings` on Android,
  `web/src/lib/syncSettings.ts`). Each app reads the names it knows and keeps the others, so a
  setting only one app has survives a sync by the other. Only settings about what the app does
  are synced:

  | Name | Values | Apps |
  |---|---|---|
  | `fullArticles` | `true`, `false` | both |
  | `markReadOnScroll` | `true`, `false` | both |
  | `services.instapaper.scope`, `services.raindrop.scope` | what the service syncs (#145): `*` for everything in Saved, else a JSON list of folder names, `""` for To read | both |
  | `sharing.recipients` | the people shared with (#235): a JSON list of email addresses in lower case, most recent first, at most 12 | both |
  | `services.instapaper.imported`, `services.raindrop.imported` | what was imported when it was connected: `{"at": ms, "count": n, "folders": [...] or null, "archive": true}`; an app without one keeps the file's | both |

  View settings stay on each device (#96), because people often want a different view on a
  phone, a tablet and a computer: the theme, dynamic colour, the reader's font, text size, spacing,
  page colour and automatic dark page, Show images and the list view. Versions before #96 synced
  them as `theme`, `dynamicColor`, `reader.font`, `reader.textSize`, `reader.spacing`,
  `reader.background`, `reader.autoDark`, `showImages` and `listView`; those names are dropped from
  the file and ignored when an older version still writes them.

  Not synced either: the Save and Share targets (each phone has its own apps), the Instapaper
  sign-in (each device signs in itself, and that sign-in is the account) and Unread | All (it is
  where you are in the list, not a setting; each device remembers its own). Older versions synced
  the sign-in as `instapaper.token`, `instapaper.username` and `instapaper.userId`, and the filter
  as `unreadOnly`; those names are dropped.
- A reader that finds a higher `version` leaves the file alone and asks to update the app.

## Per-item sync (#106)

Everything that grows with the library syncs one row per item, so the account has no size limit
and a change sends only what changed (`sync/SyncRows.kt` and `sync/ItemSync.kt` on Android,
`web/src/lib/syncRows.ts`). Each row has a key, the account's **items version** of its last save,
and JSON data (`null` once deleted):

| Key | Data |
|---|---|
| `item:<item key>` | A saved item (`data/Items.kt`): `url`, `title`, `imageUrl`, `feedUrl`, `articleId`, `createdAt`, `readingList` (`{"state": "toread" \| "archived", "addedAt"}` or `null`), `folder` (its folder while to read, #119, or `null`), `bookmark` (`{"addedAt", "collection"}` or `null`; from before #119, moved into `folder` by the Android app), `service` (the service it is kept in, `"instapaper"` or `"raindrop"`, #121, or `null`), `liked`, `tags`, `note`, `highlights` (each `{"id", "quote", "prefix", "suffix", "occurrence", "note", "createdAt"}`), `progress` (thousandths, 0 to 1000) and `sharedBy` (who shared it with you, #167: `{"email", "name", "at", "waits", "read"}` or `null`; `waits` is true while it is in the inbox (#246), whether or not it also has `readingList`, and `read` once opened there; the latest share replaces the earlier one; a `message` from before #246 is ignored) |
| `read:<feed url> <article id>` | A read feed article: `{"time": <the article's date in ms>}` |

- The item key is the link without `http(s)://`, `www.`, a trailing slash and the `#part`, in lower
  case (`ItemKeys`, web `keyOfLink`), so a saved feed article and its item share one key.
- The article id is the item's `guid` (RSS) or `id` (Atom), else its link, else `hash:` + the hex of
  Java's `String.hashCode` of `title|date|first 500 characters of the text` (see
  `FeedParser.fallbackId`). The web app computes the same ids.
- **No article text** is in a row: not the feed's text, not Reader's extraction, not the description.
  Every device downloads or extracts the text again. Tests check the payload (`ItemSyncTest`,
  `syncRows.test.ts`).
- Read marks older than 120 days leave the account (the device that notices sends their removal);
  that never marks an article unread. Marks of articles a device doesn't have yet are kept in
  `Library.readElsewhere`, so those articles arrive read.
- Kinds of rows a version doesn't know are left alone.

One sync, after the document:

1. **Read the changes:** `GET /account/items?since=<items version>` (in pages of at most 500 rows;
   a page never splits one save). Each row is merged three ways with the row as this device last
   synced it (the base, kept in the `sync_rows` table, web `syncRows` store) and this device's own
   data: whatever differs from the base on one side is a change on that side and wins; both changed,
   this device wins, except that an edit beats a removal. Items merge per field, tags as a set (each
   side's additions and removals count) and highlights per highlight.
2. **Save this device's changes:** every row whose data differs from its base, with that base's
   version (`POST /account/items`, at most 500 rows per call). A row another device saved first comes
   back as a conflict with what is there now; it is merged the same way and saved again (at most
   four rounds per sync).

The live connection's message carries both versions (`{"version": n, "items": m}`), so a save by
another device to either brings a sync.

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
- Read marks: an article read or unread on one side follows that side; both changed, read wins.
  Articles a device doesn't have yet count as unchanged; when they arrive they arrive already
  read. A read article that leaves a device (a feed's newest 200, or a removed feed) keeps its mark
  there, so it never turns unread on the others, and a feed added again brings it back read
  (#157).
- Settings: per name, a change on one side wins; both changed, this device wins.
- Without a document (the first device), the device's library becomes the document. Without a
  base (a device signing in), both sides are combined. A device that has no synced
  settings in its base (new, or last synced before settings were in the file) takes the file's
  settings, keeping its own only for names the file doesn't have, so its defaults never overwrite
  the user's choices.
