# Reader account

Signing in with an emailed link (#166) or with Instapaper (Settings › Account, web: Settings ›
Account) signs in to the user's **Reader account**: the feed loader (`worker/`, on Cloudflare) keeps the user's feeds,
folders, read articles and settings there, so every device and the web app show the same. There is
no Reader password: Instapaper confirms who the user is, and other ways to sign in can be added
later.

## Signing in with an email link (#166)

1. Settings › Account: type the address, tap **Email me a sign-in link**. The app calls
   `POST /account/email/start` (`worker/src/email.ts`), which emails a link through Resend:
   `https://rss-reader-web.wim-d57.workers.dev/signin#token=…`. Always `204`, whether or not the
   address has an account.
2. The token is signed with a key derived from `ACCOUNT_KEY` and holds the address, when it ends (30
   minutes) and a random id. Nothing is stored until it is used.
3. Opening the link: on Android the App Link opens the app (`/signin` next to the Instapaper
   callback in `AndroidManifest.xml`; a debug build opens the web app instead), on the web the page
   reads the token. Both call `POST /account/email/finish {"token"}` and get `{"key", "email"}`. A
   link works once (`410` after), on the device or browser it is opened on.
4. Which account an address opens lives in its own Durable Object, named after an HMAC of the address:
   the account id (set the first time) and the used link ids. The address itself is not stored.
   A new address gets a new account (its id derived from that HMAC).
5. **Adding an address to an account opened with Instapaper:** Settings › Account › Add an email
   address calls `POST /account/email/add` with the device key; the emailed link adds the address to
   that account (`{"email"}`), so either way in opens the same library. An address that already
   opens another account is refused (`409`).

The account key lives in its own file (`sync/ContentAccount.kt`, DataStore `account`; web:
IndexedDB `account`), apart from the Instapaper sign-in: disconnecting Instapaper doesn't sign out,
and signing out keeps Instapaper connected. Devices signed in before #166 move their key there.
The picture next to the address comes from Gravatar (`sync/Gravatar.kt`, `gravatarUrl` in
`web/src/lib/account.ts`).

## How it works (Instapaper)

1. The app swaps Instapaper's one-time sign-in code at the feed loader (`POST /instapaper/token`,
   `docs/instapaper.md`). Instapaper's answer names the Instapaper user.
2. The feed loader finds that user's account (an id derived from the Instapaper user id and the
   `ACCOUNT_KEY` secret, so it says nothing by itself) and gives this device a new **device key**
   (`<account id>.<random secret>`). It keeps only a SHA-256 hash of the secret.
3. The device keeps the key next to the Instapaper token (Android: the `instapaper` DataStore file,
   left out of backups; web: IndexedDB) and sends it with every sync.

Each account is one Cloudflare Durable Object (`Account` in `worker/src/account.ts`) with its own
small storage: the device keys, one sync document and the item rows (`docs/sync.md`).

