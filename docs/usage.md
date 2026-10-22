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

- The **bar at the bottom** switches between **Feeds**, **Saved** and **Search** (on a tablet or in
  landscape it sits at the left side; on the web, wide windows have Search at the top of the sidebar).
- **☰** opens the drawer with every place. **Feeds**: **All articles** (every feed), your
  folders with their feeds, the other feeds (each with its number of unread articles). The
  **Saved** follows: To read, your folders and Archive (see Saved). On the phone the section you're in is
  open and the other is folded shut, so the menu stays short; tap a section's name to fold it open or
  shut. On the web both sections stay open until you fold one, and stay as you left them. Below them: **Manage feeds**, **Folders and tags**, **Connected services** and **Settings**.
- **Search** (the third tab; on the web also the **/** key) searches everything: the articles of all your feeds that are on the device (titles,
  text, feed names and authors) and Saved, archived articles too (titles, websites, descriptions,
  folders, tags, notes and highlights). Every word you type must appear; capitals and accents
  don't matter ("cafe" finds "Café"). The results are one list, best match first: the whole search
  in the title, then more of its words in the title, then the newest. Each result says **Feed** or
  **Saved** when both kinds are shown. Chips above the results show **All**, only **Feeds** or only
  **Saved**. With **Feeds** chosen, two more chips narrow it to a **Folder** or one **Feed**; with
  **Saved**, to a **Folder**, a **Tag** and a **Type** (Articles, Videos or Audio). With nothing typed,
  Search lists everything those chips let through, newest first: with no filter that is all your
  feed articles and all of Saved (archive included), which is where Saved's former **All** tab went.
  **All** and **Saved** show their results as notes, like a folder; **Feeds** shows rows, like your feeds.
- Articles are grouped under **Today**, **Yesterday** and then the date; the date stays at the
  top while you scroll through that day.
- Under the title you see how many articles are unread. Two chips just above the list
  (under the folder tabs), **Unread** and **All**, choose between only unread articles and all
  articles; the chosen one is filled, with a check mark. The web app has the same two chips.
- **What stays in the Unread list.** An article you read (opened, also from Search, swiped,
  marked with its button or scrolled past) stays in the list, dimmed, so nothing disappears under
  your finger. The list starts afresh only when you pick another feed or folder, switch between
  Unread and All, or pull down (or tap refresh) yourself. Going to Saved and back, or tapping the
  same feed again, keeps it as it was; a refresh the app does by itself does too.
- Scrolling down slides the title, the folder tabs and the **Unread** and **All** chips out of the way, so you see
  more articles; scroll up a little and they come straight back.
- **Swipe** an article right past about a third of its width (you feel a short tick) to
  mark it as read, or as unread when it was read; swipe it left to save it. In the web app, the
  round button that appears on an article when you point at it does the same as swiping right,
  and so does **m** for the open article. The ☰ menu opens with the ☰ button only, so
  swiping never opens it by accident.
- **Pull down** to download new articles. The app also does this once every time it starts. All
  feeds are downloaded first and then added at once, each article in its place by date.
- **Back** inside a folder or feed returns to All; back on All closes the app.
- The round **✓✓** (Mark all as read) button at the bottom right marks everything in the section you are in as
  read: all feeds on All, the folder's feeds in a folder, or the one feed you opened, also the
  ones you haven't scrolled to. You're done with it, so the Unread list empties (All caught up).
  A message says how many; **Undo** brings them back.
- **Settings › Article list › View** chooses how big articles are in the list: **Small** (title
  only), **Medium** (title, start of the text and a small picture) or **Large** (a card with a
  wide picture).
- **Mark as read on scroll** (in **Settings › Article list**, off by default): articles are
  marked as read once you scroll them off the top of the list, only ones that were on screen: new
  articles arriving above are never marked unseen. They stay in the unread list (see above), so
  you can scroll back to them.
- If some feeds can't be downloaded, a message says how many; **Manage feeds** shows why
  for each one.

## The reader

Tap an article to read it. It is marked as read. Saved articles have no read state of their
own (archiving is what puts them away), but opening one marks the feed article it came from as
read, in both apps. Tap the article's **title** to open it on its
website (the same as **Read on website** at the end). In the top bar:

| Button | What it does |
|---|---|
| Full article (page icon) | **Show full article** / **Show feed text**: the whole article from the website, or the feed's own text. It is lit while the full article shows. |
| **Aa** | Text settings: font (Sans or Serif), text size, spacing (Compact, Normal, Relaxed), background (Theme, Sepia or Dark) and **Auto dark** (the dark page whenever the app is dark, e.g. Sepia by day and dark at night). Changes apply right away and are remembered for every article. |
| ⋮ | **Open in browser**, **Mark as unread** (back to the list, where it waits unread; the web app keeps the article open and offers **Mark as read** too), and for a saved article **Remove from Saved**. Open in browser, and links in an article, open in a tab of your browser on top of Reader, with a **Save** button in its toolbar: it opens the save sheet over the page (see Saving from other apps); save or close it to keep reading. |

The bar at the bottom is the article in your library, with **Share** at its right end (the share
menu, or straight to the app you chose). Before it is saved it has **Save** on the left: it saves the article to **To read** (by default), or sends it to the app you chose or the
share menu (see below). Once saved, Save makes way for two buttons side by side, grouped like the save sheet: first
**where it is** (To read, its folder or Archive): tap it for **To read** and **Archive**, then your
folders and **New folder**. Picking **Archive** on the phone archives it and goes back to the list,
with **Undo** in the message (it returns to where it was, in its folder); picking To read or a
folder moves it there. Next to it, **its tags**: tap it for
your tags (a check shows the ones it has; tap to add or take one off) and **Add a
tag**. New folder and Add a tag open **Organise**, where you can also write a note. The text ends with **Read on website**.
Reader remembers how far you read and opens the article there again.

**Highlights and notes.** Press and hold on a word, drag the handles over a passage, and tap
**Highlight** in the menu over it (next to Copy and Share), or **Note** to write a note with it. In
the web app, select text with the mouse and pick **Highlight** or **Note** in the small bar above it.
The passage gets a yellow marker; tap or click it to change its note or remove it. A quote mark
with the count in the bottom bar lists every highlight of the article. Highlighting a feed article
saves it to **To read**, so the highlight is kept. A highlight is kept as its passage and a few
words around it, not a position, so it is found again in the feed's text, the full article or a
newer version; when the text changed it is lighter and underlined ("approximate"), and when it
isn't there at all it is listed as "not found". A highlight is never thrown away. Highlights and
their notes sync between your devices with your Reader account, and Saved's cards show the first
one when the article has no note. On the phone a highlight stays within one paragraph.

After **Save**, the sheet that opens says where it was saved, then has the **Folder** (To read and
**Archive** first, then your folders, or **New folder** to make one), the **Tags**,
and a **Note**: one step from a feed article to its folder. **Save** keeps what you chose,
also a folder or tag you typed but didn't add yet. **Remove** takes the save back, as if you never
saved it ("Removed from Saved"); closing the sheet keeps it where it was just saved, unchanged.
The same sheet, called **Organise**, opens when you swipe a saved article right in Saved. When it
closes, a message says where the article went; after a swipe, **Add link** or a link shared from
another app it also has **Open**, which goes to the article in Saved.

**Full articles.** Many feeds contain only the start of an article, or a few paragraphs with a
"Read full article" link. Then the reader fetches the
whole article from the website by itself: a thin bar shows while it loads, and the text is
replaced by the complete article (without the site's menus, ads and comments). If that fails, the
feed's text stays and a line says so. The page button in the top bar switches
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
  and a sheet opens to tag it (see Saved).
- **an app**, picked from the apps on your phone that accept shared links: the article goes
  straight there, without the share menu.
- **Share menu**: Android's share menu opens, so you pick an app each time. This is Share's
  default.

If the chosen app has been uninstalled, the button opens the share menu instead and says so.

Connecting Instapaper changes nothing here: the buttons and their choices stay the
same, and what is in Saved is also kept in the connected service.

**Saving from other apps.** In Chrome or any app that shares links, tap Share and pick
**Reader**. Only the **Save to Reader** sheet opens, over the app you were in (the rest of Reader
stays closed): the site, then the **Folder** (To read, **Archive** for a link you keep
for reference, your folders, or **New folder**), the **Tags**, and a **Note**. **Save** saves
it and says where it went, and you are back in that app; **Cancel** saves nothing. **Add link** in
Saved opens the same sheet with a field for the address. A
link shared without a title gets the page's own title, description and picture. Shared text without
a web link says "No link to save".

## Settings

- **Account:** sign in with Instapaper to sync and to save to Instapaper; signed in, **Sync now**,
  **Sign out** and **Delete synced data** (see Sync between devices).
- **Article list:** View (Small, Medium, Large) and **Mark as read on scroll**.
- **Reader:** **Load full articles**, **Show images**, and the same text settings as **Aa**, with a preview in the page colour you chose.
- **Save and share:** see above, and **Tag with the feed's folder** (on by default): an article you
  save from a feed (Save, the swipe, the save sheet) gets its folder's name as a tag, spelled
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
- **Getting articles in:** **Save** in an article; **swipe a feed article left** in the list
  (swiping right still marks it read or unread; the save sheet opens, then the message offers
  **Open**); **Add link** (the button at the bottom right of
  Saved); and sharing a link to Reader from another app. These go to To read unless you pick a
  folder in the save sheet. A feed article you save stays the same article: its feed, text and
  picture come along, and it isn't copied.
- **Swipe in To read and Archive:** left archives (with **Undo**; in Archive, back to read),
  right opens **File in a folder**.
- **Tabs** under the title: **To read**, each **folder**, and **Archive** (to see everything at
  once, archive included, use Search with nothing typed and the **Saved** chip). Under them, chips filter the tab: the tab's
  tags, most used first; pick several to see articles with all of them. The **filter button** next to
  search picks a type: Articles, Videos or Audio (read from the link: YouTube or Vimeo are videos,
  podcast and Spotify episode links are audio).
- **To read looks like your feeds**: the same rows, in the view you chose for the
  feed list (Small, Medium, Large), with how many articles and about how long they take at the top.
  **A folder and the Archive look like a page of notes**: cards in two columns, each with its picture, the title,
  your note (or else your first highlight, quoted, or else the article's description), the site,
  its tags and how many highlights it has.
- In the reader, the bar at the bottom: **where it is** (To read and Archive, then your folders) and
  **its tags**, each a menu. **Remove from Saved** is in ⋮; removing asks first and
  takes the note, tags and highlights with it (Archive keeps it searchable instead).
- **☰ › Folders and tags**: every folder and every tag, with how many articles have it; tap one to
  rename it (renaming it to another one's name joins the two), or remove it: a tag from every
  article, a folder from its articles (they go to the Archive).
- **In the web app** Saved is the same: the same tabs, chips, type filter and cards, and **Add link**
  (the link button next to the type filter). In an article the top bar is the reading (the full
  article, Aa, ⋮) and the bar under it the library, with Share at its right end: **Save** before it is saved (it opens the same sheet), then
  the same two menus as on the phone: **where it is** (To read and Archive, your folders) and **its
  tags**. Archiving goes back to the list with **Undo**. Keys: **j**/**k** next
  and previous, **m** read or unread, **o** open on the website, **r** refresh, **/** search, and
  **Esc** closes a menu, then the article.
- **☰ › Connected services**: the service Saved is also kept in, Instapaper,
  with what it syncs; tap one for its account, its import and **What it syncs**. Feeds are
  Reader's own.
- Bookmarks from before Saved moved into folders once: each into its collection's folder, the ones
  without a collection into a folder called **Bookmarks**.

## Instapaper account

Settings › **Account** › **Sign in with Instapaper** opens Instapaper's own page in your browser.
Approve Reader there and you come back to the app (or the web page) signed in; Reader never sees your
password. The same sign-in turns on sync (see Sync between devices) and connects Saved to
Instapaper:

- **Import:** right after signing in, ☰ › Connected services › Instapaper asks what to bring in, once:
  **Import everything**, or **Choose folders** (and its archive). Each article arrives where it
  belongs: archived in the Archive, a folder in its folder, with its tags. **Import again**
  does it again later.
- **What it syncs:** everything in Saved (the default), or **Only these folders** (To read counts as
  one). What you add, file, archive or tag there, and how far you read, goes both ways, while
  the app is open (when it opens, a moment after a change, and when you pull down on Saved). An
  article you file out of those folders is archived in Instapaper, never deleted; unticking a folder
  says first how many articles that is.
- Instapaper's home list is **To read**, its **folders** are Saved's folders and its archive is the
  **Archive**. Filing into a folder Instapaper doesn't have yet makes it there.
- The reader always shows Reader's own full article, for every account: an article that came in
  from Instapaper reads exactly like one saved from a feed.
- If Instapaper doesn't answer or asks to wait, nothing is lost: the rest goes the next time.

**Sign out** removes the sign-in from this device and stops syncing here. Saved stays.

Until Instapaper has approved Reader, only the owner's own account works; other accounts see
"Instapaper hasn't approved Reader for your account yet." Setup and details: `instapaper.md`.


## Common problems

| Problem | What to do |
|---|---|
| "No feed found at this address" | Try the site's feed address instead; look for an RSS or feed link on the site. |
| "Couldn't connect" | Check the internet connection and pull down to try again. |
| A feed shows only short summaries | That is what the site publishes; tap **Read on website**. |
| Not sure Save worked | A "Saved to To read" sheet opens; the article is in **Saved › To read**. |
