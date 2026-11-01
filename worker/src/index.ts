/**
 * RSS Reader's feed loader. Browsers don't let a web page load most feeds and article pages
 * (CORS), so the web app asks this Worker: GET /?url=<https address>. It fetches that one
 * address and passes the answer back. It stores and logs nothing of that, answers only the web
 * app's own address(es), and only passes feeds and web pages, up to MAX_BYTES.
 *
 * Instapaper sign-in and the web app's Instapaper calls live in instapaper.ts (/instapaper/...),
 * Reader accounts (the synced, encrypted library) in account.ts (/account/...), signing in with
 * an emailed link in email.ts (/account/email/...).
 */

import { handleAccount } from "./account";
import { handleEmail } from "./email";
import { handleShare } from "./share";
import { handleInstapaper } from "./instapaper";

export { Account } from "./account";

export interface Env {
  /** Comma-separated web app origins, e.g. "https://content.wim.to,http://localhost:5173". */
  ALLOWED_ORIGINS: string;
  /** The Instapaper application's client ID (public) and the callback address it is registered with. */
  INSTAPAPER_CLIENT_ID?: string;
  INSTAPAPER_REDIRECT_URI?: string;
  /** The client secret. Only ever a Worker secret, never in this repository. */
  INSTAPAPER_CLIENT_SECRET?: string;
  /** Instaparser key (a secret), for the text of Instapaper articles without a web address: forwarded emails (`/instapaper/text`). */
  INSTAPARSER_API_KEY?: string;
  /** The accounts' Durable Objects (wrangler.toml) and the secret their data is encrypted with. */
  ACCOUNTS?: DurableObjectNamespace;
  ACCOUNT_KEY?: string;
  /** Sign-in emails (#166, email.ts): Resend's API key (a secret), the sender, and where links point. */
  RESEND_API_KEY?: string;
  EMAIL_FROM?: string;
  WEB_APP_ORIGIN?: string;
  /** Notifications (#235, push.ts): the VAPID key pair, public in wrangler.toml, private a secret. */
  VAPID_PUBLIC_KEY?: string;
  VAPID_PRIVATE_KEY?: string;
  /** Notifications on Android (#235, push.ts): the Firebase service account's JSON key, a secret. */
  FCM_SERVICE_ACCOUNT?: string;
  /** Optional Cloudflare rate limiting binding (see wrangler.toml). */
  LIMITER?: { limit(options: { key: string }): Promise<{ success: boolean }> };
}

export const MAX_BYTES = 10 * 1024 * 1024;
const MAX_REDIRECTS = 5;
const CACHE_SECONDS = 300;
/**
 * Names Reader honestly, in the form browsers use ("Mozilla/5.0 (compatible; …)"), because some
 * sites refuse any request whose name doesn't start like a browser's.
 */
export const USER_AGENT = "Mozilla/5.0 (compatible; Reader/web; +https://github.com/wimvdon/reader)";
const ACCEPT =
  "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5";
/** For an article page (`?kind=page`): what a browser asks for. */
const PAGE_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";

/** Answers that mean the site refuses the feed loader (a bot check, a block, too many requests). */
const BLOCKED_STATUSES = new Set([401, 403, 429, 451, 503]);

/** Content types that are feeds or pages; pictures and everything else load elsewhere or not at all. */
const ALLOWED_TYPES = [/xml/, /rss/, /atom/, /^text\/html/, /^text\/plain/, /json/, /^application\/xhtml/];

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

export default {
  fetch: (request: Request, env: Env) => handle(request, env, (input, init) => fetch(input, init)),
};

