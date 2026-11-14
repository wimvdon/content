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
  the sender's name, the subject and the HTML (cut at 512 KB; attachments dropped). Emails over 4 MB
  are refused.
- **The feed:** `GET /newsletters/<code>/<token>`, an Atom feed of the newest 50, titled Newsletters.
  The token is derived from the code with `ACCOUNT_KEY`, so the email address alone doesn't open
  the feed. Each entry links to `…/<id>`, the email as a page in a sandbox (no scripts or forms), for
  Open in browser and Save. The Android app fetches the feed directly; the web app's feed loader
  answers its own address itself.
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
