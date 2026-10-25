/**
 * Sharing with other Content users (#167), as the Android app's data/Sharing.kt does: one article at
 * a time. Receiving puts each shared article in the inbox (#246), unread, marked with who shared it,
 * whether or not it is in Saved too. A one-time copy.
 */

import { itemOf, keyOfLink, newItem, putItem, type Item, type SharedBy } from "./items";
import type { Library } from "./library";

export interface SharedArticle {
  url: string;
  title: string;
  imageUrl?: string | null;
  description?: string;
}

/** Something another Content user shared, as the account's inbox holds it. */
export interface InboxMessage {
  id: string;
  from: SharedBy;
  items: SharedArticle[];
}

/** What taking in the inbox brought: from whom, and how many articles. */
export interface Received {
  from: SharedBy[];
  count: number;
}

export const sharedArticle = (i: Item): SharedArticle => ({ url: i.url, title: i.title, imageUrl: i.imageUrl, description: i.description });

/** How the app names who shared it: the name, else the address. */
export const sharedByLabel = (s: SharedBy) => s.name.trim() || s.email;

/**
 * The library with messages taken in: each article is in the inbox, unread, with who shared it. One
 * already in Saved stays there as well; one shared again comes back unread with its latest sender.
 */
export function takeInShared(lib: Library, messages: InboxMessage[], now: number): { library: Library; received: Received } {
  let result = lib;
  let count = 0;
  const from = new Map<string, SharedBy>();
  for (const message of messages) {
    for (const shared of message.items) {
      const key = keyOfLink(shared.url);
      if (!key) continue;
      const existing = itemOf(result, key);
      const base = existing ?? newItem(result, shared.url, shared.title, now, shared.description ?? "", shared.imageUrl ?? null);
      if (!base) continue;
      const updated: Item = { ...base, sharedBy: { ...message.from, at: now, waits: true, read: false } };
      if (JSON.stringify(updated) !== JSON.stringify(existing)) {
        result = putItem(result, updated);
        count++;
      }
      from.set(message.from.email, message.from);
    }
  }
  return { library: result, received: { from: [...from.values()], count } };
}

/** The messages of an inbox answer; ones that aren't one are left out. */
export function inboxOf(body: unknown): InboxMessage[] {
  const list = (body as { messages?: unknown } | null)?.messages;
  if (!Array.isArray(list)) return [];
  return list.flatMap((raw) => {
    const m = raw as Record<string, unknown> | null;
    const from = m?.from as Record<string, unknown> | null;
    if (!m || typeof m.id !== "string" || typeof from?.email !== "string" || !from.email) return [];
    const items = (Array.isArray(m.items) ? m.items : []).flatMap((x) => {
      const a = x as Record<string, unknown> | null;
      if (!a || typeof a.url !== "string") return [];
      return [{ url: a.url, title: typeof a.title === "string" ? a.title : a.url, imageUrl: typeof a.imageUrl === "string" && a.imageUrl.startsWith("https://") ? a.imageUrl : null, description: typeof a.description === "string" ? a.description : "" }];
    });
    return [{ id: m.id, from: { email: from.email, name: typeof from.name === "string" ? from.name : "" }, items }];
  });
}
