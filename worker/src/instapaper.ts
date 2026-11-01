/**
 * Instapaper for the apps (API v2, OAuth 2 authorization code flow).
 *
 *  - POST /instapaper/token {"code": "..."}: swaps the code from Instapaper's sign-in page for an
 *    access token. This is the only place the client secret exists. Both apps use it.
 *  - /instapaper/api/...: forwards a few Instapaper calls for the web app, because Instapaper's
 *    API doesn't allow calls from a browser (no CORS). The Android app calls Instapaper directly.
 *  - POST /instapaper/text {"id": 123}: the text of one of the user's Instapaper articles that has
 *    no web address (a forwarded email, a PDF), from Instapaper's parser with Content's Instaparser
 *    key, the only place the key exists. Both apps use it, only for such articles, once each (the
 *    text is then stored in the account like any article's). Answers {"title", "html"}.
 *
 * Nothing is stored or logged. The access token only passes through. Instapaper is only a
 * connected service (#215): signing in to it opens no Content account.
 */

import type { Env } from "./index";

export const API_BASE = "https://www.instapaper.com/api/2";
export const TOKEN_URL = "https://www.instapaper.com/oauth2/token";
/** The largest text passed on (a long newsletter is well under this). */
export const MAX_TEXT_BYTES = 2 * 1024 * 1024;
const MAX_BODY_BYTES = 64 * 1024;
/** Saving a bookmark may carry the article's text (#247), so it may be larger. */
export const MAX_SAVE_BYTES = 1024 * 1024;
const USER_AGENT = "RSSReader/web (+https://github.com/wimvdon/reader)";
const LIST_PARAMS = ["section", "folder_id", "tag", "limit", "offset", "since"];

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

/**
 * The Instapaper calls the web app may make: bookmarks (list or what changed since a time, save,
 * delete, change the title or reading progress, move to the archive, home or a folder, like, tag),
 * folders (list, create, delete, reorder), tags (list, create, rename) and a bookmark's highlights
 * (list, make, delete; #164).
 */
const API_ROUTES: { method: string; path: RegExp }[] = [
  { method: "GET", path: /^\/bookmarks$/ },
  { method: "POST", path: /^\/bookmarks$/ },
  { method: "DELETE", path: /^\/bookmarks\/\d+$/ },
  { method: "POST", path: /^\/bookmarks\/\d+$/ },
  { method: "POST", path: /^\/bookmarks\/\d+\/move$/ },
  { method: "POST", path: /^\/bookmarks\/\d+\/like$/ },
  { method: "DELETE", path: /^\/bookmarks\/\d+\/like$/ },
  { method: "POST", path: /^\/bookmarks\/\d+\/tags$/ },
  { method: "GET", path: /^\/bookmarks\/\d+\/highlights$/ },
  { method: "POST", path: /^\/bookmarks\/\d+\/highlights$/ },
  { method: "DELETE", path: /^\/highlights\/\d+$/ },
  { method: "GET", path: /^\/folders$/ },
  { method: "POST", path: /^\/folders$/ },
  { method: "POST", path: /^\/folders\/reorder$/ },
  { method: "DELETE", path: /^\/folders\/\d+$/ },
  { method: "GET", path: /^\/tags$/ },
  { method: "POST", path: /^\/tags$/ },
  { method: "POST", path: /^\/tags\/\d+$/ },
];

export async function handleInstapaper(request: Request, env: Env, fetcher: Fetcher, url: URL): Promise<Response> {
  const isToken = url.pathname === "/instapaper/token";
  const isText = url.pathname === "/instapaper/text";
  const isApi = url.pathname.startsWith("/instapaper/api/");
  if (!isToken && !isText && !isApi) return plain(404, "Not found");

  // The web app always sends its address. The Android app sends none: fine for the token exchange
  // (it needs a one-time code and the redirect address is fixed here) and for an article's text (it
  // needs the user's Instapaper token), never for forwarded calls.
  const origin = request.headers.get("Origin");
  if (origin ? !allowedOrigins(env).includes(origin) : isApi) return plain(403, "Not allowed");
  const cors = origin ? corsHeaders(origin) : {};
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });

  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) return plain(429, "Too many requests", cors);
  }

  if (isText) return articleText(request, env, fetcher, cors);
  return isToken ? exchangeCode(request, env, fetcher, cors) : forward(request, fetcher, url, cors);
}

/**
 * The text of the user's Instapaper article [id], parsed by Instapaper with Content's Instaparser
 * key (the API only gives the text to its own developer without one). Only the title and the HTML
 * go back; nothing is kept. Instaparser's own limits pass on as they are: 402 when the month's
 * credits are used up, 429 when called too fast; the apps then offer to open it in Instapaper.
 */
