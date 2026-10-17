/**
 * The addresses the published web app uses. Both are public (any visitor's browser sees them),
 * so they live here rather than in Settings.
 */

/** The owner's feed loader, the Cloudflare Worker in worker/. */
export const PROXY_URL = "https://rss-reader-worker.wim-d57.workers.dev";

/** The Google Cloud OAuth client of type "Web application", for Drive sync (docs/sync.md). */
export const GOOGLE_CLIENT_ID = "213170413016-678semm4og2emqalhi7d4uqu42n0hk1m.apps.googleusercontent.com";

/** The Instapaper application (API v2): its client ID is public. The client secret lives only in the Worker. */
export const INSTAPAPER_CLIENT_ID = "94f820c94dc04d0386c71d7b0c26bd80";

/**
 * Where Instapaper sends the user back after signing in. It must be registered with the Instapaper
 * application and match INSTAPAPER_REDIRECT_URI in worker/wrangler.toml exactly. The Android app
 * receives the same address as an Android App Link.
 */
export const INSTAPAPER_REDIRECT_URI = "https://rss-reader-web.wim-d57.workers.dev/instapaper/callback";
