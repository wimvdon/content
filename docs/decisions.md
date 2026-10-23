# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-10-23: Instapaper no longer signs you in (#215)

- Signing in or making an account is done with an email link only; Instapaper is only a connected
  service that syncs Saved. Connecting Instapaper no longer opens or signs in to a Content account
  (the app ignores the account key the feed loader still sends).
- Devices signed in with Instapaper before #166 keep their account: its key moved to the Content
  account then, and **Add an email address** links an address to it.

## 2026-10-23: Signing in to sites you pay for (#180)

- Android: the site's own sign-in page in a WebView (part of Android, no new library); Done keeps the
  cookies the site set for its address. They go only to that site and its subdomains, over https,
  with the app's own requests (feeds and full articles). Works for any site, not one by one.
- The cookies are secrets: their own DataStore file, out of backups and phone transfers like the
  Instapaper and account keys, never synced, never sent to the feed loader. The WebView's own data
  (`app_webview`) is left out of backups too.
- A saved article from a signed-in site shows this phone's own full text instead of the one stored in
  the account: the stored one is often the teaser another device got.
- The web app can't read another site's cookies; there the browser extension (#168) does it.
- Rejected: reading cookies from the phone's browser (not allowed), and typing a password into
  Content (the site's own page is safer and handles two-step sign-ins).

## 2026-10-23: Connected services and OPML move into Settings

- The ☰ menu keeps only places to go (Feed, folders, Saved, tags) and Settings. Connected services
  (Instapaper) and Import/Export OPML are things you set up once, so they move into Settings, on
  Android and the web. The empty start screen keeps its Import OPML button for a first start.
- Settings' order: Account, Connected services, Move to or from another reader (OPML), then the
  reading settings.

## 2026-10-23: Reporting a badly cleaned-up article (#181)

- A report opens a pre-filled "new issue" page on GitHub in the browser (label `parser`), which the
  user submits with their GitHub account. Chosen over sending it through the feed loader with a
  token that may create issues: no new secret to keep, nothing sent without the user seeing it, and
  only someone with a GitHub account (the owner) is expected to report.
- The report holds the article's link, the problem, the comment and the app's name and version,
  never the article's text. A reported page can become a test in `testdata/articles/`.
- The title and labels are in English, for the repository; the dialog is translated.

## 2026-10-23: Instapaper syncs everything, with nothing to choose

- Connecting Instapaper brings in everything it has, once, as the first sync; the choice of what to
  import (everything, or some folders and the archive) and Import again are gone, on Android and the
  web. With Raindrop.io gone (#179) and folders turned into tags (#187), Instapaper is the only
  service and there is nothing left to choose between.
- The settings that kept a scope (`scopes`) stay readable, so older devices and synced settings
  don't break; nothing sets them any more.

## 2026-10-23: Signing in with an email link (#166)

- A Content account no longer depends on Instapaper: an emailed link (no password) signs in, and
  Instapaper becomes a connected service only. Accounts opened with Instapaper keep working and can
  add an address once, after which either way opens the same library.
- The emails go through Resend (the owner's choice), from resend.wimvandonck.be.
- The link's token is signed, not stored: nothing is kept for links never opened. Used link ids and
  which account an address opens are kept under an HMAC of the address, never the address itself.
- A link, not a code to type: the issue asked for one, and it opens the app through the existing App
  Link. A debug build can't verify App Links, so there it opens the web app.
- The picture comes from Gravatar (the issue's choice), skipped when pictures are off.

## 2026-10-23: Highlights sync both ways with Instapaper

- The owner asked for both ways, replacing the one-way copy below: highlights made, changed or
  deleted in Instapaper's own apps now come into Content too. They are looked up when a saved
  article opens and whenever its highlights are sent, so a list of every article's highlights is
  never fetched on each sync (one call per article would be too many).
- A highlight deleted in Instapaper is now deleted in Content (it used to stay and not be made
  again). A note changed on both sides keeps Content's.
- The switch is renamed **Sync highlights with Instapaper**.

## 2026-10-23: Copying highlights to Instapaper (#164)

- One way only: Content's highlights get copies in Instapaper; highlights made in Instapaper's own
  apps don't come in. Bringing them in depends on finding Instapaper's passage in Content's text,
  which #102's matching test was meant to measure first.
- Duplicates are avoided by looking at the article's highlights in Instapaper before copying (same
  passage = the copy), not by syncing Instapaper's highlight ids through the Content account. One
  extra call per article with something to copy, and no change to the sync format.
- The switch is per device, off by default, because of the free plan's five highlights a month.
- A highlight deleted in Instapaper is not made again, so deleting it there sticks.

## 2026-10-23: Saved polish: menu names, tags in the sheet, cards, the reader (#208)

- ☰'s first rows are named after the places: **Feed** and **Saved** (were All articles and All
  saved). Liked sits under Tags, first, as on the chips.
- The save sheet shows every tag in use as a chip to turn on or off; **New tag** (was Add a tag)
  only makes a tag that doesn't exist yet. All tags rather than a few recent ones: there are few,
  and a steady list is easier to find your way in.
- Chips don't move: no check mark and no bold on a chosen group chip (its fill shows it), so its
  width stays the same; the order never depends on what is chosen.
- Cards: the text is three lines at most (the first highlight, else the description); the note
  shows apart, small, two lines; tags are small chips instead of #words; the highlight count keeps
  its marker icon.
- A saved article always shows the whole article when its text is short, and the reader drops the
  switch back to the feed's text there: in Saved that is a useless short intro.
- In the bar under a saved article the label gives way before the drop-down arrow, which used to
  shrink with a long list of tags.

## 2026-10-23: No more types in Saved

- The owner found the type filter (Articles, Videos, Audio) not worth its place: it is gone from
  Saved's search bar and from Search's chips, on Android and the web.
- A type was never stored: it was read from the link each time (`typeOf`), so the database and the
  sync file don't change. The guessing code, its texts and its tests went with it.

## 2026-10-23: Liked is back (#206)

- Undoes #188 on the owner's wish: the Like in the save and Organise sheets and the reader's tags
  menu, the heart on cards, and Instapaper's like (the old "star") read and sent again.
- Liked is a group like a tag in the new navigation (#205): a ♥ Liked chip before the tags and a
  Liked row in ☰ under All saved, one at a time with the tags, and it follows the state chip
  (To read, Archive, All), so one rule holds everywhere. ☰ counts the liked items still to read.
- Old likes come back as they were: the app kept them while Liked was hidden. An article that changed
  at Instapaper during that day came in without its like and gets it back the next time it changes
  there; no full re-read of the account is forced.

## 2026-10-23: One chip row: the state, then the groups (#205)

- After #189 and #203 there were four overlapping controls (navigation bar, ☰ sections, tabs,
  chips), and ☰ listed groups in Feeds (folders) but states in Saved (To read, All).
- Now three levels, the same in both places: the **navigation bar** says where (Feeds, Saved); a
  **state chip** at the start of the chip row says what of it (Feeds: Unread or All; Saved: To
  read, **Archive** or All, so the archive can be seen on its own again); the **group chips** after
  it say which group (folders; tags). The Unread | All and To read | All tabs are gone.
- ☰ lists exactly the groups (All articles and folders with feeds; All saved and tags), never the
  states, and picking there keeps the state. Counts are what is unread or still to read.
- Saved's chips show every tag, whichever state, and a chosen tag stays when the state changes, as
  a folder does with Unread and All.
- A folder picked in ☰ and its chip are one choice (they showed the same feeds but lit up apart).
- The web app's bottom bar is gone in wide windows, where the menu at the left does its job.
- Rejected: keeping the tabs and only fixing ☰ (folders would stay second to a read filter), and a
  separate tabs row for the states (a whole row for a filter).

## 2026-10-23: The web sidebar stays open on wide windows again

- #189 made the web app's ☰ menu a slide-out drawer at every width, which the owner didn't want.
  Wide windows (Tailwind `lg`, 1024px and up) show it at the left all the time again, without ☰;
  narrow windows keep the drawer.

## 2026-10-23: Two collapsible sections in the hamburger menu

- The ☰ hamburger menu is structured into two main sections: **Feeds** and **Saved**.
- Each section collapses and expands automatically depending on which section you are in (moving
  to Saved expands Saved and folds Feeds shut, and vice versa), keeping the menu compact. Manual
  folding lasts until you switch sections.
- **Feeds** contains: All articles, folders with their feeds, loose feeds, and **Manage feeds**.
- **Saved** contains: **To read**, **All**, **Tags** (listing each tag with item counts), and
  **Manage tags**.
- Connected services and Settings remain at the bottom of the drawer.

## 2026-10-22: Folders for feeds, tags for saved (#186)

- Feeds stay grouped in folders, as in OPML; saved articles are grouped with tags. Simpler than
  tags everywhere.
- A feed's tag (#196) now always mirrors its folder: moving a feed or renaming a folder moves its
  Feed chip too, and a feed outside folders has no tag. No separate feed-tag editing.
- The save and Organise sheets and the reader's place menu no longer offer New folder: To read or
  Archive, plus tags. ☰ › Folders and tags is now ☰ › Tags.

## 2026-10-22: The APK is called content-<version>.apk

- The Gradle project name, which names the APK and the mapping file, is now `content` (#178).
  The package name stays `be.wimvandonck.reader`, so phones keep updating the installed app.
- Names that aren't shown to users stay: the Cloudflare Workers (`rss-reader-web`,
  `rss-reader-worker`), the web app's browser database (renaming it would lose its data) and the
  sync file's format name.

## 2026-10-22: Feed | Saved at the bottom, a search bar at the top (#189)

- Replaces the Feed, Saved and Archive tabs of #183 after trying them. The navigation bar is back
  with two places: **Feed** and **Saved** (a rail on wide Android windows; the web app shows the bar
  everywhere).
- The title bar is a large search bar: ☰ at its start, "Search articles" opens Search.
- Under it, two tabs: **Unread | All** in Feed, **To read | All** in Saved (All includes the
  archive, which is no longer a tab of its own).
- Then the tags as chips, **one at a time**, with an X at the end that clears the filter. In Feed
  they are the feeds' tags (#187, a new `FeedSelection.Tagged` / selection kind `tag`); in Saved the
  items' tags.
- Saved always shows notes (cards), To read too.
- The ☰ menu holds only what you manage (feeds, tags, connected services on Android, settings);
  it no longer lists folders, feeds or Saved's tabs. The web app's sidebar became the same menu.

## 2026-10-22: Feed, Saved and Archive as tabs under the title (#183, Android)

- The navigation bar (and the rail on wide windows) is gone. Three tabs under the title choose what
  the list shows: **Feed** (the feeds' articles), **Saved** (To read) and **Archive**. Tapping Feed
  again goes back to every feed.
- Search opens from a button in the title bar and has a back button of its own.
- The feed folder tabs are gone; a folder or a feed can still be picked in the ☰ drawer until feed
  management moves there (#186). Saved shows its tags as chips under the tabs.
- The web app follows in its own change.

## 2026-10-22: Feeds get tags (#187, second step)

- A feed now has tags, stored on Android in a new column (database version 6, an automatic
  migration) and in the sync file as an optional `tags` list, left out when empty so older apps read
  the same file. The web app keeps them with the feed in the browser.
- A feed in a folder that has no tags yet gets the folder's name as its tag, when the library is
  read and after every change (`feedFoldersToTags`). The folder itself stays until the screens stop
  using folders (#183 to #186).
- Saving a feed article tags it with its feed's tags (#120 used the folder's name; that is the
  fallback for a feed without tags).

## 2026-10-22: Saved's folders become tags (#187, first step)

- One way to group: tags. Each saved article's folder becomes a tag with the same name (once; a tag
  of that name isn't added twice), on Android when the library is read and after every change
  (`Saved.foldersToTags`, next to `Saved.settle`), and in the web app after every change. A folder
  synced in from an older app becomes a tag the same way.
- Instapaper syncs tags and the archive only. Content no longer reads Instapaper's folders, makes
  them or files into them; articles in a folder there stay in it.
- What Instapaper syncs is always everything in Saved: a scope of folders means nothing now, and
  leaving the scope would archive articles there. The choice is gone from both apps.
- Still to come in #187: feed folders become feed tags, and the folder controls go from the
  screens (#183 to #186).

## 2026-10-22: A service's article finds the saved one without tracking parameters

- Duplicates appeared in Saved after saving, though Instapaper had each article once. Each device
  keeps its own list of which Instapaper article is which item. A device that hadn't sent an
  article matched it by its exact link, and Instapaper can keep a link without the feed's
  tracking parameters (`utm_source` and the like), so it made a second item, which then synced
  everywhere.
- Now, when a device meets an Instapaper article it hasn't linked yet, it first looks for a saved
  item whose link is the same apart from tracking parameters (`ItemKeys.loose`, `looseKeyOfLink`)
  and isn't linked to another article there. Item keys themselves don't change.
- Duplicates made before this stay for now. Removing one by hand can remove the article at
  Instapaper too (the copy is linked to it on the device that made it), so a careful cleanup is a
  separate change.

## 2026-10-22: Liked is removed (#188)

- No Like button, no Liked chip and no heart on cards, on Android and the web. Tags do the job.
- Instapaper's like (the old "star") is no longer read or sent; stars already there stay.
- The `liked` field stays in the database and the sync format for now, unused, so no database
  migration is needed and an older app on another device reads the same file. Old likes simply
  no longer show.

## 2026-10-22: Raindrop.io is removed (#179)

- Instapaper stays the only connected service. Raindrop.io's code is gone from the Android app
  (`raindrop/`), the web app (its return page) and the feed loader (the token swap).
- When the app starts, it forgets what it had synced with Raindrop and its import record, and
  deletes the Raindrop sign-in file. Saved itself is not touched: articles that came from Raindrop
  stay in Saved, in their folders.
- The synced settings keep the name `services.raindrop.*` in their list for now, so an older app
  on another device and this one read the same file; it is unused.
- The Worker secrets `RAINDROP_CLIENT_ID` and `RAINDROP_CLIENT_SECRET` can be deleted in
  Cloudflare.

## 2026-10-22: The app is called Content (#178)

- The app shows feeds and saved articles together, so "Reader" no longer described it. Every
  user-visible name is now Content: the launcher name, the test build ("Content Dev"), the web app's
  title and install name, and the texts that mention the app in every language.
- The package name stays `be.wimvandonck.reader`: changing it would make phones refuse to update
  the installed app. Code names (classes, files, the worker's user agent) also stay; they are not
  shown to users.
- "Reader" as the name of the reading view (Settings › Reader) stays: that is the view, not the app.
- New icon: the same parts as the old feed symbol (a dot and two thick, round-ended arcs) and the
  same sky → azure → navy gradient, arranged as a C.

## 2026-10-21: Search is a tab of its own and replaces Saved's All tab (#171)

Search moves from a magnifier in the top bar to the navigation bar (Feeds, Saved, Search; on the web
also the sidebar), with filters per kind: feeds by folder or one feed, Saved by folder, tag and type.
With nothing typed and no filters it lists everything newest first, so Saved's **All** tab (and the
drawer's and sidebar's All row) went: it was the same list. The list without words is capped at 500
articles, like the 200 search results, so a big library stays quick. The logic is plain Kotlin
(`SearchResults`, unit tested) with the same rules in `web/src/lib/search.ts`. Rejected: keeping a
Search button next to the tab (two ways to one place), and a filter sheet (chips that open a menu
show the choice right away, as Material places filters on a list). The web's menus are the browser's
own select, styled as a chip, so no new package.

## 2026-10-21: One stored text per saved article, in the Reader account

So that highlights line up on every device (and future platforms), a saved article has one text,
stored in the account by the first device that extracts it; the others take it instead of
fetching the page (as Instapaper, Pocket and Readwise keep one parsed copy on their servers). The
devices extract, not the feed loader: parsing on the server would need the Workers paid plan (its
free CPU time per request is far too short) and is blocked by the same sites. Only saved articles
whose full text is loaded are stored (feed texts are the same everywhere already). First one wins,
so two devices can't overwrite each other. Encrypted like item rows. Rejected: a text per device
(highlights drift), storing every feed article (storage for nothing).

## 2026-10-21: The feed loader asks for article pages as a browser does, and says when a site blocks it

Many sites refuse the feed loader because it runs in a data center. What can be done from there
without paying or pretending: an honest browser-style name (`Mozilla/5.0 (compatible; Reader/web;
…)`, as some sites refuse anything else), a browser's Accept header and the reader's languages for
article pages, and a clear "blocked" answer (403) when a site still refuses or shows a bot check,
which the web reader explains with Read on website. This doesn't get past real bot protection: the
stored copy of saved articles and a browser extension (#168, later) do that. Rejected: pretending to
be Chrome (dishonest and easily detected), paid scraping services and residential proxies (cost, and
they would see every page read).
## 2026-10-21: The web app cleans up articles the same way as the Android app

The owner found the web reader full of leftovers. Both apps use Readability, but Android prepares the
page first (glossary buttons, hidden parts, a div per paragraph, split containers) and turns the
result into simple HTML (kept tags only, absolute links, known video players, no repeated title or
byline); the web app showed Readability's output as it was. `web/src/lib/articleExtractor.ts` now
takes Android's steps one for one, and `testdata/articles/` holds sample pages that both test suites
read, so the two can't drift apart. Rejected: running the Kotlin code in the browser (Kotlin/JS or
Wasm would change the whole web build). The browser's cache of full articles starts again (v2), so
articles already opened are cleaned the new way.

## 2026-10-20: Highlights and notes in the reader, without Instapaper copying (#102)

Highlights were built in #124 but left as a draft: it waited for a matching test on Instapaper's
parsed text, which #134 then removed, so the test no longer applied and the work never shipped.
They now ship from Reader's own text on both apps: select, Highlight or Note, a yellow mark, tap
it to edit, a list from the bottom bar. They are anchored by quote, surroundings and occurrence
(`Anchors`, the same rules in `anchors.ts`), stored on the item and synced like the rest of it.
Highlighting a feed article saves it, so the highlight is kept. Left out for now: copying
highlights to Instapaper (free accounts get five a month, and Reader can no longer see Instapaper's
text to place them), and highlights across paragraphs on Android (Compose doesn't say where a
selection starts).

## 2026-10-20: The reader's bars: library on the left, Share on the right, full article on top

The owner asked for the bottom bar to read left to right: the article in your library (Save, or
its folder and tags side by side) on the left, Share at the right end; and for the full article to
be one tap in the top bar rather than in ⋮, since it is used on many articles. Share moved down
because it acts on the article like Save, and it sits where a thumb reaches. The tags button's icon
now has the folder button's colour (it was grey). Same on the web.

## 2026-10-20: Removing a Saved folder archives its articles

A folder holds articles you filed away; removing the folder used to send them back to To read,
turning filed work into unread work again. They now go to the Archive (still searchable, with
their tags and notes), on Android and the web, and the confirmation says so. Rejected: keeping
them in To read (the old behaviour) and asking each time.

## 2026-10-20: Remove right after Save, and a UI review of both apps

Cancel on the "Saved to …" sheet kept the save, which read as if it undid it. It is now **Remove**
(red, with a trash icon): it takes the save back, restoring the item as it was (a link saved
before keeps its earlier state). Closing the sheet keeps the save, since closing never changes
anything. Rejected: Cancel undoing the save (then a swipe away would lose it too).

A review of both apps then fixed what it found, among others: text typed in the sheet is kept by
Save; empty filtered lists offer Show all; Add link starts in the folder shown; swiping left to
save follows the Save button setting; removals ask first on the web and use the error colour on
Android; screen readers hear "Folder: To read", "12 unread" and the type filter; the web's primary
colour passes WCAG AA, messages clear the bottom bars on phones, Escape closes menus and the
article, Enter submits, and feeds move within their folder. Left for later: dragging to reorder
on the web, full-screen settings pages on narrow web windows, and a shared keyboard-navigable menu.

## 2026-10-20: The reader's bar groups like the sheet: where it is, and its tags (#156)

The reader's bar for a saved article had a folder button, a heart and Archive side by side, so
folders and tags were scattered again outside the sheet. It now has two menu buttons, as the sheet
groups them: **where it is** (To read and Archive, a divider, the folders, New folder) and **its
tags** (Like, a divider, the tags, Add a tag). Archiving stays two taps and still goes back to the
list with Undo on the phone. Rejected: opening the whole Organise sheet from each button (slower
for the common one-tap change), and keeping Archive as its own button (it is a place).

## 2026-10-20: One set of rules for read and hidden articles (#157)

Both apps now follow the same rules. Anything you mark read by hand (open, Search, swipe or the web
row's button, scroll) stays in the Unread list, dimmed, until you change what you look at: another
feed or folder, the Unread/All filter, or a refresh you start. Saved and back, the same feed again,
and refreshes the app starts itself keep the list. Mark all read means you're done: the list
empties. Mark as read on scroll only marks articles that were on screen, so new ones arriving above
are never marked unseen. Saved rows never show read state; opening a saved item marks its feed
article read on both. A read article leaving a device keeps its mark (in `readElsewhere`, dropped
after the 120 days sync keeps), because sending it as gone un-read it on the other devices.
Rejected: keeping every row shown until you leave the list (then even Mark all read would leave
it full), and a separate read state for saved items.

## 2026-10-20: Archive is a folder in the sheet, and every sheet can be cancelled (#156)

The save and Organise sheet keeps organising simple: Tags hold Like and the tags, Folder holds where
the article is. Archive is offered there always (not only for a new save), next to To read with
their icons and a divider before the folders, because archiving is just another place. Every sheet
has Cancel, and closing it is Cancel: only Save changes anything. Rejected: keeping a dismissed
choice (it surprised you when you only wanted to look), and a separate Archive switch.

## 2026-10-20: Reader is the library; a service is somewhere it is also kept (#145)

Services no longer own items (#121's `Item.service`). Connecting a service runs an **import**, once
(everything, or the folders picked): each article arrives where it belongs, as it is, and what isn't
imported stays there until it changes. Each service then has a **scope**, everything in Saved by
default or a set of folders (To read counts as one), shared **both ways**: an article reaches every
service whose scope covers it, so the same folder in two scopes keeps the two in step through
Reader. Per field, what changed here is sent and what changed there comes in; never a value just
because the sides differ, and never one that just came in, because each row is what was last seen
or sent (`linked` says whether it is in step). Both sides changed: the later change wins (the
service's change time against when Reader's item last changed). Leaving the scope **archives** an
article there, never deletes it, and unticking a folder says how many first; removing an article
from Reader still asks from both or one. Raindrop's nested collections keep their origin. The scope
and the import record are synced settings (`services.<id>.scope`, `.imported`), the same on the web.
Replaced: Articles in both (`DuplicateSync`), Copy between services (`ServiceCopy`), New saves go to
(`saveService`) and the service marks on rows. Notion (#114) should fit as a service with a thinner
vocabulary; if it needs an exception, the model is wrong.

## 2026-10-20: The article's actions: reading above, library below (#139)

"Finished reading" named a moment the app doesn't have, and three ways led into one sheet. One rule
now places every action: the top bar is this reading (Back, Aa, Share, ⋮); the bottom bar is this
article in your library (Save before; the folder, which opens Organise from #143, Like and Archive
after); the end of the text is Read on website; ⋮ holds the rare ones. `FinishSheet`, its strings and
the end-of-text button are gone, and Tags left ⋮. Archive stays a button, decided: the article just
read is the one most often done with. Rejected: keeping Like in the top bar (it says what the article
is to you, not how you read it) and Save in the top bar (the bar changed shape the moment you saved).
The web reader got the same two groups.

## 2026-10-20: Save, the list icon, and a confirmation that opens the article (#142)

"Read later" named only part of what Saved is, so the action is **Save** everywhere (the reader,
the swipe, the sheet), and "Read later" is what To read means, never a button. Saved's destination
and the Save actions use Material Symbols **list** (`ic_list`); the open book stays on To read. After
the sheet, a message says where the article went, with **Open** when you are not already in it (a
swipe, Add link, a share from another app). The share screen shows that message as a snackbar of its
own before it closes; Open starts the app on the article (`MainActivity.EXTRA_OPEN_ARTICLE`).
Rejected: a Toast for shares (it can't have an action).

## 2026-10-20: One save and Organise sheet, on Android and the web (#143)

Saving, filing later and tagging an article used three sheets (`SaveSheet`, `AddedSheet`, the File
half of `FinishSheet`) with a typed folder field and a help line. Now one `SaveSheet` serves every
way in, in one order: what is saved (only before the save), the folder as chips (Archive only for a
new save), the tags with Like in front, the note. Whether the save already happened decides the
heading and Cancel. The rules are plain code (`SaveChoices` on Android, `saveChoice.ts` on the web),
tested. The sheet no longer asks which service keeps the article: #145 answers that per folder.
To read keeps its name. Rejected: one sheet per way in (the same question in three wordings).

## 2026-10-20: The web app's Saved works on the items directly, like Android's (#147)

The web app's Saved still went through `LocalSaved`, Instapaper's shape (bookmarks with number ids,
lists read a page at a time) laid over the library's items. It is gone: `web/src/lib/saved.ts` is a
port of Android's `Saved.kt`, and the screens read the items as they are. Saved now has the same
tabs (To read, All, folders, Archive), chips (Liked, the tab's tags), type filter, note and cards as
on Android, Search looks through folders, notes and highlights without loading anything, and the
wording is Android's (no `instapaper*` keys left for Saved). Kept: the move-in of Saved from before
#106 (`localSaved.ts`, `savedItems.ts`, now only that). Rejected: adding notes to `LocalSaved` (a
second shape to keep in step for every later change, #143, #139, #145).

## 2026-10-20: Reader's own article text, always (#134)

A saved article is read exactly like a feed article: the feed's text, else the full article
`ArticleExtractor` makes of its page. Instapaper's parsed text is no longer asked for or shown
("Instapaper text"), and the texts kept before are deleted at start. Why: Instapaper only gives its
text to the account that registered the app, so the owner read one text and everyone else another,
and the owner's phone and the web app disagreed; highlights (#124) need one text to anchor in. An
article with no feed text (an added link, or one from a service) now loads its page whatever the
full-articles setting, since it has only a description otherwise. Rejected: buying an Instaparser key
(not worth its price for a better parser on one device).

## 2026-10-20: The web app's Saved is the same signed in or not; Instapaper only syncs

The owner's rule: the interface is always the same; external services sync, they don't change the
experience. The web app showed Instapaper's own lists when signed in and the library's items
otherwise. Now Saved is always the items, and `serviceSync.ts` (a port of Android's
`ServiceSync.kt`, with `instapaperSync.ts` for Instapaper) syncs them in the background after each
Reader account sync. Labels and dialogs no longer change with the sign-in; folders are in name order
everywhere (Instapaper's own order isn't kept). The Worker forwards one more call, POST
`/bookmarks/{id}` (title and reading progress), to the same host. Rejected: leaving the web app's
Instapaper sync to the phone (a web-only user's saves would never reach Instapaper).

## 2026-10-20: A smoother way from a feed to a folder or the archive

The owner found feed → Saved → folder or archive not smooth. Changes: the sheet after Read later
has the folder too (set on Done or close, not while typing); Archive in the reader acts at once and
returns to the list with Undo (back into its folder), instead of a sheet asking again; a swipe save
offers File in its message; Saved's list rows swipe like the feed's (left archive or back to read,
right File), through one shared `SwipeRow`. Finished reading at the end of the text still asks, as
it is the moment to decide. Rejected: confirmation dialogs (Undo is cheaper than asking).

## 2026-10-19: Sharing to Reader shows only the save sheet (#103)

Links shared from other apps go to an exported `ShareActivity` alias of the see-through
`SaveFromTabActivity`, so only the save sheet opens over the sharing app, as the custom tab's Save
already did; `MainActivity` no longer takes shares. An alias keeps the tab's own entry unexported:
other apps can only send a shared link, which still needs the user's Save. Rejected: a second,
separate activity (the same screen twice).

## 2026-10-19: Instapaper and Raindrop together (#121)

Following #121 and the owner's decision comment there:

- **Merged view, nothing copied on its own:** an article in both services is one item (its address is
  its key), the first service's; the other has a copy. Marks on each article say where it is kept.
- **What copies share** is a setting per state (`DuplicateSync`): Archive, Liked and Tags on, Folder
  off. Title, note, progress and highlights stay per service. A copy's changes come in only for
  what is shared; a deletion in one service's own app never deletes in the other (the item moves to
  the other); removing in Reader asks (both, or one only).
- **Default for new saves:** asked once, changeable in Connected services and per save.
- **Copy is an explicit import** (Connected services), with the count and what doesn't carry over
  first, skipping what the target has, cancellable, in batches so what got through is kept.
  Rejected: ongoing two-way sync (out of scope in #121).

## 2026-10-19: Raindrop.io for Saved (#112), next to Instapaper without copying (#121)

The owner approved Raindrop.io and its network destination `api.raindrop.io` (#112).

- **Sign-in through the Worker** (OAuth 2, `worker/src/raindrop.ts`), like Instapaper's: the client
  secret stays a Worker secret, and the client ID lives there too, so the app holds neither. The
  Worker passes tokens through and keeps nothing; the app calls `api.raindrop.io` directly.
- **Callback on the web app's domain** (`/raindrop/callback`), so the existing App Link verification
  covers it; the page there hands the address to Reader or Reader Dev, because debug builds can't
  verify App Links.
- **Mapping:** Unsorted is To read, collections are folders (nested ones flattened to "Parent /
  Child", as Saved's folders are flat), an "Archive" collection made on first use is the Archive,
  favorite is Liked; notes sync (a new `ServiceFeature.Notes`).
- **No "changes since" in Raindrop's API:** a sync lists the whole account (`RemoteChanges.complete`,
  so a missing raindrop was deleted there), at most every three minutes for the quick sync after a
  change. Rejected: Raindrop's `lastUpdate` sort, which can't tell deletions.
- **Two services, nothing copied (#121):** a synced `service` field on the item (Room v5,
  auto-migrated) says which service keeps it. Items each service already had become its own; new
  saves go to the default service, asked once when both are connected (rejected: copying everything
  to both, which #121 rules out, and per-save choice, left for #121's save sheet). The first sync
  waits until both accounts are read, so neither service briefly looks like the only one.

## 2026-10-19: One Saved tab with folders (#119), replacing Reading list and Bookmarks

The owner chose #119's design over the epic's (#113) two sections: one **Saved** destination
where an item is in exactly one place, To read, one folder, or the Archive, the same for Instapaper
and (next) Raindrop.

- **Data:** a synced `folder` field on the item (Room v4, auto-migrated; per-item sync rows, merged
  per field like the rest), meaningful only while to read; archiving clears it. No new membership
  type, so the reading list's state, sync and services carry over. The web app keeps the field in
  its rows and merges until its own screens catch up.
- **Bookmarks (#108) move into folders** on read (`Saved.settle`, run when the library loads and after
  every change, so rows synced in from a device that hasn't updated move in too): the collection
  becomes the folder, without one a folder called "Bookmarks" (in the device's language). The
  Bookmarks destination, its page and its type chips (tool, repo) are gone; the type filter is now
  Article, Video, Audio, behind a filter button next to search (#119), read from the link.
- **Instapaper:** folders are folders (they were tags in #107): home is To read, `/move` with the
  folder id files, a missing folder is made. `RemoteEntry.folder` is a core field, merged three
  ways; the old folder tag is taken off by the first sync's merge.
- **Look (owner's request):** To read, All and Archive use the feed list's rows and view setting
  (they are lists to read through); a folder shows its items as notes, Noter's Keep-style outlined
  cards in a staggered grid (picture, title, note or description, site, tags), because a folder is a
  collection to browse.
- Chips: Liked first, set apart by its heart and a divider, then the tab's tags by use; several tags
  narrow together. "Remove from Saved" removes the whole item (note, tags, highlights); Archive
  keeps it.

## 2026-10-19: Saved feed articles get their folder as a tag (#120)

- Saving an article from a feed (Read later, the swipe, the save sheet, the custom tab) adds the
  feed's folder title as a tag, reusing the spelling of a tag already in use (case ignored). The
  item still goes to To read. It is a tag on the item, so it reaches Instapaper (and later
  Raindrop) through the normal service sync; no service-specific call.
- In Reader a feed is in at most one folder, so one tag. The Google Reader API case (several
  labels per subscription, #110) is for later.
- On by default, switch in Settings › Save and share. Kept per device, like the list view (not in
  the synced settings, so the web app needs no change now).

## 2026-10-19: Bookmarks, a third destination (#108)

- **Third top-level destination** in the navigation bar and rail (Feeds, Reading list, Bookmarks)
  and a third foldable section in the drawer (All and each collection with its count), as in the
  mockup.
- **Collections are flat and a bookmark is in one at most**, so they are tabs (peer views of the same
  content); types are chips (they filter). The type isn't stored: `Bookmarks.kindOf` reads it from
  the link (video sites, code hosts with owner/repository, a site's home page is a tool), so it needs
  no sync field and stays right when the rules improve.
- **What goes in** follows the membership rules on #108: Keep as bookmark (finishing an article, or ⋮
  on a feed article), the share sheet, Add bookmark. Archiving doesn't keep. Read again puts the item
  back on the reading list from the start and keeps it in Bookmarks.
- **One save sheet** ("Save to Reader") for links shared to Reader, the custom tab's Save button,
  Add bookmark and Keep as bookmark on a feed article: Reading list | Bookmark as a segmented
  button, chosen by the link (articles to the reading list, other links to Bookmarks), then
  collection, note and tags. Nothing is saved until Save. Shared links used to go on the reading
  list at once with the like-and-tag sheet; that sheet stays for Read later in the reader and the
  swipe.
- **Removing a bookmark** that isn't on the reading list removes the whole item (its note, tags
  and highlights), so no hidden item is left; the dialog says so.
- Cards in a staggered grid by default, a list as the alternative (`Settings.bookmarksView`, device
  only, not synced: it depends on the screen).
- The Tags page becomes **Collections and tags** (rename and remove both). Search gets a Bookmarks
  chip and labels bookmark-only results "Bookmark"; they open the bookmark's page.

## 2026-10-19: The reading list replaces Saved; services behind one interface (#107, #109)

Saved becomes the **Reading list**: articles you want to read, as a queue (#113).

- **Reader's own, always.** The reading list is the library's items on the reading list, with or
  without an account. Signed in, Instapaper is a *connected service* that is kept the same as the
  reading list (`ServiceSync`), instead of the screen showing Instapaper's lists directly. So the
  reading list works offline, Search covers every article, and another service can take
  Instapaper's place.
- **One interface per section** (`ReadingListService`): the core every reading-list service shares
  (link, title, to read or archived, liked, tags) is always synced; a service declares what else it
  supports (its text, progress, highlights). Service extras map onto the core: Instapaper folders
  become tags, and Reader never creates folders (Readwise Reader and Wallabag have none, so
  folders can't be part of what all services share). Only `InstapaperReadingList` knows Instapaper.
  A third service (Notion, #114) will test the abstraction; it isn't generalised further now.
- **Sync with Instapaper** uses API v2's `since` (a full copy on the device) and the same three-way
  merge as per-item sync, with the articles as last seen at Instapaper as the base. Failures keep
  everything in Reader; a rate limit is normal behaviour, not an error; the rest goes next time.
  Only while the app is on screen, like the Reader account.
- **Instapaper's parsed text** only for the owner's own account, where the API needs no key; it is
  kept on the device and never synced (API terms). Others get Reader's own extraction; no
  Instaparser key is bought (cost).
- **Lists:** To read and Archive; **Liked** is a filter chip, not a list (the mockup showed a tab; #107
  chose a chip). Swiping a feed article left is Read later; right still toggles read.
- **Finishing an article** asks what's next: Archive, or Keep as bookmark (collection, note, tags),
  which also adds it to Bookmarks (#108) and keeps it on the reading list until archived.
- **Reading progress** is saved when the article is left (not at every scroll pause, which would sync
  every few seconds) and sent to Instapaper.
- **Links without a title** (shared or added) get the page's own title, description and picture,
  read from the page (`PageInfo`, jsoup), which is one of the pages of the user's articles.
- The web app keeps its Saved screens for now and no longer copies its articles to Instapaper on
  sign-in: that copy removed them from the reading list afterwards, which per-item sync would now
  spread to every device.
- Rejected: keeping Instapaper's lists as the screen when signed in (no offline list, no full search,
  every action a network call), and an outbox of operations for the service (a diff with the last
  seen state retries by itself and can't replay stale operations).

## 2026-10-19: Sync per item instead of one document (#106)

The reading list, Bookmarks, notes and highlights would make the one synced document grow without
limit (it was capped at 8 MB). Now everything that grows syncs **one row per item** (`item:<key>`
and `read:<feed url> <article id>`), and the document keeps only folders, feeds and settings
(format version 3; older apps ask to be updated).

- **Server:** rows in the account's Durable Object storage, each encrypted, stored under an HMAC of
  its key and indexed by the version of its last save, so "changes since a version" is one ordered
  list. A save sends rows with the version each was based on; a row another device saved first comes
  back as a conflict. No SQL schema: the storage's key-value calls are enough and keep the tests in
  memory.
- **Clients:** a three-way merge per row with the row as last synced (kept per row on the device),
  per field for items, tags as a set and highlights per highlight, the same rules as the document's
  merge (this device wins a field both changed; an edit beats a removal). Rows are compared by what
  they mean, not their text, so Android and the web app can't send each other the same row forever.
- **Read marks** became rows too, so they no longer need the document; old ones (120 days) are
  removed from the account by the first device that notices, which never makes an article unread.
  Marks of articles a device doesn't have yet live in `Library.readElsewhere`.
- **No article text** is ever sent (tested): it stays on each device, which downloads or extracts it
  again. That keeps the account inside Cloudflare's free plan.
- Rejected: last-write-wins per row with timestamps (clocks differ, and one side's change to another
  field would be lost), and SQL tables in the Durable Object (more code, and harder to test).

## 2026-10-19: The library moves into a database (Room), one item per link (#106)

Reader grows into one app for feeds, a reading list and bookmarks (#113). Notes, highlights and
whole libraries of saved articles don't fit one DataStore value, and the same link could exist
twice (a feed article and a saved article). So:

- **Room** (`androidx.room`, with its KSP compiler, `com.google.devtools.ksp`) keeps the library in
  SQLite (`library.db`), approved by the owner on #106. It replaces the DataStore rule for the
  library only; settings and the Instapaper sign-in stay in DataStore. Each change writes only what
  changed (`LibraryChanges`). The whole library is still held in memory as one `Library` value,
  so the screens and the merge logic stay plain Kotlin. Room 2.8 (stable, widely documented) was
  chosen over Room 3 (new package names, little documentation yet). Its schema is exported to
  `app/schemas/` for later migrations.
- **One item per link** (`data/Items.kt`, `LibraryItems.kt`): an item has independent memberships
  (reading list: to read or archived; Bookmarks with one flat collection), a liked flag, tags, a
  note and highlights. Links that match after normalising (`ItemKeys`, the rule saved articles used
  to find their feed article) are one item; saving a feed article makes the item from it and leaves
  the article where it is. A feed article that is only in its feed has no item.
- **Texts per source** (`item_texts`): a version of an item's text records where it came from
  (feed, Reader's own extraction, a service) and stays on the device.
- **Migration:** on first start the old `library.txt` and Saved without an account (`saved`) move
  into the database once (`SavedItems.migrate`, tested on a sample), and the old files are deleted.
  Saved without an account now runs on the reading list's items (`ItemsSavedStorage`).
- **Web app:** the same model (`web/src/lib/items.ts`) in IndexedDB: feeds and folders in the
  key-value store, one store for articles and one for items, each change writing only what changed
  (`libraryStore.ts`). The old single `library` value and `saved` move in once.
- Rejected: Android's own `SQLiteOpenHelper` without a library (more hand-written code for the
  same result, and the owner approved Room), and keeping the text format with one file per item
  (no transactions, slow searches).


## 2026-10-18: Links open in a custom tab with a Save button (#95)

The owner asked for Chrome Custom Tabs with a Save button that opens the save sheet. Open in
browser and links in articles now open in a custom tab of the default browser, on top of Reader,
with Save as the toolbar's action button; it saves the page shown, like a share (#97). This adds
one library, AndroidX Browser (`androidx.browser`), which the owner approved: it is Google's
official way to open custom tabs and is small. Tapping Save opens a see-through screen over the
tab with the save sheet, so you stay on the page (the owner asked for this over first returning to
Reader, whose `singleTask` main screen would close the tab). Rejected: building the custom tab
intent by hand without the library (the extras are undocumented outside it and could break).

## 2026-10-18: View settings stay on each device (#96)

The owner asked that view settings stop syncing: people often want a different view on a
different device (small cards on the phone, large ones on a tablet, sepia in one place only). The
sync file now carries only the settings about what the app does, Load full articles and Mark as
read on scroll. The theme, dynamic colour, the reader's font, size, spacing, page colour and
automatic dark page, Show images and the list view stay where they were set. Their old names are
dropped from the file and ignored when an older version still writes them, so an old phone can't
overwrite a new one's view. Rejected: a switch to choose between synced and per-device view (more
settings for a case nobody asked for). Show images went with the view settings because it is
mostly chosen per device (to save data on a phone).

## 2026-10-18: The web app has the same features as Android

The owner asked for feature parity between the web app and Android, and for a look back at what
earlier changes had missed. Search (#90), Saved without an account and the copy on sign-in (#89)
came with their issues. The review found four more gaps, all on the web, now closed:
**Show images** and the list **View** (Small, Medium, Large) are web settings too, synced under the
names Android already used; the sidebar opens the section you're in and folds
the other (#69); and a narrow window gets the **Feeds | Saved** navigation bar at the bottom (#70).
Left as they are because they belong to one platform: Save and Share targets and dynamic colour
(Android apps and wallpaper), swipe to toggle read (touch), keyboard shortcuts (web), and the folder
tabs under the title (the web sidebar lists the same places, and a wide window has room for it).

## 2026-10-18: Unread and All as two chips

The owner found the single **Unread** chip (#77) not Material 3 compliant and not friendly (#92):
off, it didn't say what was shown, and tapping a selected chip to see everything isn't obvious.
They offered two chips or a filter icon in the top bar. Now **Unread** and **All** are two filter
chips used as one choice (Material 3 allows filter chips for a single selection), the chosen one
with a check, in the same chip row; the web app has the same instead of its Unread | All toggle.
This replaces the 2026-10-18 entry that rejected two chips. Rejected: a filter icon in the top bar
(the state can't be read at a glance and needs a menu, as the earlier icon showed) and segmented
buttons (Material reserves them for switching views or options, and they were dropped in #68 for
being too big there).

## 2026-10-18: Saved articles show like the feed article they came from

The owner found that saved cards looked different from feed cards (no website icon) and asked that
saved articles use the same engine as feeds, signed in or not (#87). A saved article is now matched
with the feed article of the same address (ignoring http(s), www., a trailing slash and the #part)
while the library still has it: its card and reader use that article's text, author and picture,
under its feed's name and icon, so the reader's "feed text or full article" works exactly as for a
feed. Without a match, the card shows Instapaper's description and picture (Android now reads the
picture too, like the web), under a feed of the same website if the user follows one, else the
website with its `/favicon.ico`. Rejected: storing a copy of the feed text with each saved article
(more data on the device and in Instapaper's description, while the library already has it), and
Instapaper's own parsed text as the "short" version (its API needs a separate paid key for accounts
other than the owner's; see `instapaper.md`).

## 2026-10-18: Search results in one list, ranked, with chips; search on the web too

The owner asked to mix feed and saved results, ranked by relevance and labelled, with filter chips
for the kind of content (#83), and for the web app to match Android. Search now shows one list
(`MixedSearch`, web `search.ts`): the whole query in the title scores highest, each word of it in
the title next, matches only in the text last; equal scores go newest first. A small label on each
row says Feed or Saved, and filter chips (All, Feeds, Saved) sit above the results, where Material 3
puts chips that filter a list. The web app had no search; it now has the same one, opened with the
magnifier in both lists or the / key, in place of the list. Rejected: a full-text relevance score
(word counts, TF-IDF): saved articles only have a title and description, so their text can't be
weighed against a feed article's whole text; titles compare fairly. Sections per kind (the old
layout) are what the owner asked to replace.

## 2026-10-18: Signing in copies the locally saved articles to Instapaper (and Saved on the web)

The owner found that articles saved without an account seemed gone after signing in (#88), and asked
for the same on the web. Signing in now copies them to Instapaper with their like, tags and archive
(`LocalSaved.uploadTo`, Android and web), oldest first so the order stays, and removes each from the
device once it is in the account. That answers the earlier worries: an article is only removed after
Instapaper has it, so a copy that stops halfway goes on later without duplicates, and the rate limit
only slows a big copy down. The web app got the same Saved without an account (`localSaved.ts`, in
IndexedDB) so both work alike. Rejected: keeping a local copy after the upload (two places to change
the same article), and asking before copying (the owner asked for it to happen).

## 2026-10-18: Saved works without an Instapaper account

The owner asked for the Instapaper part of the app to work without an account (#78). It is now
called **Saved**, and its first list (Instapaper's Unread) is **Saved** too, so it isn't confused with
unread feed articles. Signed out, the same screen runs on `LocalSaved`, a second implementation of
`InstapaperService` that keeps the saved articles on the phone in a DataStore file (`datastore/saved`,
JSON written with the sync's own `Json`), so the lists, Search, the reader's buttons and the sheet
after Save need no second code path. Without an account there were no folders (until #119 added them): Instapaper's folders
are its own, and the owner asked for everything except folders. Save's default became **Saved, in
this app** instead of the Instapaper app; a chosen app still works. The sheet after Save now also
offers Like and Archive. Local articles aren't synced and aren't copied to Instapaper on sign-in.
Rejected: Room or a database (DataStore is the rule and the list is small), local folders (asked
not to), and uploading local articles to Instapaper on sign-in (many calls against a rate-limited
API, and duplicates if it fails halfway; can come later if wanted).

## 2026-10-18: The Unread chip in a chip row, not in the top bar

The owner found the Unread chip at the end of the top app bar a misuse of chips. Material 3's top
app bar holds icon buttons; chips that filter a collection sit in a row of their own above it,
which is how the tag chips on Instapaper's Tags tab already work. The **Unread** filter chip now has
its own row under the folder tabs, inside the header that slides away on scroll. Rejected: an
icon button in the top bar (its state isn't readable at a glance, as an earlier version showed) and
Unread | All as two chips (two chips for one on/off choice).

## 2026-10-18: Search everywhere: feeds and Instapaper

The owner asked for search to be global, and whether its button could go in the navigation bar.
Search now looks through the saved Instapaper articles too, and the magnifier is in the top bar of
both destinations. Instapaper's API has no search, so `SavedSearch` reads the saved articles when
Search opens (Unread, each folder, then Archive; the newest 100 of each, 25 per call) and searches
their title, website, description and tags on the phone; the full text isn't available from
Instapaper. If Instapaper stops answering part way (rate limit, offline), the results say some saved
articles weren't searched. The search button stays in the top bar: Material 3 keeps the navigation
bar for places, search acts on the content of both, and signed out there is no bar at all.
Rejected: a Search destination in the bar (a third place for one action, missing when signed out),
reading every saved article (a long Archive would take dozens of calls and run into Instapaper's
rate limit) and searching only the Instapaper list on screen (not global).

## 2026-10-18: Outlined icons for Instapaper too

The owner noticed Instapaper's icons (Unread, Liked, Archive, folders, tags) were filled while the
feed folders' icons are outlined. Those icons were older filled Material Icons; they now use the
same Material Symbols (outlined, rounded) as the folder icons. Filled stays only where it shows a
state, as Material 3 does: the selected navigation destination and the Like heart once liked.

## 2026-10-18: Search through the feeds' articles

The owner asked whether a search through feeds and saved articles was possible. Feed articles are
all on the phone, so Search searches them there (`ArticleSearch`, unit tested): title, text, feed
name and author, every word must appear, case and accents ignored, title matches first, at most
200 results. It is an action in the Feeds top bar that opens its own page, as in Material's
search pattern; the text of every article is prepared off the main thread when the page opens.
Saved Instapaper articles are not searched: Instapaper's API has no documented search, and
loading every saved article page by page to search it locally would be slow and run into
Instapaper's rate limit. Rejected: Search as a third navigation destination (it acts on content,
not a place of its own), searching only titles (the text is what people remember) and a search
index stored on the phone (the articles are few enough to search in memory).

## 2026-10-18: The navigation bar is back, next to the drawer

The bar (Feeds | Instapaper) was removed earlier today because the drawer changed with the
destination shown. The owner missed it and asked for it back. Both problems are solved together
now: the bar (rail on wide windows) switches between the two destinations as Material 3
recommends for two to five, and the drawer keeps both sections everywhere, with the one you're in
open and the other folded shut. The bar only shows when signed in to Instapaper (one destination
needs no bar). Global search, also asked in the same issue, is a separate change. Rejected: a bar
with a third Search destination (search acts on the content of both, so it belongs in the top bar)
and dropping the drawer (single feeds, Manage and Settings need a place).

## 2026-10-18: Unread | All as one filter chip in the top bar

The connected Unread | All buttons had a row of their own under the title and tabs and drew the
eye more than the articles. The owner asked for chips. Material 3's filter chip is the component
for a filter that is on or off, so Unread | All is now one **Unread** chip at the end of the top
app bar: selected (with a check) for unread only, unselected for all. The row is gone, so the list
starts higher. The web app keeps its Unread | All switch (the issue was about Android). Rejected:
two chips (Unread and All) in their own row (still a row, and two chips for one on/off choice) and
an icon button (its state isn't readable at a glance, as an earlier version showed).



## 2026-10-18: Manage icons: the feed symbol and a bookmark, each with a pen

Manage feeds had a generic icon (lines with a pen on Android, sliders on the web) and Manage
Instapaper a bookmark, so the two didn't read as a pair and the bookmark looked like a list rather
than a page to manage. The owner asked for a feed with a pen and a bookmark with a pen. Neither
Material Symbols nor Lucide has them, so each is drawn from the set's own feed or bookmark shape
(made smaller) and the pen that the set uses in its other "manage" icons (`bookmark_manager`,
`user-pen`). Rejected: `bookmark_manager` (a folder, not a bookmark) and a pen on its own (doesn't
say what it manages).

## 2026-10-18: The drawer opens the section you're in

With Feeds and Instapaper both open, the ☰ drawer grew long (every folder and feed, then every
Instapaper folder and tag). The owner asked for the sections to fold by themselves depending on
where you are. The section being shown is now open and the other folded shut; moving to the other
section turns it round. Folding by hand still works and lasts until you move. The drawer keeps
both sections, so it still looks the same everywhere. Rejected: remembering the folds (the drawer
would stay long once both were opened) and dropping the other section (the drawer would change
with the destination again).

## 2026-10-18: Unread | All is no longer synced

Unread | All was a synced setting, so switching it on one device switched the list on every other
device as soon as it synced. The owner pointed out that it is navigation (where you are in the
list), not a setting. It is still remembered on each device, but no longer in the sync document;
older documents' `unreadOnly` is dropped on the next sync. Rejected: not remembering it at all (the
list would reset to Unread every time the app starts).

## 2026-10-18: Sync through a Reader account, signed in with Instapaper

Google Drive sync was unreliable: read marks went missing and arrived late. Three causes: Drive has
no "save only if unchanged", so two devices saving at once lost one's changes; a change made while
a sync ran (the web app) was overwritten or waited a minute; and other devices only looked every
minute (and the web app's Google sign-in lasted an hour). The owner chose an account instead, with
Instapaper as the first way to sign in, so there is no Reader password system to build.

- **Identity:** the feed loader already swaps Instapaper's sign-in code, and Instapaper's answer
  names the user. At that moment it gives the device a key to that user's account. The account id is
  an HMAC of the Instapaper user id, so other sign-in methods can map to an account later.
- **Storage:** one Cloudflare Durable Object (SQLite-backed, on the free plan) per account. It runs
  one request at a time, so a save carries the version it was based on and is refused (`409`) when
  another device saved first; the device merges again. It also holds a WebSocket per open device
  (hibernating, so quiet connections cost nothing) and tells them at once when the document changes:
  changes now arrive within a second or two. Nothing to create in the dashboard; only the secret.
  Rejected: D1 (works, but needs a database created by hand and has no live connection), Workers KV
  (changes take up to a minute to reach other locations, so syncs would fight) and polling every few
  seconds (costs requests all day).
- **Encryption:** server-side, AES-256-GCM with a key per account derived from the `ACCOUNT_KEY`
  secret, on top of Cloudflare's own. This is what comparable apps do. End-to-end encryption
  (a passphrase on every device, lost passphrase means lost data) was judged too much for a feed
  list and read marks, also later.
- **Same format and merge:** the document is the sync file of `docs/sync.md` and the three-way merge
  is unchanged; only where it lives changed. The Instapaper token is no longer in it: each device
  signs in itself.
- **Drive removed** (no users yet), with Google Sign-In (`play-services-auth`) and the Google Cloud
  setup. Also fixed: a change made during a sync is synced right after it, and the web app merges
  with the library as it is at that moment. A change syncs after one second instead of two.
- **Privacy:** Reader now keeps data on the owner's server; `PRIVACY.md` says what, how and how to
  delete it (Settings › Account › Delete synced data).

## 2026-10-18: One navigation drawer with Feeds and Instapaper as sections

The navigation bar (Feeds | Instapaper) made the ☰ drawer change with the destination shown, while
the drawer is the app's top-level navigation and should stay the same (owner's review). The bar and
rail are gone; the drawer (and the web sidebar) shows Feeds and Instapaper as sections whose header
folds them open and shut. Managing is split and named after what it manages: Manage feeds ("Feeds and
folders") and Manage Instapaper ("Instapaper folders and tags"), both in the drawer. Settings now
follows the shared order: Account first (sign-in and sync together), then the app's own sections;
the Feeds row in Settings is gone, as managing lives in the drawer. Rejected: keeping the bar and a
fixed drawer (two navigations for the same places).

## 2026-10-17: A new folder while moving an article

Moving a saved article to a folder only offered the folders that already existed; making one meant going to Manage feeds and folders first. Move to folder and the sheet after Save now end with **New folder**: a tap shows a field for the name, and adding makes the folder in Instapaper and moves the article into it in one step. Rejected: a separate dialog for the name (one more step) and creating the folder without moving the article (the point is to file it).

## 2026-10-17: Feeds and Instapaper as two destinations

Instapaper sat in the ☰ drawer under the feeds, with its own title but no tabs, so the two felt neither clearly apart nor alike. The owner asked for better separation and cohesion, Instapaper's lists treated like feed folders, and whether a navigation bar was needed. Material 3 puts two to five top-level destinations in a navigation bar and peer views of one destination in tabs, so: signed in to Instapaper, a navigation bar has Feeds and Instapaper (a navigation rail on windows 600dp and wider); each has tabs for its peer views (Feeds: All and the folders; Instapaper: Unread, Liked, Archive, its folders and Tags, with chips for the tag); and the ☰ drawer lists only the shown destination's places, as Gmail keeps its labels in the drawer next to its bottom bar. The web app's sidebar gets a Feeds | Instapaper switch and shows one destination's places at a time. Rejected: a tab per tag (tags come and go, and a long row of tags hides the folders), dropping the drawer (single feeds and Manage need a place), and a bar without Instapaper (one destination needs no bar).

## 2026-10-17: File an article in Instapaper right after saving it

The owner wanted to tag a feed article or put it in a folder as soon as it is saved, instead of finding it again under Instapaper. Save now opens a sheet (Android) or dialog (web) titled "Saved to Instapaper" with the folders as chips (Unread selected) and the same tag editor as a saved article's Tags. Every tap applies at once, like the reader's own Move and Tags; Done or closing leaves the rest as it is. Rejected: a snackbar with an action (it disappears before you decide) and asking before saving (saving should stay one tap).

## 2026-10-17: The real logo in the drawer and the web app

The ☰ drawer and the web app's sidebar opened with a generated mark (a plain feed symbol on a tinted circle), and the web app's favicon drew the feed symbol with other proportions than the Android icon. The owner asked for the real logo everywhere: both headers now show the launcher icon (its foreground on white, in a circle), and the web favicon uses the same symbol and gradient as the Android icon. Rejected: tinting the logo with the theme (it would no longer be the logo).

## 2026-10-17: One way to reorder, shared with the sibling apps

A design review of the sibling apps found four ways to put a list in order. The rule is now shared (`DESIGN.md`, "Reordering"): a drag handle at the end of each row, from `ui/theme/Reorder.kt`, which every app copies. Reader's feeds and folders already worked that way; the Instapaper folders on Manage feeds and folders had up and down arrows and now have the same handle (screen readers keep Move up and Move down). The article lists and Manage feeds and folders keep a readable width (640dp, `Layout.kt`) on tablets and in landscape, as the shared rule asks. Rejected: keeping the arrows for the short Instapaper list (two ways to do the same thing on one page).

## 2026-10-17: Instapaper lives in the reader's own navigation

The first Instapaper version had its own screen (Android) and dialog (web). The owner wanted the
saved articles, folders and tags next to the feeds and folders, opening like feed articles, and
managed like the feeds. Now the ☰ menu / sidebar has an Instapaper section (Unread, Liked, Archive,
folders, tags), its lists use the article list's rows, a saved article opens in the reader (which
loads the full article from the website, as for feeds) with like, archive, move, tags and delete,
and Manage feeds and folders manages the Instapaper folders and tags. The owner chose: saved
articles have no read state (archiving stays a deliberate action) and stay out of All articles.
The list logic is shared in spirit (`InstapaperLists` on Android, `instapaperLibrary.ts` on the web,
both tested): an article leaves a list when a change means it no longer belongs there (archived
out of Unread, moved out of Archive), and the reader keeps it open. Rejected: marking a saved
article read (or archiving it) by opening it, and mixing saved articles into All articles.
## 2026-10-17: Settings and the Instapaper sign-in are synced

The owner asked for settings, including everything Instapaper, to sync through Google Drive. The
sync file (version 2) has a `settings` table of shared names and text values (`docs/sync.md`),
merged per setting like the rest: a change on one side wins, this device wins a clash. A device
whose base has no settings yet takes the file's, so a new phone's defaults don't overwrite the
user's choices. Each app keeps names it doesn't know, so Android-only settings survive the web app.
The Instapaper token is in the table too, so signing in once signs in every device and signing
out signs out everywhere; it stays out of Android's backups as before, and only reaches devices
through the user's own Drive folder that only Reader can open (`PRIVACY.md` says so). The
version goes to 2 so that an older app asks to be updated instead of writing the file back
without settings. Not synced: the Save and Share targets (they name apps on one phone).
Rejected: per-setting timestamps (the base already tells which side changed), syncing the
Save/Share targets, and keeping the token out of the file (the owner wants one sign-in).

## 2026-10-17: Web: Google's popup only on a click

The web app showed Google's sign-in popup every time it was opened. Its Drive sign-in was kept only
for the open tab, and every sync without one asked Google for a new one; in a browser Google can
only hand one out through its popup, even when nothing has to be approved. The sign-in (about an
hour) is now kept in the browser's local storage, and only a click on Connect asks Google; a sync
without a valid sign-in stops with "needs your permission again". Rejected: a long-lived sign-in
kept by the feed loader Worker (no popup at all, but our server would hold a key to the user's
Drive app folder and need a client secret).

## 2026-10-17: Sign in with Instapaper (OAuth 2), through the feed loader Worker

Reader can now sign in to the user's Instapaper account (API v2): Save goes straight to the account, and a screen manages Unread, Archive, Liked, folders and tags. Details: `docs/instapaper.md`. Choices:

- **The Worker swaps the code for a token.** Instapaper's token request needs the client secret, and its docs offer no PKCE. A secret can't live in a public repository, an APK or a web bundle, so `worker/src/instapaper.ts` holds it as a Worker secret (`POST /instapaper/token`). This bends two earlier rules: the Android app now uses the Worker (for that swap only), and "no Reader server" now has one exception, a stateless pass-through that stores and logs nothing. `PRIVACY.md` says so. The redirect address is fixed in the Worker, so the endpoint can't be pointed at another address.
- **The web app's Instapaper calls go through the Worker** because Instapaper's API sends no CORS headers (checked with a preflight request). The Worker forwards an allowlist of calls only (bookmarks, folders, tags), refuses anything else, and never follows a redirect with the token. Android calls Instapaper directly.
- **One callback address for both apps**, an Android App Link on the web app's own address, because Instapaper accepts only http(s) callbacks (no custom URL scheme). The cost: debug builds can't verify the link (different key and package), so sign-in is tried with a released build.
- **The token never expires**, so there is no refresh code. It is stored in its own DataStore file on Android, excluded from backups and phone transfers, and in the browser's IndexedDB on the web. It is never in the Drive sync file.
- **Folders and tags** were added after checking Instapaper's TypeScript SDK and the documentation: the published OpenAPI file listed bookmarks only, but the documentation and the live API have folders and tags.

Rejected: a personal access token pasted into the app (works only for the account that owns the Instapaper application, so useless to other people), a custom URL scheme callback (not allowed), the client secret in the app or the web bundle (extractable from a public build), an encrypted token store (needs a new dependency; the backup exclusion keeps the token on the phone), Instapaper's parsed article text (needs a separate Instaparser key for other accounts; Reader's own full-article reader opens the saved link instead), and highlights (a separate feature).

## 2026-10-17: A refresh always syncs

A refresh skipped its sync when the last one was under ten seconds ago. Pulling down right after switching devices could therefore miss a read mark made a few seconds earlier elsewhere, and the next automatic sync was up to a minute away. Refresh now always syncs first (Android `SyncManager.sync()`, web `AppStore.sync()`); coming back to the app and the one-minute timer keep the ten-second gap so they don't sync twice in a row. Google Drive is not the limit here: reading the file by its id returns what the last upload wrote. Rejected: dropping the gap everywhere (starting the app would sync twice), remembering the Drive file id between syncs (two devices that create the file at the same moment would each keep their own copy; looking it up every time makes them settle on the newest one), and checking the file's version every few seconds (more code and Drive traffic than a refresh that always syncs needs; it can be added if waiting for the minute timer still feels too slow).

## 2026-10-17: The web app is not paused

The pause recorded further down ("The web app and its feed loader are paused") no longer applies. The owner is working on the web app again, and the repository already reflects it: the `web` job in
`.github/workflows/ci.yml` runs the tests, lint and build of the web app and the feed loader on every change, and Dependabot checks the npm packages in `web/` and `worker/` again. The README no longer calls the web app paused. The Cloudflare builds of `rss-reader-web` and `rss-reader-worker` are set up in the Cloudflare dashboard, not in this repository, so this entry says nothing about them. The sync format (`docs/sync.md`) is shared with Android and did not change. Rejected: deleting the earlier entry (the log keeps its history, and this entry replaces it).

## 2026-10-17: Sync every minute while the app is on screen

The owner asked for sync as often as Google allows, at least on marking as read and on refresh.
Marking as read already synced (five seconds after the last change), but other devices' changes
only came in when the app started. Now both apps sync when they come on screen and every minute
while they stay there, on refresh (before downloading, so articles read elsewhere arrive read),
and two seconds after a change. A sync within the last ten seconds is not repeated. One sync is
two or three Drive requests, so a minute is far below Google's per-user limits. Rejected: syncing
on every single read mark (the two-second wait already sends a row of them as one), syncing in
the background (no background work, see `CLAUDE.md`), and Drive's push notifications (they need a
server to receive them).
## 2026-10-17: The web app's addresses are built in

The feed loader's address and the Google client ID were settings in the web app (filled in from
the build), with a Connections section that only made sense to a developer. Both are public, so
they are now constants in `web/src/lib/config.ts` and Settings no longer shows them. Rejected:
keeping them as build variables (`.env.production`), which added a step without a benefit for a
single published site.

## 2026-10-17: New name "Reader" and package `be.wimvandonck.reader`

The owner renamed the app to Reader and asked for a package name under `be.wimvandonck`. The
application ID and the Kotlin package are now `be.wimvandonck.reader` (debug builds
`be.wimvandonck.reader.debug`, "Reader Dev"). Android treats a new application ID as a different
app: phones with the old RSS Reader (up to v0.3.4) don't update to it but install Reader next to
it, with an empty library (sync or OPML brings the feeds over). Google Cloud's Android client
had to be made again for the new package anyway. The repository, the APK file names
(`rss-reader-…`), the sync file (`rss-reader-sync.json`) and the Cloudflare names stay, so
nothing that stores or links them breaks. The shared design table in `DESIGN.md` still says
"RSS Reader" until it changes in the template. Rejected: keeping the old ID (the owner's
explicit choice) and moving data over from the old app (it can't read another app's storage).

## 2026-10-17: Sync says when Google doesn't know this build

Drive sync didn't work and the app said "Couldn't reach Google Drive", whatever Google's reason.
Google refuses an app whose package name and signing key it doesn't know (DEVELOPER_ERROR), which
is the usual setup problem; that now shows as its own message. The release workflow prints the
signing key's SHA-1 on its summary page, so the owner can register it without `keytool`
(`docs/sync.md`, step by step). Debug APKs from CI get a new key on every run and can't be
registered. Rejected: signing debug builds with the release key, or a fixed debug key as a
secret (changes the signing setup, for test builds only).

## 2026-10-17: No Instapaper account sign-in

The owner didn't ask for the Instapaper login of 2026-10-16 and wants saving tackled later, so
it is removed again (Android and web): Save goes to the chosen app (Instapaper by default) or the
share menu, as before. On the web there is no Save button for now; Share stays.

## 2026-10-17: The web app is back and finished

The owner asked to finish the web app, so the pause below is lifted the same day: its CI job and
its npm updates run again. It now matches the Android app: teasers that link to their article
load the full article (same rule and threshold), the reader switches between feed text and full
article and keeps the last 100 full articles in IndexedDB, Aa text settings (font, size,
spacing, background, Auto dark), mark as read on scroll, and embedded videos and posts as a card
that opens them in a new tab. The cards replace the YouTube and Vimeo players that played inside
the page, so the web app no longer loads those players at all. The Google client ID is built in
(`web/.env.production`). The Cloudflare builds were never switched off, as the pause entry
assumed: they fail within a second on every push, with no log on GitHub, which points to their
settings in the Cloudflare dashboard, not to the code (the same build passes in CI). Rejected:
playing videos inside the page (third-party players, unlike Android), a Show images setting and
list sizes (the owner doesn't want them on the web).

## 2026-10-17: Readability4J finds the article (new dependencies: Readability4J, jsoup)

The owner asked for a proven reader-view engine instead of more rules of our own, and for no
rules for single publications. Full articles now go through Readability4J
(`net.dankito.readability4j`, Apache 2.0), the Kotlin port of Mozilla's Readability that Firefox
Reader View uses, on a current jsoup (pinned to the latest release; Readability4J itself asks for
an old one). Readability4J's last release is from 2021, but the algorithm is stable and Android
RSS readers such as Feeder and Read You use it. Around it, a few generic steps make up for what
this older Readability lacks: glossary words in buttons inside text are kept, hidden parts are
removed, a `<div>` holding only text becomes a `<p>` (so its links stay inside the paragraph),
look-alike containers that each hold several paragraphs are joined (split articles), and after
extraction the heading that repeats the page title and short byline and comment-link lines are
dropped; the reader shows a caption written twice in a row once. slf4j, which Readability4J logs
through, has no backend in the app, so `proguard-rules.pro` tells R8 not to look for one. Feed
texts that link to their own article ("Read full article") now count as summaries whatever their
length, so feeds that send a teaser of a few paragraphs (Ars Technica) load the full article.
Rejected: running Mozilla's Readability.js in a WebView or JavaScript engine (heavy, and it brings
a browser engine back into reading), Crux (pulls in an older OkHttp and Klaxon for features we
don't use), and keeping our own scorer (each new site needed new rules).

## 2026-10-17: The web app and its feed loader are paused

The owner put the web version on hold. Its code in `web/` and `worker/` stays, so it can be
picked up again, but its CI job (`.github/workflows/ci.yml`) and its npm updates
(`.github/dependabot.yml`) are commented out; removing the `# ` in front of them turns them back
on. The Cloudflare builds of `rss-reader-web` and `rss-reader-worker` are set up in the
Cloudflare dashboard, not in this repository, and are switched off there. Android doesn't use
either, and the sync format (`docs/sync.md`) doesn't change, so the web app still fits when it
resumes. Rejected: deleting the folders (the work would have to be redone).

## 2026-10-17: The extractor finds the whole article on modern news sites

After the reader got its own text layout, full articles still lost most of their paragraphs on
real sites. Tested on saved pages from VRT NWS, Ars Technica, The Verge and 9to5Google:
paragraph scores only counted one and two levels up, so on sites that wrap every paragraph in
boxes of its own (VRT NWS) no container collected the text, and the 3-line summary, or only the
lead, won; sites that split the text over several identical containers with ads in between
(Ars Technica's `post-content`) kept only one of them. Scores now count five levels up (as
Readability does), list items no longer score as paragraphs (Readability doesn't either), the
extractor moves up from a small winning box to the nearest ancestor holding at least three times
as much paragraph text, and joins look-alike containers. Glossary words that VRT NWS puts in
buttons inside a paragraph are kept. Cached full articles get a version in their file name, so
articles cut off by the old extractor are loaded again after an update. Rejected: a library such
as jsoup or Readability4J (a new dependency; the problem was the scoring, not the HTML reading).

## 2026-10-17: The reader lays out article text itself

Full articles sometimes lost their paragraphs and layout: Compose's HTML conversion puts a single
line break between paragraphs and drops list bullets and quote bars, and the extractor turned a
`<div>` per paragraph into a line break. Article text is now split into blocks first
(`feed/TextLayout.kt`: paragraphs, headings, lists with bullets, numbers and nesting, quotes,
code), and the reader spaces and draws each block itself; only the text inside a block still
goes through Compose's HTML conversion (links, bold, italics). The extractor makes a paragraph of
every container that holds only text, and matches "unlikely" class names as whole name parts, so
utility classes like `overflow-hidden` no longer remove the article. Rejected: a WebView (heavy,
and it would bring the site's scripts back), an HTML library such as jsoup (a new dependency for
what a small splitter does).

## 2026-10-17: Less help text

The owner found the app over-explanatory; the shared rule is in `DESIGN.md` ("Wording"): help text only says what the screen can't. Removed: the paragraph above the Save and Share rows, the sentence above the address field in Add feed, and the subtitle under "Mark as read on scroll". Shortened: the feeds-screen hints (only the non-obvious "drag a feed onto a folder" stays), the OPML hint, the sync, images, auto-dark and Instapaper summaries, the caught-up text and the Dynamic color line. The web app's sync summary matches. Kept: consequences, privacy notes and setup steps. Rejected: removing every hint (the remaining ones say something the screen doesn't).

## 2026-10-16: Shared empty state on Android, same layout on the web

A Material 3 audit of the sibling apps found empty screens drawn differently in each app. Here the empty list's feed icon never showed: it was 48dp tall with 48dp of padding inside, which left nothing to draw. Android now uses the shared `ui/theme/EmptyState.kt` (`DESIGN.md`, "Empty and setup states"): an icon in a circle that fits the state (feed, all read, article), a title, one sentence and buttons. The web app's `Empty` got the same icon circle, and its empty list without the Unread filter now says "No articles yet" instead of "All caught up". "Show all articles" became "Show all", as on the web. Rejected: fixing only the padding (the three empty states would still differ between the apps).

## 2026-10-16: Instapaper: Save to the account with the Simple API

Save can now go straight to the user's Instapaper account instead of the Instapaper app, with
Instapaper's free Simple API (email or username and password, over https; no app key). Android
checks the account when signing in and reports whether each save worked; the account lives in
its own DataStore file that Android backup leaves out. The web app posts to the same API from
the browser; Instapaper's API doesn't allow web pages to read its answer, so the web app says
"Sent" rather than "Saved". The Full API (list, archive, star, folders, highlights) needs an
OAuth consumer key that Instapaper hands out after a manual review, and its bookmark methods
have historically been limited to paying subscribers, so it is not built until the owner has a
key. Rejected: routing the web app's saves through the feed loader (the password would pass our
Worker).

## 2026-10-16: The web app: static React site, feeds through the Worker

The web app is a static site in `web/` (React, TypeScript, Vite, Tailwind with shadcn-style
parts on Radix primitives, as chosen for the web in `DESIGN.md`), hosted on Cloudflare Pages
next to the feed loader Worker in `worker/`. It keeps its library in IndexedDB and syncs through
the same Drive file and three-way merge as Android (ported to TypeScript, with tests that read
the Android app's file), signing in with Google Identity Services. Its feed parser gives the same
article ids as Android so read marks match. Full articles use Mozilla's Readability; article HTML
is cleaned with DOMPurify; folder icons are the Android app's Material Symbols file, copied in at
build time. The Worker passes only feeds and pages (no pictures), only for the web app's origin,
logs nothing and is rate limited. Both live in this repository so the shared sync format changes
in one place, with their own CI job. Rejected: Compose Multiplatform for the web (Android look on
the web), a service worker for offline use (more moving parts; the app opens offline from its
stored library anyway once loaded), our own server.

## 2026-10-16: Sync through the user's own Google Drive, three-way merge, no clocks

Feeds, folders and read marks sync through one JSON file in Google Drive's hidden app folder
(`drive.appdata`: the app sees only its own file, and Google requires no paid security audit for
it). Sign-in uses Google's authorization API from `play-services-auth` (new dependency, approved
by the owner); Drive is called over the existing OkHttp, and the JSON is read and written by a
small plain-Kotlin reader so the logic stays unit-testable without a new library. Changes are
found by comparing with the file as last synced (three-way merge), not by timestamps, so wrong
clocks on a device can't lose changes. Article texts are not synced (each device downloads its
feeds), which keeps the file small. Sync runs only while the app is open. Rejected: Firebase or
our own server (an account and a server to run), kotlinx.serialization (a compiler plugin for one
small file), timestamp "last writer wins" (device clocks differ), full Drive access (sees all
files, needs an audit).

## 2026-10-16: Web app loads feeds through a small Cloudflare Worker; sync is pluggable

Browsers block a web page from loading most feeds and article pages (CORS), so the planned web
app loads them through a small, stateless Cloudflare Worker: it only fetches the feed or page it
is asked for and passes it back, keeps nothing, logs nothing, and refuses anything that isn't a
GET for a feed or page. Pictures load directly (browsers don't block those). It stays small
enough for anyone to run their own copy, and the web app can be pointed at another copy. The
Android app, including a Play Store release, keeps loading feeds directly and never uses it. Sync goes through one interface with Google Drive
(the hidden app folder) as the first provider and room for others (for example Dropbox,
OneDrive or WebDAV/Nextcloud) using the same file format. Rejected: a Google Apps Script proxy
(ties feed loading to Google while sync should not depend on one provider), a browser extension
(too much for the goal, and little use on phones), public CORS proxies and RSS-to-JSON services
(a stranger sees every feed, limits, unreliable).

## 2026-10-16: One brand, each platform's own design language

For a planned web app and a possible iOS app, the owner chose a shared brand with a native design
language per platform, rather than Material 3 everywhere: Material on Android, Apple's guidelines
on iOS, and shadcn/ui (Radix with Tailwind) on the web, themed with the app's seed colour. Shared
code is the logic, not the screens. Rejected: Material 3 on every platform (an iOS app would feel
like a port), Compose Multiplatform for the web UI (it brings the Android look along), Fluent 2
(looks like Microsoft), Carbon and Primer (made for business and developer tools). Recorded in
this app's section of `DESIGN.md`; if other apps from the template get web versions, it moves
to the template's shared rules.

## 2026-10-16: The home header scrolls away as one block

The owner wanted more room for articles and found the header's scrolling wonky: the medium top
app bar only shrank to a small bar, the tabs and Unread | All never moved, and pulling down at the
top went to pull-to-refresh instead of opening the bar again. The app bar, the folder tabs and
Unread | All now form one header that slides away while scrolling down and comes back as soon as
you scroll up, using Material's own "enter always" scroll behaviour (it also snaps the header
fully in or out when you let go). A new section starts with the header in view. The order stays
app bar, tabs, filter: Material 3 attaches primary tabs directly under the top app bar, and a
filter for the content sits below them. Rejected: putting Unread | All in the app bar (its labels
don't fit next to a long title) and above the tabs (it would split the tabs from the app bar).

## 2026-10-16: Material 3 audit: settings list, folder tabs, normal-sized Unread | All

An audit against Material 3 (owner's request) changed three things. Settings are one list with
section headers instead of collapsed cards, from the shared `SettingsKit.kt` (see the template's
decision log). The floating folder bar copied Tracker for Simkl's floating navigation pill, but
Material 3 keeps floating bars for actions and uses tabs for peer views of the same content, so
folders are now scrollable tabs under the top app bar (they fit any number of folders, which a
navigation bar doesn't). Unread | All is back to the normal button height and as wide as its
labels; the earlier "more padding" request meant room around it, so it now has 16dp above it.
Rejected: a docked navigation bar for folders (at most five items, and folders aren't app
sections) and filter chips (they would look like extra filters next to Unread | All).

## 2026-10-15: Site icons on a light tile; pens and an empty "No folder" note on the manage page

Many site icons are dark logos on a transparent background, made for light browser tabs, and
vanished in dark mode. They now sit on a fixed light tile in both modes (as Chrome does), which
also gives every icon the same shape. Rejected: tinting or inverting icons (breaks colourful
logos) and per-icon brightness checks (decoding every icon for little gain). The manage page got
a pen button per row (tapping the row still works) and a note under "No folder" when every feed
is in a folder, which also shows where to drop a feed. Asked for by the owner.

## 2026-10-15: Swipe, folder bar and filter polish after using v0.1.1

The owner found the swipe buggy, the Unread | All buttons jumpy and asked whether the folder bar
followed Tracker for Simkl. Changes: the swipe is our own small `draggable` that only slides the
row and springs back (no dismiss state to get stuck in), with a haptic tick at 30%; the drawer
no longer opens by swiping, which fought with swiping rows (it opens with ☰). The folder bar now
is Tracker for Simkl's floating pill (selected tab expands to show its name, spring motion), the
Unread | All halves have fixed equal widths and spring motion, the FAB is icon-only again, and
the list shows loading placeholders until the stored feeds are read, so "No feeds yet" no longer
flashes at start.

## 2026-10-15: Drag and drop on the manage page; renaming feeds

Asked for by the owner. Feeds and folders are reordered and feeds moved between folders by
dragging a handle, built on `LazyColumn` with our own small drag logic (`FeedArrangement`, unit
tested) instead of a reorder library: no new dependency. A folder is dragged on its own with
the other folders collapsed, so it can't land in the middle of another folder's feeds. Screen
reader users get Move up / Move down actions. Feeds can be renamed; the `renamed` flag keeps a
refresh from overwriting the user's name, and clearing the name returns to the feed's own.
Rows open an edit dialog on tap (name, folder, remove) instead of carrying three icon buttons.

## 2026-10-15: Unread | All as a connected button group; no ⋮ menu on the list

The owner asked for Material 3's connected button group for Unread/All (instead of the icon
toggle), and to drop the list's ⋮ menu: refresh is pull-down, and adding feeds belongs on the
Manage feeds and folders page, which is now the drawer's single entry for feeds. Material 3 1.4
(our Compose BOM) doesn't have the component yet, so `ReadFilter` builds it from `Surface`s
following the spec; replace it with Material's `ToggleButton` group once it is stable. The
folder bar's first tab is now "All" with the feed symbol (was "Home").

## 2026-10-15: Navigation review and a livelier list

The owner found the design boring and asked for a navigation and Material 3 review. Changes:
Back inside a folder or feed returns to Home before leaving the app (Material's "back to the
start destination"); snackbars sit above the folder bar instead of on it; Mark all as read is an
extended FAB with its label, shrinking to the icon when scrolled (clearer than an icon alone);
Home has a collapsing medium top app bar; articles are grouped under sticky day headers; the
drawer starts with the app's mark. Kept: the drawer for all destinations plus the folder bar as
a shortcut (the drawer also holds single feeds, Feeds and folders and Settings, which the bar
can't).

## 2026-10-15: Folder icons from the whole Material Symbols set, with search

The owner asked for many more folder icons ("just use an entire library with keyword search").
The app now bundles the Material Symbols set (4 229 icons, outlined rounded style, Apache 2.0)
as one text file in `assets/` (about 0.9 MB in the APK): name, category and SVG path data per
icon, generated from the `@iconify-json/material-symbols` package. Icons are drawn as vectors
from that path data, and the folder dialog searches names, categories and a few related words.
Folders now store the icon's name; the 19 icons of v0.0.3 are mapped to their Material Symbols
names. Rejected: `material-icons-extended` (a new library, the older icon set, and no search
words) and the Material Symbols font (`.woff2` only, which Android can't load, and no names to
search). Trade-off: search words are English.

## 2026-10-15: Feeds and folders get their own page

Managing feeds and folders was two sections in Settings; with folders, the feeds belong under
their folder, which a settings section can't show well. Asked for by the owner. The new page
(☰ › Feeds and folders, and a link at the top of Settings) lists each folder with its feeds,
then the feeds outside folders, with Add feed, New folder and OPML import/export. Settings keeps
only how the app looks and behaves.

## 2026-10-15: Website icons (favicons) from the sites themselves

The owner asked for favicons so sources are recognisable. The app reads the icon a feed's
website announces on its home page (the largest `apple-touch-icon` or `icon`; SVG skipped,
since that would need another Coil module), or else uses `/favicon.ico`, and downloads it with
Coil like other pictures. Looked up once per feed (after a refresh or when added) and stored with
the feed. Rejected: Google's or DuckDuckGo's favicon services (simpler, but they would learn every
site the user follows, a third party `PRIVACY.md` doesn't allow). Feeds without an icon show
their first letter on a tinted square.

## 2026-10-15: Article list views, swipe, filter in the top bar; refresh merged at once

Asked for by the owner after using v0.0.3:
- Three list views (Small without picture, Medium as before, Large card) in a new Settings ›
  Article list section, which also holds Mark as read on scroll.
- Swiping an article either way toggles read (`SwipeToDismissBox`, springs back; also an
  accessibility action).
- The Unread/All chips became one icon toggle in the top bar, with the unread count under the
  title: chips suit more than two options. Settings is only in the drawer, not also in ⋮.
- The folder bar's tabs have a fixed width (icon with name below) so switching no longer makes
  the bar change size and jump.
- A refresh used to merge each feed as soon as it was downloaded, so articles popped up in the
  middle of the list while reading. Now all feeds are downloaded first and merged in one update;
  dates in the future are clamped to now so they can't stick to the top.

## 2026-10-15: Mark as read on scroll, off by default

Asked for by the owner. An article counts as read once it has scrolled off the top of the list
(not merely shown), and stays in the Unread list until it changes, like opened articles, so
scrolling back still finds it. Off by default so nothing is marked read without the user choosing
it.

## 2026-10-15: Folders, a floating folder bar and a Mark all read button

- Feeds can be put in folders, each with a title and an icon from a fixed set of 19 (drawn in the
  app, no icon library). A feed is in at most one folder. Asked for by the owner.
- Once a folder exists, a floating bar at the bottom of the list switches between Home (all feeds)
  and each folder. The bar only shows the selected tab's title, so many folders still fit; it
  scrolls sideways when they don't. Rejected: Material's `NavigationBar` (fixed, at most five
  items, not floating).
- Mark all read moved from the top bar to a floating action button above the bar. It marks only
  the section shown (Home, a folder, or one feed) and offers Undo.
- Storage: folders are a new record type in `library.txt` and feeds got a folder field; older files
  load unchanged. OPML import and export keep the folders (nested outlines), so moving from
  Inoreader or Feedly keeps them too.

## 2026-10-15: Save and Share buttons send to any app; one OPML import

- The Save and Share buttons each have a target in Settings › Save and share: one app, picked
  from the installed apps that accept shared text (the article goes straight there), or the share
  menu. Save defaults to Instapaper (as before; the old setting carries over), Share to the share
  menu. The manifest's `<queries>` now names the share intent instead of the Instapaper package,
  so the list can be shown (package visibility, not a permission).
- "Import from Inoreader" is gone: Import/Export OPML is universal and the hint names Inoreader
  and Feedly as examples. Asked for by the owner.

## 2026-10-15: Pictures with Coil; embeds as cards

The owner asked for article pictures as list thumbnails and reader hero, plus inline pictures and
embedded content. Added **Coil 3 + OkHttp** (the same versions Tracker for Simkl uses) to load
and cache pictures; writing our own loader (downsampling, memory and disk cache) would be more
code and more bugs. The reader stays native: the article is split into text, picture and embed
blocks (`ArticleBlocks`, tested) so the reader's font, size, spacing and sepia/dark page still
apply. Embedded players (YouTube, Vimeo, posts) are cards with a preview that open the content
in its app or the browser. Chosen by the owner over a WebView reader, which would play embeds
inline but run the sites' and players' scripts. Picture addresses are upgraded to https like
feeds; tracking pixels are skipped. Settings › Reader › Show images (on by default) turns
pictures off. Coil's caches live in the cache folder, so they are never backed up.

## 2026-10-15: Icon thicker, with more padding

After using v0.0.1 the owner found the icon too thin and too tight in the launcher shape. The
symbol is now about 44dp wide instead of 50dp (more white space around it) with thicker waves and
dot (owner's pick "T3" from mockups).

## 2026-10-15: Settings screens make room for the keyboard

The app targets Android 15+ and draws edge to edge, so the system no longer shrinks the screen when the keyboard opens. `SettingsScaffold` (shared design kit, same change in every app) now adds the keyboard to its content insets, so a focused text field scrolls into view instead of hiding behind the keyboard. Rejected: opting out of edge-to-edge (not possible for apps targeting Android 15+).

## 2026-10-15: Import from Inoreader through its OPML export

The owner asked for an Inoreader import. It is a guided dialog: open inoreader.com, export the
feeds as OPML there, pick the file here (the existing OPML import). Chosen by the owner over
signing in with Inoreader's API, which would need a registered Inoreader app (client ID and
secret), an OAuth sign-in screen and inoreader.com as a new network destination. Trade-off:
read and starred states are not imported, only the feeds.

## 2026-10-15: Icon without a box

The owner chose the feed symbol on its own, with the blue gradient, over the first design (a
gradient rounded square with a white symbol inside), after comparing mockups, drawn with the proportions of the
classic feed icon the owner supplied (thick waves, round ends) so it stays clear at small sizes. This departs from the shared icon rule in
`DESIGN.md` ("one bold shape with one white detail inside"); recorded under "This app" there, and
the master copy in the template should mention the exception.

## 2026-10-15: Full articles in the reader, Android backup

- **Full articles:** for feeds that only carry a summary (under 1 500 characters of text), the
  reader downloads the article's page and shows just the article, like a browser's reader view.
  On by default (Settings › Reader › Load full articles), switchable per article. Asked for by the
  owner. The extraction (`ArticleExtractor`) is our own small HTML reader with Readability-style
  scoring, in plain Kotlin and unit tested. Rejected: jsoup + Readability4J (two new libraries for
  one feature) and a WebView (runs the site's scripts, ads and trackers). Trade-off: some unusual
  page layouts extract badly; the feed text and "Read on website" stay one tap away.
- **Where full articles live:** files in the app's cache folder (300 most recent), not DataStore.
  They can be downloaded again, Android may clear the cache when space runs low, and the cache is
  never backed up, which keeps backups small.
- **Backup:** Android's built-in Auto Backup and device-to-device transfer (`allowBackup` with
  `data_extraction_rules.xml`) cover the feed list, articles, read state and settings. Nothing in
  the app is secret, so nothing is excluded. Rejected: a key/value `BackupAgent` (more code, and
  Auto Backup already handles DataStore files).

## 2026-10-15: RSS Reader, built from the template

- **Identity:** app name "RSS Reader", application ID `io.github.impca201.rssreader` (the
  `io.github.impca201.*` scheme of Tracker for Simkl and Snoozer). The ID can still change until
  the first release; after that it never may. Debug builds are called "RSS Reader Dev", like the
  sibling apps.
- **Internet permission:** an RSS reader has to download feeds. The app only contacts the
  addresses the user adds (or types), with no account, cookies, analytics or third-party services.
  `PRIVACY.md` says so. Asked for by the owner ("build me a full RSS reader").
- **HTTPS only:** `http://` and `feed://` addresses are upgraded to `https://`, and Android's default
  block on unencrypted traffic stays on. Rejected: allowing cleartext traffic for all hosts, which
  would let anyone on the same Wi-Fi change articles. Revisit if a feed the owner needs has no
  HTTPS.
- **No new libraries beyond the sibling apps':** DataStore (already in the catalog, as `CLAUDE.md`
  asks) and `lifecycle-runtime-compose` (used by Snoozer and Tracker for Simkl, for
  `collectAsStateWithLifecycle`). Downloads use `HttpURLConnection`, feeds and OPML the platform's
  XML parser, article text Compose's `AnnotatedString.fromHtml`. Rejected: OkHttp, Room,
  kotlinx.serialization, Coil and a WebView. They would add size and setup, and a WebView would
  run the sites' scripts.
- **Storage:** the whole library (feeds, articles, read state) is one immutable `Library` value
  stored with DataStore in a small tab-separated text format (`LibraryCodec`, tested). At most 200
  articles per feed and 200 000 characters per article keep it small. Rejected: Room (a database
  and annotation processor for a few thousand rows) and a JSON library (a new dependency).
- **Reader without images:** article HTML is cleaned (scripts, embeds, images, inline colours and
  fonts removed) and shown as styled text, so the reader's font, size, spacing and sepia or dark
  background always apply, and no tracking pixels load. "Read on website" opens the full page.
  Images would need an image library (Coil); left for a later decision.
- **Save sends to Instapaper:** the reader's Save button sends the link with an `ACTION_SEND`
  intent aimed at the Instapaper app (`com.instapaper.android`), the same as choosing Instapaper in
  the share menu. No Instapaper account or API key in this app. Settings › Save for later can
  switch Save to the share menu; without Instapaper installed, the share menu opens. The manifest
  declares Instapaper in `<queries>` (package visibility, not a permission) so Settings can tell
  whether it is installed.
- **Refresh only while the app is used:** once per app start and on pull-to-refresh. Rejected for
  now: background refresh and new-article notifications (WorkManager, a new dependency, and
  battery use).
- **Languages:** English, Dutch, French, German, Spanish and Italian, like the sibling apps (the
  template has only English and Dutch). French, Spanish and Italian plurals include `many`.
- **Theme:** seed `#0091EA` (the icon's middle stop), tonal palette generated the same way as
  Snoozer's (primary from the seed's chroma, neutrals at chroma 4/8, tertiary from the icon's sky
  blue). Dynamic color off by default, per `DESIGN.md`.

## 2026-10-15: Phone testing doesn't block merging

The owner can't review code and can't always test a pull request on the device right away, so pull requests waited for a manual test that adds little over CI. Green CI (unit tests, lint, builds) is now enough to merge; the owner tests afterwards with the steps from the pull request and always before a release, and problems are fixed in a new pull request. Merging to `main` does not reach users: releases stay manual. Rejected: keeping the phone test as a merge gate (pull requests pile up and go stale).

## 2026-10-14: Shared design rules have their master copy in the template

`DESIGN.md` was kept identical in the three sibling apps by hand, with no single place to start a change, and the copies had drifted (Snoozer was missing the Loading section; the Tracker for Simkl icon row was out of date in two apps). The android-app template (`wimvdon/android-app`) now holds the master copy; each app keeps a full copy so Claude sessions can read the rules without opening another repository, and only its "This app" section is its own. Rejected: keeping the file only in the template (sessions in an app would have to fetch it every time) and a shared library for the design kit (a new dependency and more setup than three small apps need). The design kit files (`ui/theme/`) stay in the apps.

## 2026-10-13: Template baseline

- **Language/UI:** Kotlin + Jetpack Compose + Material 3, single `app` module. This is Google's
  recommended, mainstream stack with the most documentation and examples.
- **Tool versions (latest stable on this date):** Gradle 9.8.1, Android Gradle Plugin 9.4.1
  (uses its built-in Kotlin support, so no separate `kotlin-android` plugin), Kotlin 2.4.21
  (Compose compiler plugin), Compose BOM 2026.09.00. Dependabot keeps these current.
- **minSdk 31 (Android 12).** The owner installs apps on their own, recent phone, so older
  Android versions aren't worth the extra code and testing. Android 12 brings Material You dynamic
  colour (no fallback code path), the modern splash screen and per-app language settings. It still
  covers the large majority of active devices. Lower it only if someone with an older phone needs
  the app.
- **compileSdk / targetSdk 37 (Android 17)**, the latest stable API level. The minor SDK release
  37.2 exists but isn't needed by anything yet.
- **Java 17 bytecode, JDK 21 to build.** JDK 21 is what Claude Code cloud sessions provide, so local
  checks match CI exactly.
- **Version numbers:** release `X.Y.Z` → `versionCode = X*10000 + Y*100 + Z`, passed in by the release
  workflow. All other builds use `git describe --tags --long` (e.g. `1.2.0-3-gabc1234`), falling back to
  `0.0.0-dev`, so a dev build can never be mistaken for a release. Version logic lives in
  `scripts/release_version.py` (unit tested) and `app/build.gradle.kts`.
- **Debug builds use `applicationIdSuffix = ".debug"`** so a pull request's APK installs next to the
  released app instead of conflicting with it.
- **Releases are GitHub Releases with a signed APK**, published by `gh release create` (preinstalled
  on runners) instead of a third-party action, which means one fewer dependency with write access.
  The R8 mapping file is attached to each release.
- **Signing secrets** are base64 and decoded by a tolerant, tested script (`scripts/decode_keystore.py`)
  that never prints the secret. The key password is checked with a small Java program
  (`scripts/VerifySigningKey.java`), because `keytool` ignores the key password of PKCS12 keystores and
  the build would otherwise fail later with an unclear error.
- **Caching:** `gradle/actions/setup-gradle` with `cache-provider: basic` (MIT licensed). The default
  "enhanced" provider is closed source under separate terms of use.
- **CI artifacts:** the debug APK is uploaded unzipped (`archive: false`) so it can be installed on a
  phone straight from the download.
- **Languages:** English (default) + Dutch, as a working example of translations. `generateLocaleConfig`
  makes the app appear under Android's per-app language settings.
- **Persistence:** DataStore when needed (version in the catalog, not added as a dependency yet). No
  network, analytics or crash-reporting libraries.
- **No instrumented/UI tests** yet: they need an emulator in CI (slow, flaky). Logic is kept in plain
  Kotlin and unit tested instead. Manual device checks are listed in `docs/testing.md`.
- **No lint baseline:** the template has zero lint issues.
- **Play Store upload** is prepared but commented out in `release.yml` until a Play Console app and
  service account exist.
