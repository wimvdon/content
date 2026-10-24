/**
 * The inbox (#246): what other Content users shared with you, newest first, read or unread, until you
 * remove it. Saving an article from it keeps it there, as a feed article stays in its feed. Like the
 * Android app's Inbox (data/Sharing.kt).
 */

import { inInbox, itemOf, putItem, type Item } from "./items";
import type { Library } from "./library";

export const inboxItems = (lib: Library): Item[] => lib.items.filter(inInbox).sort((a, b) => (b.sharedBy?.at ?? 0) - (a.sharedBy?.at ?? 0));

/** How many are unread: the bell's dot shows while there are any. */
export const inboxUnread = (lib: Library) => lib.items.filter((i) => inInbox(i) && !i.sharedBy?.read).length;

/** The library with item `key` marked read (or unread) in the inbox; the same when it isn't there. */
export function markInboxRead(lib: Library, key: string, read = true): Library {
  const item = itemOf(lib, key);
  if (!item?.sharedBy || !inInbox(item) || (item.sharedBy.read === true) === read) return lib;
  return putItem(lib, { ...item, sharedBy: { ...item.sharedBy, read } });
}

/** The library with item `key` out of the inbox; it stays in Saved if it is saved. */
export function removeFromInbox(lib: Library, key: string): Library {
  const item = itemOf(lib, key);
  if (!item?.sharedBy || !inInbox(item)) return lib;
  return putItem(lib, { ...item, sharedBy: { ...item.sharedBy, waits: false } });
}