async function articleText(request: Request, env: Env, fetcher: Fetcher, cors: Record<string, string>): Promise<Response> {
  if (request.method !== "POST") return plain(405, "Only POST", cors);
  const key = env.INSTAPARSER_API_KEY;
  if (!key) return plain(503, "Article texts aren't set up on this server", cors);
  const auth = request.headers.get("Authorization") ?? "";
  if (!/^Bearer [\w.~+/=-]{8,512}$/.test(auth)) {
    return plain(401, "Send the Instapaper token as Authorization: Bearer <token>", cors);
  }
  const body = await readJson(request);
  const id = body && typeof body.id === "number" && Number.isSafeInteger(body.id) && body.id > 0 ? body.id : 0;
  if (!id) return plain(400, 'Send the bookmark as JSON: {"id": 123}', cors);

  let upstream: Response;
  try {
    upstream = await fetcher(`${API_BASE}/bookmarks/${id}/parse`, {
      method: "POST",
      headers: { Authorization: auth, Accept: "application/json", "Content-Type": "application/json", "User-Agent": USER_AGENT },
      body: JSON.stringify({ instaparser_api_key: key }),
      redirect: "manual",
    });
  } catch {
    return plain(502, "Couldn't reach Instapaper", cors);
  }
  if (upstream.status >= 300 && upstream.status < 400) return plain(502, "Bad redirect", cors);
  if (!upstream.ok) return plain(upstream.status, "Instapaper didn't give the text", cors);
  const text = await upstream.text();
  if (text.length > MAX_TEXT_BYTES) return plain(413, "Too large", cors);
  type Parsed = { metadata?: { title?: unknown }; content?: { body?: unknown } } | null;
  let parsed: Parsed;
  try {
    parsed = JSON.parse(text) as Parsed;
  } catch {
    parsed = null;
  }
  const html = typeof parsed?.content?.body === "string" ? parsed.content.body : "";
  if (!html.trim()) return plain(502, "Instapaper sent no text", cors);
  const title = typeof parsed?.metadata?.title === "string" ? parsed.metadata.title : "";
  return json({ title, html }, 200, cors);
}

async function exchangeCode(
  request: Request,
  env: Env,
  fetcher: Fetcher,
  cors: Record<string, string>,
): Promise<Response> {
  if (request.method !== "POST") return plain(405, "Only POST", cors);
  const { INSTAPAPER_CLIENT_ID: clientId, INSTAPAPER_CLIENT_SECRET: secret, INSTAPAPER_REDIRECT_URI: redirect } = env;
  if (!clientId || !secret || !redirect) return plain(503, "Instapaper isn't set up on this server", cors);

  const body = await readJson(request);
  const code = body && typeof body.code === "string" ? body.code : "";
  if (!/^[\w.~-]{1,512}$/.test(code)) return plain(400, 'Send the code as JSON: {"code": "..."}', cors);

  let upstream: Response;
  try {
    upstream = await fetcher(TOKEN_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
        Accept: "application/json",
        "User-Agent": USER_AGENT,
      },
      body: new URLSearchParams({ client_id: clientId, client_secret: secret, redirect_uri: redirect, code }).toString(),
      redirect: "manual",
    });
  } catch {
    return plain(502, "Couldn't reach Instapaper", cors);
  }
  if (!upstream.ok) {
    return plain(upstream.status === 400 || upstream.status === 401 ? 400 : 502, "Instapaper didn't accept the code", cors);
  }
  const answer = (await upstream.json().catch(() => null)) as {
    access_token?: unknown;
    user?: { id?: unknown; username?: unknown };
  } | null;
  const token = typeof answer?.access_token === "string" ? answer.access_token : "";
  if (!token) return plain(502, "Instapaper sent no token", cors);

  // Only what the apps need goes back: the token and who it belongs to.
  const user = {
    id: typeof answer?.user?.id === "number" ? answer.user.id : null,
    username: typeof answer?.user?.username === "string" ? answer.user.username : "",
  };
  return json({ access_token: token, user }, 200, cors);
}

async function forward(request: Request, fetcher: Fetcher, url: URL, cors: Record<string, string>): Promise<Response> {
  const subPath = url.pathname.slice("/instapaper/api".length);
  if (!API_ROUTES.some((r) => r.method === request.method && r.path.test(subPath))) {
    return plain(API_ROUTES.some((r) => r.path.test(subPath)) ? 405 : 404, "Not available", cors);
  }
  const auth = request.headers.get("Authorization") ?? "";
  if (!/^Bearer [\w.~+/=-]{8,512}$/.test(auth)) {
    return plain(401, "Send the Instapaper token as Authorization: Bearer <token>", cors);
  }

  const target = new URL(API_BASE + subPath);
  if (request.method === "GET") {
    for (const name of LIST_PARAMS) {
      const value = url.searchParams.get(name);
      if (value !== null) target.searchParams.set(name, value);
    }
  }
  let body: string | undefined;
  if (request.method === "POST") {
    body = await request.text();
    if (body.length > (subPath === "/bookmarks" ? MAX_SAVE_BYTES : MAX_BODY_BYTES)) return plain(413, "Too large", cors);
  }

  let upstream: Response;
  try {
    upstream = await fetcher(target.toString(), {
      method: request.method,
      headers: {
        Authorization: auth,
        Accept: "application/json",
        "User-Agent": USER_AGENT,
        ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      },
      body,
      // The token must never follow a redirect to somewhere else.
      redirect: "manual",
    });
  } catch {
    return plain(502, "Couldn't reach Instapaper", cors);
  }
  if (upstream.status >= 300 && upstream.status < 400) return plain(502, "Bad redirect", cors);
  return new Response(await upstream.text(), {
    status: upstream.status,
    headers: { ...cors, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

async function readJson(request: Request): Promise<Record<string, unknown> | null> {
  const text = await request.text();
  if (text.length > MAX_BODY_BYTES) return null;
  try {
    const value: unknown = JSON.parse(text);
    return value && typeof value === "object" && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function allowedOrigins(env: Env): string[] {
  return (env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((o) => o.trim())
    .filter(Boolean);
}

function corsHeaders(origin: string): Record<string, string> {
  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
    "Access-Control-Allow-Headers": "Authorization, Content-Type",
    "Access-Control-Max-Age": "86400",
    Vary: "Origin",
  };
}

function json(value: unknown, status: number, headers: Record<string, string>): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: { ...headers, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
}

function plain(status: number, text: string, headers: Record<string, string> = {}): Response {
  return new Response(text, { status, headers: { ...headers, "Content-Type": "text/plain; charset=utf-8" } });
}
