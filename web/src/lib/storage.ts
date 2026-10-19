/**
 * The browser's storage, on IndexedDB: a tiny key-value store ("kv") for settings, the sign-in and
 * the library's feeds and folders, and one store per article and per item (#106, libraryStore.ts).
 */

const DB = "rss-reader";
const STORE = "kv";
export const ARTICLES = "articles";
export const ITEMS = "items";
export const SYNC_ROWS = "syncRows";

let opening: Promise<IDBDatabase> | null = null;

export function db(): Promise<IDBDatabase> {
  opening ??= new Promise((resolve, reject) => {
    // 2 added the articles and items stores, 3 the synced item rows (#106).
    const request = indexedDB.open(DB, 3);
    request.onupgradeneeded = () => {
      const d = request.result;
      if (!d.objectStoreNames.contains(STORE)) d.createObjectStore(STORE);
      if (!d.objectStoreNames.contains(ARTICLES)) d.createObjectStore(ARTICLES);
      if (!d.objectStoreNames.contains(ITEMS)) d.createObjectStore(ITEMS, { keyPath: "key" });
      if (!d.objectStoreNames.contains(SYNC_ROWS)) d.createObjectStore(SYNC_ROWS, { keyPath: "key" });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => {
      opening = null;
      reject(request.error);
    };
  });
  return opening;
}

export async function load<T>(key: string): Promise<T | undefined> {
  const d = await db();
  return new Promise((resolve, reject) => {
    const request = d.transaction(STORE, "readonly").objectStore(STORE).get(key);
    request.onsuccess = () => resolve(request.result as T | undefined);
    request.onerror = () => reject(request.error);
  });
}

export async function save<T>(key: string, value: T): Promise<void> {
  const d = await db();
  return new Promise((resolve, reject) => {
    const tx = d.transaction(STORE, "readwrite");
    tx.objectStore(STORE).put(value, key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}

export async function remove(key: string): Promise<void> {
  const d = await db();
  return new Promise((resolve, reject) => {
    const tx = d.transaction(STORE, "readwrite");
    tx.objectStore(STORE).delete(key);
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
  });
}
