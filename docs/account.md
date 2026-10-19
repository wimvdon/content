# Reader account

Signing in with Instapaper (Settings › Account, web: Settings › Account) also signs in to the
user's **Reader account**: the feed loader (`worker/`, on Cloudflare) keeps the user's feeds,
folders, read articles and settings there, so every device and the web app show the same. There is
no Reader password: Instapaper confirms who the user is, and other ways to sign in can be added
later.

## How it works

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
| `POST /account/items` | `{"rows": [{"key", "baseVersion", "data"}]}` (at most 500): each row is saved when its stored version is still `baseVersion` (0 for new), all with one new items version; the others come back in `conflicts` with what is stored now: `{"version", "saved": [{"key", "version"}], "conflicts": [...]}` |
| `GET /account/live?key=…` | A WebSocket that sends `{"version": n, "items": m}` whenever a device saves (and once right after connecting) |
| `POST /account/signout` | Forgets this device's key |
| `DELETE /account` | Deletes the document and every device key |

All calls send the key as `Authorization: Bearer <key>` (the WebSocket, which a browser can't give
headers, in the address). A wrong or forgotten key gets `401`: the app then asks to sign in again.

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

- **Sign out** forgets this device's key on the server and the Instapaper sign-in on the device.
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
Rows that were deleted stay as small markers, so other devices learn of the removal.
