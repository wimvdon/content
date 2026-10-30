/**
 * Notifications when something is shared with you (#235). Each signed-in device may give the feed
 * loader one push address, kept encrypted in its account (account.ts) and forgotten when the device
 * signs out. Sharing then pokes every device of the receiver:
 *
 *  - a browser through Web Push, with an empty message signed with the VAPID key pair (the public half
 *    in wrangler.toml as VAPID_PUBLIC_KEY, the private half the secret VAPID_PRIVATE_KEY). The page's
 *    service worker shows "Shared with you" and opens Saved › Shared with me. Nothing about what was
 *    shared goes through the browser's push service.
 *  - an Android phone through Firebase Cloud Messaging, with a data-only message ({"kind": "shared"}
 *    and, so the notification can show it, a preview: the first article's title, intro and picture
 *    address, who shared it and how many articles) sent with the service account in the secret
 *    FCM_SERVICE_ACCOUNT (its JSON key file). Firebase sees that preview; the app shows the
 *    notification in its own language and downloads the picture itself.
 *
 * Without the keys, nothing is sent and devices are told there are no notifications.
 */

import type { Env } from "./index";

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

export type PushTarget = { kind: "web"; endpoint: string } | { kind: "fcm"; token: string };

/** How long a push service keeps a poke for a device that is offline. */
const TTL_SECONDS = 24 * 60 * 60;

/** A push target a device may register, or null when it isn't one. */
export function pushTargetOf(body: unknown): PushTarget | null {
  const b = body as { kind?: unknown; endpoint?: unknown; token?: unknown } | null;
  if (b?.kind === "web" && typeof b.endpoint === "string" && b.endpoint.startsWith("https://") && b.endpoint.length <= 2048) {
    return { kind: "web", endpoint: b.endpoint };
  }
  if (b?.kind === "fcm" && typeof b.token === "string" && /^[\w:.-]{20,4096}$/.test(b.token)) return { kind: "fcm", token: b.token };
  return null;
}

/** Whether browsers can get notifications: both halves of the VAPID key pair are set. */
export const pushReady = (env: Env) => Boolean(env.VAPID_PUBLIC_KEY && env.VAPID_PRIVATE_KEY);

/** Whether Android phones can get notifications: the Firebase service account is set. */
export const fcmReady = (env: Env) => Boolean(env.FCM_SERVICE_ACCOUNT);

/** Whether any notification can be sent. */
export const anyPushReady = (env: Env) => pushReady(env) || fcmReady(env);

/** What an Android notification shows of a share: the first article, who shared it, and how many. */
export interface SharePreview {
  title: string;
  intro: string;
  imageUrl: string | null;
  from: string;
  count: number;
}

/** Firebase allows 4 KB of data per message; these keep a preview well within it. */
const PREVIEW_TITLE = 150;
const PREVIEW_INTRO = 300;
const PREVIEW_IMAGE = 1000;

/**
 * The preview as FCM data (strings only), cut to fit. Firebase refuses a message whose data uses one
 * of its own keys ("from", "notification", "message_type", "google…", "gcm…"), so the sender is "sender".
 */
export function previewData(preview: SharePreview): Record<string, string> {
  const data: Record<string, string> = {
    title: preview.title.slice(0, PREVIEW_TITLE),
    intro: preview.intro.slice(0, PREVIEW_INTRO),
    sender: preview.from.slice(0, 100),
    count: String(preview.count),
  };
  if (preview.imageUrl && preview.imageUrl.length <= PREVIEW_IMAGE) data.image = preview.imageUrl;
  return data;
}

/** Pokes one device: "sent", "gone" (the address no longer works: forget it) or "failed". Only a phone gets the preview. */
export async function poke(target: PushTarget, env: Env, fetcher: Fetcher, now = Date.now(), preview?: SharePreview): Promise<"sent" | "gone" | "failed"> {
  if (target.kind === "fcm") return sendFcm(target.token, env, fetcher, now, preview);
  if (!pushReady(env)) return "failed";
  try {
    const audience = new URL(target.endpoint).origin;
    const jwt = await vapidJwt(audience, env.WEB_APP_ORIGIN ?? "https://github.com/wimvdon/content", env.VAPID_PUBLIC_KEY!, env.VAPID_PRIVATE_KEY!, now);
    const answer = await fetcher(target.endpoint, {
      method: "POST",
      headers: { TTL: String(TTL_SECONDS), Urgency: "normal", Authorization: `vapid t=${jwt}, k=${env.VAPID_PUBLIC_KEY}` },
    });
    if (answer.status === 404 || answer.status === 410) return "gone";
    return answer.ok ? "sent" : "failed";
  } catch {
    return "failed";
  }
}

