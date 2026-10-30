/**
 * Sharing with other Content users (#167). A signed-in device sends an article to someone by email
 * address; they land in that person's inbox, and their devices take them into
 * Saved. Nothing is shared unless the user shares it.
 *
 *  - POST /account/share/check {"email"}: {"exists": bool}, whether the address has a Content account,
 *                               so the app can say so before sending.
 *  - POST /account/share       {"to", "from": {"email", "name"}, "items": [{"url", "title", ...}], "message"?}:
 *                               204 once in their inbox; 404 when the address has no account; 403 when
 *                               "from" isn't an address of the sender's own account.
 *  - GET  /account/inbox       {"messages": [...]}: what was shared with this account, oldest first.
 *  - POST /account/inbox/done  {"ids": [...]}: taken in, so it leaves the inbox.
 *  - GET  /account/push/key    {"publicKey"}: the VAPID public key for Web Push (#235), or 404 when
 *                               notifications aren't set up on this server.
 *  - PUT  /account/push        {"kind": "web", "endpoint"} or {"kind": "fcm", "token"}: where this device gets notifications when
 *                               something is shared with it (push.ts); DELETE forgets it.
 *  - POST /account/profile     {"email"}: {"name"}, the name on that address's Gravatar profile (#234),
 *                               or "" when it has none. The feed loader asks Gravatar, so devices
 *                               don't send the address's hash to Gravatar themselves for names.
 *
 * Sharing fills in the sender's name from their Gravatar profile when the device sent none, so the
 * receiver sees a name.
 *
 * All with the device key ("Authorization: Bearer <key>"). The sender's address is checked against
 * the address directory (email.ts), so nobody can send as someone else. Messages are stored
 * encrypted in the recipient's account, like its items, and deleted once a device takes them in.
 */

import { allowedOrigins, corsHeaders, plain } from "./account";
import { emailIdOf, normalEmail } from "./email";
import type { Env } from "./index";
import { anyPushReady, poke, pushReady, pushTargetOf, type PushTarget, type SharePreview } from "./push";

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

const KEY_PATTERN = /^([0-9a-f]{32})\.([A-Za-z0-9_-]{43})$/;
/** The most articles one share may hold. */
export const MAX_SHARED_ITEMS = 500;
const MAX_BODY = 512 * 1024;
const MAX_TEXT = 2048;
/** The longest message for the receiver. */
export const MAX_MESSAGE = 1000;

export async function handleShare(request: Request, env: Env, url: URL, fetcher: Fetcher): Promise<Response> {
  const origin = request.headers.get("Origin");
  if (origin && !allowedOrigins(env).includes(origin)) return plain(403, "Not allowed");
  const cors = origin ? corsHeaders(origin) : {};
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  const route = routeOf(request.method, url.pathname);
  if (!route) return plain(["/account/share", "/account/share/check", "/account/inbox", "/account/inbox/done", "/account/profile", "/account/push", "/account/push/key"].includes(url.pathname) ? 405 : 404, "Not available", cors);
  if (!env.ACCOUNTS || !env.ACCOUNT_KEY) return plain(503, "Accounts aren't set up on this server", cors);
  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: `share:${ip}` });
    if (!success) return plain(429, "Too many requests", cors);
  }
  const key = KEY_PATTERN.exec((request.headers.get("Authorization") ?? "").replace(/^Bearer /, ""));
  if (!key) return plain(401, "Sign in again", cors);
  const [, accountId, secret] = key;
  const ns = env.ACCOUNTS;
  const own = ns.get(ns.idFromName(accountId));
  const auth = { "X-Account": accountId, "X-Secret": secret };

  if (route === "pushKey") return pushReady(env) ? json({ publicKey: env.VAPID_PUBLIC_KEY }, cors) : plain(404, "No notifications on this server", cors);
  if (route === "pushSet" || route === "pushForget") {
    let target: PushTarget | null = null;
    if (route === "pushSet") {
      target = pushTargetOf(await request.json().catch(() => null));
      if (!target) return plain(400, "Not a push address", cors);
    }
    const answer = await own.fetch("https://account/pushSet", { method: "POST", headers: auth, body: JSON.stringify({ target }) });
    return withCors(answer, cors);
  }

  if (route === "inbox" || route === "inboxDone") {
    const body = route === "inboxDone" ? await request.text() : undefined;
    if (body && body.length > MAX_BODY) return plain(413, "Too large", cors);
    const answer = await own.fetch(`https://account/${route}`, { method: request.method, headers: auth, body });
    return withCors(answer, cors);
  }

  if (!(await own.fetch("https://account/knows", { method: "POST", headers: auth })).ok) return plain(401, "Sign in again", cors);
  const text = await request.text();
  if (text.length > MAX_BODY) return plain(413, "Too large", cors);
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(text) as Record<string, unknown>;
  } catch {
    return plain(400, "Not JSON", cors);
  }
  const masterKey = env.ACCOUNT_KEY;
  const accountOf = async (email: string): Promise<string | null> => {
    const emailId = await emailIdOf(masterKey, email);
    const answer = await ns.get(ns.idFromName(`email:${emailId}`)).fetch("https://account/emailAccount", { headers: { "X-Account": emailId } });
    if (!answer.ok) return null;
    return ((await answer.json()) as { account?: string }).account ?? null;
  };

  if (route === "profile") {
    const email = normalEmail(body.email);
    if (!email) return plain(400, "Not an email address", cors);
    return json({ name: await gravatarName(email, fetcher) }, cors);
  }

  if (route === "check") {
    const email = normalEmail(body.email);
    if (!email) return plain(400, "Not an email address", cors);
    return json({ exists: (await accountOf(email)) !== null }, cors);
  }

  const message = shareOf(body);
  if (!message) return plain(400, "Not a share", cors);
  if ((await accountOf(message.from.email)) !== accountId) return plain(403, "Add this address to your account first", cors);
  if (!message.from.name) message.from.name = await gravatarName(message.from.email, fetcher);
  const to = await accountOf(message.to);
  if (!to) return plain(404, "No Content account for this address", cors);
  const inbox = ns.get(ns.idFromName(to));
  const id = crypto.randomUUID();
  const added = await inbox.fetch("https://account/inboxAdd", {
    method: "POST",
    headers: { "X-Account": to },
    body: JSON.stringify({ id, at: Date.now(), from: message.from, items: message.items, message: message.message }),
  });
  if (!added.ok) return plain(503, "Try again", cors);
  if (anyPushReady(env)) await notify(inbox, to, env, fetcher, previewOf(message));
  return new Response(null, { status: 204, headers: cors });
}

