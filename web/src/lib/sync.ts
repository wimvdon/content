import { articleKey, DEFAULT_ICON, sortTime, type Feed, type Library } from "./library";

/**
 * The sync file shared with the Android app (docs/sync.md) and the three-way merge, a port of
 * app/src/main/kotlin/.../sync/SyncDocument.kt and SyncMerge.kt.
 */

export interface SyncFolder {
  id: string;
  title: string;
  icon: string;
}

export interface SyncFeed {
  url: string;
  title: string;
  renamed: boolean;
  folderId: string | null;
  siteUrl: string | null;
  iconUrl: string | null;
}

export interface SyncDocument {
  folders: SyncFolder[];
  feeds: SyncFeed[];
  /** Read article key → the article's date in ms. */
  read: Record<string, number>;
}

export const FORMAT = "rss-reader-sync";
export const VERSION = 1;
export const KEEP_READ_MS = 120 * 24 * 60 * 60 * 1000;

/** When sync runs while the app is open, the same as the Android app's SyncTiming. */
export const CHANGE_DELAY_MS = 2_000;
/** While the page is visible it syncs this often, so changes from other devices come in. */
export const VISIBLE_INTERVAL_MS = 60_000;
/** Coming back or the next minute skips sync when it ran this recently. A refresh never does. */
export const MIN_GAP_MS = 10_000;

/** True when a sync is worth doing: never synced, or not within the last MIN_GAP_MS. */
export function syncDue(lastSyncedAt: number | null, now: number): boolean {
  if (lastSyncedAt === null) return true;
  const since = now - lastSyncedAt;
  return since < 0 || since >= MIN_GAP_MS;
}

export const emptyDocument = (): SyncDocument => ({ folders: [], feeds: [], read: {} });

export const feedUrlOf = (key: string) => key.split(" ")[0];

export function documentOf(lib: Library): SyncDocument {
  const read: Record<string, number> = {};
  for (const a of lib.articles) if (a.read) read[articleKey(a)] = sortTime(a);
  return {
    folders: lib.folders.map((f) => ({ id: f.id, title: f.title, icon: f.icon })),
    feeds: lib.feeds.map((f) => ({ url: f.url, title: f.title, renamed: f.renamed, folderId: f.folderId, siteUrl: f.siteUrl, iconUrl: f.iconUrl })),
    read,
  };
}

export function encode(doc: SyncDocument): string {
  const read: Record<string, number> = {};
  for (const key of Object.keys(doc.read).sort()) read[key] = doc.read[key];
  return JSON.stringify({
    format: FORMAT,
    version: VERSION,
    folders: doc.folders.map((f) => ({ id: f.id, title: f.title, icon: f.icon })),
    feeds: doc.feeds.map((f) => ({ url: f.url, title: f.title, renamed: f.renamed, folderId: f.folderId, siteUrl: f.siteUrl, iconUrl: f.iconUrl })),
    read,
  });
}

/** Null when the text isn't a sync file, or is from a newer version (it is then left alone). */
export function decode(text: string): SyncDocument | null {
  let root: unknown;
  try {
    root = JSON.parse(text);
  } catch {
    return null;
  }
  if (!isObject(root) || root.format !== FORMAT || typeof root.version !== "number" || root.version > VERSION) return null;
  const folders: SyncFolder[] = [];
  for (const f of Array.isArray(root.folders) ? root.folders : []) {
    if (!isObject(f) || typeof f.id !== "string" || !f.id || typeof f.title !== "string" || !f.title.trim()) continue;
    if (folders.some((x) => x.id === f.id)) continue;
    folders.push({ id: f.id, title: f.title, icon: typeof f.icon === "string" && f.icon.trim() ? f.icon : DEFAULT_ICON });
  }
  const folderIds = new Set(folders.map((f) => f.id));
  const feeds: SyncFeed[] = [];
  for (const f of Array.isArray(root.feeds) ? root.feeds : []) {
    if (!isObject(f) || typeof f.url !== "string" || !f.url || feeds.some((x) => x.url === f.url)) continue;
    feeds.push({
      url: f.url,
      title: typeof f.title === "string" && f.title.trim() ? f.title : f.url,
      renamed: f.renamed === true,
      folderId: typeof f.folderId === "string" && folderIds.has(f.folderId) ? f.folderId : null,
      siteUrl: typeof f.siteUrl === "string" && f.siteUrl ? f.siteUrl : null,
      iconUrl: typeof f.iconUrl === "string" && f.iconUrl ? f.iconUrl : null,
    });
  }
  const read: Record<string, number> = {};
  if (isObject(root.read)) for (const [k, v] of Object.entries(root.read)) if (typeof v === "number") read[k] = Math.trunc(v);
  return { folders, feeds, read };
}

