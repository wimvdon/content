# Usage guide

## First launch

The app starts with **No feeds yet**. Tap **Add feed**, or **Import OPML** to bring your feeds
from another reader.

## Adding feeds

Tap **Add feed** (on the empty screen or on the **Manage feeds** page, ☰ › Manage
feeds)
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
the empty start screen or on the **Manage feeds** page) and pick the file. All feeds are added (feeds
you already follow are skipped), in the same folders as in your old reader, and the app downloads
their articles. **Export OPML** does the reverse, folders included. Read and starred states don't
come along.

## Folders

Open **☰ › Manage feeds**. This page lists
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

Sign in with Instapaper (Settings › **Account** › **Sign in with Instapaper**, see below) on every
phone and in the web app, and your feeds, folders, read articles and settings stay the same on all
of them. Reader keeps them in your Reader account on its own server, encrypted. A phone signing in
for the first time combines its feeds with the account's and takes the settings you already have.
Only Load full articles and Mark as read on scroll sync. How the app looks (theme, the reader's
font, size, spacing and page colour, Show images and the list view) stays on each device, so a
phone and a laptop can each have their own. The Save and Share apps stay per phone too.

The app syncs while it is on screen: when you open it, **at once when another device changes
something** (an article read on the laptop turns read on the phone within a second or two), about a
second after you read an article or change a feed, when you refresh, and when you tap **Sync now**.
The line under your name in Settings › Account says when it last did, or what went wrong.

- **Sign out** stops syncing on this device (and signs out of Instapaper here). Your feeds stay.
- **Delete synced data** removes everything Reader keeps for your account from its server and
  signs out every device. Each device keeps its own feeds and read articles.

See [account.md](account.md) and [sync.md](sync.md) for how it works.

## Reading the list

- The **bar at the bottom** switches between **Feeds** and **Saved** (on a tablet or in landscape it
  sits at the left side).
- **☰** opens the drawer with every place. **Feeds**: **All articles** (every feed), your
  folders with their feeds, the other feeds (each with its number of unread articles). The
  **Saved** follows: To read, All, your folders and Archive (see Saved). The section you're in is
  open and the other is folded shut, so the menu stays short; tap a section's name to fold it open or
  shut. Below them: **Manage feeds**, **Folders and tags**, **Connected services** and **Settings**.
