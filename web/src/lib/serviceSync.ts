/**
 * Keeps Saved and a connected service the same, as the Android app's services/ServiceSync.kt does
 * (#109, #119, #121): Saved is always the library's items, and the service only syncs. Reads what
 * changed at the service since last time and merges it three ways with the entry as last seen there
 * and Reader's own (a change on one side wins; both changed, Reader's; tags as a set), then sends
 * the service what changed here. An item kept in another service (its `service`) is not sent; a copy
 * of it here only shares archive, like and tags. A failed call keeps everything and is tried again.
 */

import { cleanTags, itemOf, keyOfLink, newItem, putItem, type Item } from "./items";
import type { Library } from "./library";
import { mergeTags } from "./syncRows";

/** An article at the service, as far as Saved syncs it. */
export interface RemoteEntry {
  remoteId: string;
  url: string;
  title: string;
  description: string;
  imageUrl: string | null;
  /** When it was saved there, in milliseconds. */
  addedAt: number;
  archived: boolean;
  liked: boolean;
  tags: string[];
  /** Its folder, only while not archived. */
  folder: string | null;
  /** How far it was read, 0 to 1. */
  progress: number;
}

export interface RemoteChanges {
  entries: RemoteEntry[];
  /** Ids deleted at the service since the cursor. */
  deleted: string[];
  cursor: string;
}

/** A service Saved can sync with (Instapaper on the web). */
export interface SavedSyncService {
  id: string;
  /** Whether it keeps reading progress. */
  progress: boolean;
  changes(cursor: string | null): Promise<RemoteChanges>;
  add(url: string, title: string, tags: string[], archived: boolean, folder: string | null): Promise<RemoteEntry>;
  update(have: RemoteEntry, want: RemoteEntry): Promise<RemoteEntry>;
  remove(remoteId: string): Promise<void>;
}

/** What this browser last saw at the service: its cursor and each article as it had it, by item key. */
export interface ServiceSyncState {
  cursor: string | null;
  rows: Record<string, RemoteEntry>;
}

export const emptyServiceSyncState: ServiceSyncState = { cursor: null, rows: {} };

/** Why a sync stopped early: the problem's name, as the service gives it. */
export class ServiceSyncError extends Error {
  readonly problem: string;

  constructor(problem: string) {
    super(problem);
    this.problem = problem;
  }
}

/** A library that lost most of its articles at once is more likely damaged than the user's wish. */
const MAX_SAFE_REMOVALS = 20;
const PROGRESS_STEP = 0.01;

export interface ServiceSyncResult {
  state: ServiceSyncState;
  /** Why it stopped early, or null when everything got through. */
  problem: string | null;
}

/**
 * One sync with [service]. [update] applies a change to the library as it is right now and returns
 * the result, so changes made during the sync are kept. New items in no service yet go to this one
 * when [isDefault].
 */
export async function syncService(
  service: SavedSyncService,
  state: ServiceSyncState,
  update: (change: (lib: Library) => Library) => Library,
  now: number,
  isDefault = true,
): Promise<ServiceSyncResult> {
  const rows: Record<string, RemoteEntry> = { ...state.rows };
  let cursor = state.cursor;
  const progress = service.progress;
  const problemOf = (e: unknown) => (e instanceof ServiceSyncError ? e.problem : "server");
  try {
    const changes = await service.changes(cursor);
    update((lib) => takeIn(lib, rows, changes, progress, service.id, now));
    cursor = changes.cursor;
  } catch (e) {
    return { state: { cursor, rows }, problem: problemOf(e) };
  }
  // What this service already has, synced before items knew their service, is its own.
  let library = update((lib) => lib.items.reduce((acc, i) => (i.service === null && i.readingList && rows[i.key] ? putItem(acc, { ...i, service: service.id }) : acc), lib));
  try {
    for (const item of library.items) {
      if (!item.readingList) continue;
      const have = rows[item.key];
      // Only what belongs to this service goes there: nothing is copied between services (#121).
      if (!have && item.service !== service.id && !(item.service === null && isDefault)) continue;
      const own = entryOf(item, have?.remoteId ?? "");
      const want = have && isCopy(item, service.id) ? share(own, have) : own;
      if (!have) {
        let added = await service.add(item.url, item.title, item.tags, item.readingList.state === "archived", folderOf(item));
        if (item.service === null) library = update((lib) => changeKept(lib, item.key, (i) => (i.service === null ? { ...i, service: service.id } : i)));
        const target = { ...want, remoteId: added.remoteId };
        if (!sameCore(added, target, progress)) added = await service.update(added, target);
        rows[item.key] = added;
      } else if (!sameCore(have, want, progress)) {
        rows[item.key] = await service.update(have, want);
      }
    }
    library = update((lib) => lib);
    const gone = Object.keys(rows).filter((key) => !itemOf(library, key)?.readingList);
    if (gone.length > MAX_SAFE_REMOVALS && gone.length * 2 > Object.keys(rows).length) return { state: { cursor, rows }, problem: "refused" };
    for (const key of gone) {
      await service.remove(rows[key].remoteId);
      delete rows[key];
    }
  } catch (e) {
    return { state: { cursor, rows }, problem: problemOf(e) };
  }
  return { state: { cursor, rows }, problem: null };
}

