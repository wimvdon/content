/**
 * Signing in with an email address (#166): Content emails a sign-in link, and opening it signs that
 * device or browser in. No password. Instapaper stays a way in for accounts it opened.
 *
 *  - POST /account/email/start  {"email"}: emails a sign-in link. Always
 *                               answers 204, so it says nothing about which addresses have accounts.
 *  - POST /account/email/add    {"email"} with a device key: emails a link that adds the address to
 *                               this account, so either way in opens the same library.
 *  - POST /account/email/finish {"token"} (from the link): for a sign-in, a new device key and the
 *                               address ({"key", "email"}); for an added address, {"email"}. 410
 *                               when the link is used or too old; 409 when the address already
 *                               belongs to another account.
 *
 * The link carries a token signed with a key derived from ACCOUNT_KEY: the address, what it is for,
 * when it ends and a random id, so nothing has to be stored before it is opened. Each link works
 * once. Which account an address opens is kept in its own Durable Object, named after an HMAC of the
 * address: it stores the account id and the used link ids, never the address. The emails go out
 * through Resend (RESEND_API_KEY); Resend sees the address and the email. Nothing is logged.
 */

import { accountIdOf, addDeviceKey, allowedOrigins, base64url, corsHeaders, derive, hex, plain } from "./account";
import type { Env } from "./index";

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

/** How long a sign-in link works. */
export const LINK_MS = 30 * 60 * 1000;
/** The longest address accepted (RFC 5321). */
const MAX_EMAIL = 254;
const EMAIL_PATTERN = /^[^\s@<>()",;:]+@[^\s@<>()",;:]+\.[^\s@<>()",;:]+$/;
const RESEND_URL = "https://api.resend.com/emails";
/** The path the web app and the Android app (an App Link) open a sign-in link at. */
export const LINK_PATH = "/signin";

/** What a link is for: a sign-in, or adding the address to an account. */
export interface LinkClaims {
  email: string;
  /** The account the address is added to; absent for a sign-in. */
  account?: string;
  /** When the link stops working, in milliseconds. */
  expires: number;
  /** A random id, so each link works once. */
  id: string;
}

/** The address as one account knows it: trimmed and in lower case, or null when it isn't one. */
export function normalEmail(text: unknown): string | null {
  if (typeof text !== "string") return null;
  const email = text.trim().toLowerCase();
  return email.length <= MAX_EMAIL && EMAIL_PATTERN.test(email) ? email : null;
}

export async function handleEmail(request: Request, env: Env, url: URL, fetcher: Fetcher, clock: () => number = Date.now): Promise<Response> {
  const origin = request.headers.get("Origin");
  if (origin && !allowedOrigins(env).includes(origin)) return plain(403, "Not allowed");
  const cors = origin ? corsHeaders(origin) : {};
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: cors });
  const action = url.pathname.slice("/account/email/".length);
  if (!["start", "add", "finish"].includes(action)) return plain(404, "Not available", cors);
  if (request.method !== "POST") return plain(405, "Not available", cors);
  if (!env.ACCOUNTS || !env.ACCOUNT_KEY) return plain(503, "Accounts aren't set up on this server", cors);
  if (env.LIMITER) {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const { success } = await env.LIMITER.limit({ key: `email:${ip}` });
    if (!success) return plain(429, "Too many requests", cors);
  }
  const text = await request.text();
  if (text.length > 4096) return plain(413, "Too large", cors);
  let body: Record<string, unknown>;
  try {
    body = JSON.parse(text) as Record<string, unknown>;
  } catch {
    return plain(400, "Not JSON", cors);
  }
  if (!body || typeof body !== "object") return plain(400, "Not JSON", cors);

  if (action === "finish") {
    const claims = typeof body.token === "string" ? await readToken(env.ACCOUNT_KEY, body.token, clock()) : null;
    if (!claims) return plain(410, "This link is used or too old", cors);
    return finish(env, claims, cors);
  }

  const email = normalEmail(body.email);
  if (!email) return plain(400, "Not an email address", cors);
  if (!env.RESEND_API_KEY) return plain(503, "Email isn't set up on this server", cors);
  let account: string | undefined;
  if (action === "add") {
    const key = /^([0-9a-f]{32})\.([A-Za-z0-9_-]{43})$/.exec((request.headers.get("Authorization") ?? "").replace(/^Bearer /, ""));
    if (!key) return plain(401, "Sign in again", cors);
    const stub = env.ACCOUNTS.get(env.ACCOUNTS.idFromName(key[1]));
    const known = await stub.fetch("https://account/knows", { method: "POST", headers: { "X-Account": key[1], "X-Secret": key[2] } });
    if (!known.ok) return plain(401, "Sign in again", cors);
    account = key[1];
  }
  const claims: LinkClaims = { email, ...(account ? { account } : {}), expires: clock() + LINK_MS, id: base64url(crypto.getRandomValues(new Uint8Array(16))) };
  const link = `${webAppOrigin(env)}${LINK_PATH}#token=${await makeToken(env.ACCOUNT_KEY, claims)}`;
  const sent = await sendEmail(env, fetcher, email, link, account ? "add" : "signin");
  if (!sent) return plain(502, "The email couldn't be sent", cors);
  return new Response(null, { status: 204, headers: cors });
}

