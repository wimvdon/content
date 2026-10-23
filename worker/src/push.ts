/**
 * Notifications when something is shared with you (#235). Each signed-in device may give the feed
 * loader one push address, kept encrypted in its account (account.ts) and forgotten when the device
 * signs out. Sharing then pokes every device of the receiver:
 *
 *  - a browser through Web Push, with an empty message signed with the VAPID key pair (the public half
 *    in wrangler.toml as VAPID_PUBLIC_KEY, the private half the secret VAPID_PRIVATE_KEY). The page's
 *    service worker shows "Shared with you" and opens Saved › Shared with me. Nothing about what was
 *    shared goes through the browser's push service.
 *
 * Without the keys, nothing is sent and devices are told there are no notifications.
 */

import type { Env } from "./index";

type Fetcher = (input: string, init?: RequestInit) => Promise<Response>;

export type PushTarget = { kind: "web"; endpoint: string };

/** How long a push service keeps a poke for a device that is offline. */
const TTL_SECONDS = 24 * 60 * 60;

/** A push target a device may register, or null when it isn't one. */
export function pushTargetOf(body: unknown): PushTarget | null {
  const b = body as { kind?: unknown; endpoint?: unknown } | null;
  if (b?.kind === "web" && typeof b.endpoint === "string" && b.endpoint.startsWith("https://") && b.endpoint.length <= 2048) {
    return { kind: "web", endpoint: b.endpoint };
  }
  return null;
}

/** Whether notifications can be sent: both halves of the VAPID key pair are set. */
export const pushReady = (env: Env) => Boolean(env.VAPID_PUBLIC_KEY && env.VAPID_PRIVATE_KEY);

/** Pokes one device: "sent", "gone" (the address no longer works: forget it) or "failed". */
export async function poke(target: PushTarget, env: Env, fetcher: Fetcher, now = Date.now()): Promise<"sent" | "gone" | "failed"> {
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