/**
 * The VAPID token (RFC 8292): a JWT signed with ES256 for the push service's origin, valid 12 hours.
 * The public key is the uncompressed P-256 point (65 bytes) and the private key its scalar d (32
 * bytes), both base64url, as web-push tools write them.
 */
export async function vapidJwt(audience: string, subject: string, publicKey: string, privateKey: string, now: number): Promise<string> {
  const point = fromBase64Url(publicKey);
  if (point.length !== 65 || point[0] !== 4) throw new Error("VAPID_PUBLIC_KEY isn't an uncompressed P-256 key");
  const key = await crypto.subtle.importKey(
    "jwk",
    { kty: "EC", crv: "P-256", x: toBase64Url(point.slice(1, 33)), y: toBase64Url(point.slice(33, 65)), d: privateKey, ext: true },
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );
  const encode = (value: unknown) => toBase64Url(new TextEncoder().encode(JSON.stringify(value)));
  const unsigned = `${encode({ typ: "JWT", alg: "ES256" })}.${encode({ aud: audience, exp: Math.floor(now / 1000) + 12 * 60 * 60, sub: subject })}`;
  // WebCrypto signs ECDSA as r || s, which is what a JWT wants.
  const signature = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${toBase64Url(new Uint8Array(signature))}`;
}

export function toBase64Url(bytes: Uint8Array): string {
  let text = "";
  for (const b of bytes) text += String.fromCharCode(b);
  return btoa(text).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function fromBase64Url(text: string): Uint8Array {
  const plain = atob(text.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(text.length / 4) * 4, "="));
  return Uint8Array.from(plain, (c) => c.charCodeAt(0));
}

// Firebase Cloud Messaging (HTTP v1) for the Android app.

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

/** The service account in FCM_SERVICE_ACCOUNT, or null when it isn't one. */
export function serviceAccountOf(text: string | undefined): ServiceAccount | null {
  try {
    const parsed = JSON.parse(text ?? "") as Partial<ServiceAccount>;
    return parsed.client_email && parsed.private_key && parsed.project_id ? (parsed as ServiceAccount) : null;
  } catch {
    return null;
  }
}

/** Google's access token, kept while it is valid (an hour) by this copy of the feed loader. */
let accessToken: { value: string; until: number; account: string } | null = null;

/** An OAuth access token for sending with FCM: a JWT signed with the service account's key, swapped at Google. */
async function fcmAccessToken(account: ServiceAccount, fetcher: Fetcher, now: number): Promise<string | null> {
  if (accessToken && accessToken.account === account.client_email && accessToken.until > now + 60_000) return accessToken.value;
  const pem = account.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const key = await crypto.subtle.importKey("pkcs8", fromBase64Url(pem), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const encode = (value: unknown) => toBase64Url(new TextEncoder().encode(JSON.stringify(value)));
  const iat = Math.floor(now / 1000);
  const unsigned = `${encode({ alg: "RS256", typ: "JWT" })}.${encode({ iss: account.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat, exp: iat + 3600 })}`;
  const signature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));
  const answer = await fetcher("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${toBase64Url(signature)}` }).toString(),
  });
  if (!answer.ok) return null;
  const body = (await answer.json()) as { access_token?: string; expires_in?: number };
  if (!body.access_token) return null;
  accessToken = { value: body.access_token, until: now + (body.expires_in ?? 3600) * 1000, account: account.client_email };
  return body.access_token;
}

/** Sends the data-only "shared" message to one phone, with the preview when there is one. */
async function sendFcm(token: string, env: Env, fetcher: Fetcher, now: number, preview?: SharePreview): Promise<"sent" | "gone" | "failed"> {
  const account = serviceAccountOf(env.FCM_SERVICE_ACCOUNT);
  if (!account) return "failed";
  try {
    const bearer = await fcmAccessToken(account, fetcher, now);
    if (!bearer) return "failed";
    const answer = await fetcher(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`, {
      method: "POST",
      headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data: { kind: "shared", ...(preview ? previewData(preview) : {}) }, android: { priority: "high", ttl: "86400s" } } }),
    });
    // An app that was uninstalled or a token that was replaced: forget it. Any other refusal (a message
    // Firebase didn't accept, for one) is a failure, and the phone stays known.
    if (answer.status === 404) return "gone";
    if (answer.status === 400 && isBadToken(await answer.text())) return "gone";
    return answer.ok ? "sent" : "failed";
  } catch {
    return "failed";
  }
}

/** Whether Firebase's 400 is about the token itself (unregistered or not a token), not about the message. */
export function isBadToken(body: string): boolean {
  return /UNREGISTERED/.test(body) || /registration token/i.test(body);
}

/** Forgets the cached access token (tests). */
export function resetFcmToken(): void {
  accessToken = null;
}
