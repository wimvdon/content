# Privacy policy

_Last updated: 2026-10-23 (people you shared with)_

Reader has no analytics, advertising or crash reporting. It works without an account. If you sign
in (with your email address, or with Instapaper), Reader keeps your feed list,
read articles, saved articles (with their folders, notes, tags and highlights) and settings on its own
server (the owner's Cloudflare Worker), encrypted, so your devices stay the same.

## What the app sends, and to whom

- **The feeds you add.** To show new articles, the app downloads each feed you subscribed to
  from its own website: when the app starts, when you pull down to refresh, and when you add or
  import a feed. When you add a website, the app also loads that page and, if needed, a few common
  feed addresses on the same site to find its feed. These websites see your IP address and the
  app's name and version (the "user agent"), like any visit. No cookies or identifiers are sent.
- **Article pages.** When you open an article whose feed has only a summary, the app downloads
  that article's web page to show the whole article (turn this off in Settings › Reader › Load
  full articles; you can also switch it per article). Only the article text is kept; the page's
  scripts, images and trackers are not loaded. The website sees the same as for its feed.
- **Pictures.** Article pictures, list thumbnails and video previews are downloaded from where
  the feed or article says they are: usually the website itself or its image server (CDN), and
  for YouTube videos i.ytimg.com. Those servers see your IP address, like any visit. Turn this off
  in Settings › Reader › Show images. Embedded videos and posts are not played inside the app;
  tapping one opens it in its app or your browser.
- **Website icons.** Once per feed, the app loads the home page of the feed's website to find
  the site's small icon (favicon), and then downloads that icon (from the site, or wherever the
  site says it is, often its image server). If the site names none, its `/favicon.ico` is tried.
  The icon is shown next to the feed's name. These servers see the same as for the feed.
  A saved article from a website you don't follow shows that website's `/favicon.ico`, loaded from
  the same website the article itself comes from.
