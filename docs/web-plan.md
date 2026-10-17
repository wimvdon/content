# Finishing the web app

A briefing for the agent that picks this up. The web app (`web/`) and its feed loader
(`worker/`) work and pass their checks; what is left is listed below. Remove each item from this
file in the pull request that finishes it, and delete the file with the last one.

## How to work

- Follow `CLAUDE.md`, including the "Web app and feed loader" section. One item per branch and
  pull request; squash-merge it yourself once CI is green.
- The Android app is the reference for behaviour and wording: find how it does each thing in its
  code and `docs/usage.md`, then build it the web way. Don't change Android code for this.
- Do your own research in the code; this file only says what, not how.

## To do

1. **Teasers.** Feed text that links to its own article ("Read full article") counts as a
   summary whatever its length, as on Android, so the full article loads (Ars Technica).
2. **Feed text or full article.** The reader can switch between the two, as Android's
   ⋮ › Show feed text / Show full article; loaded full articles are kept for a while, so opening
   one again is instant.
3. **Reader text settings (Aa).** Font, text size, spacing, background (Theme, Sepia, Dark) and
   Auto dark, remembered for every article, also in Settings › Reader.
4. **Mark as read on scroll.** A setting, as on Android, for articles scrolled past in the list.
5. **Embedded content.** Videos and posts in articles (YouTube, Vimeo, X and others) show as a
   card with a preview that opens them. Check what `PRIVACY.md` allows for the previews.
6. **Cleanups.** The four `set-state-in-effect` warnings from `npm run lint`; the example address
   in `docs/sync.md` step 5 (the web app is at `https://rss-reader-web.wim-d57.workers.dev`).

Not wanted: a Show images setting, and Small/Medium/Large list views.

## Known problems outside the code

- The Cloudflare checks (Workers Builds: `rss-reader-web` and `rss-reader-worker`) fail within a
  second on every push, with no log on GitHub. The cause is in the Cloudflare dashboard, which
  you can't reach; the owner is looking at it. Don't treat these checks as yours to fix unless
  the owner shares the build log; the GitHub CI checks are the ones that must be green.
- Drive sync needs a Google client ID of type "Web application"; the owner is creating it. When
  it arrives, it goes into `web/.env.production` as `VITE_GOOGLE_CLIENT_ID` (it isn't a secret).
