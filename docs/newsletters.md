# Newsletter addresses

An email address per tag that turns the newsletters sent to it into a feed: Kill the Newsletter's
idea, built into Content's feed loader (`worker/src/newsletters.ts`). Signed in to a Content account,
+ › Subscribe to newsletter › Create address picks a tag and gives an address such as
`content+k7q2m9abcdef@in.wim.to`; the app subscribes to its feed under that tag, so new issues show in
Feeds like any article: unread, Mark all as read, the chips' dots, the reader and Search all work.

## In the apps

+ › Subscribe to newsletter › **Your own address** (`ui/home/AddChoiceSheet.kt`, the web's
`Dialogs.tsx`), like Add feed: a name for the feed (sent as the address's `tag`, which titles its
feed) and a topic, then Create address, which calls the feed loader and subscribes to the feed under
that topic, and shows the address with Copy. A newsletter address's feed (`NewsletterAddresses`, the
web's `newsletterAddresses.ts`, recognised by its `/newsletters/<code>/<token>` address) has an
envelope as its icon, "Email address" in Manage feeds, and its email address with Copy in Edit feed
(read from `GET /account/newsletters`). Removing it removes the address (`DELETE
/account/newsletters`). Without a Content account, the page offers Sign in.

## How it works

- **Receiving:** Cloudflare Email Routing sends `NEWSLETTER_ADDRESS` (`worker/wrangler.toml`,
  `content@in.wim.to`) to the Worker, whose `email` handler (`receiveEmail`) takes the code after the
  `+` (Email Routing's subaddressing delivers `content+<code>@…` by the rule for `content@…`). On a domain
  of its own with a catch-all, `<code>@…` works too.
- **Storing:** each code is an inbox, a Durable Object of the `Account` class named
  `newsletter:<code>` (no class of its own, so no migration). It knows its owner and tag and keeps
  the newest 100 emails, none older than 90 days, encrypted with a key derived from `ACCOUNT_KEY`:
  the sender's name, the subject and the HTML (cut at 512 KB; attachments dropped). Emails over 4 MB
  are refused.
- **The feed:** `GET /newsletters/<code>/<token>`, an Atom feed of the newest 50, titled by the tag.
  The token is derived from the code with `ACCOUNT_KEY`, so the email address alone doesn't open
  the feed. Each entry links to `…/<id>`, the email as a page in a sandbox (no scripts or forms), for
  Open in browser and Save. The Android app fetches the feed directly; the web app's feed loader
  answers its own address itself.
- **The account:** `GET /account/newsletters` lists the addresses, `POST` with `{"tag"}` makes one
  (at most 50), `DELETE ?code=` removes one and its emails; deleting the account deletes them all.
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
4. Test: make an address in the app, send it an email, and refresh the feed.

Another domain (or address) means changing `NEWSLETTER_ADDRESS` in `worker/wrangler.toml`; addresses
already made keep the domain they were made with only in what the apps show, so change it before
anyone uses them.
