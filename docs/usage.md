# Usage guide

## First launch

The app starts with **No feeds yet**. Tap **Add feed**, or **Import OPML** to bring your feeds
from another reader.

## Adding feeds

Tap **Add feed** (on the empty screen or on ☰ › **Manage feeds and Saved** › Feeds) and type an
address. Any of these work:

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
the empty start screen or in ☰ › Manage feeds and Saved › Feeds, under Move to or from another
reader) and pick the file. All feeds are added (feeds
you already follow are skipped), in the same folders as in your old reader, and the app downloads
their articles. **Export OPML** does the reverse, folders included. Read and starred states don't
come along.

## Folders

Open **☰ › Manage feeds and Saved** (the Feeds tab). This page lists each folder with its feeds
under it, then the feeds outside folders ("No folder"; when every feed is in a folder, a note
there says so), and at the end Import OPML and Export OPML.

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
articles. The ☰ drawer lists the folders too, and the feeds outside folders after them; a folder
shows its feeds while you are in it (the folder or one of its feeds), so the list stays short. A
feed added while a folder is open goes into that folder. Long-pressing Content's icon on the home
screen offers your first four folders: tap one to open the app on it (#251).

## Sync between devices

Sign in on every phone and in the web app, and your feeds, folders, read articles and settings stay the same on all
of them. Reader keeps them in your Reader account on its own server, encrypted. A phone signing in
for the first time combines its feeds with the account's and takes the settings you already have.

**Signing in:** Settings › **Account**: type your email address and tap **Email me a sign-in link**.
Open the link in that email on the same phone (it opens Content) or in the same browser. A link
works once, for 30 minutes. No password. Your Gravatar picture shows next to your address, and your Gravatar name above it (#234; also the name people see on what you share with them), if you
have one. **Add an email address** adds a second address to the same library. Instapaper is only a
connected service (#215), not a way to sign in (☰ › Manage feeds and Saved › Saved): connecting or
disconnecting it doesn't sign you in or out.

Only Load full articles and Mark as read on scroll sync. How the app looks (theme, the reader's
font, size, spacing and page colour, Show images and the list view) stays on each device, so a
phone and a laptop can each have their own.

The app syncs while it is on screen: when you open it, **at once when another device changes
something** (an article read on the laptop turns read on the phone within a second or two), about a
second after you read an article or change a feed, when you refresh, and when you tap **Sync now**.
The line under your name in Settings › Account says when it last did, or what went wrong.

- **Sign out** stops syncing on this device. Your feeds stay, and Instapaper stays connected.
- **Delete synced data** removes everything Reader keeps for your account from its server and
  signs out every device. Each device keeps its own feeds and read articles.

See [account.md](account.md) and [sync.md](sync.md) for how it works.

## Reading the list

- The **bar at the bottom** switches between **Feeds** and **Saved** (on a tablet or in landscape it
  sits at the left side; on the web, a wide window has no bar: the menu at the left does it).
- Under the search bar, one row of chips. The first chip says **what you see**: in Feeds **Unread**
  or **All**, in Saved **To read**, **Archive** or **All**; tap it to pick another. After it come
  your **folders** (Feeds) or **Liked** and your **tags** (Saved): tap one to show only that, tap it again or the
  **X** to show everything again. Changing the first chip keeps the folder or tag you picked.
- **☰** opens the drawer (on the web, a wide window shows it at the left all the time, without ☰).
  It has two sections with the same choices as the chips: **Feeds** (**Feed**, your folders, the
  other feeds, each with its unread count; a folder shows its feeds while you are in it) and
  **Saved** (**Saved**, then **Liked** and then your tags, each with how many
  are still to read). Both start open; tap a section's name to fold it, and it stays that way (#251).
  At the bottom: **Manage feeds and Saved** and **Settings**.
- **Search** (tap the search bar; on the web also the **/** key) searches everything: the articles of all your feeds that are on the device (titles,
  text, feed names and authors) and Saved, archived articles too (titles, websites, descriptions,
  folders, tags, notes and highlights). Every word you type must appear; capitals and accents
  don't matter ("cafe" finds "Café"). The results are one list, best match first: the whole search
  in the title, then more of its words in the title, then the newest. Each result says **Feed** or
  **Saved** when both kinds are shown. Chips above the results show **All**, only **Feeds** or only
  **Saved**. With **Feeds** chosen, two more chips narrow it to a **Folder** or one **Feed**; with
  **Saved**, to a **Folder** and a **Tag**. With nothing typed,
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
  Unread and All, or refresh yourself. Going to Saved and back, or tapping the
  same feed again, keeps it as it was; a refresh the app does by itself does too.
- Scrolling down slides the title, the folder tabs and the **Unread** and **All** chips out of the way, so you see
  more articles; scroll up a little and they come straight back.
- **Swipe** a feed article either way past about a third of its width (you feel a short tick) to
  save it (#221). **Press and hold** it for a menu: **Mark as read** (or unread), **Save** and
  **Share**. In the web app, right-click it (or press and hold on a touch screen) for the same
  menu; the round button that appears on an article when you point at it marks it read or unread,
  and so does **m** for the open article. The ☰ menu opens with the ☰ button only, so
  swiping never opens it by accident.
- **Pull down** to download new articles; with nothing (unread) to show, the **Refresh** button
  does the same (#251). The app also does this once every time it starts. All feeds are downloaded
  first and then added at once, each article in its place by date.
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
- If some feeds can't be downloaded, a message says how many; **Manage feeds and Saved** shows why
  for each one.

## The reader

Tap an article to read it. It is marked as read. Saved articles have no read state of their
own (archiving is what puts them away), but opening one marks the feed article it came from as
read, in both apps. Tap the article's **title** to open it on its
website (the same as **Read on website** at the end). In the top bar:

| Button | What it does |
|---|---|
| Full article (page icon) | **Show full article** / **Show feed text**: the whole article from the website, or the feed's own text. It is lit while the full article shows. Not in a saved article: that always shows the whole article. |
| **Aa** | Text settings: font (Sans or Serif), text size, spacing (Compact, Normal, Relaxed), background (Theme, Sepia or Dark) and **Auto dark** (the dark page whenever the app is dark, e.g. Sepia by day and dark at night). Changes apply right away and are remembered for every article. |
| ⋮ | **Open in browser**, **Mark as unread** (back to the list, where it waits unread; the web app keeps the article open and offers **Mark as read** too), **Sign in to this site** (#180: for a site you pay for, such as standaard.be; sign in on its page and tap **Done**, and from then on its articles load in full; only while you aren't signed in to that site yet; Settings › Sites you're signed in to lists them, with **Remove**), **Report a problem with this article** (#181: pick what is wrong, add a comment if you like, and **Send** opens a pre-filled report on GitHub in your browser, with the article's link and the text Content shows, #251, so it can be compared with the website; a text too long for the address is cut short there and put whole on the clipboard to paste; submit it there), and for a saved article **Remove from Saved**. Open in browser, and links in an article, open in a tab of your browser on top of Reader, with a **Save** button in its toolbar: it opens the save sheet over the page (see Saving from other apps); save or close it to keep reading. |

The bar at the bottom is the article in your library, with **Share** at its right end (Content's
share sheet, or straight to the app you chose). Before it is saved it has **Save** on the left: it
saves the article to **To read** (by default), or sends it to the app you chose (see below). Once
saved, Save makes way for **its folder** (To read or Archive): tap it for **Organise**, the sheet
with the folder, Like, the tags and the note (#251: on a phone there was no room for the folder and
the tags side by side). Next to Share, a quote mark with a count opens the article's highlights and
notes. The text ends with **Read on website**. Reader remembers how far you read and opens the
article there again.

**Highlights and notes.** Press and hold on a word, drag the handles over a passage, and tap
**Highlight** in the menu over it, or **Note** to write a note with it: on the phone they come
first, then Copy, Share, Select all and the apps that act on text, such as Translate (#251). In
the web app, select text with the mouse and pick **Highlight** or **Note** in the small bar above it.
The passage gets a yellow marker; tap or click it to change its note or remove it. A quote mark
with the count in the bottom bar lists every highlight of the article. Highlighting a feed article
saves it to **To read**, so the highlight is kept. A highlight is kept as its passage and a few
words around it, not a position, so it is found again in the feed's text, the full article or a
newer version; when the text changed it is lighter and underlined ("approximate"), and when it
isn't there at all it is listed as "not found". A highlight is never thrown away. Highlights and
their notes sync between your devices with your Reader account, and Saved's cards show the first
one when the article has no note. On the phone a highlight stays within one paragraph.
With Instapaper connected, highlights are the same both ways too, with nothing to turn on (#251):
yours go to the article in Instapaper, and ones you make, change or delete in Instapaper's own apps
come into Content (when the article opens, or at the next sync). Free Instapaper accounts allow
five new ones a month: the others stay in Content, say "Not in Instapaper yet" in the list of
highlights, and are tried again the next day.

After **Save**, **Save and share** opens (#256): the **Folder** (To read and **Archive** first, then
your folders, or **New folder** to make one), the **Tags** with **Like** in front (every tag you use,
tap to turn one on or off; **New tag** makes one that doesn't exist yet), a **Note**, and, signed
in, **Send with Content**: the people you sent something to before, tap one or more, or **Add
address** for someone new (Content checks they have an account). The button then says **Save**,
**Save and send** or, for a link you add or share from another app with **Don't save** picked
(nothing lands in Saved), **Send**. It keeps what you chose, also a folder or tag you typed but
didn't add yet, and one message says where it went and who got it. **Remove** takes the save back, as if you never
saved it ("Removed from Saved"); closing the sheet keeps it where it was just saved, unchanged.
The same sheet opens when you press and hold a saved article in Saved, a row in To read or a card
elsewhere, and pick **Organise** (web: right-click it); **Share** and **Remove from Saved** (with
**Undo**) are in that menu too, and in To read also **Archive** (or **To read** in the Archive). Cards don't swipe. When it
closes, a message says where the article went; after a swipe, **Add link** or a link shared from
another app it also has **Open**, which goes to the article in Saved.

**Inbox** (#167, #246). Signed in, a bell sits in the search bar at the top. What other Content
users send you lands in the inbox behind it, not in Saved: a list like a feed's, newest first, with
who sent it (their Gravatar picture and name) where the feed's name would be. Unread ones are bold
with a dot, and a dot on the bell says something unread waits; opening one marks it read. Swipe one
to save it (it stays in the inbox, as a feed article stays in its feed), or press and hold (web:
right-click) for **Mark as read/unread**, **Save**, **Share** and **Remove from inbox** (with
**Undo**). Something already in Saved arrives in the inbox too, and stays where it is in Saved; the
same article shared again comes back unread with its latest sender. Removing a saved article from
Saved leaves it in the inbox. Opening the inbox checks for new shares first, so they show up right away.
For a notification when something arrives, see Notifications below.

**Full articles.** Many feeds contain only the start of an article, or a few paragraphs with a
"Read full article" link. Then the reader fetches the
whole article from the website by itself: a thin bar shows while it loads, and the text is
replaced by the complete article (without the site's menus, ads and comments). If that fails, the
feed's text stays and a line says so. The page button in the top bar switches
between the two for the open article; **Settings › Reader › Load full articles** turns the
automatic loading off. Full articles are kept for a while, so opening one again is instant.
**Read on website** at the end always opens the original page. 

**Pictures and videos.** The list shows each article's picture as a thumbnail; a link you save or
share from another app gets the picture (and description) the page itself names. In the reader the
picture appears above the title, and the pictures inside the article appear between the
paragraphs, with their captions. Embedded videos and posts (YouTube, Vimeo, X, Instagram,
Spotify and others) show as a card with a preview; tap it to watch or open it in its app or the
browser. **Settings › Reader › Show images** turns pictures and previews off (for example to save
data); embed cards stay, without preview.

## Save and Share

The reader has a **Save** button and a **Share** button (#256: there are no settings for them any
more). **Save** saves the article to **To read** and opens **Save and share**, where you can also
send it to people with Content (see Saved). **Share** opens Content's share sheet.

**Content's share sheet** (#251, on the phone). At the top, signed in, **Send with Content**: the
people you sent something to before, with their Gravatar picture or first letter (one tap sends it
to their inbox), and **Email address**, which opens a field in the sheet to type another one and
send it (#256; it says at once when there is no Content account for it). Below, the apps: the ones you pick most
in this sheet first (Android doesn't tell apps which ones you use elsewhere, so Content counts the
picks here, on this phone only; until then well-known messaging and mail apps come first), then
**Copy link**, and **More** for Android's own share menu with every app.

Connecting Instapaper changes nothing here: what is in Saved is also kept in the connected service.

**From other apps** (#235, #256). Sharing a link to **Content** opens **Save and share** over the app
you were in. Signed in, the share menu also has the last few people you sent something to at the
top: pick one and Save and share opens on **Don't save** with that person picked; tap **Send**. In
the web app, signed in, **Share** opens Send with Content with the people you sent to before, and
**Other apps** (or **Copy link** where the browser has no share menu) for everything else. One article at a time: sharing a whole
tag is gone (#251). You can send to yourself too: it lands in your own inbox, with a notification
on every device where they are on.

**Notifications** (#235). Settings › Account › **Notify me when something is shared with me**, on the
phone and in the web app. On the phone it asks Android's permission and then shows "Something was
shared with you" even when Content is closed; tap it for the inbox. In the web app it
asks your browser's permission; from then on the browser shows "Something was shared with you", even
with Content closed, and clicking it opens the inbox. Per browser; signing out turns it
off.

**Saving from other apps.** In Chrome or any app that shares links, tap Share and pick
**Reader**. Only the **Save to Reader** sheet opens, over the app you were in (the rest of Reader
stays closed): the site, then the **Folder** (To read, **Archive** for a link you keep
for reference, your folders, or **New folder**), the **Tags** with **Like** in front, and a **Note**. **Save** saves
it and says where it went, and you are back in that app; **Cancel** saves nothing. **Add link** in
Saved opens the same sheet with a field for the address. A
link shared without a title gets the page's own title, description and picture. Shared text without
a web link says "No link to save".

## Settings

- **Account:** sign in with an emailed link to sync; signed in, your address, **Sync now**,
  **Sign out**, **Delete synced data** and **Add an email address** (see Sync between devices).
- **Article list:** View (Small, Medium, Large) and **Mark as read on scroll**.
- **Reader:** **Load full articles**, **Show images**, and the same text settings as **Aa**, with a preview in the page colour you chose.
- **Appearance:** Theme (System, Light, Dark) and Dynamic color (wallpaper colours instead of
  the app's blue).

Your feeds and Saved are managed on one page, **Feeds and Saved** (#222, #251): ☰ › **Manage feeds
and Saved**, next to Settings, with a tab for each.

- **Feeds:** every feed with its address (or why its last download failed), grouped by folder. Add,
  rename, reorder (drag) or remove feeds (a removed feed's articles go too), create, edit, reorder
  and remove folders, and put feeds in folders. At the end, **Import OPML** / **Export OPML**, to
  move your feeds between readers.
- **Saved:** **Tag with the feed's folder** (on by default, on the phone): an article you save from
  a feed (Save, the swipe, the save sheet) gets its folder's name as a tag, spelled like a tag you
  already use. It still goes to To read. Renaming or removing the folder later doesn't change tags
  already given. Each device keeps its own choice. Then **Instapaper** (see Instapaper account) and
  Saved's tags, to rename or remove.

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

- **Where you stopped reading:** a saved article opens where you left it, on the phone and in the
  web app, and that place syncs between your devices and with Instapaper (both ways), so you can
  start on one and go on on another. An article you barely began or finished opens at the top.
- **Each article is in one place:** **To read** (where everything new arrives), **one folder**, or
  the **Archive**. Filing an article in a folder takes it out of To read; archiving it takes it out of
  its folder.
- **Getting articles in:** **Save** in an article; **swipe a feed article** either way in the list,
  or press and hold it › **Save** (the save sheet opens, then the message offers **Open**); **Add link** (the button at the bottom right of
  Saved); and sharing a link to Reader from another app. These go to To read unless you pick a
  folder in the save sheet. A feed article you save stays the same article: its feed, text and
  picture come along, and it isn't copied.
- **Swipe in To read and Archive:** left archives (with **Undo**; in Archive, back to read),
  right opens **File in a folder**.
- **The chips** under the search bar: first **To read**, **Archive** or **All** (tap it to pick),
  then **Liked** (set apart by its heart) and your tags, most used first, one at a time.
- **To read looks like your feeds**: the same rows, in the view you chose for the
  feed list (Small, Medium, Large), with how many articles and about how long they take at the top.
  **A folder and the Archive look like a page of notes**: cards in two columns, each with its picture, the title,
  a few lines of your first highlight (quoted) or else the article's description, a glimpse of your
  note in small letters, its tags as small chips, then the site, a marker icon with how many
  highlights it has, and a heart when liked.
- In the reader, the bar at the bottom: **its folder** (To read or Archive), which opens Organise.
  **Remove from Saved** is in ⋮; removing asks first and takes the note, tags and highlights with it
  (Archive keeps it searchable instead).
- **☰ › Manage feeds and Saved › Saved**: every tag, with how many articles have it; tap one to
  rename it (renaming it to another one's name joins the two), or remove it from every article.
- **In the web app** Saved is the same: the same chips and cards, and **Add link**
  (the link button in the search bar). In an article the top bar is the reading (the full
  article, Aa, ⋮) and the bar under it the library, with Share at its right end: **Save** before it is saved (it opens the same sheet), then
  the same two menus as on the phone: **where it is** (To read and Archive, your folders) and **its
  tags** (Like, your tags). Archiving goes back to the list with **Undo**. Keys: **j**/**k** next
  and previous, **m** read or unread, **o** open on the website, **r** refresh, **/** search, and
  **Esc** closes a menu, then the article.
- **☰ › Manage feeds and Saved › Saved › Instapaper**: the service Saved is also kept in. Everything
  in Saved syncs. Feeds are Content's own.

## Instapaper account

☰ › **Manage feeds and Saved** › Saved › **Sign in with Instapaper** opens Instapaper's own page in
your browser. Approve it there and you come back connected; the app never sees your Instapaper
password. Instapaper only keeps Saved in step: it is not how you sign in to Content (#215, see Sync
between devices). There are no options (#251):

- **Import, once, or not:** right after connecting, Content asks **Import from Instapaper?**
  **Import** brings in everything Instapaper has now, each article where it belongs: archived in
  the Archive, liked, with its tags. **Don't import** leaves what is there until it changes. Closed without an answer, the Instapaper section
  asks the same with two buttons.
- **Everything syncs:** everything in Saved, highlights included. What you add, archive, like or
  tag there, and how far you read, goes both ways, while the app is open (when it opens, a moment
  after a change, and when you pull down on Saved). Articles in Instapaper's folders arrive in To read.
- Instapaper's home list is **To read** and its archive is the **Archive**.
- The reader always shows Reader's own full article, for every account: an article that came in
  from Instapaper reads exactly like one saved from a feed.
- If Instapaper doesn't answer or asks to wait, nothing is lost: the rest goes the next time.

**Sign out** removes the sign-in from this device and stops syncing here. Saved stays.

Until Instapaper has approved Reader, only the owner's own account works; other accounts see
"Instapaper hasn't approved Reader for your account yet." Setup and details: `instapaper.md`.


## The browser extension (web app)

The web app can't load other websites itself, so it uses Content's feed loader, which many news sites
block. The **Content extension** (Chrome, Edge, Firefox and other Chromium browsers; see
`extension/README.md` to install it) loads those pages from your own browser instead.

- **Save to Content:** click the C in the toolbar, then the button: the web app opens and saves the
  page you're on. Or right-click a page (**Save to Content**) or a link (**Save link to Content**).
- **Sites you pay for** (#180): there is no separate sign-in in the web app. Sign in on the site
  itself, in a normal tab of the same browser; with the extension installed, Content then loads its
  articles in full with that sign-in. Open the article again (or reload the web app) after signing in.
- In your browser's language (English, Dutch, French, German, Spanish or Italian).

## Common problems

| Problem | What to do |
|---|---|
| "No feed found at this address" | Try the site's feed address instead; look for an RSS or feed link on the site. |
| "Couldn't connect" | Check the internet connection and pull down to try again. |
| A feed shows only short summaries | That is what the site publishes; tap **Read on website**. |
| Not sure Save worked | The **Save** sheet opens and the message says "Saved to To read"; the article is in **Saved › To read**. |