/** The library after what changed at the service; [rows] then holds the service's articles as seen now. */
export function takeIn(lib: Library, rows: Record<string, RemoteEntry>, changes: RemoteChanges, progress: boolean, serviceId: string, now: number): Library {
  let result = lib;
  const byRemoteId = new Map(Object.entries(rows).map(([key, e]) => [e.remoteId, key]));
  for (const remote of changes.entries) {
    const key = byRemoteId.get(remote.remoteId) ?? keyOfLink(remote.url);
    if (!key) continue;
    const base = rows[key];
    const item = itemOf(result, key);
    const local = item?.readingList ? entryOf(item, remote.remoteId) : null;
    let merged: RemoteEntry | null;
    if (local && item && isCopy(item, serviceId)) {
      // A copy of another service's item: only what the copies share comes in.
      const shared = share(merge(base, local, remote, progress), local);
      merged = sameCore(shared, local, progress) ? null : shared;
    } else if (local) {
      merged = merge(base, local, remote, progress);
    } else if (base && sameCore(base, remote, progress)) {
      // Taken out of Saved here and not sent yet: it stays out, unless the service changed it since.
      merged = null;
    } else {
      merged = remote;
    }
    if (merged) result = put(result, key, item, merged, remote, serviceId, now);
    rows[key] = remote;
  }
  for (const gone of changes.deleted) {
    const key = byRemoteId.get(gone);
    if (!key) continue;
    const base = rows[key];
    delete rows[key];
    const item = itemOf(result, key);
    if (!base || !item?.readingList) continue;
    // Deleted from a copy: the item stays, kept in its own service.
    if (isCopy(item, serviceId)) continue;
    // Changed here since: the edit stays (and is sent again); otherwise it leaves Saved.
    if (!sameCore(base, entryOf(item, base.remoteId), progress)) continue;
    result = putItem(result, { ...item, readingList: null, folder: null, liked: false, tags: [] });
  }
  return result;
}

/** Three-way merge of the core: a change on one side wins; both changed, Reader's. */
export function merge(base: RemoteEntry | undefined, local: RemoteEntry, remote: RemoteEntry, progress: boolean): RemoteEntry {
  const pick = <T>(b: T | undefined, l: T, r: T): T => (l === b ? r : l);
  const merged: RemoteEntry = {
    ...local,
    title: pick(base?.title, local.title, remote.title),
    archived: pick(base?.archived, local.archived, remote.archived),
    liked: pick(base?.liked, local.liked, remote.liked),
    tags: mergeTags(base?.tags ?? [], local.tags, remote.tags),
    folder: sameName(local.folder, base?.folder ?? null) ? remote.folder : local.folder,
    progress: progress ? pick(base?.progress, local.progress, remote.progress) : local.progress,
  };
  return merged.archived ? { ...merged, folder: null } : merged;
}

/** True when [item] is kept in another service and [serviceId] has a copy of it (#121). */
export const isCopy = (item: Item, serviceId: string) => item.service !== null && item.service !== serviceId;

/** [keep] with what two copies of an article share taken from [from]: archive, like and tags (#121). */
export function share(from: RemoteEntry, keep: RemoteEntry): RemoteEntry {
  return { ...keep, archived: from.archived, liked: from.liked, tags: from.tags, folder: from.archived ? null : keep.folder };
}

/** A saved item as a service entry. */
export function entryOf(item: Item, remoteId: string): RemoteEntry {
  return {
    remoteId,
    url: item.url,
    title: item.title,
    description: item.description,
    imageUrl: item.imageUrl,
    addedAt: item.readingList?.addedAt ?? item.createdAt,
    archived: item.readingList?.state === "archived",
    liked: item.liked,
    tags: item.tags,
    folder: folderOf(item),
    progress: item.progress,
  };
}

/** The same core: archived, liked, title, folder and tags (case ignored), and progress when it syncs. */
export function sameCore(a: RemoteEntry, b: RemoteEntry, progress: boolean): boolean {
  const tags = (t: string[]) => [...new Set(t.map((x) => x.toLowerCase()))].sort().join("\n");
  return (
    a.archived === b.archived &&
    a.liked === b.liked &&
    a.title === b.title &&
    sameName(a.folder, b.folder) &&
    tags(a.tags) === tags(b.tags) &&
    (!progress || Math.abs(a.progress - b.progress) < PROGRESS_STEP)
  );
}

const folderOf = (i: Item) => (i.readingList?.state === "toread" && i.folder?.trim() ? i.folder.trim() : null);

const sameName = (a: string | null, b: string | null) => (a ?? "").trim().toLowerCase() === (b ?? "").trim().toLowerCase();

function changeKept(lib: Library, key: string, change: (i: Item) => Item): Library {
  const item = itemOf(lib, key);
  return item ? putItem(lib, change(item)) : lib;
}

function put(lib: Library, key: string, item: Item | undefined, entry: RemoteEntry, remote: RemoteEntry, serviceId: string, now: number): Library {
  const base = item ?? newItem(lib, remote.url, remote.title, now, remote.description, remote.imageUrl);
  if (!base) return lib;
  return putItem(lib, {
    ...base,
    key,
    title: entry.title.trim() || base.title,
    description: base.description || remote.description,
    imageUrl: base.imageUrl ?? remote.imageUrl,
    readingList: { state: entry.archived ? "archived" : "toread", addedAt: base.readingList?.addedAt ?? remote.addedAt },
    liked: entry.liked,
    tags: cleanTags(entry.tags),
    folder: entry.archived ? null : entry.folder,
    // Kept in this service from now on, unless it is saved in another already (a duplicate).
    service: (base.readingList ? base.service : null) ?? serviceId,
    progress: Math.min(1, Math.max(0, entry.progress)),
  });
}
