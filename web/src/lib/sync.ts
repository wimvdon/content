import { SyncError, type ReadAnswer, type WriteAnswer } from "./account";
import { articleKey, DEFAULT_ICON, READ_MARKS_KEPT_MS, type Feed, type Library } from "./library";

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

/**
 * Folders, feeds and settings. Read marks, the reading list and Bookmarks sync per item instead
 * (syncRows.ts, #106).
 */
export interface SyncDocument {
  folders: SyncFolder[];
  feeds: SyncFeed[];
  /** Shared setting name → text value (syncSettings.ts). */
  settings: Record<string, string>;
  /**
   * The read marks of a version 2 file (article key → date in ms), only when reading one: the first
   * sync of this version moves them to per-item sync. Never written.
   */
  legacyRead?: Record<string, number>;
}

export const FORMAT = "rss-reader-sync";
// 2 added settings; 3 moved the read marks to per-item sync (#106). Older apps leave a newer file
// alone and ask to be updated.
export const VERSION = 3;
export const KEEP_READ_MS = READ_MARKS_KEPT_MS;

/** When sync runs while the app is open, the same as the Android app's SyncTiming. */
export const CHANGE_DELAY_MS = 1_000;
/**
 * While the page is visible and the live connection is down, it syncs this often, so changes from
 * other devices still come in. With the live connection they come in at once.
 */
export const VISIBLE_INTERVAL_MS = 60_000;
/** Coming back or the next minute skips sync when it ran this recently. A refresh never does. */
export const MIN_GAP_MS = 10_000;

/** True when a sync is worth doing: never synced, or not within the last MIN_GAP_MS. */
export function syncDue(lastSyncedAt: number | null, now: number): boolean {
  if (lastSyncedAt === null) return true;
  const since = now - lastSyncedAt;
  return since < 0 || since >= MIN_GAP_MS;
}

export const emptyDocument = (): SyncDocument => ({ folders: [], feeds: [], settings: {} });

export function documentOf(lib: Library): SyncDocument {
  return {
    folders: lib.folders.map((f) => ({ id: f.id, title: f.title, icon: f.icon })),
    feeds: lib.feeds.map((f) => ({ url: f.url, title: f.title, renamed: f.renamed, folderId: f.folderId, siteUrl: f.siteUrl, iconUrl: f.iconUrl })),
    settings: {},
  };
}

export function encode(doc: SyncDocument): string {
  return JSON.stringify({
    format: FORMAT,
    version: VERSION,
    folders: doc.folders.map((f) => ({ id: f.id, title: f.title, icon: f.icon })),
    feeds: doc.feeds.map((f) => ({ url: f.url, title: f.title, renamed: f.renamed, folderId: f.folderId, siteUrl: f.siteUrl, iconUrl: f.iconUrl })),
    settings: Object.fromEntries(Object.keys(doc.settings).sort().map((k) => [k, doc.settings[k]])),
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
  const settings: Record<string, string> = {};
  if (isObject(root.settings)) for (const [k, v] of Object.entries(root.settings)) if (typeof v === "string") settings[k] = v;
  return { folders, feeds, settings, legacyRead: root.version < 3 ? read : {} };
}

export function merge(base: SyncDocument | null, local: SyncDocument, remote: SyncDocument): SyncDocument {
  const b = base ?? emptyDocument();
  const folders = mergeRecords(b.folders, local.folders, remote.folders, (f) => f.id);
  const folderIds = new Set(folders.map((f) => f.id));
  const feeds = mergeRecords(b.feeds, local.feeds, remote.feeds, (f) => f.url).map((f) =>
    f.folderId && !folderIds.has(f.folderId) ? { ...f, folderId: null } : f,
  );
  // A device without synced settings yet takes the file's, so its defaults don't overwrite them.
  const settings = Object.keys(b.settings).length === 0 ? { ...local.settings, ...remote.settings } : mergeSettings(b.settings, local.settings, remote.settings);
  return { folders, feeds, settings, legacyRead: remote.legacyRead ?? {} };
}

/** Per setting: a change in this browser wins, otherwise the other devices' value (or its removal). */
function mergeSettings(base: Record<string, string>, local: Record<string, string>, remote: Record<string, string>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const k of new Set([...Object.keys(base), ...Object.keys(local), ...Object.keys(remote)])) {
    const value = local[k] === base[k] ? remote[k] : local[k];
    if (value !== undefined) out[k] = value;
  }
  return out;
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

/** This device's side. */
export function localDocument(lib: Library): SyncDocument {
  return documentOf(lib);
}

/**
 * Makes the library match the merged document: folders and feeds (removed ones lose their
 * articles). A version 2 file's read marks are kept for the articles this browser doesn't have, so
 * per-item sync takes them over.
 */
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
  const articles = lib.articles.filter((a) => urls.has(a.feedUrl));
  const have = new Set(articles.map(articleKey));
  const legacy = Object.entries(merged.legacyRead ?? {}).filter(([k]) => !have.has(k) && !(k in lib.readElsewhere));
  return {
    ...lib,
    feeds,
    articles: articles.length === lib.articles.length ? lib.articles : articles,
    folders: merged.folders.map((f) => ({ ...f })),
    readElsewhere: legacy.length === 0 ? lib.readElsewhere : { ...lib.readElsewhere, ...Object.fromEntries(legacy) },
  };
}

export function hasNewFeeds(lib: Library, merged: SyncDocument): boolean {
  return merged.feeds.some((f) => !lib.feeds.some((x) => x.url === f.url));
}

function isObject(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

/** The document as this browser last saved or read it, with the account's version of it. */
export interface SyncBase {
  version: number;
  text: string;
}

export interface SyncTarget {
  read(knownVersion: number | null): Promise<ReadAnswer>;
  write(baseVersion: number, text: string): Promise<WriteAnswer>;
}

/** Saves after another device's save are tried again this often, then given up until next time. */
const MAX_ATTEMPTS = 4;

/**
 * One sync: reads the account's document, lets mergeIn merge it into this browser's library (it
 * gets the base and the remote document, applies the result and returns it), and saves the result
 * when it differs. When another device saved in between, merges again with that document. Returns
 * the new base.
 */
export async function syncRound(
  target: SyncTarget,
  saved: SyncBase | null,
  mergeIn: (base: SyncDocument | null, remote: SyncDocument | null) => SyncDocument,
): Promise<SyncBase> {
  let base = saved ? decode(saved.text) : null;
  let answer: ReadAnswer | WriteAnswer = await target.read(saved && base ? saved.version : null);
  for (let attempt = 1; ; attempt++) {
    const remote = answer.kind === "unchanged" ? base : answer.text === null ? null : decode(answer.text);
    if (answer.kind !== "unchanged" && answer.text !== null && !remote) throw new SyncError("unreadableFile");
    // Without a document there is nothing to merge with: this browser's library becomes it.
    const out = mergeIn(remote ? base : null, remote);
    const text = encode(out);
    if (remote && encode(remote) === text) return { version: answer.version, text };
    const result = await target.write(answer.version, text);
    if (result.kind === "saved") return { version: result.version, text };
    if (attempt >= MAX_ATTEMPTS) throw new SyncError("server");
    // What was just merged in is this browser's state now, so it is the base for the next try.
    base = remote;
    answer = result;
  }
}
