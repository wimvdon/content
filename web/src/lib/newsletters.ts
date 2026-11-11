/**
 * Newsletters: the emails that reach Saved through Instapaper (Instapaper's "private content",
 * whose address is Instapaper's own page, instapaperLinks.ts). One that comes in after Instapaper is
 * connected waits unread (bold, with a dot) until it is opened, marked as read, archived or removed,
 * when Mark incoming newsletters as unread is on (off: they come in read, and turning it off reads them);
 * the first import brings them in read. Nothing makes one unread again, except Undo right after Mark
 * all as read. Saved's Newsletters chip shows them; a dot on Saved and the chip says one
 * waits. The same rules as the Android app's Newsletters.kt.
 */

import { instapaperBookmarkId } from "./instapaperLinks";
import { changeItem, itemOf, putItem, type Item } from "./items";
import type { Library } from "./library";

export const isNewsletter = (url: string | null | undefined): boolean => instapaperBookmarkId(url) !== null;

/** Whether a newsletter waits unread: the dot on Saved and the chip (with the setting on). */
export const hasUnreadNewsletter = (lib: Library): boolean => lib.items.some((i) => i.unread === true);

/** The library with item `key` read; the same when it wasn't unread. */
export function markNewsletterRead(lib: Library, key: string): Library {
  const item = itemOf(lib, key);
  return item?.unread ? putItem(lib, { ...item, unread: false }) : lib;
}

/** The keys of the unread ones among `items` (the list shown): what Mark all as read marks, and Undo brings back. */
export const unreadNewsletterKeys = (items: Item[]): string[] => items.filter((i) => i.unread === true).map((i) => i.key);

/** The library with items `keys` read (Mark all as read). */
export const markNewslettersRead = (lib: Library, keys: string[]): Library => keys.reduce(markNewsletterRead, lib);

/** Undo of Mark all as read: items `keys` unread again, those still to read. */
export const undoMarkNewslettersRead = (lib: Library, keys: string[]): Library =>
  keys.reduce((out, key) => changeItem(out, key, (i) => (i.readingList?.state === "toread" ? { ...i, unread: true } : i)), lib);