| Call | What it does |
|---|---|
| `GET /account/sync` | The document and its version (`{"version": n, "data": {...} \| null}`); `304` when `If-None-Match: "n"` is still the latest |
| `PUT /account/sync` | `{"baseVersion": n, "data": {...}}`: saved as version n + 1, or `409` with the current version and document when another device saved first |
| `GET /account/items?since=n` | The item rows changed after items version n: `{"version": m, "rows": [{"key", "version", "data"}], "more": bool}`, at most 500 rows per page; ask again with `since=m` while `more` |
| `GET /account/texts?key=…` | The stored text of a saved item (`{"html"}`), or `404` when none is stored |
| `POST /account/texts` | `{"key", "html"}` (HTML up to 1 MB): stored when the item has no text yet (`201`); one already stored wins and comes back (`200`) |
| `POST /account/items` | `{"rows": [{"key", "baseVersion", "data"}]}` (at most 500): each row is saved when its stored version is still `baseVersion` (0 for new), all with one new items version; the others come back in `conflicts` with what is stored now: `{"version", "saved": [{"key", "version"}], "conflicts": [...]}` |
| `GET /account/live?key=…` | A WebSocket that sends `{"version": n, "items": m}` whenever a device saves (and once right after connecting) |
| `POST /account/share/check` | `{"email"}`: `{"exists"}`, whether that address has a Content account (#167) |
| `POST /account/share` | `{"to", "from": {"email", "name"}, "items": [...], "tag"?, "message"?}`: into the other account's inbox; `404` without an account there, `403` when `from` isn't this account's address |
| `GET /account/inbox` | What others shared with this account: `{"messages": [...]}` |
| `POST /account/inbox/done` | `{"ids"}`: taken in, so they leave the inbox |
| `GET /account/push/key` | `{"publicKey"}`: the VAPID public key for Web Push (#235), or `404` when notifications aren't set up |
| `PUT /account/push` | `{"kind": "web", "endpoint"}` (a browser) or `{"kind": "fcm", "token"}` (the Android app's Firebase token): where this device gets a notification when something is shared with the account (one per device, encrypted, forgotten on sign-out); `DELETE` takes it back. Sharing then pokes every device's address with an empty, VAPID-signed Web Push message and forgets addresses the push service says are gone |
| `POST /account/profile` | `{"email"}`: `{"name"}`, the name on that address's Gravatar profile, or `""` (#234). The feed loader asks Gravatar (`api.gravatar.com/v3/profiles/<SHA-256 of the address>`); sharing also fills in the sender's name this way when the device sent none |
| `POST /account/signout` | Forgets this device's key |
| `DELETE /account` | Deletes the document and every device key |

All calls send the key as `Authorization: Bearer <key>` (the WebSocket, which a browser can't give
headers, in the address). A wrong or forgotten key gets `401`: the app then asks to sign in again.

## Stored texts

Each saved article gets one text in the account, the article as the reader shows it, so every
device shows the same text and highlights find their passage in it (`data/SharedText.kt`,
`web/src/lib/sharedText.ts`). The first device to open a saved article that needs its full text
extracts it and stores it; every other device takes that text and doesn't fetch the page at all,
which also helps the web app with sites that block the feed loader. When two devices extract it
at the same moment, the one stored first wins and both show it. Offline or signed out, a device
shows its own extraction. Texts are compressed and encrypted like item rows (tied to their account
and item), and are deleted with the rest of the account. A text that is no longer saved stays until
the account is deleted (later: removed with its item).

## Encryption

The document is compressed (gzip) and encrypted with AES-256-GCM before it is stored, with a key
derived for each account from the `ACCOUNT_KEY` secret (HKDF-SHA-256). The encrypted data is tied to
its account and version, so it can't be copied to another account or rolled back. Cloudflare also
encrypts its storage. This protects against someone getting a copy of the stored data; the feed
loader itself (and so the owner, who controls it) can decrypt it, as with most apps of this kind.

Item rows are encrypted the same way, each on its own (not compressed), tied to its account, id and
version. They are stored under an id derived from the row's key with `ACCOUNT_KEY` (HMAC-SHA-256), so
the stored names don't show links or feed addresses.
End-to-end encryption (a passphrase on every device) was considered and not chosen, see
`docs/decisions.md`.

## Signing out and deleting

- **Sign out** forgets this device's key on the server and on the device. Instapaper stays connected (#166).
  The library stays on the device; other devices keep syncing.
- **Delete synced data** (Settings › Account) deletes the document, the item rows and every device key. Every
  device is signed out of sync; the library stays on each device. Signing in again starts a new
  document from that device's library.

## Owner setup (once)

The Durable Objects are created by the deploy itself (`wrangler.toml`, `[[durable_objects.bindings]]`
and `[[migrations]]`); there is no database to create. Only the encryption secret is needed:

1. Make a random key of at least 32 characters (a password manager's generator, 40 or more
   characters, or `openssl rand -base64 32`). Keep a copy in the password manager.
2. Cloudflare dashboard › Workers & Pages › `rss-reader-worker` › Settings › Variables and Secrets ›
   Add, type **Secret**, name `ACCOUNT_KEY`, the key as value. (Or `npx wrangler secret put ACCOUNT_KEY` in `worker/`.)

For email sign-in (#166), the Resend API key goes in the same place, as a **Secret** named
`RESEND_API_KEY` (a plain text variable would be removed by the next deploy, which writes the
`[vars]` of `wrangler.toml`). The sender (`EMAIL_FROM`) and where links point (`WEB_APP_ORIGIN`)
are in `wrangler.toml`; the domain resend.wimvandonck.be must be verified in Resend. Without the
key, Email me a sign-in link says the email couldn't be sent (`503`).

For Android notifications, the Firebase service account's JSON key goes in the same place as the
**Secret** `FCM_SERVICE_ACCOUNT` (`docs/installation.md`, "Notifications"); the feed loader signs in to
Google with it and sends each phone a data-only message (`{"kind": "shared"}`).

For notifications when something is shared (#235, `worker/src/push.ts`), the private half of the
VAPID key pair goes in the same place, as a **Secret** named `VAPID_PRIVATE_KEY` (its public half is
`VAPID_PUBLIC_KEY` in `wrangler.toml`). Without it, `GET /account/push/key` answers `404` and the
apps don't offer notifications. A new pair means every browser has to turn notifications on again.

Without it, sign-in still works for Instapaper, sync says it isn't available, and the account calls
answer `503`. **Never change or lose the key**: the stored documents can then no longer be read and
the account ids change, so every device would start over from its own library.

## Limits and cost

Durable Objects with SQLite storage are part of Cloudflare's free plan: 100,000 requests a day for
the whole account and 5 GB of storage. A device on screen makes one live connection and a request per
change (and one a minute only while the live connection is down); an idle connection costs nothing
(hibernation). The document (folders, feeds, settings) is small; everything that grows is an item
row (at most 64 KB each), so an account has no size limit beyond the plan's 5 GB. Each saved row
costs three storage writes (the free plan allows 100,000 a day): marking 500 articles read is 1,500.
Rows that were deleted stay as small markers, so other devices learn of the removal. A stored text
is typically 5 to 15 KB once compressed (one write when stored, one read per device that opens it),
so 1,000 saved articles take some 5 to 15 MB of the 5 GB.