/** Uses the link once: a sign-in gets a device key of the address's account, an added address joins its account. */
async function finish(env: Env, claims: LinkClaims, cors: Record<string, string>): Promise<Response> {
  const masterKey = env.ACCOUNT_KEY!;
  const ns = env.ACCOUNTS!;
  const emailId = await emailIdOf(masterKey, claims.email);
  const directory = ns.get(ns.idFromName(`email:${emailId}`));
  const claim = await directory.fetch("https://account/emailClaim", {
    method: "POST",
    headers: { "X-Account": emailId },
    body: JSON.stringify({ id: claims.id, expires: claims.expires, account: claims.account ?? (await accountIdOf(masterKey, "email", emailId)), add: Boolean(claims.account) }),
  });
  if (claim.status === 410) return plain(410, "This link is used or too old", cors);
  if (claim.status === 409) return plain(409, "This address belongs to another account", cors);
  if (!claim.ok) return plain(503, "Try again", cors);
  const { account } = (await claim.json()) as { account: string };
  if (claims.account) return json({ email: claims.email }, cors);
  const key = await addDeviceKey(env, account);
  if (!key) return plain(503, "Try again", cors);
  return json({ key, email: claims.email }, cors);
}

/**
 * The directory side, in the address's own Durable Object: which account the address opens (set the
 * first time) and which links were used. An added address may only join when it has no account yet,
 * or already that one.
 */
export async function emailClaim(storage: { get<T>(key: string): Promise<T | undefined>; put<T>(key: string, value: T): Promise<void> }, body: string, now: number): Promise<Response> {
  const { id, expires, account, add } = JSON.parse(body) as { id: string; expires: number; account: string; add: boolean };
  const used = (await storage.get<Record<string, number>>("used")) ?? {};
  if (used[id] || expires < now) return plain(410, "Used");
  const current = await storage.get<string>("account");
  if (add && current && current !== account) return plain(409, "Taken");
  for (const [k, until] of Object.entries(used)) if (until < now) delete used[k];
  used[id] = expires;
  await storage.put("used", used);
  const result = current ?? account;
  if (!current) await storage.put("account", result);
  return json({ account: result });
}

/** The address's directory name: the same every time, and meaningless without ACCOUNT_KEY. */
export async function emailIdOf(masterKey: string, email: string): Promise<string> {
  const key = await crypto.subtle.importKey("raw", await derive(masterKey, "reader-email-id", 32), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return hex(new Uint8Array(await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(email)))).slice(0, 32);
}

export async function makeToken(masterKey: string, claims: LinkClaims): Promise<string> {
  const payload = base64url(new TextEncoder().encode(JSON.stringify(claims)));
  return `${payload}.${base64url(new Uint8Array(await sign(masterKey, payload)))}`;
}

/** The claims of a token this server signed and that hasn't ended, else null. */
export async function readToken(masterKey: string, token: string, now: number): Promise<LinkClaims | null> {
  const [payload, mac, extra] = token.split(".");
  if (!payload || !mac || extra !== undefined || token.length > 2048) return null;
  const key = await crypto.subtle.importKey("raw", await derive(masterKey, "reader-email-link", 32), { name: "HMAC", hash: "SHA-256" }, false, ["verify"]);
  let ok = false;
  try {
    ok = await crypto.subtle.verify("HMAC", key, fromBase64url(mac), new TextEncoder().encode(payload));
  } catch {
    return null;
  }
  if (!ok) return null;
  try {
    const claims = JSON.parse(new TextDecoder().decode(fromBase64url(payload))) as LinkClaims;
    if (typeof claims.email !== "string" || typeof claims.expires !== "number" || typeof claims.id !== "string") return null;
    return claims.expires >= now ? claims : null;
  } catch {
    return null;
  }
}

async function sign(masterKey: string, payload: string): Promise<ArrayBuffer> {
  const key = await crypto.subtle.importKey("raw", await derive(masterKey, "reader-email-link", 32), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload));
}

function fromBase64url(text: string): Uint8Array {
  const b64 = text.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((text.length + 3) % 4);
  return Uint8Array.from(atob(b64), (c) => c.charCodeAt(0));
}

/** Where sign-in links point: the web app (the Android app opens the same address as an App Link). */
function webAppOrigin(env: Env): string {
  return (env.WEB_APP_ORIGIN ?? allowedOrigins(env)[0] ?? "").replace(/\/$/, "");
}

/**
 * The sign-in email, in English only (#219): the one text that isn't in every language, since the
 * address alone doesn't say which language to write in.
 */
export function emailContent(link: string, kind: "signin" | "add"): { subject: string; text: string; html: string } {
  const subject = kind === "signin" ? "Sign in to Content" : "Add this address to Content";
  const action = kind === "signin" ? "sign in to Content" : "add this address to your Content account";
  const text = `Open this link to ${action}:\n${link}\n\nIt works once, for 30 minutes. Didn't ask for it? Ignore this email.\n`;
  const safe = link.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  const html = `<p>Open this link to ${action}:</p><p><a href="${safe}">${kind === "signin" ? "Sign in" : "Add address"}</a></p><p>It works once, for 30 minutes. Didn't ask for it? Ignore this email.</p>`;
  return { subject, text, html };
}

async function sendEmail(env: Env, fetcher: Fetcher, to: string, link: string, kind: "signin" | "add"): Promise<boolean> {
  const { subject, text, html } = emailContent(link, kind);
  try {
    const answer = await fetcher(RESEND_URL, {
      method: "POST",
      headers: { Authorization: `Bearer ${env.RESEND_API_KEY}`, "Content-Type": "application/json" },
      body: JSON.stringify({ from: env.EMAIL_FROM ?? "Content <signin@resend.wimvandonck.be>", to: [to], subject, text, html }),
    });
    return answer.ok;
  } catch {
    return false;
  }
}

function json(value: unknown, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(value), { status: 200, headers: { ...headers, "Content-Type": "application/json" } });
}
