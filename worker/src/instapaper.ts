/**
 * Instapaper for the apps (API v2, OAuth 2 authorization code flow).
 *
 *  - POST /instapaper/token {"code": "..."}: swaps the code from Instapaper's sign-in page for an
 *    access token. This is the only place the client secret exists. Both apps use it.
 *  - /instapaper/api/...: forwards a few Instapaper calls for the web app, because Instapaper's
 *    API doesn't allow calls from a browser (no CORS). The Android app calls Instapaper directly.
 *
 * Nothing is stored or logged. The access token only passes through. A successful sign-in also
 * gives the device a key to the user's Reader account (account.ts), which keeps the synced library.
 */

import { newDeviceKey } from "./account";
import type { Env } from "./index";

export const API_BASE = "https://www.instapaper.com/api/2";
export const TOKEN_URL = "https://www.instapaper.com/oauth2/token";
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
  const isApi = url.pathname.startsWith("/instapaper/api/");
  if (!isToken && !isApi) return plain(404, "Not found");

  // The web app always sends its address. The Android app sends none: fine for the token exchange
  // (it needs a one-time code and the redirect address is fixed here), never for forwarded calls.
  const origin = request.headers.get("Origin");
  if (origin ? !allowedOrigins(env).includes(origin) : isApi) return plain(403, "Not allowed");
  const cors = origin ? corsHeaders(origin) : {};
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });

  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) return plain(429, "Too many requests", cors);
  }

  return isToken ? exchangeCode(request, env, fetcher, cors) : forward(request, fetcher, url, cors);
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

  // Only what the apps need goes back: the token, who it belongs to, and the key to their account.
  const user = {
    id: typeof answer?.user?.id === "number" ? answer.user.id : null,
    username: typeof answer?.user?.username === "string" ? answer.user.username : "",
  };
  const accountKey = user.id === null ? null : await newDeviceKey(env, "instapaper", user.id).catch(() => null);
  return json({ access_token: token, user, account: accountKey ? { key: accountKey } : null }, 200, cors);
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