- **Signing in with your email address (#166).** Settings › Account › Email me a sign-in link
  sends the address you type to the owner's feed loader, which emails you a link through Resend
  (resend.com, from resend.wimvandonck.be), under Resend's privacy policy: Resend sees the address
  and the email. The link works once, for 30 minutes. The feed loader doesn't store the address:
  it keeps only a scrambled code of it (HMAC, with a secret only the feed loader has) to find your
  account, and the ids of used links. The app keeps the address on your device, to show it and
  your picture: signed in with an address, the app loads your picture from Gravatar
  (gravatar.com/avatar/ followed by a scrambled form, SHA-256, of the address), under Gravatar's
  privacy policy; there is none when the address has no Gravatar. Turn pictures off in Settings ›
  Reader › Show images to skip it. Your name comes from your public Gravatar profile too (#234): the
  app asks the owner's feed loader, which asks Gravatar (api.gravatar.com) with the same scrambled
  form of the address, so Gravatar sees the feed loader, not you. When you share with another Content
  user, the feed loader looks up your Gravatar name the same way, so they see who it is from.
  **People you shared with** (#235): the app remembers the last twelve addresses you sent something
  to and keeps them with your synced settings (encrypted, see Sync below), so every device offers
  them. To show them, it loads their Gravatar picture (as for yours, unless pictures are off) and asks
  the feed loader for their Gravatar name, which asks Gravatar with the scrambled form of the address.
- **Sync with your Reader account (off unless you sign in).** While the app is
  on screen (when it opens, a second after a change, on refresh, on Sync now, and at once when
  another of your devices changed something; never in the background) it reads and saves your data
  in your account on the owner's feed loader (a Cloudflare Worker, built from `worker/`, at
  rss-reader-worker.wim-d57.workers.dev): one document with your feed list (addresses, names,
  folders, website and icon addresses), your folders and two settings (Load full articles and Mark
  as read on scroll; not the view settings such as theme, text settings, pictures and list view, the
  Unread filter, or the Save and Share targets, which stay on each device); and one record per item:
  which articles you read in the last 120 days (feed address, article id and date), and each article
  you saved (its address, title, picture address, the feed it came
  from, when you added it, whether you archived or liked it, its collection, tags, your note, your
  highlights with the highlighted passage and a few words around it, and how far you read it).
  For each article you saved it also keeps one copy of its text as the reader shows it (the
  article cleaned up from its web page, with picture addresses but not the pictures), stored by
  the first of your devices that opens it, so every device shows the same text and your
  highlights line up. It doesn't hold the text of articles you only read in a feed, your
  Instapaper token or your Instapaper password. While the app
  is on screen it also keeps a connection open to the feed loader that only says when the document
  changed. The document and each record are stored encrypted (AES-256-GCM, with a key for your
  account derived from a secret that only the feed loader has) in Cloudflare's storage (Durable
  Objects), the records under scrambled names that don't show their addresses. The feed loader can
  decrypt them to send them to your devices; the owner could too, but doesn't look at them. Your account is found by a code derived from your email address or your Instapaper user number; each device gets
  its own key, of which only a scrambled form (hash) is stored. Nothing is logged. Cloudflare sees
  your IP address, under Cloudflare's privacy policy. **Sign out** (Settings › Account) forgets this
  device's key; **Delete synced data** deletes the document, the records, the stored article texts and every device's key from the server
  at once (your devices keep their own copy of the library).
- **Instapaper (off unless you sign in).** Settings › Instapaper › Sign in opens Instapaper's own
  page in your browser, where you approve Reader; Reader never sees your Instapaper password.
  Instapaper then sends back a one-time code, which the app gives to the owner's feed loader (a
  Cloudflare Worker, built from `worker/`). The loader adds Reader's client secret, swaps the code
  for an access token at Instapaper and hands the token back without keeping it. From Instapaper's
  answer it also takes your Instapaper user number to find your Reader account and give this device
  its key (see Sync above); nothing is logged, and Cloudflare sees your IP address, under
  Cloudflare's privacy policy. With the token the
  app talks to www.instapaper.com (the Android app directly, the web app through the feed loader,
  because browsers aren't allowed to call Instapaper), under Instapaper's privacy policy. The
  Android app keeps your reading list and your Instapaper account the same while it is open: it
  reads everything in your account and what changed since its last look, and sends the title, link,
  tags, like, archive and reading progress of the articles on your reading list, and removes from
  Instapaper what you take off the reading list. On the app owner's own account it also asks
  Instapaper for its text of an article you open, which is kept on your device only. The web app
  reads and changes your saved list (archive, like, delete, move to a folder, tags, folders).
  **Sync highlights with Instapaper** (off unless you turn it on, per device): the passage you
  highlighted and its note go to that article in Instapaper, and the app reads the article's
  highlights there (when you open it, and before sending), so ones made in Instapaper come into
  Content and none is made twice. Signing out deletes the token and the account key from
  your device; the token stays valid at Instapaper until you revoke it there.
- **Raindrop.io is no longer supported** (#179). The app no longer talks to raindrop.io. Updating
  the app deletes a Raindrop sign-in it still kept; revoke the app in Raindrop's settings to end
  it there too.
- **Sites you sign in to (Android, #180).** ⋮ › Sign in to this site opens that site's own page
  in a small browser inside the app, where you sign in as on any website (the site sees what it
  always sees, and its page loads its own scripts there). Done keeps the cookies that site set, on
  this phone only, in a file of their own that Android's backup and phone transfer leave out; they
  are never synced and never sent to the owner's feed loader. From then on the app sends them only
  to that site (and its subdomains), over https, when it loads that site's feeds and articles, so
  articles you pay for load in full. Settings › Sites you're signed in to › Remove forgets them.
- **Sharing with another Content user (#167).** Only when you send something: ⋮ › Send to a
  Content user (one article) or Manage tags › the people icon (a tag's articles). The app first asks
  the owner's feed loader whether the address you typed has a Content account. What you send (each
  article's address, title, picture address and short description, and the tag's name) and your own
  email address go to that person's account, so their Saved shows it came from you. It waits there,
  encrypted like everything in an account, until one of their devices takes it in, and is then
  deleted from the server. Nothing is shared unless you share it, and nobody can send something in
  your name: the feed loader checks the address is yours.
- **Reporting a badly cleaned-up article** (⋮ › Report a problem with this article, #181): Send
  opens a pre-filled "new issue" page on github.com in your browser, with the article's link, what
  you picked, your comment, and the app's name and version; not the article's text. Nothing is sent
  until you submit it there with your GitHub account, and the issue is then public, under GitHub's
  privacy policy.
- **Links you open** (Read on website, links in articles) open in your browser, under the
  website's own privacy policy.
- **Links you add or share to Reader.** A link put on the reading list without a title (Add link,
  or shared from another app) is loaded once from its website to read the page's title, description
  and picture, like opening the article. The website sees the same as for an article page.
- **Save and Share.** By default Save keeps the article's title and link on the app's own reading
  list on your phone (synced as described above when you are signed in). When you set Save or Share to an app in Settings, or pick one in the
  share menu, the title and link go to that app, under its privacy policy. Signed in to Instapaper
  (see above), the reading list is also kept in your Instapaper account. Nothing is sent otherwise.

## What the app stores on your device

- Your feed list (with each feed's icon address) and folders, the downloaded articles and which ones you read.
- If you are signed in: a copy of the synced document and records as last synced, to merge changes, and read marks of articles another device read that this one hasn't downloaded yet.
- Your settings (theme, text settings, full articles, pictures, Save and Share targets, the Unread filter, mark as read on scroll).
- Your reading list: each article's address, title, description, picture address, when you added
  it, whether you liked or archived it, its tags and how far you read it (in the app's database; on
  the web, the browser's storage). Without an account it is not sent anywhere; signed in, see above.
- The text of full articles you opened, and recently shown pictures, in the app's cache (Android
  may clear it).
- If you signed in: this device's Reader account key and your email address, in a file of their
  own (`datastore/account`); if you connected Instapaper: the access token and your Instapaper user
  name (`datastore/instapaper`). Android's backup and phone-to-phone transfer leave both out.

Android's own backup (to your Google account, if you turned that on) and phone-to-phone transfer
include the feed list, articles, read state, settings and the articles saved without an account, not the cache and not the Instapaper sign-in. Removing a feed deletes its articles; uninstalling the app deletes everything.

## Background activity

None. The app only downloads feeds and articles, and syncs, while you use it.

## Permissions

- **Internet:** to download feeds and articles, and to sync.

The app also declares that it looks for apps that accept shared links, so Settings › Save and
share can list them. This is not a permission and reveals nothing to those apps. Reader itself
accepts links shared from other apps; it saves them to Saved exactly like its Save button, and
only when you pick Reader in the share menu.

## The web app

The web version (built from `web/`, published by the owner on Cloudflare at
`https://rss-reader-web.wim-d57.workers.dev`) runs in your browser and keeps your feed list, articles, read state and settings in the
browser's storage on that device (IndexedDB). There are no analytics; the only account is the
Reader account that signing in with Instapaper opens.

- **Feeds and article pages** go through the owner's feed loader (a Cloudflare Worker, built
  from `worker/`), because browsers don't let a web page load most feeds directly. It loads
  only the address the web app asks for and passes it back; it stores none of it and logs
  nothing, and only the web app loads feeds through it. Cloudflare runs it, so Cloudflare sees which feed or page is
  loaded and your IP address, under Cloudflare's privacy policy. It keeps a copy of a feed for at
  most five minutes in Cloudflare's cache, shared by everyone reading that feed. For an article
  page it passes on your browser's preferred languages (as your browser would), so the site
  answers in your language; the site never sees your IP address, only Cloudflare's. The Android app
  uses it only to finish signing in to Instapaper and for sync (see above).
- **Pictures and website icons** load directly from the websites, without telling them which
  page you came from.
- **Embedded videos and posts** show as a card; nothing plays inside the page. YouTube cards
  show the video's preview picture from i.ytimg.com, which then sees your IP address. Clicking
  a card opens the video or post on its own website in a new tab.
- **Sync** works as described above, through the same feed loader: the web app reads and saves
  the same document in your Reader account and keeps the same live connection while the page is
  visible. Whether a sidebar section is folded shut is remembered in the browser's local storage.
- **Instapaper** (off unless you sign in, in Settings › Account): the page's sign-in leaves for
  Instapaper's own website and returns to the web app, which swaps the one-time code for an access
  token (and this browser's Reader account key) through the feed loader. Both are kept in the
  browser's storage (IndexedDB) only. While the page is open, Saved is kept the same as your
  Instapaper account through the feed loader to www.instapaper.com: it reads what changed there and
  sends the title, link, tags, like, archive, folder and reading progress of your saved articles,
  and removes there what you remove from Saved. The loader passes
  the token on, and keeps and logs nothing.
- Removing the site's data in your browser deletes everything the web app stored.

If this changes, this file will be updated **before** the change is released.

Questions: open an issue in this repository.

## The browser extension (optional, #168)

The Content browser extension (`extension/`) only works with the Content web app: it runs on the web
app's own address and nowhere else. When the web app opens an article, it asks the extension to load
that page from your browser, with your browser's cookies for that site (so sites you're signed in to
load as you see them); that request goes from your computer straight to the site, not through the
owner's feed loader. **Save to Content** opens the web app with the page's address and title. The
extension keeps nothing, sends nothing anywhere else, and doesn't look at the sites you visit. It
asks for access to all sites only because articles can be on any site.