- **Search** (the magnifier at the top right, on Feeds and on Saved; on the web also the
  **/** key) searches everything: the articles of all your feeds that are on the device (titles,
  text, feed names and authors) and Saved, archived articles too (titles, websites, descriptions,
  folders, tags, notes and highlights). Every word you type must appear; capitals and accents
  don't matter ("cafe" finds "Café"). The results are one list, best match first: the whole search
  in the title, then more of its words in the title, then the newest. Each result says **Feed** or
  **Saved**. Chips above the results show **All**, only **Feeds** or only **Saved**.
- Articles are grouped under **Today**, **Yesterday** and then the date; the date stays at the
  top while you scroll through that day.
- Under the title you see how many articles are unread. Two chips just above the list
  (under the folder tabs), **Unread** and **All**, choose between only unread articles and all
  articles; the chosen one is filled, with a check mark. The web app has the same two chips. Articles you open from the unread list stay in it until you switch feed, filter or
  refresh, so the list doesn't jump.
- Scrolling down slides the title, the folder tabs and the **Unread** and **All** chips out of the way, so you see
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
- If some feeds can't be downloaded, a message says how many; **Manage feeds** shows why
  for each one.

## The reader

Tap an article to read it. It is marked as read. Tap the article's **title** to open it on its
website (the same as **Read on website** at the end). In the top bar:

| Button | What it does |
|---|---|
| **Aa** | Text settings: font (Sans or Serif), text size, spacing (Compact, Normal, Relaxed), background (Theme, Sepia or Dark) and **Auto dark** (the dark page whenever the app is dark, e.g. Sepia by day and dark at night). Changes apply right away and are remembered for every article. |
| Open book | **Read later**: saves the article to **To read** (by default), or sends it to the app you chose or the share menu (see below). Once it is saved, the same screen shows Saved's buttons (below). |
| ♥ | When saved: **Like** (the heart fills). |
| Share | **Share**: the share menu, or straight to the app you chose. |
| ⋮ | **Open in browser** and **Mark as unread**; a feed article not saved yet also has **File in a folder** (the save sheet); a saved one also **Tags** and **Remove from Saved**. Open in browser, and links in an article, open in a tab of your browser on top of Reader, with a **Save** button in its toolbar: it opens the save sheet over the page (see Saving from other apps); save or close it to keep reading. |

A saved article has **File** and **Archive** at the bottom, and **Finished reading** at the end of the
text. **Archive** archives it at once and goes back to the list, with **Undo** in the message (it
returns to where it was, in its folder). **File** opens a sheet with the folder, a note and tags.
**Finished reading** asks what's next: **Archive** (done with it; it stays searchable) or **File in a
folder**; File is chosen first when the article has highlights or a note. Reader remembers how far you
read and opens the article there again.

After **Read later**, the sheet that opens has **Like**, the **Folder** (empty: To read; type one or tap
one you have) and **Tags**: one step from a feed article to its folder. The folder is set when you tap
**Done** or close the sheet.

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

- **Saved, in this app** (Save only, and its default): the article goes to **To read** in Saved,
  and a sheet opens to like and tag it (see Saved).
- **an app**, picked from the apps on your phone that accept shared links: the article goes
  straight there, without the share menu.
- **Share menu**: Android's share menu opens, so you pick an app each time. This is Share's
  default.

If the chosen app has been uninstalled, the button opens the share menu instead and says so.

Connecting Instapaper or Raindrop.io changes nothing here: the buttons and their choices stay the
same, and what is in Saved is also kept in the connected service.

**Saving from other apps.** In Chrome or any app that shares links, tap Share and pick
**Reader**. Only the **Save to Reader** sheet opens, over the app you were in (the rest of Reader
stays closed): the site, then the **Folder** (empty: To read; type one or tap one you have), a
**Note** and **Tags** (and **Save to** with both services connected). **Save** saves it and says where
it went, and you are back in that app; **Cancel** saves nothing. A
link shared without a title gets the page's own title, description and picture. Shared text without
a web link says "No link to save".

## Settings

- **Account:** sign in with Instapaper to sync and to save to Instapaper; signed in, **Sync now**,
  **Sign out** and **Delete synced data** (see Sync between devices).
- **Article list:** View (Small, Medium, Large) and **Mark as read on scroll**.
- **Reader:** **Load full articles**, **Show images**, and the same text settings as **Aa**, with a preview in the page colour you chose.
- **Save and share:** see above, and **Tag with the feed's folder** (on by default): an article you
  save from a feed (Read later, the swipe, the save sheet) gets its folder's name as a tag, spelled
  like a tag you already use. It still goes to To read, not into a folder. Renaming or removing the
  folder later doesn't change tags already given. Each device keeps its own choice.
- **Appearance:** Theme (System, Light, Dark) and Dynamic color (wallpaper colours instead of
  the app's blue).

Your feeds are managed on their own page, **☰ › Manage feeds** ("Feeds and folders"): every feed with its address (or why
  its last download failed), grouped by folder. Add, rename, reorder (drag) or remove feeds (a
  removed feed's articles go too), create, edit, reorder and remove folders, put feeds in folders, and **Import OPML** /
  **Export OPML** to move your feeds between readers.

## Backup and moving to a new phone

The app uses Android's built-in backup. When **Backup by Google One** (or **Google backup**) is on
in the phone's settings, Android backs up your feeds, read state and settings to your Google
account about once a day, while the phone is charging and on Wi-Fi. Setting up a new phone with
that backup, or copying data from the old phone, restores them when the app is installed again
from the same source. Downloaded full articles are not backed up; they load again when needed.

## Language

The app follows the phone's language. To choose another one for this app only: Android
**Settings › System › Languages › App languages › Reader**.

## Saved

**Saved** (#119) holds everything you save, in one place: articles to read, the ones you file in
folders, and the archive. It is Reader's own: it works without an account, is kept on the device
(and in its backup), and syncs between your devices through your Reader account. Signed in to
Instapaper, it is also kept in your Instapaper account (see Instapaper account).

- **Each article is in one place:** **To read** (where everything new arrives), **one folder**, or
  the **Archive**. Filing an article in a folder takes it out of To read; archiving it takes it out of
  its folder.
- **Getting articles in:** **Read later** in an article; **swipe a feed article left** in the list
  (swiping right still marks it read or unread; the message then offers **File** for the folder sheet); **Add link** (the button at the bottom right of
  Saved); and sharing a link to Reader from another app. These go to To read unless you pick a
  folder in the save sheet. A feed article you save stays the same article: its feed, text and
  picture come along, and it isn't copied.
- **Swipe in To read, All and Archive:** left archives (with **Undo**; in Archive, back to read),
  right opens **File in a folder**.
- **Tabs** under the title: **To read**, **All** (everything, archive included), each **folder**, and
  **Archive**. Under them, chips filter the tab: **Liked** (set apart by its heart), then the tab's
  tags, most used first; pick several to see articles with all of them. The **filter button** next to
  search picks a type: Articles, Videos or Audio (read from the link: YouTube or Vimeo are videos,
  podcast and Spotify episode links are audio).
- **To read, All and Archive look like your feeds**: the same rows, in the view you chose for the
  feed list (Small, Medium, Large), with how many articles and about how long they take at the top.
  **A folder looks like a page of notes**: cards in two columns, each with its picture, the title,
  your note (or else the article's description), the site, its tags and its highlights.
- In the reader: **♥** to like, **File** and **Archive** at the bottom, with the folder it is in.
  **File** (and **Finished reading** at the end of the text) asks where it goes: Archive, or **File
  in a folder** with a note and tags (type a folder, or tap one you have; leave it empty for To
  read). **Tags** and **Remove from Saved** are in ⋮; removing asks first and takes the note, tags
  and highlights with it (Archive keeps it searchable instead).
- **☰ › Folders and tags**: every folder and every tag, with how many articles have it; tap one to
  rename it (renaming it to another one's name joins the two), or remove it: a tag from every
  article, a folder from its articles (they go back to To read).
- **☰ › Connected services**: which service each section is connected to. Saved can be connected to
  Instapaper, Raindrop.io or both; feeds are Reader's own.
- Bookmarks from before Saved moved into folders once: each into its collection's folder, the ones
  without a collection into a folder called **Bookmarks**.

## Highlights

Select text in an article (press and hold, then drag the handles) and tap **Highlight** in the menu
over it, or **Note** to write a note with it. The passage gets a yellow marker; tap it to change the
note or remove the highlight. A highlight is made within one paragraph.

- An article with highlights shows how many at the bottom of the reader (the quote mark); tap it for
  the list. In a folder, each note card shows the count.
- Highlighting a feed article saves it to Saved (To read), so the highlight is kept.
- Highlights are anchored by their passage and the words around it, not by position: they are found
  again in another version of the text (the feed's, the full article, Instapaper's). When the text
  differs, the marker is lighter and underlined, and the highlight says it is approximate; when the
  passage isn't in this text, it is listed as not found. A highlight is never thrown away.
- They sync between your devices with your Reader account. With Instapaper connected, highlights
  made in Instapaper's apps come in when you open the article; **☰ › Connected services › Copy
  highlights to Instapaper** (off by default) also sends yours there. Free Instapaper accounts may
  make five a month: after that a highlight says "Not in Instapaper" and Reader tries again a day
  later.

## Instapaper account

Settings › **Account** › **Sign in with Instapaper** opens Instapaper's own page in your browser.
Approve Reader there and you come back to the app (or the web page) signed in; Reader never sees your
password. The same sign-in turns on sync (see Sync between devices) and connects Saved to
Instapaper:

- Everything in your Instapaper account comes into Saved, and everything in Saved goes to
  Instapaper. From then on, what you add, file, archive, like or tag, and how far you read, goes both
  ways, while the app is open (when it opens, a moment after a change, and when you pull down on
  Saved).
- Instapaper's home list is **To read**, its **folders** are Saved's folders and its archive is the
  **Archive**. Filing into a folder Instapaper doesn't have yet makes it there.
- On the owner's own Instapaper account the reader shows **Instapaper's text** of an article
  ("Instapaper text" above the title). Other accounts get Reader's own full article, as Instapaper
  only gives its text to the account that registered the app.
- If Instapaper doesn't answer or asks to wait, nothing is lost: the rest goes the next time.

**Sign out** removes the sign-in from this device and stops syncing here. Saved stays.

Until Instapaper has approved Reader, only the owner's own account works; other accounts see
"Instapaper hasn't approved Reader for your account yet." Setup and details: `instapaper.md`.

## Raindrop.io

☰ › **Connected services** › **Connect Raindrop.io** opens Raindrop's own page in your browser.
Approve Reader there and you come back connected ("Connected to Raindrop.io"). On a test build
(Reader Dev) a page opens instead: tap **Open in Reader Dev**.

- Everything in your Raindrop account comes into Saved: **Unsorted** is To read, **collections** are
  folders (a collection inside another shows as "Parent / Child"), **favorites** are Liked, and tags
  and notes come along. Archiving puts the article in a Raindrop collection called **Archive**
  (made the first time).
- What you add, file, archive, like, tag or note goes to Raindrop while the app is open, and
  changes made in Raindrop come in when the app opens and when you pull down on Saved.
- Removing an article from Saved moves it to Raindrop's trash.
- **Disconnect** stops it on this device. Saved stays.

**Instapaper and Raindrop together:** each keeps its own articles and nothing is copied from one to
the other unless you ask. Saved shows both, with a small mark on each article: a serif **I** for
Instapaper, a **drop** for Raindrop.

- The first time both are connected, Reader asks where new saves go; change it later in Connected
  services › **New saves go to**, or for one save with **Save to** in the save sheet.
- An article in both shows once. Connected services › **Articles in both** chooses what reaches both
  copies: Archive, Liked and Tags (on), Folder (off).
- Removing an article both keep asks: from both, or from one only (it then stays in Saved).
- Connected services › **Copy between services** copies all articles of one service to the other,
  once, after showing how many. Articles already there are skipped; you can cancel.

Details: `raindrop.md`.

## Common problems

| Problem | What to do |
|---|---|
| "No feed found at this address" | Try the site's feed address instead; look for an RSS or feed link on the site. |
| "Couldn't connect" | Check the internet connection and pull down to try again. |
| A feed shows only short summaries | That is what the site publishes; tap **Read on website**. |
| Not sure Save worked | A "Saved to To read" sheet opens; the article is in **Saved › To read**. |
