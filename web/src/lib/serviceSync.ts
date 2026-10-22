/**
 * Keeps Saved and a connected service in step within the service's scope, as the Android app's
 * services/ServiceSync.kt does (#109, #145). Reader is the library; a service is somewhere it is
 * also kept. Per field: what changed here is sent, what changed there comes in, and when the same
 * field changed on both sides between two rounds the later change wins. A value is never sent just
 * because the two sides differ, or sent back right after it came in, because each row is what was
 * last seen or sent there. An article that leaves the scope is archived there, never deleted; one
 * removed from Reader is removed there. Connecting starts with importService, once. A failed call
 * keeps everything and is tried again.
 */

import { cleanTags, itemOf, keyOfLink, looseKeyOfLink, newItem, putItem, type Item } from "./items";
import type { Library } from "./library";
import { covers, coversPlace, EVERYTHING, includes, type ServiceImport, type ServiceScope } from "./serviceScope";
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
  /** When it last changed there, in milliseconds; 0 when the service doesn't say. */
  changedAt?: number;
  /** In a row: false for one seen there but not in step (not imported, or archived when it left the scope). */
  linked?: boolean;
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
  /** Its folders by name, for choosing what to import. */
  folderNames(): Promise<string[]>;
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
  /** How many articles an import brought in. */
  imported?: number;
}

const isLinked = (row: RemoteEntry | undefined) => row !== undefined && row.linked !== false;

/**
 * One sync with [service] within [scope]. [update] applies a change to the library as it is right
 * now and returns the result, so changes made during the sync are kept. [editedAt] says when each
 * item last changed here, for "the later change wins"; [elsewhere] holds the items another service
 * keeps in step.
 */
export async function syncService(
  service: SavedSyncService,
  state: ServiceSyncState,
  update: (change: (lib: Library) => Library) => Library,
  now: number,
  scope: ServiceScope = EVERYTHING,
  editedAt: Record<string, number> = {},
  elsewhere: Set<string> = new Set(),
): Promise<ServiceSyncResult> {
  const rows: Record<string, RemoteEntry> = { ...state.rows };
  let cursor = state.cursor;
  const progress = service.progress;
  const problemOf = (e: unknown) => (e instanceof ServiceSyncError ? e.problem : "server");
  try {
    const changes = await service.changes(cursor);
    // A first look without rows here (another device imported): what isn't in Reader yet stays there.
    const adopt = cursor !== null;
    update((lib) => takeIn(lib, rows, changes, progress, now, scope, adopt, editedAt, elsewhere));
    cursor = changes.cursor;
  } catch (e) {
    return { state: { cursor, rows }, problem: problemOf(e) };
  }
  try {
    let library = update((lib) => lib);
    for (const item of library.items) {
      if (!item.readingList) continue;
      const have = rows[item.key];
      const inScope = covers(scope, item);
      if (have && isLinked(have) && !inScope) {
        // Left the scope: archived there, which is what filing it elsewhere meant here.
        const archived = have.archived ? have : await service.update(have, { ...have, archived: true, folder: null });
        rows[item.key] = { ...archived, linked: false };
      } else if (!inScope) {
        continue;
      } else if (!have) {
        let added = await service.add(item.url, item.title, item.tags, item.readingList.state === "archived", folderOf(item));
        const target = { ...entryOf(item, added.remoteId) };
        if (!sameCore(added, target, progress)) added = await service.update(added, target);
        rows[item.key] = { ...added, linked: true };
      } else {
        // In step (again): only what differs from what was last seen or sent goes.
        const want = entryOf(item, have.remoteId);
        rows[item.key] = { ...(sameCore(have, want, progress) ? have : await service.update(have, want)), linked: true };
      }
    }
    library = update((lib) => lib);
    const gone = Object.keys(rows).filter((key) => isLinked(rows[key]) && !itemOf(library, key)?.readingList);
    const linked = Object.values(rows).filter(isLinked).length;
    if (gone.length > MAX_SAFE_REMOVALS && gone.length * 2 > linked) return { state: { cursor, rows }, problem: "refused" };
    for (const key of gone) {
      await service.remove(rows[key].remoteId);
      delete rows[key];
    }
  } catch (e) {
    return { state: { cursor, rows }, problem: problemOf(e) };
  }
  return { state: { cursor, rows }, problem: null };
}

/**
 * Brings in what the service has, once, when it is connected (#145): everything, or what [what]
 * picks. Each article arrives where it belongs, as it is; one Reader has already keeps its own
 * values and gains the service's tags. What isn't imported is remembered as seen, so it stays there
 * until it changes.
 */
export async function importService(
  service: SavedSyncService,
  state: ServiceSyncState,
  what: Pick<ServiceImport, "folders" | "archive">,
  update: (change: (lib: Library) => Library) => Library,
  now: number,
): Promise<ServiceSyncResult> {
  const rows: Record<string, RemoteEntry> = { ...state.rows };
  let count = 0;
  try {
    const changes = await service.changes(null);
    update((lib) => {
      let result = lib;
      const loose = looseIndex(lib, rows);
      for (const remote of changes.entries) {
        const key = keyFor(result, rows, loose, remote.url);
        if (!key) continue;
        if (!includes(what, remote.folder, remote.archived)) {
          if (!isLinked(rows[key])) rows[key] = { ...remote, linked: false };
          continue;
        }
        const item = itemOf(result, key);
        const local = item?.readingList ? entryOf(item, remote.remoteId) : null;
        result = put(result, key, item, local ? merge(undefined, local, remote, service.progress) : remote, remote, now);
        rows[key] = { ...remote, linked: true };
        count++;
      }
      return result;
    });
    return { state: { cursor: changes.cursor, rows }, problem: null, imported: count };
  } catch (e) {
    return { state, problem: e instanceof ServiceSyncError ? e.problem : "server" };
  }
}

