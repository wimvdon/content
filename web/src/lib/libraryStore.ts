/**
 * The library in IndexedDB (#106): feeds and folders in the key-value store, every article and every
 * item in its own store, so a change writes only what changed (like the Android app's Room tables).
 * On first use the library of older versions (one "library" value, and "saved" for Saved without an
 * account) moves in once and is then deleted.
 */

import { articleKey, emptyLibrary, type Article, type Feed, type Folder, type Library } from "./library";
import { itemsOf, sameItem, type Item } from "./items";
import { localSavedOf } from "./localSaved";
import { migrateSaved } from "./savedItems";
import { ARTICLES, db, ITEMS } from "./storage";

const KV = "kv";

/** What to write after a change: feeds and folders in full when they changed, else only what changed. */
export interface LibraryChanges {
  feeds: Feed[] | null;
  folders: Folder[] | null;
  articles: Article[];
  deletedArticles: string[];
  items: Item[];
  deletedItems: string[];
}

export function changesBetween(old: Library, next: Library): LibraryChanges {
  const [articles, deletedArticles] = diff(old.articles, next.articles, articleKey, (a, b) => JSON.stringify(a) === JSON.stringify(b));
  const [items, deletedItems] = diff(old.items, next.items, (i) => i.key, sameItem);
  return {
    feeds: old.feeds === next.feeds || JSON.stringify(old.feeds) === JSON.stringify(next.feeds) ? null : next.feeds,
    folders: old.folders === next.folders || JSON.stringify(old.folders) === JSON.stringify(next.folders) ? null : next.folders,
    articles,
    deletedArticles,
    items,
    deletedItems,
  };
}

export const allOf = (lib: Library): LibraryChanges => ({
  feeds: lib.feeds,
  folders: lib.folders,
  articles: lib.articles,
  deletedArticles: [],
  items: lib.items,
  deletedItems: [],
});

export const isEmpty = (c: LibraryChanges) =>
  c.feeds === null && c.folders === null && c.articles.length + c.deletedArticles.length + c.items.length + c.deletedItems.length === 0;

function diff<T>(old: T[], next: T[], key: (t: T) => string, same: (a: T, b: T) => boolean): [T[], string[]] {
  if (old === next) return [[], []];
  const before = new Map(old.map((t) => [key(t), t]));
  const changed = next.filter((t) => {
    const was = before.get(key(t));
    return was === undefined || (was !== t && !same(was, t));
  });
  const kept = new Set(next.map(key));
  return [changed, [...before.keys()].filter((k) => !kept.has(k))];
}

/** A library read from older or damaged storage, made safe to use. */
export function libraryOf(value: Partial<Library> | undefined): Library {
  if (!value) return emptyLibrary();
  return {
    feeds: Array.isArray(value.feeds) ? value.feeds : [],
    folders: Array.isArray(value.folders) ? value.folders : [],
    articles: Array.isArray(value.articles) ? value.articles : [],
    items: itemsOf(value.items),
  };
}

function request<T>(r: IDBRequest<T>): Promise<T> {
  return new Promise((resolve, reject) => {
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
  });
}

function done(tx: IDBTransaction): Promise<void> {
  return new Promise((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error);
  });
}

/** The stored library; the first time, the older versions' library moved in. */
export async function loadLibrary(): Promise<Library> {
  const d = await db();
  const tx = d.transaction([KV, ARTICLES, ITEMS], "readonly");
  const kv = tx.objectStore(KV);
  const [legacy, saved, feeds, folders, articles, items] = await Promise.all([
    request(kv.get("library")),
    request(kv.get("saved")),
    request(kv.get("feeds")),
    request(kv.get("folders")),
    request(tx.objectStore(ARTICLES).getAll()),
    request(tx.objectStore(ITEMS).getAll()),
  ]);
  if (legacy === undefined && saved === undefined) return libraryOf({ feeds, folders, articles, items });
  // Older versions: one "library" value, and the articles saved without an account in "saved".
  const moved = migrateSaved(libraryOf(legacy as Partial<Library> | undefined), localSavedOf(saved));
  await writeLibrary(allOf(moved), true);
  return moved;
}

/** Writes what changed, all or nothing; [replace] first empties the stores and drops the old values. */
export async function writeLibrary(changes: LibraryChanges, replace = false): Promise<void> {
  if (!replace && isEmpty(changes)) return;
  const d = await db();
  const tx = d.transaction([KV, ARTICLES, ITEMS], "readwrite");
  const kv = tx.objectStore(KV);
  const articles = tx.objectStore(ARTICLES);
  const items = tx.objectStore(ITEMS);
  if (replace) {
    articles.clear();
    items.clear();
    kv.delete("library");
    kv.delete("saved");
  }
  if (changes.feeds) kv.put(changes.feeds, "feeds");
  if (changes.folders) kv.put(changes.folders, "folders");
  for (const key of changes.deletedArticles) articles.delete(key);
  for (const a of changes.articles) articles.put(a, articleKey(a));
  for (const key of changes.deletedItems) items.delete(key);
  for (const i of changes.items) items.put(i);
  await done(tx);
}