type Route = "check" | "share" | "inbox" | "inboxDone" | "profile" | "pushKey" | "pushSet" | "pushForget";

function routeOf(method: string, path: string): Route | null {
  if (path === "/account/share/check") return method === "POST" ? "check" : null;
  if (path === "/account/share") return method === "POST" ? "share" : null;
  if (path === "/account/inbox") return method === "GET" ? "inbox" : null;
  if (path === "/account/inbox/done") return method === "POST" ? "inboxDone" : null;
  if (path === "/account/profile") return method === "POST" ? "profile" : null;
  if (path === "/account/push/key") return method === "GET" ? "pushKey" : null;
  if (path === "/account/push") return method === "PUT" ? "pushSet" : method === "DELETE" ? "pushForget" : null;
  return null;
}

/** What a device may share, cleaned up, or null when it isn't one. */
export function shareOf(body: Record<string, unknown>): { to: string; from: { email: string; name: string }; items: { url: string; title: string; imageUrl: string | null; description: string }[]; message: string | null } | null {
  const to = normalEmail(body.to);
  const fromBody = body.from as Record<string, unknown> | null;
  const fromEmail = normalEmail(fromBody?.email);
  if (!to || !fromEmail || !Array.isArray(body.items) || body.items.length === 0 || body.items.length > MAX_SHARED_ITEMS) return null;
  const items = [];
  for (const raw of body.items) {
    const item = raw as Record<string, unknown> | null;
    const url = typeof item?.url === "string" ? item.url.trim() : "";
    if (!/^https?:\/\//.test(url) || url.length > MAX_TEXT) return null;
    const imageUrl = typeof item?.imageUrl === "string" && item.imageUrl.startsWith("https://") && item.imageUrl.length <= MAX_TEXT ? item.imageUrl : null;
    items.push({ url, title: clip(item?.title) || url, imageUrl, description: clip(item?.description) });
  }
  // A short message for the receiver (#167), shown with what was shared.
  const message = clip(body.message).slice(0, MAX_MESSAGE) || null;
  return { to, from: { email: fromEmail, name: clip(fromBody?.name).slice(0, 100) }, items, message };
}

/** What a phone's notification shows of a share: the first article, its intro (or the message), who shared it. */
export function previewOf(message: NonNullable<ReturnType<typeof shareOf>>): SharePreview {
  const first = message.items[0];
  return { title: first.title, intro: first.description || message.message || "", imageUrl: first.imageUrl, from: message.from.name || message.from.email, count: message.items.length };
}

/** Pokes every device of the receiver (#235); addresses that no longer work are forgotten. Never fails the share. */
async function notify(inbox: DurableObjectStub, account: string, env: Env, fetcher: Fetcher, preview: SharePreview): Promise<void> {
  try {
    const answer = await inbox.fetch("https://account/pushTargets", { headers: { "X-Account": account } });
    const { targets } = (await answer.json()) as { targets: { device: string; target: PushTarget }[] };
    const results = await Promise.all(targets.map(async (t) => ({ device: t.device, result: await poke(t.target, env, fetcher, Date.now(), preview) })));
    const gone = results.filter((r) => r.result === "gone").map((r) => r.device);
    if (gone.length > 0) await inbox.fetch("https://account/pushDrop", { method: "POST", headers: { "X-Account": account }, body: JSON.stringify({ devices: gone }) });
  } catch {
    // A notification is a nicety; the share itself is in the inbox.
  }
}

/**
 * The name on the Gravatar profile of an address (#234): its display name, or "" when the address has
 * no public profile or Gravatar doesn't answer. Gravatar finds a profile by the SHA-256 of the address.
 */
export async function gravatarName(email: string, fetcher: Fetcher): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(email.trim().toLowerCase()));
  const hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  try {
    const answer = await fetcher(`https://api.gravatar.com/v3/profiles/${hash}`, { headers: { Accept: "application/json" } });
    if (!answer.ok) return "";
    const profile = (await answer.json()) as { display_name?: unknown } | null;
    return typeof profile?.display_name === "string" ? profile.display_name.trim().slice(0, 100) : "";
  } catch {
    return "";
  }
}

function clip(value: unknown): string {
  return typeof value === "string" ? value.trim().slice(0, MAX_TEXT) : "";
}

function withCors(answer: Response, cors: Record<string, string>): Response {
  const headers = new Headers(answer.headers);
  for (const [k, v] of Object.entries(cors)) headers.set(k, v);
  return new Response(answer.body, { status: answer.status, headers });
}

function json(value: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(value), { status: 200, headers: { ...headers, "Content-Type": "application/json" } });
}
