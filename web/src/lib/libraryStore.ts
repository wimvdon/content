/**
 * The library in IndexedDB (#106): feeds and folders in the key-value store, every article and every
 * item in its own store, so a change writes only what changed (like the Android app's Room tables).
 * On first use the library of older versions (one "library" value, and "saved" for Saved without an
 * account) moves in once and is then deleted.
 */

import { articleKey, emptyLibrary, type Article, type Feed, type Folder, type Library } from "./library";
import { itemsOf, sameItem, type Item } from "./items";
import { ARTICLES, db, ITEMS, SYNC_ROWS } from "./storage";
import type { ItemRow, ItemSyncState } from "./syncRows";

const KV = "kv";

/** What to write after a change: feeds and folders in full when they changed, else only what changed. */
export interface LibraryChanges {
  feeds: Feed[] | null;
  folders: Folder[] | null;
  articles: Article[];
  deletedArticles: string[];
  items: Item[];
  deletedItems: string[];
  /** The whole map when it changed (it changes only while syncing), else null. */
  readElsewhere: Record<string, number> | null;
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
    readElsewhere: old.readElsewhere === next.readElsewhere || JSON.stringify(old.readElsewhere) === JSON.stringify(next.readElsewhere) ? null : next.readElsewhere,
  };
}

export const allOf = (lib: Library): LibraryChanges => ({
  feeds: lib.feeds,
  folders: lib.folders,
  articles: lib.articles,
  deletedArticles: [],
  items: lib.items,
  deletedItems: [],
  readElsewhere: lib.readElsewhere,
});

export const isEmpty = (c: LibraryChanges) =>
  c.feeds === null && c.folders === null && c.readElsewhere === null && c.articles.length + c.deletedArticles.length + c.items.length + c.deletedItems.length === 0;

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
    readElsewhere: readElsewhereOf(value.readElsewhere),
  };
}

function readElsewhereOf(value: unknown): Record<string, number> {
  if (typeof value !== "object" || value === null || Array.isArray(value)) return {};
  return Object.fromEntries(Object.entries(value).filter(([, t]) => typeof t === "number"));
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

/** The stored library. */
export async function loadLibrary(): Promise<Library> {
  const d = await db();
  const tx = d.transaction([KV, ARTICLES, ITEMS], "readonly");
  const kv = tx.objectStore(KV);
  const [feeds, folders, articles, items, readElsewhere] = await Promise.all([
    request(kv.get("feeds")),
    request(kv.get("folders")),
    request(tx.objectStore(ARTICLES).getAll()),
    request(tx.objectStore(ITEMS).getAll()),
    request(kv.get("readElsewhere")),
  ]);
  return libraryOf({ feeds, folders, articles, items, readElsewhere });
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
  if (changes.readElsewhere) kv.put(changes.readElsewhere, "readElsewhere");
  for (const key of changes.deletedArticles) articles.delete(key);
  for (const a of changes.articles) articles.put(a, articleKey(a));
  for (const key of changes.deletedItems) items.delete(key);
  for (const i of changes.items) items.put(i);
  await done(tx);
}

/** What this browser last synced of the account's items (syncRows.ts). */
export async function loadItemSyncState(): Promise<ItemSyncState> {
  const d = await db();
  const tx = d.transaction([KV, SYNC_ROWS], "readonly");
  const [version, rows] = await Promise.all([request(tx.objectStore(KV).get("itemsVersion")), request(tx.objectStore(SYNC_ROWS).getAll())]);
  const out: Record<string, ItemRow> = {};
  for (const r of rows as ItemRow[]) if (typeof r?.key === "string" && typeof r.version === "number" && r.data) out[r.key] = r;
  return { version: typeof version === "number" ? version : 0, rows: out };
}

/** Stores the state after a sync that started from previous, writing only the rows that changed; null forgets it all. */
export async function writeItemSyncState(previous: ItemSyncState, next: ItemSyncState | null): Promise<void> {
  const d = await db();
  const tx = d.transaction([KV, SYNC_ROWS], "readwrite");
  const rows = tx.objectStore(SYNC_ROWS);
  if (next === null) {
    rows.clear();
    tx.objectStore(KV).delete("itemsVersion");
  } else {
    // Deleted rows aren't kept: saving that key again later costs one conflict, which brings the deletion back.
    for (const key of Object.keys(previous.rows)) if (!next.rows[key]?.data) rows.delete(key);
    for (const row of Object.values(next.rows)) if (row.data && previous.rows[row.key] !== row) rows.put(row);
    tx.objectStore(KV).put(next.version, "itemsVersion");
  }
  await done(tx);
}
