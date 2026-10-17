# Usage guide

## First launch

The app starts with **No feeds yet**. Tap **Add feed**, or **Import OPML** to bring your feeds
from another reader.

## Adding feeds

Tap **Add feed** (on the empty screen or on the **Manage feeds and folders** page, ☰ › Manage
feeds and folders)
and type an address. Any of these work:

- a feed address (`https://example.com/feed.xml`);
- a website (`example.com`): the app looks for the feed the site announces, then tries common
  places such as `/feed` and `/rss.xml`;
- a `feed://` link.

Addresses are always opened over HTTPS. A site that only works over plain `http://` can't be
added (Android blocks unencrypted connections).

After adding, the list switches to the new feed. The app also looks up the website's small icon
(favicon) and shows it next to the feed's name in the list, the ☰ menu, Settings and the reader.
Until it is found, or when a site has none, the first letter of the feed's name is shown. Icons
sit on a light tile, so dark logos stay visible in dark mode.

## Moving from another reader

Export your feeds as an **OPML** file in your old reader (in Inoreader: Preferences ›
Import/Export; Feedly, NetNewsWire and most others have the same), then tap **Import OPML** (on
the empty start screen or on the **Manage feeds and folders** page) and pick the file. All feeds are added (feeds
you already follow are skipped), in the same folders as in your old reader, and the app downloads
their articles. **Export OPML** does the reverse, folders included. Read and starred states don't
come along.

## Folders

Open **☰ › Manage feeds and folders** (also reachable from the top of Settings). This page lists
each folder with its feeds under it, then the feeds outside folders ("No folder"; when every
feed is in a folder, a note there says you can drag a feed onto it).

- **New folder:** give it a title and pick an icon: the whole Material Symbols set (over 4 000
  icons) is there. Type in the search field to find one (icon names are in English, such as
  *news*, *car*, *soccer* or *food*); with nothing typed, popular topics come first.