export function pruned(doc: SyncDocument, keepSince: number): SyncDocument {
  const urls = new Set(doc.feeds.map((f) => f.url));
  const read: Record<string, number> = {};
  for (const [k, t] of Object.entries(doc.read)) if (t >= keepSince && urls.has(feedUrlOf(k))) read[k] = t;
  return { ...doc, read };
}

export function merge(base: SyncDocument | null, local: SyncDocument, remote: SyncDocument): SyncDocument {
  const b = base ?? emptyDocument();
  const folders = mergeRecords(b.folders, local.folders, remote.folders, (f) => f.id);
  const folderIds = new Set(folders.map((f) => f.id));
  const feeds = mergeRecords(b.feeds, local.feeds, remote.feeds, (f) => f.url).map((f) =>
    f.folderId && !folderIds.has(f.folderId) ? { ...f, folderId: null } : f,
  );
  return { folders, feeds, read: mergeRead(b.read, local.read, remote.read) };
}

function same<T>(a: T | undefined, b: T | undefined): boolean {
  return JSON.stringify(a) === JSON.stringify(b);
}

function mergeRecords<T>(base: T[], local: T[], remote: T[], key: (t: T) => string): T[] {
  const b = new Map(base.map((t) => [key(t), t]));
  const l = new Map(local.map((t) => [key(t), t]));
  const r = new Map(remote.map((t) => [key(t), t]));
  const merged = new Map<string, T>();
  for (const k of new Set([...l.keys(), ...r.keys(), ...b.keys()])) {
    const pick = same(l.get(k), b.get(k)) ? r.get(k) : same(r.get(k), b.get(k)) ? l.get(k) : (l.get(k) ?? r.get(k));
    if (pick !== undefined) merged.set(k, pick);
  }
  const baseOrder = base.map(key).filter((k) => l.has(k));
  const localOrder = local.map(key).filter((k) => b.has(k));
  const localOrderChanged = !same(baseOrder, localOrder);
  const [first, second] = localOrderChanged ? [local, remote] : [remote, local];
  const order = new Set<string>();
  for (const k of [...first.map(key), ...second.map(key)]) if (merged.has(k)) order.add(k);
  return [...order].map((k) => merged.get(k)!);
}

function mergeRead(base: Record<string, number>, local: Record<string, number>, remote: Record<string, number>): Record<string, number> {
  const out: Record<string, number> = {};
  for (const k of new Set([...Object.keys(base), ...Object.keys(local), ...Object.keys(remote)])) {
    const inBase = k in base;
    const inLocal = k in local;
    const read = inLocal === inBase ? k in remote : inLocal;
    if (read) out[k] = local[k] ?? remote[k] ?? base[k];
  }
  return out;
}

/** This device's side: articles it doesn't have count as unchanged. */
export function localDocument(lib: Library, base: SyncDocument | null): SyncDocument {
  const doc = documentOf(lib);
  const have = new Set(lib.articles.map(articleKey));
  const read: Record<string, number> = {};
  for (const [k, t] of Object.entries(base?.read ?? {})) if (!have.has(k)) read[k] = t;
  return { ...doc, read: { ...read, ...doc.read } };
}

export function apply(lib: Library, merged: SyncDocument): Library {
  const existing = new Map(lib.feeds.map((f) => [f.url, f]));
  const feeds: Feed[] = merged.feeds.map((f) => {
    const old = existing.get(f.url);
    return {
      url: f.url,
      title: f.title,
      renamed: f.renamed,
      folderId: f.folderId,
      siteUrl: f.siteUrl ?? old?.siteUrl ?? null,
      iconUrl: f.iconUrl ?? old?.iconUrl ?? null,
    };
  });
  const urls = new Set(feeds.map((f) => f.url));
  const articles = lib.articles
    .filter((a) => urls.has(a.feedUrl))
    .map((a) => {
      const read = articleKey(a) in merged.read;
      return a.read === read ? a : { ...a, read };
    });
  return { feeds, articles, folders: merged.folders.map((f) => ({ ...f })) };
}

export function hasNewFeeds(lib: Library, merged: SyncDocument): boolean {
  return merged.feeds.some((f) => !lib.feeds.some((x) => x.url === f.url));
}

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}
