/**
 * The addresses the published web app uses. Both are public (any visitor's browser sees them),
 * so they live here rather than in Settings.
 */

/** The owner's feed loader, the Cloudflare Worker in worker/. */
export const PROXY_URL = "https://rss-reader-worker.wim-d57.workers.dev";

/** The Google Cloud OAuth client of type "Web application", for Drive sync (docs/sync.md). */
export const GOOGLE_CLIENT_ID = "213170413016-678semm4og2emqalhi7d4uqu42n0hk1m.apps.googleusercontent.com";