export async function handle(request: Request, env: Env, fetcher: Fetcher): Promise<Response> {
  const requestUrl = new URL(request.url);
  if (requestUrl.pathname.startsWith("/instapaper/")) return handleInstapaper(request, env, fetcher, requestUrl);
  if (requestUrl.pathname.startsWith("/account/email/")) return handleEmail(request, env, requestUrl, fetcher);
  if (requestUrl.pathname.startsWith("/account/share") || requestUrl.pathname.startsWith("/account/inbox") || requestUrl.pathname === "/account/profile" || requestUrl.pathname.startsWith("/account/push")) return handleShare(request, env, requestUrl, fetcher);
  if (requestUrl.pathname === "/account" || requestUrl.pathname.startsWith("/account/")) return handleAccount(request, env, requestUrl);
  const origin = request.headers.get("Origin");
  const allowed = (env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((o) => o.trim())
    .filter(Boolean);
  if (!origin || !allowed.includes(origin)) return plain(403, "Not allowed");
  const cors = corsHeaders(origin);
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  if (request.method !== "GET") return plain(405, "Only GET", cors);

  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) return plain(429, "Too many requests", cors);
  }

  const target = checkTarget(new URL(request.url).searchParams.get("url"));
  if (!target) return plain(400, "Give an https address as ?url=", cors);
  // An article page is asked for as a browser asks for it, in the reader's own languages.
  const page = requestUrl.searchParams.get("kind") === "page";
  const language = request.headers.get("Accept-Language")?.slice(0, 200);
  const headers: Record<string, string> = { "User-Agent": USER_AGENT, Accept: page ? PAGE_ACCEPT : ACCEPT };
  if (page && language) headers["Accept-Language"] = language;

  let current = target;
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    let upstream: Response;
    try {
      upstream = await fetcher(current, {
        redirect: "manual",
        headers,
        // Cloudflare's cache: many readers of one feed cost one download per few minutes.
        cf: { cacheTtl: CACHE_SECONDS, cacheEverything: true },
      } as RequestInit);
    } catch {
      return plain(502, "Couldn't reach the site", cors);
    }
    if (upstream.status >= 300 && upstream.status < 400) {
      const location = upstream.headers.get("Location");
      const next = location ? checkTarget(resolveLocation(location, current)) : null;
      if (!next) return plain(502, "Bad redirect", cors);
      current = next;
      continue;
    }
    // Refused, or a "prove you're human" page instead of the page: the web app says so (403).
    if (BLOCKED_STATUSES.has(upstream.status) || upstream.headers.get("cf-mitigated") === "challenge") {
      return plain(403, "The site blocks the feed loader", cors);
    }
    if (!upstream.ok) return plain(upstream.status === 404 || upstream.status === 410 ? 404 : 502, "The site answered " + upstream.status, cors);
    const type = upstream.headers.get("Content-Type") ?? "";
    if (type && !ALLOWED_TYPES.some((re) => re.test(type.toLowerCase()))) return plain(415, "Not a feed or page", cors);
    const length = Number(upstream.headers.get("Content-Length") ?? "0");
    if (length > MAX_BYTES) return plain(413, "Too large", cors);
    let body: Uint8Array | null;
    try {
      body = await readLimited(upstream);
    } catch {
      // The site broke off the answer halfway.
      return plain(502, "Couldn't reach the site", cors);
    }
    if (!body) return plain(413, "Too large", cors);
    return new Response(body, {
      status: 200,
      headers: {
        ...cors,
        "Content-Type": type || "application/octet-stream",
        "X-Final-Url": current,
        "Cache-Control": `private, max-age=${CACHE_SECONDS}`,
      },
    });
  }
  return plain(502, "Too many redirects", cors);
}

/** An https address on a public host name, or null. */
export function checkTarget(raw: string | null): string | null {
  if (!raw) return null;
  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return null;
  }
  if (url.protocol !== "https:" || url.username || url.password) return null;
  if (url.port && url.port !== "443") return null;
  const host = url.hostname.toLowerCase();
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return null;
  // Addresses by number (IPv4 or IPv6) are refused: feeds have names, and this keeps out
  // private networks.
  if (/^[\d.]+$/.test(host) || host.startsWith("[")) return null;
  if (!host.includes(".")) return null;
  url.hash = "";
  return url.toString();
}

/** A redirect's Location against the address that sent it, or null when it isn't an address at all. */
function resolveLocation(location: string, base: string): string | null {
  try {
    return new URL(location, base).toString();
  } catch {
    return null;
  }
}

async function readLimited(response: Response): Promise<Uint8Array | null> {
  if (!response.body) return new Uint8Array();
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let size = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > MAX_BYTES) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const out = new Uint8Array(size);
  let offset = 0;
  for (const c of chunks) {
    out.set(c, offset);
    offset += c.byteLength;
  }
  return out;
}

function corsHeaders(origin: string): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Expose-Headers": "X-Final-Url",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function plain(status: number, text: string, headers: Record<string, string> = {}): Response {
  return new Response(text, { status, headers: { ...headers, "Content-Type": "text/plain; charset=utf-8" } });
}
