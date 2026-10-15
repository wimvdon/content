# Usage guide

## First launch

The app starts with **No feeds yet**. Tap **Add feed**, or **Import OPML** to bring your feeds
from another reader.

## Adding feeds

Tap **Add feed** (on the empty screen, in the ☰ drawer, in the ⋮ menu or in **Settings › Feeds**)
and type an address. Any of these work:

- a feed address (`https://example.com/feed.xml`);
- a website (`example.com`): the app looks for the feed the site announces, then tries common
  places such as `/feed` and `/rss.xml`;
- a `feed://` link.

Addresses are always opened over HTTPS. A site that only works over plain `http://` can't be
added (Android blocks unencrypted connections).

After adding, the list switches to the new feed.

## Moving from Inoreader

Tap **Import from Inoreader** (on the empty start screen, or in **Settings › Feeds**) and follow
the three steps:

1. **Open Inoreader** opens inoreader.com in your browser; sign in.
2. In Inoreader, go to **Preferences › Import/Export** and export your feeds as **OPML**. The file
   goes to your downloads.
3. Back in RSS Reader, tap **Choose file** and pick that file.

All your feeds are added (folders are flattened; feeds you already follow are skipped) and the app
downloads their articles. Your Inoreader account is not linked to the app, and read/starred
states don't come along. The same works for any other reader that exports OPML: use **Import
OPML**.

## Reading the list

- **☰** opens the drawer: **All articles** and every feed, each with its number of unread
  articles.
- The **Unread** and **All** chips choose what the list shows. Articles you open from the Unread
  list stay in it until you switch feed, filter or refresh, so the list doesn't jump.
- **Pull down** to download new articles. The app also does this once every time it starts.
- **✓✓** marks everything in the current list as read.
- If some feeds can't be downloaded, a message says how many; **Settings › Feeds** shows why for
  each one.

## The reader

Tap an article to read it. It is marked as read. In the top bar:

| Button | What it does |
|---|---|
| **Aa** | Text settings: font (Sans or Serif), text size, spacing (Compact, Normal, Relaxed) and background (Theme, Sepia or Dark). Changes apply right away and are remembered for every article. |
| Bookmark | **Save**: sends the article to Instapaper, or opens the share menu (see below). |
| Share | Shares the article's link with any app. |
| ⋮ | **Open in browser** and **Mark as unread**. |

**Full articles.** Many feeds contain only the start of an article. Then the reader fetches the
whole article from the website by itself: a thin bar shows while it loads, and the text is
replaced by the complete article (without the site's menus, ads and comments). If that fails, the
feed's text stays and a line says so. ⋮ › **Show feed text** / **Show full article** switches
between the two for the open article; **Settings › Reader › Load full articles** turns the
automatic loading off. Full articles are kept for a while, so opening one again is instant.
**Read on website** at the end always opens the original page. 

**Pictures and videos.** The list shows each article's picture as a thumbnail. In the reader the
picture appears above the title, and the pictures inside the article appear between the
paragraphs, with their captions. Embedded videos and posts (YouTube, Vimeo, X, Instagram,
Spotify and others) show as a card with a preview; tap it to watch or open it in its app or the
browser. **Settings › Reader › Show images** turns pictures and previews off (for example to save
data); embed cards stay, without preview.

## Saving to Instapaper

**Settings › Save for later** chooses what **Save** does:

- **Instapaper** (default): the article goes straight to the Instapaper app, which saves it to
  your Instapaper account. If Instapaper isn't installed, Save opens the share menu instead and
  the setting offers **Get Instapaper**.
- **Share menu**: Save opens Android's share menu, so you can pick Pocket, a notes app or anything
  else.

## Settings

- **Feeds:** every feed with its address (or why its last download failed), remove a feed (its
  articles are removed too), add a feed, **Import OPML** / **Export OPML** to move your feeds
  between readers, and **Import from Inoreader**.
- **Reader:** **Load full articles**, **Show images**, and the same text settings as **Aa**, with a preview.
- **Save for later:** see above.
- **Appearance:** Theme (System, Light, Dark) and Dynamic color (wallpaper colours instead of
  the app's blue).

## Backup and moving to a new phone

The app uses Android's built-in backup. When **Backup by Google One** (or **Google backup**) is on
in the phone's settings, Android backs up your feeds, read state and settings to your Google
account about once a day, while the phone is charging and on Wi-Fi. Setting up a new phone with
that backup, or copying data from the old phone, restores them when the app is installed again
from the same source. Downloaded full articles are not backed up; they load again when needed.

## Language

The app follows the phone's language. To choose another one for this app only: Android
**Settings › System › Languages › App languages › RSS Reader**.

## Common problems

| Problem | What to do |
|---|---|
| "No feed found at this address" | Try the site's feed address instead; look for an RSS or feed link on the site. |
| "Couldn't connect" | Check the internet connection and pull down to try again. |
| A feed shows only short summaries | That is what the site publishes; tap **Read on website**. |
| Not sure Save worked | Instapaper shows a short "Saved" message; open Instapaper to see the article. |
