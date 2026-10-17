# Reader on the web

The web version of Reader: the same feeds, folders and read articles as the Android app,
synced through your own Google Drive (see `../docs/sync.md`). It is a static site (HTML,
JavaScript, CSS): it runs entirely in the browser and keeps its data there (IndexedDB).

- Design: shadcn/ui style (Radix primitives with Tailwind), themed with the app's colours
  (`DESIGN.md`, "Other platforms"). Three panes on wide screens; one at a time on phones.
- Feeds and article pages load through the feed loader in `../worker/` (browsers block most
  feeds otherwise). Pictures load directly.
- Full articles for feeds that send a summary or a teaser use Mozilla's Readability (Firefox
  Reader View); the reader switches between the feed's text and the full article, and keeps the
  last 100 full articles in the browser.
- Text settings (Aa, also in Settings): font, size, spacing, background (theme, sepia, dark) and
  Auto dark, as in the Android app. Settings › Article list › Mark as read on scroll.
- Article HTML is cleaned with DOMPurify before it is shown. Video players and other embeds
  become a card that opens them in a new tab; nothing plays inside the page.
- Keyboard: `j`/`k` next/previous article, `m` read/unread, `o` open on the website, `r` refresh.
- Languages: English, Dutch, French, German, Spanish, Italian (the browser's language).

## Addresses it uses

Both are public (every visitor's browser sees them), so they are built in, in
`src/lib/config.ts`, and are not settings:

| Constant | What |
|---|---|
| `PROXY_URL` | The feed loader's address (`../worker/`) |
| `GOOGLE_CLIENT_ID` | The Google Cloud OAuth client ID of type "Web application" (docs/sync.md, step 5) |

## Deploying (Cloudflare Workers)

It is published as a Worker with static assets (`wrangler.jsonc`), next to the feed loader:

1. Workers & Pages › Create › Import a repository › this repository, name `rss-reader-web`.
2. Root directory `web`, build command `npm run build`, deploy command `npx wrangler deploy`.
3. The feed loader's address and the Google client ID are in `src/lib/config.ts`.
4. The site's address (`https://rss-reader-web.<account>.workers.dev`) must be in
   `ALLOWED_ORIGINS` in `../worker/wrangler.toml` and in the Google client's Authorized JavaScript
   origins.

Every push to `main` then publishes the new version; pull requests get a preview build. Any
static host with https works too: upload the contents of `dist/` after `npm run build`.

## Developing

```sh
npm ci
npm run dev
npm test
npm run lint
npm run build
```
