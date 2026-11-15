# Newsletter addresses

An email address per tag, and one for No tag, that turns the newsletters sent to it into a feed:
Kill the Newsletter's idea, built into Content's feed loader (`worker/src/newsletters.ts`). Signed
in to a Content account, every tag simply has an address such as `content+k7q2m9abcdef@in.wim.to`
(2026-11-12), and its feed, called **Newsletters**, under that tag, so new issues show in Feeds like
any article: unread, Mark all as read, the chips' dots, the reader and Search all work.

## In the apps

- **Made by the apps, one per tag:** after a sync whose tags changed, and whenever the Newsletter
  feeds page opens, the apps ask the feed loader for every tag's and No tag's address that is
  missing (`NewsletterAddresses.missing`, the web's `missingTags`). The feed loader gives each tag one
  address and hands back the same one when asked again, also when two devices ask at once. It knows
  a tag only by the SHA-256 of its id (`tagHash`): a tag's id can hold its name, which never leaves
  the device.
- **The feeds follow the addresses** (`NewsletterAddresses.reconcile`, the web's `reconcile`): one
  Newsletters feed per address whose tag is in the library, under that tag (titled in the app's
  language when it is added, with its own name so a refresh keeps it); a newsletter feed of no
  address, or of a tag that is gone, is removed, and one under another tag goes back.
- **Nothing by hand:** + › Add feed › **Newsletter feed** and Settings › **Newsletter feeds** show
  the same page (`ui/home/AddChoiceSheet.kt`, `NewsletterFeedPage`; the web's `NewsletterDialog` in
  `Dialogs.tsx`): the tag chips (No tag first, **Edit tags** after them) pick a tag
  (`NewsletterAddresses.chosenTag`, the web's `chosenTag`: the first tag until you pick one), and the
  page shows its address with **Copy address** and **Share** (on the web only where the browser can
  share), and what its feed has received (`NewsletterAddresses.activity`, the web's
  `newsletterActivity`). Newsletter feeds don't appear in RSS feeds' Manage feeds, so they can't be
  renamed, moved or removed. Without a Content account, the page offers Sign in.
- **The sender, not "Newsletters":** each newsletter shows who sent it in the feed's place, in the
  list and above the reader's title (`NewsletterAddresses.sender`, the web's `senderOf`), with the
  `/favicon.ico` of the sender's website and a small envelope on its corner
  (`hasEnvelopeBadge`), so it still reads as a newsletter; the reader then leaves the sender out of the line under
  the title. The feed loader takes the website from the sender's address, without a mail
  subdomain (`senderSite`: news@email.example.com → https://example.com, hi@x.example.co.uk →
  https://example.co.uk), and puts it in the entry as Atom's `<author><uri>`; the apps keep it as
  the article's `authorUrl`. Emails kept before 2026-11-15 had no website; the feed loader looks
  for it once per email (`guessSite`): the address when the sender's name was one, or else the
  website the email links to most (at least twice), leaving out mailing services, social networks
  and link shorteners. It keeps what it found (`siteGuessed`, found or not), at most 10 emails per
  feed load (`BACKFILL_PER_LOAD`) to stay within a request's CPU time, so the apps get the website
  at their next refreshes. An email where none is found shows the sender's name with the envelope.
- **Each sender a source of its own** (2026-11-15, `NewsletterSenders`, the web's
  `newsletterSenders.ts`): a sender is its website (`keyOf`, the web's `senderKey.ts`: bozar.be, so
  newsletter@bozar.be and events@bozar.be are one), on a platform many newsletters send from
  (`SHARED_SITES`: substack.com, beehiiv.com, gmail.com, …) its website and its name
  (`substack.com/platformer`), and for emails without a website its name (`name:de standaard`).
  Filtering on a Newsletters feed keeps the sender on every row; the feed's senders then follow it
  as chips (`FeedChips.senders`, the web's `feedChipRow`), each a selection of its own
  (`FeedSelection.Sender`, the web's `{kind: "sender"}`) with the senders combined into it. The
  Newsletter feed page lists the tag's senders under the address, each with a page: a name of its
  own, Mute (its newsletters are left out of the lists, counts and chips, `withoutMuted`, as hidden
  words do), Combine with (merged into another sender, which keeps its name and icon; what was
  combined into it moves along) and Separate. The page also opens where the sender is (2026-11-15):
  a sender chip's long press (the web: right-click or press and hold), a newsletter's long press ›
  About <sender> (beside **Mute <sender>**), and the sender line above a newsletter's title in the
  reader (`NewsletterSenders.senderOf`, the web's `senderOfArticle`). Mute and Combine with show a
  message with Undo, which puts back the choices as they were. The choices are one synced setting,
  `articles.newsletterSenders` (`docs/sync.md`). The feed loader is unchanged: the full address
  never leaves it.
- **A cleaner email** (2026-11-15, `EmailCleanup`, the web's `emailCleanup.ts`): before a newsletter
  (an article of a Newsletters feed, never an ordinary RSS article) becomes the reader's blocks, the
  email's own chrome is left out: a "View in browser" / "View online" / "Read online" / "Bekijk in je
  browser" / "Online bekijken" style link near the top (at most 400 characters of text before it, the
  link's whole text at most 80 characters, with "Having trouble viewing this email?" right before it;
  English, Dutch, French, German, Spanish and Italian), hidden preheader text (`display:none` always;
  `max-height:0`, `opacity:0`, `font-size:0` and `mso-hide:all` only on plain text of at most 500
  characters, so a wrapper around pictures or links stays) and spacer pictures (a name like
  `spacer.gif` or `blank.gif`, or 0 or 1 pixel in the picture's style). Conservative: an element whose
  end can't be found stays, and so does everything else. The original is one tap away: the reader's
  ⋮ menu reads **Show original email** instead of Open in browser, and opens the email's own page
  (below) in the in-app browser (a new tab on the web). Pictures that are tracking pixels of mail
  platforms are never loaded, in any article (see PRIVACY.md).
- **Unsubscribe** (2026-11-15, `NewsletterUnsubscribe`, the web's `unsubscribe.ts`): an
  **Unsubscribe** button on a sender's page, and in the reader's ⋮ for a newsletter feed's email
  (`ReaderMenu`, the web's `readerMenu`, after Show original email). The feed loader unsubscribes, not the
  device (the owner's choice): the app takes the sender's newest email (`newestOf`, the web's
  `newestEmailOf`; the id from the entry's `urn:content:newsletter:<code>:<id>`) and POSTs its id
  to `<feed URL>/unsubscribe` (Android in `sync/AccountClient.kt`'s `NewsletterUnsubscriber`). The
  answer decides: `{done}` shows "Unsubscribed from <sender>" with **Mute** (which mutes it with
  Undo, so the issues already here leave the list); `{open}` opens that https page in the browser
  (Android a custom tab; the web a new tab without its opener, and when the browser blocks it as a
  pop-up because the answer took a while, a message with **Open**); `{mailto}` opens a new email;
  `{none}` says the sender offers none here and to open its newest email; a failure says it
  couldn't. Only https pages and `mailto:` links are opened (`answerOf`).
- **Removing a tag** removes its Newsletters feed (`Library.removeFolder`, the web's `removeTopic`)
  and its address (`DELETE /account/newsletters`). An address whose tag is gone otherwise (merged
  into another tag, or removed while offline) is removed a day after it was made
  (`NewsletterAddresses.orphans`): sooner, its tag may still be on its way from another device.
- **Addresses from before 2026-11-12**, made by name, are no longer listed; the next request for an
  address removes them with their emails.

## How it works

- **Receiving:** Cloudflare Email Routing sends `NEWSLETTER_ADDRESS` (`worker/wrangler.toml`,
  `content@in.wim.to`) to the Worker, whose `email` handler (`receiveEmail`) takes the code after the
  `+` (Email Routing's subaddressing delivers `content+<code>@…` by the rule for `content@…`). On a domain
  of its own with a catch-all, `<code>@…` works too.
- **Storing:** each code is an inbox, a Durable Object of the `Account` class named
  `newsletter:<code>` (no class of its own, so no migration). It knows its owner and keeps
  the newest 100 emails, none older than 90 days, encrypted with a key derived from `ACCOUNT_KEY`:
  the sender's name, their website (`site`, from the address's domain), the subject and the HTML
  (cut at 512 KB; attachments dropped). Emails over 4 MB
  are refused.
- **The feed:** `GET /newsletters/<code>/<token>`, an Atom feed of the newest 50, titled Newsletters.
  The token is derived from the code with `ACCOUNT_KEY`, so the email address alone doesn't open
  the feed. Each entry links to `/newsletters/<code>/e/<id>/<emailToken>`, the email as a page in a
  sandbox (no scripts or forms), for Open in browser, Share and Save. Its token (`emailToken`) is
  derived from the code and the email's id, so the link opens that one email and never the feed
  (2026-11-15; before, the link was the feed's address with `/<id>`, which still opens the email for
  articles kept from then). Report a problem isn't offered for a newsletter
  (`NewsletterAddresses.isNewsletterEmail`, the web's `isNewsletterEmail`): its report is public. The Android app fetches the feed directly; the web app's feed loader

  the feed. Each entry links to `…/<id>`, the email as a page in a sandbox (no scripts or forms), for
  Show original email (the reader's Open in browser) and Save. The Android app fetches the feed directly; the web app's feed loader
  answers its own address itself.
- **Unsubscribing:** receiving keeps an email's `List-Unsubscribe` and `List-Unsubscribe-Post`
  (`unsubscribeOf`: the first https address and the first `mailto:` of its `<…>` list; `oneClick`
  for `List-Unsubscribe=One-Click` with an https address) as `unsubscribe`, encrypted with the rest.
  `POST /newsletters/<code>/<token>/unsubscribe` with `{"id": "<16 hex>"}`, checked with the feed's
  token (no sign-in), asks the inbox for that email (`nlUnsubscribe`: its `unsubscribe` and HTML)
  and answers (`unsubscribeAnswer`): with one-click, the Worker POSTs `List-Unsubscribe=One-Click`
  (`application/x-www-form-urlencoded`) to the https address, only when `checkTarget` accepts it
  (https, a public host name, no numbers or `localhost`/`.local`/`.internal`), without following
  redirects and within 10 seconds, and answers `{"done": true}` on a 2xx; else `{"open": <the https
  address>}`; else `{"mailto": …}`; else the first link in the HTML whose text or address says
  unsubscribe (`unsubscribeLinkOf`, in English, Dutch, French, German, Spanish and Italian:
  unsubscribe, uitschrijven, afmelden, désabonner, se désinscrire, abbestellen, abmelden, darse de
  baja, cancelar suscripción, annulla iscrizione, disiscriviti) as `{"open"}` when `checkTarget`
  accepts it; else `{"none": true}`. Emails kept before 2026-11-15 have no `unsubscribe`, so only
  the HTML is looked at. The web app calls the route directly, with CORS for `ALLOWED_ORIGINS` (a
  preflight, since it sends JSON); the Android app sends no Origin. The rate limiter applies when
  configured.
- **The account:** `GET /account/newsletters` lists the addresses (`{code, tagHash, address,
  feedUrl, createdAt}`), `POST` with `{"tagHash"}` (null: No tag) gives that tag's address, made the
  first time (201) or the one it has (200), at most 200; `DELETE ?code=` removes one and its emails;
  deleting the account deletes them all.
- **CPU:** the Workers Free plan gives 10 ms of CPU per request. Receiving only parses the email
  (`postal-mime`) and stores it, inside the inbox's Durable Object. A very large newsletter could
  still go over; the Workers Paid plan lifts the limit.

## One-time setup (owner, Cloudflare dashboard)

Code alone can't do this: Email Routing is switched on in the dashboard, and a catch-all on a
subdomain isn't possible, hence one rule with subaddressing.

1. **wim.to › Email › Email Routing.** Turn it on **for the subdomain `in.wim.to` only**
   (Settings › Subdomains › Add subdomain › `in`); Cloudflare adds its MX and TXT records. Never let
   it change the MX records of `wim.to` itself: that is where the owner's own email arrives. Check
   under DNS › Records that they are unchanged.
2. **Email Routing › Settings:** turn on **Subaddressing**.
3. **Routing rules › Create address:** `content` @ `in.wim.to`, action **Send to a Worker**, destination
   **content-worker** (once this Worker, with its `email` handler, is deployed).
4. Test: open Settings › Newsletter feeds in the app, send an email to a tag's address, and refresh the feed.

Another domain (or address) means changing `NEWSLETTER_ADDRESS` in `worker/wrangler.toml`; addresses
already made keep the domain they were made with only in what the apps show, so change it before
anyone uses them.
