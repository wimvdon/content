/**
 * Notifications when something is shared with you (#235), in this browser: Web Push through the
 * feed loader (worker/src/push.ts). Turning them on asks the browser's permission, registers the
 * service worker (public/sw.js) and gives the feed loader this browser's push address; turning
 * them off takes it back. Only while signed in; nothing about what was shared goes through the
 * browser's push service, only a poke.
 */

import { AccountClient } from "./account";
import { t } from "./i18n";

/** Whether this browser can show pushed notifications at all. */
export const pushSupported = () => typeof window !== "undefined" && "serviceWorker" in navigator && "PushManager" in window && "Notification" in window;

/** The service worker's address, with the notification's text for it to show. */
export const serviceWorkerUrl = () => `/sw.js?${new URLSearchParams({ title: t("pushSharedTitle"), body: t("pushSharedBody") })}`;

/** The VAPID public key (base64url) as the bytes PushManager.subscribe wants. */
export function applicationServerKey(publicKey: string): Uint8Array<ArrayBuffer> {
  const plain = atob(publicKey.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(publicKey.length / 4) * 4, "="));
  const bytes = new Uint8Array(new ArrayBuffer(plain.length));
  for (let i = 0; i < plain.length; i++) bytes[i] = plain.charCodeAt(i);
  return bytes;
}

/** This browser's push subscription, if notifications are on. */
async function subscription(): Promise<PushSubscription | null> {
  if (!pushSupported()) return null;
  const registration = await navigator.serviceWorker.getRegistration("/");
  return (await registration?.pushManager.getSubscription()) ?? null;
}

/** Whether notifications are on in this browser. */
export async function notificationsOn(): Promise<boolean> {
  return (await subscription()) !== null && Notification.permission === "granted";
}

export type TurnOnResult = "on" | "denied" | "unavailable" | "failed";

/** Turns notifications on: permission, service worker, push address to the feed loader. */
export async function turnOn(client: AccountClient): Promise<TurnOnResult> {
  if (!pushSupported()) return "unavailable";
  const publicKey = await client.pushKey();
  if (!publicKey) return "unavailable";
  if ((await Notification.requestPermission()) !== "granted") return "denied";
  try {
    const registration = await navigator.serviceWorker.register(serviceWorkerUrl(), { scope: "/" });
    await navigator.serviceWorker.ready;
    const sub = (await registration.pushManager.getSubscription()) ?? (await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: applicationServerKey(publicKey) }));
    await client.setPush(sub.endpoint);
    return "on";
  } catch {
    return "failed";
  }
}

/** Turns notifications off: the feed loader forgets the address and the browser drops it. */
export async function turnOff(client: AccountClient | null): Promise<void> {
  const sub = await subscription();
  if (client) await client.setPush(null).catch(() => undefined);
  await sub?.unsubscribe().catch(() => false);
}

/** Keeps the service worker's text in this page's language, when notifications are on. */
export async function refreshServiceWorker(): Promise<void> {
  if (await notificationsOn()) await navigator.serviceWorker.register(serviceWorkerUrl(), { scope: "/" }).catch(() => undefined);
}