/** The library after what changed at the service, within [scope]; [rows] then holds the service's articles as seen now. */
export function takeIn(
  lib: Library,
  rows: Record<string, RemoteEntry>,
  changes: RemoteChanges,
  progress: boolean,
  now: number,
  scope: ServiceScope = EVERYTHING,
  adopt = true,
  editedAt: Record<string, number> = {},
  elsewhere: Set<string> = new Set(),
): Library {
  let result = lib;
  const byRemoteId = new Map(Object.entries(rows).map(([key, e]) => [e.remoteId, key]));
  const loose = looseIndex(lib, rows);
  for (const remote of changes.entries) {
    const key = byRemoteId.get(remote.remoteId) ?? keyFor(result, rows, loose, remote.url);
    if (!key) continue;
    const base = rows[key];
    const item = itemOf(result, key);
    const saved = item?.readingList ? item : undefined;
    const linked = isLinked(base);
    const changedThere = !base || !sameCore(base, remote, progress);
    const landsInScope = coversPlace(scope, remote.folder, remote.archived);
    let link = linked;
    if (saved && linked) {
      // In step: per field, what changed there comes in.
      const remoteLater = (remote.changedAt ?? 0) > 0 && (remote.changedAt ?? 0) > (editedAt[key] ?? 0);
      result = put(result, key, saved, merge(base, entryOf(saved, remote.remoteId), remote, progress, remoteLater), remote, now);
    } else if (saved) {
      // The same link in both, not in step yet: it joins when Reader's is in the scope.
      if (covers(scope, saved)) {
        result = put(result, key, saved, merge(undefined, entryOf(saved, remote.remoteId), remote, progress), remote, now);
        link = true;
      }
    } else if (linked) {
      // Taken out of Saved here and not sent yet: it stays out, unless it changed there since.
      if (changedThere) result = put(result, key, item, remote, remote, now);
    } else if (base) {
      // Seen there but not in step: only a change there that lands in the scope brings it in.
      if (changedThere && landsInScope) {
        result = put(result, key, item, remote, remote, now);
        link = true;
      }
    } else if (adopt && landsInScope) {
      result = put(result, key, item, remote, remote, now);
      link = true;
    }
    rows[key] = { ...remote, linked: link };
  }
  for (const gone of changes.deleted) {
    const key = byRemoteId.get(gone);
    if (!key) continue;
    const base = rows[key];
    delete rows[key];
    const item = itemOf(result, key);
    if (!base || !isLinked(base) || !item?.readingList) continue;
    // Changed here since: the edit stays (and is sent again); otherwise it leaves Saved.
    if (!sameCore(base, entryOf(item, base.remoteId), progress)) continue;
    // Another service keeps it in step: it stays in Saved.
    if (elsewhere.has(key)) continue;
    result = putItem(result, { ...item, readingList: null, folder: null, liked: false, tags: [] });
  }
  return result;
}

/**
 * Three-way merge of the core: a field changed on one side takes that side's value; changed on
 * both, the later change wins ([remoteLater]: the service's; otherwise Reader's). Tags as a set.
 */
/**
 * Saved items not yet in step with this service, by their link without tracking parameters: where an
 * article the service has but this browser hasn't linked can belong. Another device may have sent it,
 * and the service may keep its link without those parameters; matching only the exact link then made
 * a second item (a duplicate here, not at the service). Like the Android app's ServiceSync.looseIndex.
 */
export function looseIndex(lib: Library, rows: Record<string, RemoteEntry>): Map<string, string> {
  const index = new Map<string, string>();
  for (const item of lib.items) {
    if (!item.readingList || isLinked(rows[item.key])) continue;
    const loose = looseKeyOfLink(item.url);
    if (loose && !index.has(loose)) index.set(loose, item.key);
  }
  return index;
}

/** The item a service's link belongs to: the same link, else a saved item from [loose] not yet linked, else a new one. */
export function keyFor(lib: Library, rows: Record<string, RemoteEntry>, loose: Map<string, string>, url: string): string | null {
  const exact = keyOfLink(url);
  if (!exact) return null;
  if (itemOf(lib, exact) || rows[exact]) return exact;
  const looseKey = looseKeyOfLink(url);
  const match = looseKey ? loose.get(looseKey) : undefined;
  return match && !isLinked(rows[match]) ? match : exact;
}

export function merge(base: RemoteEntry | undefined, local: RemoteEntry, remote: RemoteEntry, progress: boolean, remoteLater = false): RemoteEntry {
  const pick = <T>(b: T | undefined, l: T, r: T): T => (l === b ? r : r === b || b === undefined ? l : remoteLater ? r : l);
  const folder = sameName(local.folder, base?.folder ?? null)
    ? remote.folder
    : !base || sameName(remote.folder, base.folder)
      ? local.folder
      : remoteLater
        ? remote.folder
        : local.folder;
  const merged: RemoteEntry = {
    ...local,
    title: pick(base?.title, local.title, remote.title),
    archived: pick(base?.archived, local.archived, remote.archived),
    liked: pick(base?.liked, local.liked, remote.liked),
    tags: mergeTags(base?.tags ?? [], local.tags, remote.tags),
    folder,
    progress: progress ? pick(base?.progress, local.progress, remote.progress) : local.progress,
  };
  return merged.archived ? { ...merged, folder: null } : merged;
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

function put(lib: Library, key: string, item: Item | undefined, entry: RemoteEntry, remote: RemoteEntry, now: number): Library {
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
    progress: Math.min(1, Math.max(0, entry.progress)),
  });
}
