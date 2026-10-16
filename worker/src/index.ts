/**
 * RSS Reader's feed loader. Browsers don't let a web page load most feeds and article pages
 * (CORS), so the web app asks this Worker: GET /?url=<https address>. It fetches that one
 * address and passes the answer back. It stores nothing and logs nothing, answers only the web
 * app's own address(es), and only passes feeds and web pages, up to MAX_BYTES.
 */

export interface Env {
  /** Comma-separated web app origins, e.g. "https://rss-reader.pages.dev,http://localhost:5173". */
  ALLOWED_ORIGINS: string;
  /** Optional Cloudflare rate limiting binding (see wrangler.toml). */
  LIMITER?: { limit(options: { key: string }): Promise<{ success: boolean }> };
}

export const MAX_BYTES = 10 * 1024 * 1024;
const MAX_REDIRECTS = 5;
const CACHE_SECONDS = 300;
const USER_AGENT = "RSSReader/web (+https://github.com/wimvdon/rss-reader)";
const ACCEPT =
  "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5";

/** Content types that are feeds or pages; pictures and everything else load elsewhere or not at all. */
const ALLOWED_TYPES = [/xml/, /rss/, /atom/, /^text\/html/, /^text\/plain/, /json/, /^application\/xhtml/];

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

export default {
  fetch: (request: Request, env: Env) => handle(request, env, (input, init) => fetch(input, init)),
};

export async function handle(request: Request, env: Env, fetcher: Fetcher): Promise<Response> {
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

  let current = target;
  for (let i = 0; i <= MAX_REDIRECTS; i++) {
    let upstream: Response;
    try {
      upstream = await fetcher(current, {
        redirect: "manual",
        headers: { "User-Agent": USER_AGENT, Accept: ACCEPT },
        // Cloudflare's cache: many readers of one feed cost one download per few minutes.
        cf: { cacheTtl: CACHE_SECONDS, cacheEverything: true },
      } as RequestInit);
    } catch {
      return plain(502, "Couldn't reach the site", cors);
    }
    if (upstream.status >= 300 && upstream.status < 400) {
      const location = upstream.headers.get("Location");
      const next = location ? checkTarget(new URL(location, current).toString()) : null;
      if (!next) return plain(502, "Bad redirect", cors);
      current = next;
      continue;
    }
    if (!upstream.ok) return plain(upstream.status === 404 || upstream.status === 410 ? 404 : 502, "The site answered " + upstream.status, cors);
    const type = upstream.headers.get("Content-Type") ?? "";
    if (type && !ALLOWED_TYPES.some((re) => re.test(type.toLowerCase()))) return plain(415, "Not a feed or page", cors);
    const length = Number(upstream.headers.get("Content-Length") ?? "0");
    if (length > MAX_BYTES) return plain(413, "Too large", cors);
    const body = await readLimited(upstream);
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
