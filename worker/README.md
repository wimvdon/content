# Feed loader (Cloudflare Worker)

Browsers don't let a web page load most feeds and article pages (CORS). The web app in `web/`
asks this Worker instead: `GET https://<worker>/?url=<https address>`.

What it does, and doesn't:

- Fetches exactly the address it is given (https only, public host names only, at most 5
  redirects, at most 10 MB) and passes the answer back, with the final address in `X-Final-Url`.
- Passes only feeds and web pages (XML, HTML, JSON, text). Pictures load directly in the browser.
- Answers only the web app's own address(es) in `ALLOWED_ORIGINS`.
- At most 120 requests per minute per IP address (`LIMITER`).
- Stores nothing and logs nothing (`observability` off). Cloudflare's cache keeps a feed for 5
  minutes so many readers of one feed cost one download.
- The Android app never uses it.

## Deploying

Once, in the Cloudflare dashboard (free plan):

1. Workers & Pages › Create › Import a repository › this repository.
2. Name `rss-reader-worker` (it must match `name` in `wrangler.toml`). Root directory: `worker`. Build command: none. Deploy command: `npx wrangler deploy`.
3. After the first deploy, note its address (`https://rss-reader-worker.<you>.workers.dev`) and
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
