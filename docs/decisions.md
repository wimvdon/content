# Decision log

Newest first. Add an entry for every notable choice: what was decided, why, and what was rejected.

## 2026-11-01: One chip row in Feeds, and a tighter header (phone and web)

Choosing a topic in Feeds used to add a second chip row with its feeds, so the header could be four
rows (search bar, switch, topics, feeds): about a third of a phone screen. Now the one row shows only
the chosen topic, then its feeds; tapping the topic brings every topic back (`FeedChips.rowTopics`
and `rowLoose`; the web's `feedChipRow`, which gains the topic's feeds as chips). The chips also sit
right under the switch: their 48dp touch area is the gap. Owner's choice.
- Rejected: Unread / All as a chip in the row (saves another row, but a view would look like one
  more filter).

## 2026-11-01: + is the floating button; Mark all as read moves to the end of the list (Android)

The floating button in Feeds was Mark all as read, and **+** sat in the search bar. Now **+** is the
floating button in Feeds, Saved and the Inbox, and **Mark all as read** is an outlined button under
the last article of Unread (`ListEnd`). Owner's idea.
- Material 3's FAB is for the screen's main action: adding fits that better than clearing, and it
  is now in the same place in every destination. The search bar is just search and Settings.
- At the end of the list the button says what it means (you've seen it all) and no longer covers
  the last article. The cost: marking a long list read means scrolling to its end first.
- The web keeps + and Mark all as read as small buttons in the list's header: floating buttons are
  a phone pattern.

## 2026-11-01: The reader's actions in a floating toolbar

The reader's bottom app bar (Save, or Archive and Organise; the highlights; Share) becomes a
**floating toolbar**, on the phone and the web: a pill at the bottom in the middle, over the text,
that slides away while you read down and comes back when you scroll up, at the top and at the end of
the article (`ReaderToolbarVisibility`; the web's `toolbarHidden`).
- Material 3's toolbars are for a set of actions on what is on screen, and a reading surface should
  stay calm: the text gets the whole screen while reading, the actions are one small scroll up away.
- **Built from a `Surface` and a `Row`**, no new library: Material 3 1.4 (our BOM) has no public
  `FloatingToolbar` yet. When it gets one, switching is a small change.
- The text keeps room under its end so the last lines scroll clear; messages (Archived, Undo) show
  above the toolbar.
- Rejected: a floating toolbar for Unread / All (a view choice, not an action: that is the segmented switch).

## 2026-11-01: A switch instead of tabs for Unread / All and To read / All

The owner liked the To read / Archive switch in the save sheet and felt the tabs weren't right:
Unread and All (To read and All) are the same articles narrowed, not separate pages.
- **A segmented switch** (`ViewSwitch`, Material 3's `SingleChoiceSegmentedButtonRow`) replaces the
  tabs on the phone and the web. Material 3 keeps tabs for peer pages and segmented buttons for a
  small single choice that changes a view; chips stay for the groups (topics, Liked).
- **No swiping sideways** between the two any more: with a switch there is nothing to swipe to.
  Only the view shown is computed (the pager needed both lists).
- **The web: the switch sits in the sidebar** on a wide window, between the places and the filters
  (where, which view, which group), and above the list on a phone-sized one. Refresh moved into the
  search bar, in every place, as the tabs no longer hold it.
- **The web's chip row drags with the mouse** (or a pen), as a finger scrolls it; a short move stays
  a click, and a drag doesn't choose the chip it started on.
- Rejected: chips for the view (they read as filters, reflow the row, and an "All" chip that is
  never visibly chosen was #205's problem); a floating toolbar (that is for actions, not views).

## 2026-11-01: To read or Archive in the save sheet, Cancel at the bottom left

The owner kept struggling with the saved article's buttons:
- **The Save and Organise sheets choose To read or Archive** again (a segmented choice above the
  topics, To read to start with when saving), and **Save** shows its icon. Replaces #305's Save and
  archive button, which hid that Organise couldn't move an article.
- **Cancel at the bottom left of every sheet** (`SheetActions`), Remove next to it, the main button
  at the bottom right; Done at the bottom right in Manage feeds and Manage topics. Reverses #305's
  Cancel at the top right: the owner expects it where dialogs have it.
- **A saved article's Archive stays a quick action, but the article stays open** (a message with
  Undo). Archived, there is no Back to read: only Organise.
- **The reader's ⋮ has Show full article / Show feed text** (no page icon in the top bar), in the
  order Mark as unread, that, Open in browser, Sign in, Report a problem. **Remove from Saved left
  ⋮**: it is in Organise.

## 2026-11-01: Feeds' tabs are Unread and All

The owner's choice: Feeds' second tab is **All** (every article, read ones too) instead of **Read**,
like Saved's **To read** and **All**. Both apps: `FeedTab.All` on Android, `"all"` on the web; the
web's sidebar has **All** under Saved instead of the Archive. The stored setting (`unreadOnly`)
stays the same, so nothing needs migrating. Rejected: keeping Read (a list of what you've finished
is rarely what you look for; All also finds an article read a moment ago).

## 2026-11-01: The web's layout: Android's levels, laid out for a large screen

The owner found the web behind and stuck: the Inbox was a bell in the search bar, Mark all as read
and Refresh were icons in the search bar, and the chip row couldn't be scrolled with a mouse. The
rule: **the same hierarchy as Android, not the same widgets**.
- **Places** (Android's navigation bar): Feeds, Saved and the Inbox, at the top of the sidebar on a
  wide window and in the bar at the bottom on a phone-sized one. The ☰ drawer is gone: a narrow
  window has the bar, the chips, and Settings in the search bar, as on Android.
- **Views** (Android's tabs) stay tabs above the list, with **Refresh** at their end (in Saved too;
  the Inbox has it next to its title). Rejected: Refresh in the sidebar (far from what it refreshes)
  and only the r key (not discoverable).
- **Filters** (Android's chips): on a wide window a list under the places in the sidebar, for the
  place shown; the chip row only on narrow windows, where a mouse wheel now scrolls it sideways.
- **Mark all as read** is a floating button at the bottom right of the list, as Android's round
  button, only in Unread.

## 2026-11-01: Forwarded emails from Instapaper, with Instapaper's text through an Instaparser key

A user's forwarded emails never reached Saved: Instapaper gives them no address (`url` null,
"private content"), and Content left every article without a web address out. Now:
- They come in with **Instapaper's own page as their address** (`instapaper.com/read/<id>`), so the
  rest of the app (keys, sync, Open in browser) treats them as any article. Rejected: a new kind of
  item without an address (every part of both apps and the sync format assumes one).
- **Their text comes from Instapaper's parser**, through the feed loader with Content's Instaparser
  key (`POST /instapaper/text`), only for these articles, only when one is opened, then stored in the
  account like any text, so each costs one credit of the free tier (1,000 a month, the owner's key).
  This reverses #134 for these articles only: there is no web page to take the text from, and the key
  is now free. Articles with an address keep Content's own text. Rejected: showing only a card that
  opens Instapaper (no reading in Content, no highlights); signing in to instapaper.com inside Content
  (Android only, and depends on Instapaper's page).
- The key is a Worker secret (`INSTAPARSER_API_KEY`), like the client secret: never in the apps.
- A cursor from before (no `p:` prefix) reads the whole account once, so the emails skipped come in.
- The API reference (now readable) confirms the `since` paging both apps use: offset by bookmarks
  plus deleted ids, stop at a short page.

## 2026-11-01: One sheet with pages, secondary tabs (#323)

The owner found the search bar, tabs and chips heavy on Android, and sheets opening on top of each
other confusing:
- **A sheet opened from within a sheet is a page of it** (`ContentSheet`): Edit topics in Add feed,
  Edit feed and Save/Organise; a feed and Add feed in Manage feeds; New topic and a topic in Manage
  topics. The page's header is a back arrow and its title; the arrow or Back (the phone's back
  gesture, Escape on the web) shows the sheet under it as it was. Material advises against stacking
  dialogs or sheets; one surface whose content changes keeps the place clear, and Android's back
  gesture works step by step. The pages stay composed while hidden (each is declared inside the one
  under it), so what was typed stays. The web's `Dialog` does the same with a portal into the open
  dialog, and Settings' Manage feeds and Manage topics are pages of the Settings dialog. Rejected: a
  navigation library inside the sheet (a dependency, and the sheets' state already lives where they
  are declared).
- **Secondary tabs** instead of primary ones: Material's tabs for views within one place, lighter
  (a thin line under the chosen tab, no bold indicator). Rejected: Unread/Read in the bottom bar (that
  bar is for the app's places, Feeds and Saved; mixing views into it confuses the two), a segmented
  button (it suggests tapping, while the lists swipe) and a filter chip (what #205 had, too hidden).
- **The header slides away** while scrolling down on the web too, as it already did on Android.

## 2026-11-01: Claude Code sessions don't build the APK before pushing

Sessions ran `./gradlew testDebugUnitTest lintDebug assembleDebug` before every push, and CI then
runs the same and more. The APK built in the session was never used: the owner installs the one
from CI. Sessions now run only the unit tests and lint, which catch nearly every problem and
compile the app's code and resources. CI still builds the debug and release APKs, so a packaging
problem still turns the pull request red. Rejected: no local checks at all (every test or lint
failure would then cost a CI round trip).

## 2026-11-01: Manage sheets, Integrations, Edit topics, swiping tabs; the web the same

Follow-up the owner asked for after #320, which had left the web app behind (CLAUDE.md asks for
parity; that was a miss):
- **Manage feeds and Manage topics are sheets** (dialogs on the web), opened from **two large
  buttons at the top of Settings**, so other places can open them too. Settings' pages are now
  Account, **Integrations** (OPML and Instapaper, which are ways in and out rather than reading
  settings or topics), Theme, Article list and Article view.
- **Edit topics instead of New topic and Edit in the sheets**: the Add feed, Edit feed and
  Save/Organise sheets end their topic chips with one **Edit topics** button that opens Manage topics
  over the sheet; Done or Back returns to the sheet as it was. One place edits topics, and the
  sheets stay about the feed or article. The sheet still follows a topic renamed or removed there.
- **Swipe between tabs** (Android: a `HorizontalPager` under the tabs, both tabs' lists computed so
  the next one slides in; the web: a sideways touch swipe on the list).
- **The web app now matches** #320 and this: the tabs (Unread, Read; To read, All), the + choice,
  Search's one chip row with the Feeds menu, Settings with the two buttons and pages, Manage feeds and
  Manage topics as chip dialogs, the Edit feed dialog with topic chips, Edit topics in the sheets.
  New: `reorderFeedsIn` (dragging feeds, as on Android), `articlesInTab`, `searchTopics`,
  `menuFeeds`, `toggleFeed`, `withTopic`, all tested.

## 2026-11-01: Tabs, one +, one Search row, Settings as pages

At the owner's request (Android; the web app followed in the next entry):
- **Tabs replace the state chip** (#205): Feeds has **Unread** and **Read** (only the articles
  already read, `FeedTab`; All is gone, as it mixed the two), Saved **To read** and **All** (All
  replaced the Archive, which mixed nothing but hid what is still to read). Material's primary tabs:
  they switch between views of the same content, which a chip with a menu hid. The chips below are
  the topics (and the feeds without one), as before. Mark all read only in Unread.
- **One + everywhere**: the search bar's + did Add feed in Feeds and the save sheet in Saved without
  saying so. It now asks first (the Add sheet: **Subscribe to feed**, **Save article**), so it is
  the same in Feeds, Saved and the Inbox.
- **Search has one chip row**: **Feeds** (a menu: All feeds, or several feeds ticked; with a topic
  picked only its feeds, and a topic lets go of picked feeds without it), **Saved**, then the
  **topics**, which narrow both kinds. Rejected: a kind row (All, Feeds, Saved) plus a filter row
  per kind, two rows where a topic means the same for both; and a Topic menu chip, since the topics
  are chips everywhere else.
- **Settings is a list of pages** (Account, Manage feeds, Manage topics, Theme, Article list,
  Article view), each with the same large top app bar. The shared rule in DESIGN.md ("one scrolling
  list") is for the template's small apps; this app's list had grown to ten sections, so the owner
  chose pages here (noted in DESIGN.md's "This app"). OPML moved to Manage feeds, Instapaper to
  Manage topics (it syncs topics as tags), the paywalled sites to Article view.
- **Managing is chips, not lists**: Manage feeds shows the feeds as chips by topic, Manage topics the
  topics as chips, both with the grip and pen of the sheets' Edit mode (`ReorderableChips`, now for
  feeds too). The topic list and topic pages are gone; merging is renaming to the other topic's name,
  as in the sheets. Editing a feed is a sheet (`FeedSheet`) like Add feed, with the topic chips,
  instead of a dialog with radio buttons.

## 2026-11-01: Sheets alike: Cancel at the top right, Save and Organise headings, the web like Android

At the owner's request, after #305 and #314:
- **Save and archive** (was "Save to Archive"), with the Archive's box; **Save** has To read's open
  book while saving, so the icons say where the article goes.
- The one save sheet is headed **Save** while saving (a new link, a share, just saved) and
  **Organise** for an article saved before; "Organise" for a new save read wrong.
- **Cancel at the top right of every sheet** (one `SheetHeader`): save/Organise, Add feed, the topic
  sheet, a highlight, Report a problem. Their bottom row keeps only Remove (left) and the main
  button. On the web the same dialogs show Cancel in words instead of ×.
- **New topic and Edit are text buttons**, not chips: they are actions, not topics.
- **The web follows Android**: + in the search bar adds a feed (Feeds) or a link (Saved); Manage
  topics left the ☰ menu for Settings; the sheets edit topics in their chips (#314).

## 2026-11-01: Articles older than two years arrive read (#317)

The owner saw very old articles keep turning up in Unread, probably since the release that changed
the app ID (a new install). Read marks are only kept and synced for two years (#245), so an article
older than that, which a feed still lists, has no mark: on a new install, in a fresh browser or once
its mark went, it came back unread. Now a feed article dated more than two years ago is read whenever its feed is refreshed
(`Library.merge`, `mergeFeed` on the web, tested): new ones arrive read, and ones that already came
back unread are cleared. Undated ones are unaffected; marking such an article unread lasts until the
next refresh. Rejected: keeping marks longer (the account
grows with every article ever read, for articles nobody wants to read now).

## 2026-11-01: Topics edited in their chips, on the phone and the web (#314)

- At the owner's request, editing topics happens in the Add feed and Organise sheets' own topic
  chips instead of a Manage topics page inside the sheet (#307): **New topic** and **Edit** at the
  end of the chips; Edit adds a drag handle and a pen to each chip. Pen (tap): name, icon, remove;
  renaming to another topic's name merges them, so no separate Merge. Drag: reorder.
- Topics look the same everywhere: their icon on every topic chip, in Feeds and Saved (Saved's had
  none) and in both sheets (Organise's had none).
- Chips wrap over lines, so the design kit's `ReorderableColumn` doesn't fit; dragging uses its own
  small code in `ui/feeds/TopicChips.kt`, with the "which chip is under the finger" and "move one"
  rules in plain Kotlin (`data/ChipOrder.kt`, tested). Screen readers get Move up / Move down.
- The web app had no topic editing in its sheets; it now has the same chips (`TopicChips.tsx`):
  HTML drag and drop, ← → on a focused chip, and the topic dialog. Its Add feed dialog gets the topic
  chips too (it took the topic shown). `reorderTopics`, `withTopicRenamed` and `withoutTopic` are
  ported from Android, with tests.

## 2026-11-01: The Archive is a state, not a list; no more swipes (#305)

- At the owner's request (option B in the issue): the Archive behaves like read and unread in
  Feeds, a state you put an article in when you're done with it, not a list you pick next to topics.
- **The save sheet** no longer has the *To read / Archive* chips (the List row). Its buttons say where
  the article goes: **Save to Archive** (outlined) and **Save** (filled, To read). **Cancel** moved to
  the top right, next to the heading (the web's sheet already had × there). The message says where it
  went ("Saved to Archive"); saved from the reader, it now has **Undo**.
- **Organise** (an article saved before) only has the topics with Like, the note, Remove and Save:
  it doesn't move the article.
- **The reader's bar**: "To read ▾" and the unlabelled box icon became two labelled buttons,
  **Archive** (turns into **Back to read** once archived, like Mark as read / unread) and
  **Organise** (a tag icon). On the web the place and Tags menus became the same two buttons.
- **No swipes anywhere** (Feeds, Inbox, Saved): Saved already used cards without swipes, so the
  owner chose the long press everywhere instead. A Saved card's long press now starts with
  **Archive** (or **Back to read**); feeds and the Inbox already had Save in theirs. `SwipeRow` is gone.
- Rejected: option C (keep the chips, style them as a toggle), which kept two controls for one thing.
  "Save to Archive" over "Save and archive": it names where the article goes, not two steps.

## 2026-11-01: Adding from the search bar, topics managed in the sheets (#307)

- At the owner's request (Android): the Manage topics icon and the X are gone from the chip rows.
  The X isn't needed since one chip is picked at a time and tapping it again lets go; Manage
  topics stays in Settings.
- **+ in the search bar** adds what the place holds: in Feeds a feed, in Saved a link. Saved's FAB
  (Add link) is gone, so the two places add the same way.
- **Add feed is a sheet**, like the Organise (save) sheet, with the topic as chips (it was a dialog that took
  the topic shown, without a way to pick one). The share menu's Add feed keeps its dialog.
- **A pen next to the topics** in the Add feed and Organise sheets turns the sheet into Manage topics
  (reorder, rename, icon, remove, New topic) with Back to the sheet, rather than leaving for a
  screen and losing what was typed. Topic renames and removals made there update the sheet's own
  choice (`SaveChoices.withTopicRenamed`, `withoutTopic`, tested) so Save doesn't bring an old
  name back. Remove asks on that page: no dialog opens on top of a sheet.
- Not done: the owner's aside that Send with Content's recipients could follow the same pattern;
  left for its own issue.

## 2026-11-01: The save sheet is Organise, without sending (#304)

- At the owner's request ("Remove share from Save and share, make it = Organise"), saving and
  organising are the same sheet, headed **Organise** every way in (the reader's Save, a swipe (gone since #305), Add
  link, the share menu, the custom tab's Save): the list, topics with Like, the note, Cancel and
  Save. **Send with Content is gone from it**; sending is Share's (Content's share sheet, the web's
  Share) and the share menu's Send with Content. Both apps alike.
- Android's share menu target that opened the sheet is called **Save** (it was "Save and share");
  a share target named "Organise" wouldn't say that it saves.
- Rejected: keeping "Save and share" as the heading for a new save (it named sending the sheet no
  longer does).

## 2026-10-31: No ☰ on Android: every place is a chip

- At the owner's request the Android app has no navigation drawer. It listed Feeds' places (the
  topics and each feed) but not Saved's, which were already chips, so it mostly repeated them.
- Feeds' chip row now holds the topics and, after a divider, the feeds without a topic; while a
  topic (or one of its feeds) is chosen a second row shows its feeds (`data/FeedChips.kt`, tested).
  Tapping a feed there shows it alone, with its topic still lit; tapping it again goes back to the
  topic.
- Settings is the search bar's last button: the account's Gravatar picture while signed in (with
  Show images on), else a cogwheel, as Material's search bar puts an avatar there.
- Manage topics is both the icon at the end of the chip rows (Feeds and Saved) and a row in
  Settings (owner's choice).
- The web app keeps its sidebar: on a computer there is room for it, and it was not asked to change.
- Rejected: keeping ☰ for Manage topics and Settings only (two entries do not need a drawer); a
  bottom sheet of feeds (one more tap than a chip row).

## 2026-10-31: One Instapaper sign-in per account, settings per app, no No topic page

- **Instapaper is connected once for the whole account** (at the owner's request): the token goes
  into the account's synced settings (`services.instapaper.token`, encrypted like the rest of the
  account), and every device takes it in; disconnecting anywhere, or Instapaper refusing the token,
  disconnects every device. A device that never connected sends nothing, so it doesn't disconnect
  the others. The same user's new token keeps what was synced. Rejected: keeping it per device
  (signing in on every device was the complaint); the account remembering it on the server outside
  the sync file (a second place to keep in step).
- **Settings sync per app** (at the owner's request): all of the web app's settings under `web.`,
  all of the Android app's under `android.`, so every browser looks and works the same and every
  phone does, but the web and Android never change each other. This replaces #96 (view settings on
  each device) and the shared `fullArticles` and `markReadOnScroll`, which are read once and then
  dropped. Still per device: notifications, the Save and Share targets, the share sheet's counts,
  Unread | All.
- **The feeds without a topic are listed loose under the topics** in Manage topics, on both apps;
  the No topic page is gone (backing out of Add feed opened it on the web). Back from Add feed goes
  to where you were.
- **Web: Merge with… and Remove topic are in a ⋮ menu** on a topic's page, Merge with… in a small
  dialog of its own, as on Android; the merge picker no longer sits on the page.

## 2026-10-31: The web app at content.wim.to, the Workers renamed to content-

- The owner moved the web app to **https://content.wim.to** and the feed loader to
  **https://content-worker.wim.to**, and renamed the Cloudflare Workers from `rss-reader-web` and
  `rss-reader-worker` to `content-web` and `content-worker`; the old addresses answer "not found",
  so the released app could no longer sync or sign in.
- Everything that named the old addresses follows: the feed loader's address in the app, the web app
  and the extension; sign-in links and the App Link the phone verifies; the Instapaper callback
  (also changed in Instapaper's application settings); the player pages' origin; the Workers' names
  in `wrangler.toml` and `wrangler.jsonc` (Workers Builds refuses a name that differs).
- The feed loader still lets `content-web.wim-d57.workers.dev` in, next to content.wim.to.
## 2026-10-31: The Inbox in the navigation bar, ☰ without folding, Saved without All, Archive in one tap

- At the owner's request (Android): **the Inbox is a destination**, third in the navigation bar
  (signed in only, with a badge while something is unread) and a row in ☰, instead of a bell in the
  search bar that opened a screen of its own. Notifications and "Open" after a share go there.
- **☰'s sections no longer fold**: they are plain headers, always open.
- **Saved's state chip is To read or Archive**: All mixed the two; Search with nothing typed still
  lists everything. A kept All filter shows To read.
- **Archive in the reader**: an icon button next to the article's list, until it is archived. One
  tap archives it, closes the article and shows Archived with Undo (it was Organise › Archive ›
  Save). Rejected: a matching "To read" button on archived articles (the owner: not needed; Organise
  does it), archiving automatically at the end of an article (surprising), and Archive as a quick
  option in Organise (still two taps).
- The web app is unchanged for now.

## 2026-10-31: Topic pages only manage; chips only for what has something

- At the owner's request: a topic's page no longer leads into reading (its Feeds and Saved buttons
  and the link to its saved articles are gone): Back from there goes to Manage topics, not to where
  you read, so it read as navigation that wasn't. It shows what the topic holds, as text.
- The pen in the search bar (Edit topic) is gone: Manage topics is in ☰.
- A topic chip, and Saved's Liked chip, show only when they have something in the state shown
  (with Unread, topics with unread articles; in the Archive, topics with archived ones). The chosen
  one stays, so a chip never disappears under your finger. ☰ still lists every topic with feeds: it
  is the map of places, not a filter.
- Manage topics has two buttons: Add feed (the FAB) and New topic above it, quieter. Material 3's
  FAB menu would be the component, but it isn't in the stable library the app uses (1.4.0).
- Spotify always gets its full player (352 high, with the cover); the compact one (152) was cramped
  on a phone, the more so with a large font size, which the player view no longer applies.

## 2026-10-31: Places in ☰, a page per topic, the account first, players loaded directly

- **☰ is the map of every place** (the owner's rule): Feeds (Feed, the topics with feeds and their
  feeds, the feeds without a topic) and Saved (To read, Archive). Filters (Unread, Liked, Saved's
  topics) are only chips: Liked and Saved's topics left ☰. Back goes up a level (feed → topic → All,
  Saved → Feeds).
- **The chip row stays as it was**, the state chip, a divider, the groups, the same in both
  destinations. Rejected after drawing it out: topics as tabs in Feeds and To read | Archive as tabs
  in Saved (right by Material's book, but the two screens would work the other way round, which the
  owner had avoided on purpose).
- **Topics have pages** (the owner found the manage page a layer on top of feed management): Manage
  topics lists them with what they hold; a topic's page has the two ways into reading, its feeds,
  its saved articles, Edit, Merge with… (an action now, no longer a rename to another topic's name)
  and Remove. A pen in the search bar opens the page of the topic being read.
- **OPML moved to Settings** › Move to or from another reader, before Appearance (the owner agreed
  to undo #251's "here, not in Settings": it is a one-off move of data, not organising). No extra
  Add feed button in Feeds (the owner's choice): Add feed is on a topic's page and the first screen.
- **First launch puts the account first**: a welcome with the emailed sign-in link, Add feed and
  Import below it. Signed in without feeds, it is the old No feeds yet.
- **Embedded players on Android load the player itself**, with Content's web app as referrer,
  instead of an iframe in a page of our own: without a page address YouTube refuses (error 153, a
  black box), and other players (Bandcamp, …) didn't show either. Third-party cookies are allowed
  in that view only, and the view isn't clipped.

## 2026-10-31: Topics: one way to group feeds and saved articles

- The owner's idea: feed folders and Saved's tags become **topics**, managed in one place. A topic
  is a feed's folder and a saved article's tag at once: a feed is in one topic at most (the owner
  agreed), a saved article has any number. Saved's own folders had already become tags (#187).
- No change to the sync file or Instapaper: a topic is stored as a folder (`folders`), and a saved
  article's topics as its tags, by name. `topicsForTags` (Android and web) keeps them in step on
  every change: a tag without a topic gets one (id `topic:<name>`, so two devices make the same
  one), and topics with the same name are joined. Instapaper's tags are topics.
- Renaming a topic renames it on its saved articles; renaming it to another one's name joins them;
  removing it keeps its feeds and articles without it. A topic stays until it is removed, also when
  nothing uses it any more (it is a group you made, not a leftover).
- One order everywhere: the topics' order from Feeds and topics, for Feeds' and Saved's chips, the
  drawer and the sheets (Saved's chips were most used first, #220).
- A saved feed article always gets its feed's topic, now also in the web app; the phone-only
  switch Tag with the feed's folder is gone.
- Navigation and settings were reviewed at the same time (docs/architecture.md, "Navigation and
  settings: where everything lives"): ☰'s Manage feeds and Saved becomes **Feeds and topics**, one
  page without tabs; Instapaper moves to Settings, under the Content account (DESIGN.md puts
  connections first); Sites you're signed in to moves next to Reader; Search's Saved folder filter
  (always empty since #187) is gone. The save sheet's To read/Archive choice is called **List**.
- Rejected: a separate Topics page next to Feeds (a topic's feeds are what you manage most, so they
  belong together), and one topic choice shared by Feeds and Saved (they are different places, and
  a topic often has only feeds or only saved articles).

## 2026-10-31: The app ID is to.wim.content

- At the owner's request (the owner's domain is wim.to; the web app may move to content.wim.to
  later), the application id and the code's package changed from `be.wimvandonck.content` to
  `to.wim.content` (debug builds: `to.wim.content.debug`), in one step this time.
- As with the previous change, Android treats it as a new app next to the old one, which gets no
  more updates. Signing in with your email brings back the library; Instapaper, sites signed in to
  and per-device settings are set up again, and the old app is uninstalled by hand.
- `assetlinks.json` lists all three packages, so sign-in links and the Instapaper callback open the
  new app, and the old ones keep working until they are removed. Same signing key.
- Push notifications need the new package in Firebase (two Android apps, `to.wim.content` and
  `to.wim.content.debug`) and a new `GOOGLE_SERVICES_JSON` secret. Until then the notifications
  switch is hidden, as the build reads Firebase's settings by package name.
- Room's exported schemas moved with the database class; the database itself is new with the app.
- Not changed: the web app's and feed loader's addresses (for when content.wim.to is set up), the
  sign-in email's sender (resend.wimvandonck.be) and the browser extension's id.

## 2026-10-31: Picture sizes are read the way browsers read them (Substack)

- Some inline pictures of Substack posts didn't load. Substack's image server puts commas in its
  addresses (`…/w_1456,c_limit,f_auto,…/picture.png`), and the reader split a picture's list of
  sizes (`srcset`) at every comma, so it picked a torn-off piece as the address. Now both apps read
  `srcset` as browsers do (an address runs until a space; `ArticleBlocks.srcsetCandidates`, web
  `parseSrcset`), in the reader and in the full-article extractor.
- Fixed here rather than reported upstream: Defuddle, which finds the article, already reads
  `srcset` correctly; the bug was only in Content's own code.

## 2026-10-31: Embedded players play inside the article, on tap

- The owner asked for YouTube, Spotify and the other major players in the reader, replacing the
  2026-10-15 choice of cards that only open the app or browser. A card still comes first, so
  nothing loads from a service until it is tapped; then the service's own player takes its place
  (Android: a small WebView showing only that player, loaded under the web app's address because
  YouTube refuses players without a page address; web: a sandboxed iframe). Links the player opens
  go to the browser or the service's app.
- Known players, the same list on both (`feed/Embeds.kt`, `web/src/lib/embeds.ts`): YouTube (from
  youtube-nocookie.com), Vimeo, Spotify, SoundCloud, Dailymotion, Apple Music, Apple Podcasts,
  Bandcamp, TikTok, each with the size it asks for (fixed heights for the audio players). A site's
  own video file plays in a plain video player. X and Instagram posts need their sites' scripts in
  the page, so they stay cards that open the post; unknown iframes stay cards too (they could be
  anything, such as ads or forms).
- PRIVACY.md now names the players. No new library: WebView is part of Android.

## 2026-10-31: The drawer's logo without its circle

- At the owner's request, the logo at the top of the drawer (and the web app's sidebar) is the C on
  its own, 32dp, no longer on a white circle with an edge.

## 2026-10-31: Any Substack link adds its publication; shorter import steps

- At the owner's request, Add feed takes any Substack link for its publication
  (`FeedDiscovery.substackPublication`, web `substackPublication`): a note or profile on
  `substack.com/@name`, an `open.substack.com/pub/name/…` link or a post on `name.substack.com`
  becomes `https://name.substack.com/`, whose page announces the feed. Only Add feed does this, not
  links saved to Saved. A Substack writer's handle is usually their publication's name; when it
  isn't, Add feed says it found no feed.
- The import steps no longer say to use a computer or to move the file to the phone: the web
  (also on a phone) does it, and people know how to open a downloaded file.

## 2026-10-31: Import guides for Feedly, Inoreader and Substack

- At the owner's request, Manage feeds and Saved › Feeds has numbered steps under Import OPML for
  Feedly, Inoreader and Substack, each in an `ExpandableSetting` with numbered steps styled like
  Snoozer's `NumberedSteps` (a filled circle with the number), as in the owner's other apps. The web
  app shows the same steps in `<details>`. Substack has no export: each publication is added through
  the share menu's Add feed, from its home page (a post or a note isn't the publication).

## 2026-10-31: Send with Content as an email's To field (#284)

- The owner found picking people unclear: which chip was picked, which could be, and what Add
  address did. Send with Content now looks like the To field people know from Gmail: the people
  picked are chips inside a To box with an ×, a field after them takes a name or address, and the
  people shared with before are suggestions below it (up to five, filtered as you type; `SendTo.
  suggestions`), with "Add …" for a whole new address (`SendTo.newAddress`). Picked and not picked
  are now two different places instead of one chip's state. Forgetting someone moved to the
  suggestions (long-press, then trash). Same on the web.
- Rejected: keeping the chips and only renaming Add address (the picked/not picked state stayed hard
  to read); a separate people picker screen (more taps for the common case).

## 2026-10-30: Notifications show what was shared, silently

- At the owner's request, a "shared with you" notification on Android shows the first article's
  title and intro, its picture as the thumbnail, who shared it and how many more there are, and
  arrives silently (a new, quiet channel; the old one, which made a sound, is deleted). Android's
  settings can make it louder.
- The owner chose to send the preview **inside the push**: the feed loader adds it to the Firebase
  message (`previewData`, cut to fit Firebase's 4 KB), so Firebase sees the title, intro, picture
  address and sender (not the link). The phone downloads the picture itself (Coil, 5 seconds at
  most), the only download outside the app being used (`PRIVACY.md`). Browsers still get an empty
  push. Android now sends the picture and intro it knows when sharing (`Sharing.articleFor`).
- **Fix, the same day:** the preview named the sender `from`, a key Firebase keeps for itself, so
  Firebase refused every message, and the feed loader took the refusal (`INVALID_ARGUMENT`) for a
  gone phone and forgot its token: no notifications at all. The key is now `sender`; only a refusal
  about the token itself (`UNREGISTERED`, "registration token") forgets a phone; and the app gives
  its token to the feed loader again each time it starts while notifications are on, so phones that
  were forgotten get them back.
- Rejected: the phone fetching the share from the account when poked (Google would see nothing, but
  a background download from the account); no preview at all.

## 2026-10-30: Forgetting recipients, a steadier drop

- **People shared with can be forgotten** (owner's request): hold one in Send with Content (or
  right-click it on the web) and every person shared with before gets an ×; tapping it takes them
  off the synced list (`sharing.recipients`), Done ends it. Screen readers get Remove as an action on
  each person. Content's share sheet's Send is a full-width button under the people.
- **Feeds no longer jump when dropped** in Manage feeds and folders: the list keeps the new order
  until it is stored (it showed the old order for a moment), and the dropped row glides into its
  place instead of snapping there. The shared `ReorderableColumn` in the design kit snaps the same
  way; changing it belongs in the android-app template first.

## 2026-10-30: Three share targets from other apps

- At the owner's request, a link shared from another app no longer offers **Don't save** in the save
  sheet. Android's share menu shows separate targets instead: **Save and share** (the save sheet),
  **Send with Content** (only sends; the people in the share menu open this one) and **Add feed**
  (Add feed with the link filled in). Two aliases of `SaveFromTabActivity` and one small activity;
  `ShareTargets` tells the aliases apart. Inside the app, Don't save stays in the sheet.
- The message after saving from another app goes when the screen is tapped anywhere (but Open), so
  you are back in the other app at once.
- **Every one ends with Content's own message** (a snackbar, as after saving), not a toast: "Sent
  to …" and "Added …" waited for the screen to close and didn't always show. The screen stays
  while sending or adding, which goes on in the app's scope even if it closes. The intent filters'
  priorities ask Android for the order Save and share, Send with Content, Add feed; its share menu
  may still rank the ones used most first.
- **Send with Content looks the same everywhere** (owner's request): Content's share sheet (and the
  web app's Share) use the Save and share sheet's chips, Add address with a filled + that adds the
  person to the chips, and one Send for everyone picked, instead of one tap sending and a typed
  address with a share icon.
- **Organise no longer offers Send with Content** (Share is next to it in the bottom bar); it has
  Remove at the bottom left instead, with Undo, and is headed Organise.
- **The sheets line up** (owner's request after a review of all of them): Remove sits at the bottom
  left of every saved article's sheet (just saved, or Organise), with Cancel and Save at the right;
  Remove acts at once with Undo everywhere (the reader's ⋮ asked first; the web's Organise had no
  Undo); and **Don't save** is gone from the app too: to only send, there is Share and the share
  menu's Send with Content.
- Rejected: hiding Send with Content while signed out (switching an alias on and off with the
  account); signed out it says to sign in first, as Send with Content does elsewhere.

## 2026-10-30: Defuddle finds the article, in both apps

- At the owner's request, **Defuddle** (https://github.com/kepano/defuddle, MIT, npm `defuddle`)
  replaces Mozilla's Readability in the web app and Readability4J in the Android app. It is used as
  it is, starting from scratch: none of the adjustments made around Readability remain (the 5-level
  scoring for Readability4J, #271; the page's intro put first, #266; our own removal of the repeated
  title, a `<p>` for a div per paragraph, merging line breaks). Only what the reader needs stays:
  the tags it can show, absolute links, known video players, and a minimum length.
- **One extractor for both apps, kept up to date as a dependency**: `web/src/lib/articleExtractor.ts`.
  The Android build bundles it from `web/` (`bundleArticleExtractor` in `app/build.gradle.kts`, which
  runs `npm ci` and `npm run build:android`) into the app's assets, and `WebArticleExtractor` runs
  it in a hidden WebView (Android's own, no new library). So both apps use the Defuddle version in
  `web/package-lock.json`, and Dependabot's weekly npm update brings a new Defuddle to both, checked
  by CI. Building the Android app now needs Node.js (CI and the release workflow set it up).
- The WebView is offline (`blockNetworkLoads`, no file or content access) and gets the downloaded
  page as a string; Defuddle reads it as an inert document, so the page's scripts don't run and its
  pictures don't load. Defuddle's site-specific lookups on other servers are off (`useAsync: false`).
  No new network destinations (`PRIVACY.md` unchanged). The view is let go after a minute unused.
- Readability4J (and its slf4j rule in `proguard-rules.pro`) and `@mozilla/readability` are gone.
  jsoup stays for reading page details (`PageInfo`).
- Tests: the sample pages in `testdata/articles/` are now checked once, in the web app's tests
  (they cover both apps' extractor); `SharedArticlesTest` is gone with the Kotlin extractor.
  Where Defuddle differs from Readability the samples follow Defuddle: it keeps the intro above
  the article as a heading, leaves out a link back to the top, a video's poster, a list of two
  short words and a paragraph marked `md:hidden`. jsdom (the tests' browser) refuses Defuddle's
  long selectors; `web/test/longSelectors.ts` asks them in parts, as a browser would answer.
- Full articles are cached anew (Android `v7-`, web `fullArticle-v5:`).
- Rejected: porting Defuddle to Kotlin (about 20,000 lines to keep in step by hand; the apps would
  drift apart again); committing the bundled file (a Defuddle update would then need a second,
  manual step); androidx.javascriptengine (a new library, and it has no DOM).

## 2026-10-28: The page's intro, and pictures for older items (#266, #275)

- **The intro a page shows above its article** (#266, De Standaard; also Wired's subtitle) is
  outside the part Readability keeps. Both apps now put the page's own summary (its description,
  as Readability's excerpt reads it) first, but only when the page shows that exact text as an
  element of its own and the article doesn't contain it already. A description meant only for
  search engines and link previews stays out. Readability itself is unchanged (#236). Sample
  pages: `intro-outside-body.json`, `description-not-shown.json`. Full articles are cached anew
  (Android `v6-`, web `fullArticle-v4:`).
- **A saved article without a picture gets the page's when it opens** (#275): items from the inbox,
  from Instapaper, or saved before #268 never had their page read. Opening one reads it once more
  while it still lacks a picture or description.
- **Not changed: standaard.be's IPO article** (#272, #274). Your browser's copy of the page comes out
  right in both apps; the copy Content downloads holds no article text (only the picture, author,
  date and the "Most read" column), which is what a site sends when you aren't signed in.
  **Sign in to this site** in the reader's menu sends your standaard.be sign-in with the request.

## 2026-10-28: Open issues of 6.1.1 (#263, #267, #268, #269, #270, #271)

- **Remove from Saved in the long-press menu** (#263), on rows and cards, in both apps, with Undo
  instead of the reader's question (the menu is quick to reach, Undo is as quick to take back). On
  Android, To read's rows get the menu too: Organise, Archive (or To read), Share, Remove.
- **The page's own picture for every saved link** (#267, #268): a link shared from another app
  brings its title, so the page was never read and the overview had no picture. Now the page is
  read whenever the item lacks a title, description or picture, and only what is missing is filled
  in (`Item.describedBy`). Same address as the article itself, so nothing new in PRIVACY.md.
- **Reading progress on Android** (#270) waits for the text that stays: not while the full text
  loads, and not until the page's height has been the same for 400 ms (pictures arrive later).
  Before, it scrolled back on the short first page and leaving the article stored that much lower
  place as the new progress (`data/ReadingPosition.kt`, tested, like the web's `readingProgress.ts`).
- **The web app refreshes on every page load without waiting for Instapaper** (#269): the refresh
  waits for the account's part of the sync (read marks and feeds), as on Android, while Instapaper
  goes on beside it. With no feeds yet but an account, it refreshes too, for the feeds the sync brings.
- **Android scores as today's Readability** (#271): Readability4J ports an older Readability that
  gives a paragraph's score to 3 levels of containers; Readability.js (Firefox, the web app) gives it
  to 5. On Wired, each part of the article sits in its own few containers, so Android kept only the
  last part while the web app had all of it. `ArticleExtractor` now asks Readability4J for 5 levels
  (one override, no rule of ours, so #236 holds). Cached full articles go to `v5-`; texts already
  stored in the account stay as stored. Sample page: `testdata/articles/parts-in-wrappers.json`.
- **Not reproduced:** the two standaard.be articles (#266, #272) answer this session's downloads
  with a Cloudflare check, so their pages couldn't be looked at; they stay open.
- **Not changed:** the inbox's long-press menu (#262) already has Save; it hides when the article
  is in Saved already. Closed at the owner's request.

## 2026-10-25: Reading progress in the web app too; the reader's bar on Android

- Asked for by the owner: the place you stopped reading a saved article syncs, also with
  Instapaper. Android kept it already (the item's `progress`, synced with the account and sent to
  and read from Instapaper's own progress); the web app didn't. Now it does the same: a saved article
  opens where it was left once its text is there (not when barely begun or finished, 2% and 98%),
  and the place is stored when the article is left or the tab goes to the background, when it moved
  at least a hundredth (`web/src/lib/readingProgress.ts`, tested). Rejected: storing on every scroll
  (a sync and an Instapaper call for each).
- The ☰ menu loses its Tags heading (Liked and the tags follow Saved). The reader's bar on Android:
  an arrow down on the folder button, and Share always at the right edge (the folder button and the
  room before Share each took half of the free space, so a short folder name left Share short of it).

## 2026-10-25: The extension: right-click, its own languages, icons without the white box (#257)

- Asked for by the owner. **Right-click**: Save to Content on a page and Save link to Content on a
  link (the `contextMenus` permission, which reads nothing and adds no warning when installing).
- **Languages:** the extension's name, description and texts in the app's six languages through
  `_locales` (Chrome and Firefox both read it); the popup only knew Dutch and English.
- **Icons:** the favicon and the extension's icon drew the Android launcher icon whole, white box and
  adaptive-icon safe zone included, so the C filled less than half of a tab or toolbar icon. Now the
  C alone, cropped to 2 units of air (`web/public/icon.svg`'s viewBox `28 28 52 52`; the extension
  gets PNGs at 16, 32, 48 and 128 pixels drawn from the same shapes; the 128 one keeps the 16 pixels
  of air Chrome asks for around 96). The web app's sidebar puts it in its own white circle.
- **Where a box belongs:** a tab, toolbar or right-click icon is the C alone. An installed web app
  is an app icon: `icon-192/512.png` ("any") are the C on a white rounded square that fills the
  canvas, the C at about two thirds of it as the launcher icon shows it; `icon-maskable-192/512.png`
  are white to the edges with the C inside the middle 80%, for Android to cut its own shape;
  `apple-touch-icon.png` is the same for iPhone and iPad (no transparency, iOS rounds the corners).
  Web notifications show the app icon. The Android launcher icon stays as it is. Rejected: keeping the white box (it is what made the C small) and a dark
  outline for dark tabs (the sky-to-navy C reads on both).
- **The popup's button stayed grey in some browsers** (Arc, Vivaldi, Opera and the like open the
  popup as a window of its own, so "the active tab of the current window" was no page). It now asks
  for the window used last first, then the current one.

## 2026-10-25: Save and share, one rule for sheets and dialogs (#256)

Asked for by the owner after a review of every sheet and flow.

- **Save and share.** The one save sheet (#143) is now Save and share, everywhere: new, just saved
  and organising a saved one. Under the note, signed in, **Send with Content**: the people shared
  with as chips, several at once, and Add address (checked for a Content account before it joins
  them). The button says Save, Save and send or Send, and one message says where it went and who
  got it. Before the save there is also **Don't save**, to only send it; tags and note hide then.
  The rules are plain Kotlin (`data/SendTo.kt`) and TypeScript (`web/src/lib/sendTo.ts`), tested
  alike. No message to the receiver: the inbox doesn't show one since #246.
- **Sharing from other apps** opens Save and share. The separate "Send with Content" target (the
  `SendActivity` alias) and its dialog are gone. The people shared with stay in Android's share
  menu (Direct share); they open Save and share on Don't save with that person picked.
- **Share stays its own action**, Content's share sheet with the apps used most. Its Email address
  is a field in the sheet now, not a dialog on top of it.
- **Settings › Save button and Share button are gone**: Save is always Save and share, Share always
  the share sheet, which already shows the apps you use most first. Their stored values are left
  alone and no longer read.
- **Sheets and dialogs:** a bottom sheet for anything done with an article or that needs room
  (Report a problem and the folder sheet with its icon search are sheets now); a dialog only to
  confirm or type one name. The web keeps its dialogs, which are the desktop's sheets.
- Rejected: a separate Send sheet next to Save (two places to send from), and always saving what is
  sent (a person picked in the share menu wants to send, not save).

## 2026-10-25: Links with tracking parameters are the same item, and duplicates join (#253)

- The Archive held articles twice: once with Flipboard's `?utm_source=flipboard&…` and once
  without (from the feed or Instapaper). The item key now leaves out tracking parameters
  (`ItemKeys.of`, web `keyOfLink`), the same list `ItemKeys.loose` already ignored for Instapaper
  since 2026-10-22. Other parameters stay as they were, so no other key changes.
- Duplicates made before join once, on each device, when it reads the library and after every
  change (`joinDuplicateItems`, in `LibraryRepository` and the web app's `updateLibrary`):
  everything of the user's on either stays (tags, notes, highlights, liked, progress), the reading
  list place is the latest one, and the item already under the clean key keeps its link and title.
  Items synced in from an older app under an old key join the same way, so the account settles
  after one round.
- A service's rows move with their items (`ServiceSync.rekey`, web `rekeyed`). When the service
  had the article twice, the second copy keeps its old key, which no item has any more, so the
  next sync removes that copy there, as for any article taken out of Saved (with the same safety
  net for large removals). Rejected: leaving the copy there (it would come back as a new article
  once the main one is removed).

## 2026-10-25: Read marks are kept for two years, not 120 days (#245)

Articles read on one device stayed unread on the others for GORILLA VS. BEAR. Not because of the dot
in its name: the feed posts rarely, so half of the 35 articles it lists are older than 120 days, and
read marks of articles that old were never synced (and dropped on every device). Both apps now keep
them for two years (`READ_MARKS_KEPT_MS` in `data/Library.kt` and `web/src/lib/library.ts`). A mark
is a key and a date, so the account grows a little, well inside Cloudflare's free plan. Marks that
were never sent are sent at the next sync. Rejected: keeping marks as long as the article is still on
a device (devices would disagree and send the same mark back and forth), and storing when an
article was read (a new field in both apps' storage for the same result).

## 2026-10-25: Review round: simpler Instapaper, one manage screen, Content's own share sheet (#251)

All asked for by the owner in one review, released as a major version.

- **Instapaper without options.** Everything in Saved syncs both ways, highlights included (the
  Copy highlights switch is gone, so is the scope that only ever meant everything). Connecting asks
  once, **Import from Instapaper?**: import everything it has now, or not (then what is there stays
  until it changes; Saved still goes there). The answer is the synced import record
  (`services.instapaper.imported`, now `{"at", "count", "everything"}`); records from before count as
  everything. `services.<id>.scope` is dropped from the sync file.
- **One manage screen.** ☰'s Manage feeds and Manage tags became one **Manage feeds and Saved**
  next to Settings, with a Feeds tab (now with OPML import and export) and a Saved tab (Tag with the
  feed's folder, Instapaper, the tags). Settings lost those three. The same on the web.
- **The ☰ menu** keeps Feeds and Saved open as you leave them (no more folding by itself); a feed
  folder shows its feeds only while you are in it, so the list stays short.
- **The reader's bar** for a saved article is its folder (opens Organise), the highlights and
  Share: the folder and tags menus side by side didn't fit on a phone. Organise's "Where" is "Folder".
- **Selection menu:** Highlight and Note come first. Compose only adds an app's items after its own,
  so the reader builds the whole menu itself: Highlight, Note, Copy, Share, Select all, then the apps
  that act on text (queried with `PROCESS_TEXT`, a package-visibility query, not a permission).
  Rejected: keeping Compose's items (ours stayed in the overflow).
- **Content's own share sheet** (Android) in place of Android's: Send with Content to the people
  shared with (one tap sends) or another address, then the apps picked most in this sheet, Copy
  link and More (Android's menu). Android doesn't tell an app which share targets someone uses, so
  Content counts picks in its own sheet, on the phone only; until then well-known messaging and
  mail apps come first. Rejected: `PACKAGE_USAGE_STATS` (a special permission, and it says which
  apps are used, not where people share). The chooser buttons of #248 are gone with it.
- **Launcher shortcuts:** the people (Direct share) are kept out of the launcher
  (`setExcludedFromSurfaces`, Android 13 and later); the launcher's long-press menu offers the first
  four feed folders instead.
- **Reports** carry the text Content shows, cut at a word when the address would get too long
  (then copied whole to the clipboard to paste). PRIVACY.md says so.
- **The article parser** is Readability as it is: after it, only what the reader can't show is
  dropped (scripts, drawings, form fields); our own dropping of nav, header, footer, aside, form,
  dialog and menu blocks is gone. Android keeps Readability4J's "Extended" variant: it is the port
  closest to today's Firefox Readability (lazy and noscript pictures), which plain Readability4J
  predates.
- **Sharing a whole tag is gone**, in both apps and the feed loader: one article at a time.
- **Sign in to this site** only shows while you aren't signed in to that site.
- **Removed, as the owner is the only user:** the move-in of the library and Saved from before
  #106 (`LegacyLibrary`, `LibraryCodec`, `LocalSaved`, `savedItems.ts`, `localSaved.ts`), the move of
  bookmarks from before #119, Raindrop.io's leftovers, the Content account key an Instapaper sign-in
  used to bring (the feed loader no longer hands one out), the two-service remove dialog, the old
  Save target values, and "From …" on Saved's cards (the inbox shows who shared).
- **Fixed on the way:** reporting an article that isn't saved did nothing on Android; the web
  app's privacy link pointed to the old repository.

## 2026-10-24: Buttons for Send with Content in Content's own share sheet (#248)

- At the owner's request, Content's own share sheet shows Send with Content and up to four recent
  people as the sheet's custom action buttons (`EXTRA_CHOOSER_CUSTOM_ACTIONS`, Android 14 and later;
  Android shows at most five). Each opens Send with Content over the app, with the person filled in.
- There, the Send with Content alias and its Direct share people are left out of the sheet, so
  nothing shows twice. Other apps' share menus keep the alias and the people (custom actions only
  exist in the sheet an app opens itself). Before Android 14, Content's sheet works as in #246.
- This replaces the rejection of custom actions in #246's entry: they are used alongside the alias,
  not instead of it.

## 2026-10-24: Instapaper gets Content's text (#247)

- At the owner's request, a new article goes to Instapaper with the text Content shows for it
  (API v2's `content` on `POST /bookmarks`), so both show the same article. It is the one stored text
  every device shows (`SharedText`), extracted once if no device has it yet.
- Only for articles saved in the last seven days: connecting Instapaper to a large Saved would
  otherwise load every article's page at once. Older ones go as links, as before.
- Only on creation: Instapaper has no call to replace an article's text later.
- Refused text (too large, or a server error) falls back to the link alone, so a sync never stops
  on it. The feed loader allows 1 MiB for saving a bookmark, 64 KiB for its other Instapaper calls.
- Rejected: sending the text later, when the article is first opened (Instapaper can't take it
  after creation), and sending the feed's own text (it is often only a summary).

## 2026-10-24: An inbox for what is shared, and sending from the share menu (#246)

- At the owner's request, what other Content users send no longer goes into Saved (the Shared with
  me state is gone): it lands in an **inbox** behind a bell in the search bar, shown only while
  signed in. It works like a feed: newest first, unread in bold with a dot, read once opened, and a
  dot on the bell while something is unread. It has no folders or tags of its own.
- Saving from the inbox keeps the article there, as a feed article stays in its feed; removing it
  from the inbox (with Undo) or from Saved leaves the other. An article already in Saved now arrives
  in the inbox too, and the same article shared again comes back unread with its latest sender. This
  fixes the earlier behaviour where an article that was already saved seemed not to arrive.
- In the sync format, `sharedBy.waits` now means "in the inbox", independent of `readingList`, and
  gains `read`; `message` is dropped (`docs/sync.md`). Both apps change together, as before.
- The message to the receiver is dropped: it was rarely needed, and the inbox shows who sent it.
- Sending lives in the share menu instead of a menu entry of its own: on Android a second share
  alias, **Send with Content** (`ui.SendActivity`), enabled only while signed in, and the people
  shared with as sharing shortcuts pointing at it. Content's own share menu now keeps them (it still
  leaves out the save alias, which has its own button). The web app's Share opens Send with Content
  with the people shared with, and Other apps for the browser's share menu. Sharing a whole tag stays
  in Manage feeds and tags.
- Rejected: custom actions in Android's share menu (`EXTRA_CHOOSER_CUSTOM_ACTIONS`): they only show in
  Content's own menu, while the alias and shortcuts also work from every other app; and keeping
  inbox articles in Saved with a flag (Saved and the inbox are different places for the owner).

## 2026-10-23: The app ID is be.wimvandonck.content

- At the owner's request, the application id changed from `be.wimvandonck.reader` to
  `be.wimvandonck.content` (debug builds: `be.wimvandonck.content.debug`), before Firebase was set up
  so the push notifications are registered under the new name.
- Android treats it as a new app: it installs next to the old one, which doesn't update to it. Signing
  in with your email brings back the library (feeds, Saved, highlights, synced settings); Instapaper,
  sites signed in to and per-device settings are set up again. The old app is then uninstalled by hand.
- `assetlinks.json` lists both packages, so sign-in links and the Instapaper callback open the new
  app, and the old one keeps working until it is removed. Same signing key for both.
- The launcher alias from the package rename isn't needed any more and is gone.

## 2026-10-23: The code's package is be.wimvandonck.content

- The Kotlin package (and Android's `namespace`) is now `be.wimvandonck.content`, after the app's
  name. The **application id stays `be.wimvandonck.reader`**: phones know the installed app by it,
  and changing it would make Content a different app that can't update the old one (CLAUDE.md).
- The launcher entry keeps its old component name (`be.wimvandonck.reader.ui.MainActivity`) through
  an activity-alias, so home-screen icons and shortcuts keep working after the update.
- Room's exported schemas moved with the database class (`app/schemas/be.wimvandonck.content...`);
  the database file and its tables are unchanged.

## 2026-10-23: Feeds and tags on one screen (#222)

- Managing feeds (with their folders) and managing Saved's tags is one screen, **Feeds and tags**,
  with a tab for each, on Android and the web. ☰ keeps Manage feeds under Feeds and Manage tags under
  Saved, each opening the screen on its own tab; switching tabs doesn't add a step to Back.

## 2026-10-23: Readability as it is (#236)

- At the owner's request, the article extractors no longer change the page before Readability or
  its result after it: gone are keeping glossary words in buttons, removing hidden parts, turning a
  div per paragraph into a paragraph, joining an article split over look-alike containers (Ars
  Technica), and dropping the byline and comments link. Both apps show what Readability4J (Android)
  and Readability.js (web) find.
- Kept, because they are about showing the result rather than finding it: the reader's simple HTML
  (safe tags, absolute links, known video players), leaving out the heading that repeats the title
  shown above it, and the minimum length for an article.
- The two libraries now differ in small ways the removed steps used to even out (a div per
  paragraph: Readability4J may split it around a link). The shared sample pages check the text, not
  the exact paragraphs, there; the samples for the removed steps are gone.
- Cached full articles get a new version (Android `v4-`, web `fullArticle-v3:`), so they are
  extracted again. Texts already stored in the account (sharedText) stay as they were stored.

## 2026-10-23: Android notifications through Firebase (#235)

- At the owner's request (and with their OK for the new library and permission), the Android app can
  notify you when something is shared with you: Firebase Cloud Messaging, the only way to wake an app
  on Android without it running. New: `com.google.firebase:firebase-messaging` (through the Firebase
  BOM; no Analytics), and the `POST_NOTIFICATIONS` permission, asked only when turned on.
- Opt-in per phone (Settings › Account). Firebase's automatic start is off in the manifest, so no
  token exists, and Google isn't contacted, until it is turned on; off or signed out deletes it.
- No `google-services` Gradle plugin: `app/build.gradle.kts` reads `app/google-services.json` (from
  the `GOOGLE_SERVICES_JSON` secret in CI) into BuildConfig, and the app starts Firebase with those
  settings. Without the file the build works and the setting isn't shown.
- The message is data-only (`{"kind": "shared"}`): the app shows its own text in the phone's language,
  and taps open Saved › Shared with me. Nothing is downloaded in the background; the article comes in
  with the next sync when the app opens.
- The feed loader signs in to Google with the service account (`FCM_SERVICE_ACCOUNT`, an RS256 JWT
  with WebCrypto) and keeps the access token for its hour.

## 2026-10-23: Web notifications when something is shared (#235)

- At the owner's request, the web app can notify you when something is shared with you, even with
  the tab closed: Web Push from the feed loader (`worker/src/push.ts`) to a small service worker
  (`web/public/sw.js`). Opt-in per browser in Settings › Account; signing out turns it off.
- The push carries no payload, so it needs only a VAPID signature (ES256 with WebCrypto), no message
  encryption and no library; the browser's push service learns nothing about what was shared. The
  notification says "Something was shared with you" and opens Saved › Shared with me.
- The service worker's text comes in its address from `i18n.ts` (`?title=…&body=…`), so the wording
  stays in one place; a plain file in `public/`, not built, so it works in every browser.
- One push address per device, under its device key's hash, so the Android app can add its Firebase
  token the same way (next change).

## 2026-10-23: Direct share to people (#235)

- The four most recent recipients are published as Android sharing shortcuts (`ui/DirectShare.kt`,
  `res/xml/shortcuts.xml`), so the system share menu offers them. Picking one opens Send to a Content
  user over the other app (the share alias's screen), with the address filled in.
- AndroidX Core's `ShortcutManagerCompat`, already in the app; no new library. Only while signed in;
  signing out takes them away. Their icons are the Gravatar picture (Show images on) or the first
  letter, drawn once when the list changes.

## 2026-10-23: Remembered recipients (#235)

- Send to a Content user offers the last twelve people you sent to as chips above the address,
  each with their Gravatar picture (else their first letter) and name (else the address); one tap
  fills in the address. Sending adds the person, first.
- Kept with the synced settings (`sharing.recipients`, `docs/sync.md`), so every device offers the
  same people, rather than a new kind of row: a short list that changes rarely. Not a contacts list:
  nobody is added except by sending them something. No way to remove one yet; the list drops the
  oldest past twelve.
- Android hands the list to the dialog through a CompositionLocal set around the navigation in
  `MainActivity`, because the dialog opens from the reader and from Manage tags.

## 2026-10-23: Names from Gravatar (#234)

- Your name in Settings › Account, and the name people see on what you share, come from your public
  Gravatar profile (its display name), as the picture already did. Without a profile it stays the
  email address.
- The feed loader asks Gravatar (`POST /account/profile`), not the device, so Gravatar sees the feed
  loader rather than each phone or browser, and the app's HTTP stays in `AccountClient`. Sharing
  fills in the sender's Gravatar name on the feed loader, so a name arrives even from an older app.
- Gravatar's v3 profile API is used without an API key (public fields only, with Gravatar's rate
  limit); a failed lookup just shows the address.

## 2026-10-23: Shared with me, a place of its own in Saved (#167)

- At the owner's request, what other Content users send no longer lands in To read: it waits in
  **Shared with me**, a fourth state in Saved's state chip (To read, Shared with me, Archive, All),
  until it is moved to To read or the Archive (or removed). This replaces the "Shared with you" chip
  and menu row of the first version.
- A dot (no count) on the state chip, on Shared with me in its menu, on Saved in the menu at the left
  and on the Saved tab says something waits. No push notification yet: that needs Web Push and
  Firebase, each its own change.
- The sender can add a message (up to 1000 characters); the card shows it under "From …".
- In the sync format, `sharedBy` gains `message`, `at` and `waits` (`docs/sync.md`). An item that only
  waits has no `readingList`; `waits` keeps it from being dropped. Older apps read it as an item with
  nothing on it, which is why both apps change together.

## 2026-10-23: Long-press menus, and swiping saves (#221)

- A feed article: press and hold for **Mark as read/unread**, **Save** and **Share**; swiping
  either way saves (marking read moved from the swipe into the menu). A saved article's card: press
  and hold for **Organise** and **Share**; cards don't swipe.
- The web app has the same menus on right-click, and on press and hold on a touch screen.
- Screen readers get the menu's entries as actions on the row.

## 2026-10-23: Sharing with other Content users (#167)

- A one-time copy, as the issue suggested to start: one article, or everything with a tag. It lands
  in the other person's Saved, To read (a shared tag keeps its name as a tag there) and is theirs from
  then on; later changes on either side don't follow.
- Where shared things go in Saved (the question on the issue): a **Shared with you** group next to
  Liked, on the chips and in ☰ under Tags, once something was ever shared. Each card says who it is
  from. Not a separate section: a shared article is a saved article like any other.
- Delivery goes through an inbox in the recipient's account on the feed loader (`worker/src/share.ts`),
  encrypted and emptied as soon as a device takes it in during sync. Sending needs an email address
  on your own account, which the feed loader checks, so nobody can send in someone else's name.
- Notifications: for now the app says it in a message when something arrives. Choosing between none,
  in the app, a web notification or an email is left for later.
- The Android database gets a column for who shared an item (version 7, an automatic migration).

## 2026-10-23: Tags in the order you last used them (#220)

- Everywhere tags are listed (Saved's chips, the menu under Tags, the save sheet, Manage tags), the
  tag of the article saved last comes first: by the newest article that carries it (when it was put
  in Saved). Equal times go alphabetically. Before, the chips went by how many articles had the tag
  and the rest alphabetically, which kept new tags at the end.

## 2026-10-23: The sign-in email is in English only (#219)

- Every text in the apps is in every language; the sign-in email is the one exception. The feed
  loader only knows the address it writes to, not the reader's language, and two languages in one
  email read worse than one.
- Dutch: Delete synced data is now **Account voor Content verwijderen** (#218).

## 2026-10-23: A browser extension for the web app (#168)

- Manifest V3, one folder (`extension/`), plain JavaScript without a build step or dependencies, for
  Chrome, Edge and Firefox. Installed by hand for now; publishing (Chrome's $5 fee, Firefox free) later.
- The web app and the extension talk through window messages; the extension's bridge runs only on
  the web app's address, so no other site can use it to fetch pages with the user's cookies.
- Article pages come from the extension when it is installed (sign-ins and paywalls you pay for
  work, #180); feeds keep using the feed loader, and the extension only when the loader is refused.
- **Save to Content** opens the web app with `?save=` instead of handing over the page's HTML: the
  web app then loads the page through the extension anyway, and the extension needs no access to the
  Content account.

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
