/**
 * Saved without an account is the library's reading list (#106): LocalSaved sees the items on the
 * reading list as its saved articles, and its changes become changes to those items. Ids are worked
 * out from the item's key and the tag's name, so they stay the same between calls. The same as the
 * Android app's instapaper/SavedItems.kt.
 */

import { cleanTags, isWebLink, itemOf, keyOfLink, newItem, putItem, type Item } from "./items";
import type { Library } from "./library";
import type { LocalArticle, LocalSavedData } from "./localSaved";

/** The reading list as LocalSaved's data. */
export function savedData(lib: Library): LocalSavedData {
  const articles: LocalArticle[] = lib.items.filter((i) => i.readingList !== null).map(articleOf);
  const tags = new Map<number, { id: number; name: string }>();
  for (const i of lib.items) if (i.readingList) for (const name of i.tags) tags.set(tagIdOf(name), { id: tagIdOf(name), name });
  const folders = new Map<number, { id: number; name: string }>();
  for (const i of lib.items) {
    const name = folderOf(i);
    if (name && !folders.has(folderIdOf(name))) folders.set(folderIdOf(name), { id: folderIdOf(name), name });
  }
  const ids = [...articles.map((a) => a.id), ...tags.keys(), ...folders.keys()];
  return { articles, tags: [...tags.values()], folders: [...folders.values()], nextId: Math.max(0, ...ids) + 1 };
}

function articleOf(i: Item): LocalArticle {
  return {
    id: idOf(i.key),
    url: i.url,
    title: i.title,
    time: Math.floor((i.readingList?.addedAt ?? i.createdAt) / 1000),
    liked: i.liked,
    archived: i.readingList?.state === "archived",
    tagIds: i.tags.map(tagIdOf),
    folderId: folderOf(i) ? folderIdOf(folderOf(i)!) : null,
  };
}

/** An item's folder while it is to read (#119). */
const folderOf = (i: Item) => (i.readingList?.state === "toread" && i.folder?.trim() ? i.folder.trim() : null);

/**
 * Applies what LocalSaved changed (before → after) to the library's items: new saved articles join
 * the item of their link (no copy), changed ones change their item, removed ones leave the reading
 * list (Bookmarks keeps them).
 */
export function applySaved(lib: Library, before: LocalSavedData, after: LocalSavedData, now: number): Library {
  const old = new Map(before.articles.map((a) => [a.id, a]));
  const tagName = new Map(after.tags.map((t) => [t.id, t.name]));
  const folderName = new Map((after.folders ?? []).map((f) => [f.id, f.name]));
  let result = lib;
  for (const a of after.articles) {
    const previous = old.get(a.id);
    if (previous && JSON.stringify(previous) === JSON.stringify(a)) continue;
    const key = keyOfLink(a.url);
    const existing = (previous ? result.items.find((i) => idOf(i.key) === a.id) : undefined) ?? (key ? itemOf(result, key) : undefined);
    const base = existing ?? newItem(result, a.url, a.title !== a.url ? a.title : null, now);
    if (!base) continue;
    // Saving again sets a new time; otherwise the stored milliseconds stay.
    const addedAt = previous && previous.time === a.time ? (base.readingList?.addedAt ?? a.time * 1000) : a.time * 1000;
    result = putItem(result, {
      ...base,
      // A link saved without a title is titled with its address; the feed article's title is better.
      title: a.title.trim() && a.title !== a.url ? a.title : base.title,
      readingList: { state: a.archived ? "archived" : "toread", addedAt },
      // Archiving takes it out of its folder (#119).
      folder: a.archived || a.folderId == null ? null : (folderName.get(a.folderId) ?? base.folder),
      liked: a.liked,
      tags: cleanTags(a.tagIds.flatMap((id) => (tagName.has(id) ? [tagName.get(id)!] : []))),
    });
  }
  const kept = new Set(after.articles.map((a) => a.id));
  for (const gone of before.articles.filter((a) => !kept.has(a.id))) {
    const item = result.items.find((i) => idOf(i.key) === gone.id);
    if (!item) continue;
    // Off the reading list; an item that is also a bookmark stays, with its tags and like.
    result = putItem(result, item.bookmark ? { ...item, readingList: null } : { ...item, readingList: null, liked: false, tags: [] });
  }
  return result;
}

/**
 * The library after moving in the articles saved in this browser before #106 (IndexedDB "saved"):
 * each becomes the item of its link on the reading list, with its title, time, like, archive and
 * tags. Links that turn out to be the same keep the newest.
 */
export function migrateSaved(lib: Library, saved: LocalSavedData): Library {
  const names = new Map(saved.tags.map((t) => [t.id, t.name]));
  return [...saved.articles]
    .filter((a) => isWebLink(a.url))
    .sort((a, b) => a.time - b.time)
    .reduce((acc, a) => {
      const time = a.time * 1000;
      const key = keyOfLink(a.url);
      const base = (key ? itemOf(acc, key) : undefined) ?? newItem(acc, a.url, a.title !== a.url ? a.title : null, time);
      if (!base) return acc;
      return putItem(acc, {
        ...base,
        title: a.title.trim() && a.title !== a.url ? a.title : base.title,
        readingList: { state: a.archived ? "archived" : "toread", addedAt: time },
        liked: base.liked || a.liked,
        tags: cleanTags([...base.tags, ...a.tagIds.flatMap((id) => (names.has(id) ? [names.get(id)!] : []))]),
      });
    }, lib);
}

/** A saved article's id: the same for the same item every time (a 53-bit hash of the key). */
export const idOf = (key: string) => hash(`item:${key}`);

export const tagIdOf = (name: string) => hash(`tag:${name.toLowerCase()}`);

export const folderIdOf = (name: string) => hash(`folder:${name.trim().toLowerCase()}`);

/** cyrb53: a fast 53-bit string hash, so the id is a safe integer; never 0. */
function hash(text: string): number {
  let h1 = 0xdeadbeef;
  let h2 = 0x41c6ce57;
  for (let i = 0; i < text.length; i++) {
    const c = text.charCodeAt(i);
    h1 = Math.imul(h1 ^ c, 2654435761);
    h2 = Math.imul(h2 ^ c, 1597334677);
  }
  h1 = Math.imul(h1 ^ (h1 >>> 16), 2246822507) ^ Math.imul(h2 ^ (h2 >>> 13), 3266489909);
  h2 = Math.imul(h2 ^ (h2 >>> 16), 2246822507) ^ Math.imul(h1 ^ (h1 >>> 13), 3266489909);
  return 4294967296 * (2097151 & h2) + (h1 >>> 0) || 1;
}
