# Feed loader (Cloudflare Worker)

Browsers don't let a web page load most feeds and article pages (CORS). The web app in `web/`
asks this Worker instead: `GET https://<worker>/?url=<https address>`.

What it does, and doesn't:

- Fetches exactly the address it is given (https only, public host names only, at most 5
  redirects, at most 10 MB) and passes the answer back, with the final address in `X-Final-Url`.
- Passes only feeds and web pages (XML, HTML, JSON, text). Pictures load directly in the browser.
- Answers only the web app's own address(es) in `ALLOWED_ORIGINS`.
- At most 120 requests per minute per IP address (`LIMITER`).
- Stores nothing of what it loads and logs nothing (`observability` off). Cloudflare's cache keeps a
  feed for 5 minutes so many readers of one feed cost one download.
- The Android app uses it only to finish signing in to Instapaper (`POST /instapaper/token`) and for
  the Reader account (`/account/...`).

## Deploying

Once, in the Cloudflare dashboard (free plan):

1. Workers & Pages › Create › Import a repository › this repository.
2. Name `content-worker` (it must match `name` in `wrangler.toml`). Root directory: `worker`. Build command: none. Deploy command: `npx wrangler deploy`.
3. After the first deploy, note its address (`https://content-worker.<you>.workers.dev`) and
   put it in the web app's settings (see `web/README.md`).
4. Put the web app's address in
   `ALLOWED_ORIGINS` in `wrangler.toml` (or under the Worker's Settings › Variables).

Every push to `main` then deploys the new version.

## Developing

```sh
npm ci
npm test
npm run typecheck
npx wrangler dev
```

## Instapaper

`src/instapaper.ts` answers `/instapaper/...` (details in `docs/instapaper.md`):

- `POST /instapaper/token` with `{"code": "..."}` swaps the one-time sign-in code for an access token.
  It adds the client secret and the fixed redirect address, and returns only the token and user.
  Web and Android both use it (Android sends no `Origin`; the code is one-time and the redirect
  address can't be chosen by the caller).
- `/instapaper/api/...` forwards a short list of Instapaper calls for the web app (browsers can't call
  Instapaper). Only the web app's own addresses are answered; everything else is refused.

Set once: the client ID and redirect address are in `wrangler.toml`. The client secret is **not**; add
it as a Worker secret, never in the repository: Cloudflare dashboard › this Worker › Settings ›
Variables and Secrets › Add › type **Secret** › `INSTAPAPER_CLIENT_SECRET` (or
`npx wrangler secret put INSTAPAPER_CLIENT_SECRET`).

## Reader accounts

`src/account.ts` answers `/account/...` (details in `docs/account.md`): one Durable Object per
account keeps its device keys (hashed) and the sync document, compressed and encrypted with a key
derived from the `ACCOUNT_KEY` secret, and tells the open devices when it changes (WebSocket). The
Durable Objects come with the deploy (`wrangler.toml`); add the secret once: Cloudflare dashboard ›
this Worker › Settings › Variables and Secrets › Add › type **Secret** › `ACCOUNT_KEY` (a random
text of at least 32 characters; never change or lose it), or `npx wrangler secret put ACCOUNT_KEY`.