- **Drag** the grip (⋮⋮) at the right of a row to reorder. Drop a feed under another folder to
  move it there, or under "No folder" to take it out. Dragging a folder moves it with its feeds;
  while you drag it, the feeds are hidden. The order is the one the ☰ menu and the bottom bar
  use. (With a screen reader, use the row's Move up / Move down actions.)
- **Tap a feed** (or its pen) to rename it, choose its folder or remove it. A name you give stays when the
  feed updates; clear the name to use the feed's own again.
- **Tap a folder** (or its pen) to change its title or icon, or remove it (its feeds stay, under "No folder").

Once there is a folder, tabs appear under the title: **All** (every feed) and then each folder
with its icon and name; swipe the tabs sideways when they don't fit. Tap one to see only its
articles. The ☰ drawer lists the folders too, each with its feeds, and the feeds outside folders
after them. A feed added while a folder is open goes into that folder.

## Sync between devices

Settings › **Sync** › **Connect** links the app to your Google Drive. Google asks once whether
Reader may use its own hidden folder there (it can't see your other files). From then on
your feeds, folders, read articles and settings stay the same on every phone where you connect,
and in the web app. Signed in to Instapaper on one, you are signed in on all of them (and signing
out signs out everywhere). The Save and Share apps stay per phone. A phone connecting for the first
time takes the settings you already have. The app syncs while it is on screen: when you open it, every minute, a
couple of seconds after you read an article or change a feed, when you refresh, and when you tap
**Sync now**; the line under Google Drive says when it last did, or what went wrong.
**Disconnect** stops syncing on this phone. See [sync.md](sync.md) for how it works.

## Reading the list

- **☰** opens the drawer: **All articles** (every feed), your folders with their feeds, the other feeds
  (each with its number of unread articles), **Manage feeds and folders** and **Settings**.
- Articles are grouped under **Today**, **Yesterday** and then the date; the date stays at the
  top while you scroll through that day.
- Under the title you see how many articles are unread. Below it, **Unread | All** switches
  between only unread articles and all articles (the chosen one is filled and has a check
  mark). Articles you open from the unread list stay in it until you switch feed, filter or
  refresh, so the list doesn't jump.
- Scrolling down slides the title, the folder tabs and **Unread | All** out of the way, so you see
  more articles; scroll up a little and they come straight back.
- **Swipe** an article left or right past about a third of its width (you feel a short tick) to
  mark it as read, or as unread when it was read. The ☰ menu opens with the ☰ button only, so
  swiping never opens it by accident.
- **Pull down** to download new articles. The app also does this once every time it starts. All
  feeds are downloaded first and then added at once, each article in its place by date.
- **Back** inside a folder or feed returns to All; back on All closes the app.
- The round **✓✓** (Mark all as read) button at the bottom right marks everything in the section you are in as
  read: all feeds on All, the folder's feeds in a folder, or the one feed you opened. A message
  says how many; **Undo** brings them back.
- **Settings › Article list › View** chooses how big articles are in the list: **Small** (title
  only), **Medium** (title, start of the text and a small picture) or **Large** (a card with a
  wide picture).
- **Mark as read on scroll** (in **Settings › Article list**, off by default): articles are
  marked as read once you scroll them off the top of the list. They stay in the unread list until
  you switch feed, filter or refresh, so you can scroll back to them.
- If some feeds can't be downloaded, a message says how many; **Manage feeds and folders** shows why
  for each one.

## The reader

Tap an article to read it. It is marked as read. Tap the article's **title** to open it on its
website (the same as **Read on website** at the end). In the top bar:

| Button | What it does |
|---|---|
| **Aa** | Text settings: font (Sans or Serif), text size, spacing (Compact, Normal, Relaxed), background (Theme, Sepia or Dark) and **Auto dark** (the dark page whenever the app is dark, e.g. Sepia by day and dark at night). Changes apply right away and are remembered for every article. |
| Bookmark | **Save**: sends the article to the app you chose (Instapaper by default), or opens the share menu (see below). Signed in to Instapaper, it saves straight to your account. |
| Share | **Share**: the share menu, or straight to the app you chose. |
| ⋮ | **Open in browser** and **Mark as unread**. |

**Full articles.** Many feeds contain only the start of an article, or a few paragraphs with a
"Read full article" link. Then the reader fetches the
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

## Save and Share

The reader has a **Save** button (bookmark) and a **Share** button. In **Settings › Save and
share** you choose, for each, where it sends the article:

- **an app**, picked from the apps on your phone that accept shared links: the article goes
  straight there, without the share menu. Save is set to **Instapaper** by default.
- **Share menu**: Android's share menu opens, so you pick an app each time. This is Share's
  default.

If the chosen app has been uninstalled, the button opens the share menu instead and says so.

## Settings

- **Feeds › Manage feeds and folders** (at the top; the same page as in ☰): every feed with its address (or why
  its last download failed), grouped by folder. Add, rename, reorder (drag) or remove feeds (a
  removed feed's articles go too), create, edit, reorder and remove folders, put feeds in folders, and **Import OPML** /
  **Export OPML** to move your feeds between readers.
- **Article list:** View (Small, Medium, Large) and **Mark as read on scroll**.
- **Reader:** **Load full articles**, **Show images**, and the same text settings as **Aa**, with a preview.
- **Save and share:** see above.
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
**Settings › System › Languages › App languages › Reader**.

## Instapaper account

Settings › **Instapaper** › **Sign in** opens Instapaper's own page in your browser. Approve Reader
there and you come back to the app (or the web page) signed in; Reader never sees your password.

Signed in:

- **Save** (the bookmark button in the reader) saves the article to your Instapaper account.
- Settings › Instapaper › **Saved articles** (the web app: **Instapaper** in the sidebar) shows
  Unread, Archive, Liked, your folders and your tags. On each article: like, archive (or move back
  to Unread), move to a folder, add or remove tags, delete for good (asks first), or open it on its
  website. **Manage folders** creates, reorders and deletes folders; **Manage tags** renames tags.
- **Sign out** removes the sign-in from this device.

Until Instapaper has approved Reader, only the owner's own account works; other accounts see
"Instapaper hasn't approved Reader for your account yet." Setup and details: `instapaper.md`.

## Common problems

| Problem | What to do |
|---|---|
| "No feed found at this address" | Try the site's feed address instead; look for an RSS or feed link on the site. |
| "Couldn't connect" | Check the internet connection and pull down to try again. |
| A feed shows only short summaries | That is what the site publishes; tap **Read on website**. |
| Not sure Save worked | Instapaper shows a short "Saved" message; open Instapaper to see the article. |
