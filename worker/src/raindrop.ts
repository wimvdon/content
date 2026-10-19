/**
 * Raindrop.io sign-in for the Android app (#112, OAuth 2 authorization code flow). The client secret
 * exists only here, as a Worker secret; the app never sees it.
 *
 *  - GET /raindrop/authorize?state=...: sends the browser on to Raindrop's sign-in page with this
 *    Worker's client ID and fixed callback address, so the app needs neither.
 *  - POST /raindrop/token {"code": "..."}: swaps the code from the callback for tokens.
 *  - POST /raindrop/refresh {"refresh_token": "..."}: Raindrop's tokens last two weeks; this gets
 *    new ones.
 *
 * Nothing is stored or logged; the tokens only pass through. The app calls Raindrop's API directly.
 */

import type { Env } from "./index";

export const AUTHORIZE_URL = "https://raindrop.io/oauth/authorize";
export const TOKEN_URL = "https://raindrop.io/oauth/access_token";
const MAX_BODY_BYTES = 16 * 1024;
const USER_AGENT = "RSSReader/web (+https://github.com/wimvdon/reader)";

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

export async function handleRaindrop(request: Request, env: Env, fetcher: Fetcher, url: URL): Promise<Response> {
  const route = url.pathname;
  if (route !== "/raindrop/authorize" && route !== "/raindrop/token" && route !== "/raindrop/refresh") return plain(404, "Not found");
  const { RAINDROP_CLIENT_ID: clientId, RAINDROP_CLIENT_SECRET: secret, RAINDROP_REDIRECT_URI: redirect } = env;
  if (!clientId || !secret || !redirect) return plain(503, "Raindrop isn't set up on this server");

  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: ip });
    if (!success) return plain(429, "Too many requests");
  }

  if (route === "/raindrop/authorize") {
    if (request.method !== "GET") return plain(405, "Only GET");
    const state = url.searchParams.get("state") ?? "";
    if (!/^[\w-]{8,128}$/.test(state)) return plain(400, "Send a state");
    const target = new URL(AUTHORIZE_URL);
    target.searchParams.set("client_id", clientId);
    target.searchParams.set("redirect_uri", redirect);
    target.searchParams.set("response_type", "code");
    target.searchParams.set("state", state);
    return new Response(null, { status: 302, headers: { Location: target.toString(), "Cache-Control": "no-store" } });
  }

  if (request.method !== "POST") return plain(405, "Only POST");
  const body = await readJson(request);
  const grant =
    route === "/raindrop/token"
      ? { grant_type: "authorization_code", code: typeof body?.code === "string" ? body.code : "", redirect_uri: redirect }
      : { grant_type: "refresh_token", refresh_token: typeof body?.refresh_token === "string" ? body.refresh_token : "" };
  const given = ("code" in grant ? grant.code : grant.refresh_token) ?? "";
  if (!/^[\w.~-]{1,512}$/.test(given)) return plain(400, "Send the code or refresh token as JSON");

  let upstream: Response;
  try {
    upstream = await fetcher(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json", "User-Agent": USER_AGENT },
      body: JSON.stringify({ ...grant, client_id: clientId, client_secret: secret }),
      redirect: "manual",
    });
  } catch {
    return plain(502, "Couldn't reach Raindrop");
  }
  if (!upstream.ok) return plain(upstream.status >= 400 && upstream.status < 500 ? 400 : 502, "Raindrop didn't accept it");
  const answer = (await upstream.json().catch(() => null)) as { access_token?: unknown; refresh_token?: unknown; expires_in?: unknown } | null;
  const token = typeof answer?.access_token === "string" ? answer.access_token : "";
  if (!token) return plain(502, "Raindrop sent no token");
  // Only what the app needs goes back.
  return json({
    access_token: token,
    refresh_token: typeof answer?.refresh_token === "string" ? answer.refresh_token : "",
    expires_in: typeof answer?.expires_in === "number" ? answer.expires_in : null,
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

function json(value: unknown): Response {
  return new Response(JSON.stringify(value), { status: 200, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}

function plain(status: number, text: string): Response {
  return new Response(text, { status, headers: { "Content-Type": "text/plain; charset=utf-8" } });
}
