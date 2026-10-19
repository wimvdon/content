/**
 * Per-item sync (#106, docs/sync.md), a port of the Android app's sync/SyncRows.kt and
 * sync/ItemSync.kt: one row per item ("item:<item key>") and one per read feed article
 * ("read:<feed url> <article id>"), each with its JSON data, or null when it is gone. No article
 * text: an item row holds the link, title, picture address, memberships, tags, note and highlights;
 * a read row only the article's date.
 */

import { cleanTags, isWebLink, itemOf as itemByKey, putItem, type Highlight, type Item } from "./items";
import { articleKey, setRead, sortTime, type Article, type Library } from "./library";
import { KEEP_READ_MS } from "./sync";

export const ITEM = "item:";
export const READ = "read:";

export type RowData = Record<string, unknown>;

export interface ItemRow {
  key: string;
  version: number;
  data: RowData | null;
}

export interface ItemsPage {
  version: number;
  rows: ItemRow[];
  more: boolean;
}

export interface ItemChange {
  key: string;
  baseVersion: number;
  data: RowData | null;
}

export interface ItemsWritten {
  version: number;
  saved: Record<string, number>;
  conflicts: ItemRow[];
}

/** The account's items (worker/src/account.ts): AccountClient in the app, a fake in tests. */
export interface ItemsTarget {
  readItems(since: number): Promise<ItemsPage>;
  writeItems(rows: ItemChange[]): Promise<ItemsWritten>;
}

/** What this browser last synced of the account's items: the version and every row as synced (the base). */
export interface ItemSyncState {
  version: number;
  rows: Record<string, ItemRow>;
}

export const emptyItemSyncState = (): ItemSyncState => ({ version: 0, rows: {} });

export function itemData(i: Item): RowData {
  return {
    url: i.url,
    title: i.title,
    imageUrl: i.imageUrl,
    feedUrl: i.feedUrl,
    articleId: i.articleId,
    createdAt: i.createdAt,
    readingList: i.readingList ? { state: i.readingList.state, addedAt: i.readingList.addedAt } : null,
    folder: i.folder,
    service: i.service,
    bookmark: i.bookmark ? { addedAt: i.bookmark.addedAt, collection: i.bookmark.collection } : null,
    liked: i.liked,
    tags: i.tags,
    note: i.note,
    highlights: i.highlights.map((h) => ({ id: h.id, quote: h.quote, prefix: h.prefix, suffix: h.suffix, occurrence: h.occurrence, note: h.note, createdAt: h.createdAt })),
    // In thousandths, so both apps write the same number.
    progress: Math.round(i.progress * 1000),
  };
}

const str = (v: unknown): string | null => (typeof v === "string" ? v : null);
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : null);
const obj = (v: unknown): Record<string, unknown> | null => (typeof v === "object" && v !== null && !Array.isArray(v) ? (v as Record<string, unknown>) : null);

function highlightOf(v: unknown): Highlight | null {
  const m = obj(v);
  const id = str(m?.id);
  const quote = str(m?.quote);
  if (!m || !id || !quote) return null;
  return {
    id,
    quote,
    prefix: str(m.prefix) ?? "",
    suffix: str(m.suffix) ?? "",
    occurrence: Math.max(0, num(m.occurrence) ?? 0),
    note: str(m.note) ?? "",
    createdAt: num(m.createdAt) ?? 0,
  };
}

/** The item an item row describes, or null when the data isn't one; `local` gives what isn't synced (the description). */
export function itemOfRow(key: string, data: RowData, local: Item | undefined): Item | null {
  const url = str(data.url);
  if (!url || !isWebLink(url)) return null;
  const createdAt = num(data.createdAt) ?? 0;
  const reading = obj(data.readingList);
  const bookmark = obj(data.bookmark);
  const state = reading?.state === "toread" || reading?.state === "archived" ? reading.state : null;
  const seen = new Set<string>();
  const imageUrl = str(data.imageUrl);
  return {
    key,
    url,
    title: str(data.title)?.trim() ? str(data.title)! : url,
    description: local?.description ?? "",
    imageUrl: imageUrl?.startsWith("https://") ? imageUrl : null,
    feedUrl: str(data.feedUrl),
    articleId: str(data.articleId),
    createdAt,
    readingList: reading && state ? { state, addedAt: num(reading.addedAt) ?? createdAt } : null,
    folder: str(data.folder)?.trim() || null,
    service: str(data.service) || null,
    bookmark: bookmark ? { addedAt: num(bookmark.addedAt) ?? createdAt, collection: str(bookmark.collection)?.trim() || null } : null,
    liked: data.liked === true,
    tags: cleanTags(Array.isArray(data.tags) ? data.tags.filter((t): t is string => typeof t === "string") : []),
    note: str(data.note) ?? "",
    highlights: (Array.isArray(data.highlights) ? data.highlights : []).flatMap((h) => {
      const parsed = highlightOf(h);
      if (!parsed || seen.has(parsed.id)) return [];
      seen.add(parsed.id);
      return [parsed];
    }),
    progress: Math.min(1, Math.max(0, (Number(data.progress) || 0) / 1000)),
  };
}

export const readData = (time: number): RowData => ({ time });

export const readTimeOf = (data: RowData | null | undefined): number | null => (data ? num(data.time) : null);

/** This browser's data for a row. A read mark older than keepSince counts as gone; unknown kinds stay as they were (base). */
export function localRow(lib: Library, key: string, keepSince: number, base: RowData | null, articles?: Map<string, Article>): RowData | null {
  if (key.startsWith(ITEM)) {
    const item = itemByKey(lib, key.slice(ITEM.length));
    return item ? itemData(item) : null;
  }
  if (key.startsWith(READ)) {
    const k = key.slice(READ.length);
    const article = (articles ?? new Map(lib.articles.map((a) => [articleKey(a), a]))).get(k);
    const time = article ? (article.read ? sortTime(article) : null) : (lib.readElsewhere[k] ?? null);
    return time !== null && time >= keepSince ? readData(time) : null;
  }
  return base;
}

/** Every row the library has now (item and read rows), by key. */
export function localRows(lib: Library, keepSince: number): Map<string, RowData> {
  const rows = new Map<string, RowData>();
  for (const item of lib.items) rows.set(ITEM + item.key, itemData(item));
  const have = new Set<string>();
  for (const a of lib.articles) {
    const k = articleKey(a);
    have.add(k);
    if (a.read && sortTime(a) >= keepSince) rows.set(READ + k, readData(sortTime(a)));
  }
  for (const [k, time] of Object.entries(lib.readElsewhere)) if (!have.has(k) && time >= keepSince) rows.set(READ + k, readData(time));
  return rows;
}

/** Two rows' data mean the same (each app may write its JSON a little differently). */
export function sameRow(key: string, a: RowData | null, b: RowData | null): boolean {
  if (a === null || b === null) return a === b;
  if (key.startsWith(ITEM)) {
    const k = key.slice(ITEM.length);
    const x = itemOfRow(k, a, undefined);
    const y = itemOfRow(k, b, undefined);
    return JSON.stringify(x && itemData(x)) === JSON.stringify(y && itemData(y));
  }
  if (key.startsWith(READ)) return readTimeOf(a) === readTimeOf(b);
  return JSON.stringify(a) === JSON.stringify(b);
}

/**
 * Three-way merge of one row: whatever differs from base on one side is a change on that side and
 * wins; both changed, this browser wins, except that an edit beats a removal. Items merge per field,
 * tags as a set and highlights per highlight.
 */
export function mergeRow(key: string, base: RowData | null, local: RowData | null, remote: RowData | null): RowData | null {
  if (sameRow(key, local, base)) return remote;
  if (sameRow(key, remote, base) || sameRow(key, local, remote)) return local;
  if (local === null || remote === null) return local ?? remote;
  if (!key.startsWith(ITEM)) return local;
  const k = key.slice(ITEM.length);
  const b = base ? itemOfRow(k, base, undefined) : null;
  const l = itemOfRow(k, local, undefined);
  const r = itemOfRow(k, remote, undefined);
  if (!l) return remote;
  if (!r) return local;
  return itemData(mergeItems(b, l, r));
}

const eq = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

export function mergeItems(b: Item | null, l: Item, r: Item): Item {
  const pick = <K extends keyof Item>(field: K): Item[K] => (eq(l[field], b?.[field]) ? r[field] : l[field]);
  return {
    ...l,
    url: pick("url"),
    title: pick("title"),
    imageUrl: pick("imageUrl"),
    feedUrl: pick("feedUrl"),
    articleId: pick("articleId"),
    createdAt: Math.min(l.createdAt, r.createdAt),
    readingList: pick("readingList"),
    folder: pick("folder"),
    // Once kept in a service it stays there.
    service: pick("service") ?? l.service ?? r.service,
    bookmark: pick("bookmark"),
    liked: pick("liked"),
    tags: mergeTags(b?.tags ?? [], l.tags, r.tags),
    note: pick("note"),
    highlights: mergeHighlights(b?.highlights ?? [], l.highlights, r.highlights),
    progress: pick("progress"),
  };
}

/** Tags as a set: each side's additions and removals both count (case ignored). */
export function mergeTags(base: string[], local: string[], remote: string[]): string[] {
  const has = (list: string[], tag: string) => list.some((t) => t.toLowerCase() === tag.toLowerCase());
  const kept = local.filter((t) => has(remote, t) || !has(base, t));
  const added = remote.filter((t) => !has(local, t) && !has(base, t));
  return cleanTags([...kept, ...added]);
}

/** Highlights per id: a change on one side wins; both changed, this browser; an edit beats a removal. */
export function mergeHighlights(base: Highlight[], local: Highlight[], remote: Highlight[]): Highlight[] {
  const b = new Map(base.map((h) => [h.id, h]));
  const l = new Map(local.map((h) => [h.id, h]));
  const r = new Map(remote.map((h) => [h.id, h]));
  const ids = [...new Set([...local.map((h) => h.id), ...remote.map((h) => h.id), ...base.map((h) => h.id)])];
  return ids
    .flatMap((id) => {
      const pick = eq(l.get(id), b.get(id)) ? r.get(id) : eq(r.get(id), b.get(id)) ? l.get(id) : (l.get(id) ?? r.get(id));
      return pick ? [pick] : [];
    })
    .sort((x, y) => x.createdAt - y.createdAt);
}

/**
 * The library after taking a row's merged data: an item row puts or removes the item; a read row
 * marks the article read or unread, or, for an article this browser doesn't have, remembers it in
 * readElsewhere. An article older than keepSince whose mark was dropped stays as it is.
 */
export function applyRow(lib: Library, key: string, data: RowData | null, keepSince: number): Library {
  if (key.startsWith(ITEM)) {
    const itemKey = key.slice(ITEM.length);
    const local = itemByKey(lib, itemKey);
    const item = data ? itemOfRow(itemKey, data, local) : null;
    if (item) return putItem(lib, item);
    if (data || !local) return lib;
    return { ...lib, items: lib.items.filter((i) => i.key !== itemKey) };
  }
  if (key.startsWith(READ)) return applyReads(lib, new Map([[key.slice(READ.length), readTimeOf(data)]]), keepSince);
  return lib;
}

/** Read marks (article key → date, null: unread) in one step, so thousands of them stay quick. */
export function applyReads(lib: Library, reads: Map<string, number | null>, keepSince: number): Library {
  if (reads.size === 0) return lib;
  const read = new Set<string>();
  const unread = new Set<string>();
  const elsewhere = { ...lib.readElsewhere };
  let elsewhereChanged = false;
  const articles = new Map(lib.articles.map((a) => [articleKey(a), a]));
  for (const [k, time] of reads) {
    const article = articles.get(k);
    if (article) {
      if (time === null && sortTime(article) < keepSince) continue;
      (time !== null ? read : unread).add(k);
    } else if (time === null) {
      if (k in elsewhere) {
        delete elsewhere[k];
        elsewhereChanged = true;
      }
    } else if (elsewhere[k] !== time) {
      elsewhere[k] = time;
      elsewhereChanged = true;
    }
  }
  let result = lib;
  if (read.size > 0) result = setRead(result, read, true);
  if (unread.size > 0) result = setRead(result, unread, false);
  return elsewhereChanged ? { ...result, readElsewhere: elsewhere } : result;
}

/** Saves after other devices' changes are tried again this often, then given up until next time. */
const MAX_ATTEMPTS = 4;
/** At most this many rows per save (the account's limit). */
const BATCH = 500;

/**
 * The library after merging rows from the account into it; base then holds them as synced. Rows
 * this browser already has at that version (its own saves coming back) are skipped. A deleted row
 * is kept in base only with keepDeleted (a conflict): saving that key again must name the deletion's
 * version.
 */
export function takeIn(lib: Library, base: Record<string, ItemRow>, rows: ItemRow[], keepSince: number, keepDeleted = false): Library {
  let result = lib;
  // Taking a row in only changes that row's article, so the articles as they were still answer for the others.
  const articles = new Map(lib.articles.map((a) => [articleKey(a), a]));
  // Read marks are applied together at the end: one at a time would be slow with thousands.
  const reads = new Map<string, number | null>();
  for (const row of rows) {
    const known = base[row.key];
    if (known && known.version >= row.version) continue;
    // Data this version can't read is left alone: neither applied nor taken as the base.
    if (row.data && row.key.startsWith(ITEM) && !itemOfRow(row.key.slice(ITEM.length), row.data, undefined)) continue;
    const local = localRow(result, row.key, keepSince, known?.data ?? null, articles);
    const merged = mergeRow(row.key, known?.data ?? null, local, row.data);
    if (!sameRow(row.key, merged, local)) {
      if (row.key.startsWith(READ)) reads.set(row.key.slice(READ.length), readTimeOf(merged));
      else result = applyRow(result, row.key, merged, keepSince);
    }
    if (row.data === null && !keepDeleted) delete base[row.key];
    else base[row.key] = row;
  }
  return applyReads(result, reads, keepSince);
}

/** The rows whose data in this browser differs from what was last synced, to save. */
export function itemChanges(lib: Library, base: Record<string, ItemRow>, keepSince: number): ItemChange[] {
  const local = localRows(lib, keepSince);
  const out: ItemChange[] = [];
  for (const [key, data] of local) {
    const known = base[key];
    if (!sameRow(key, data, known?.data ?? null)) out.push({ key, baseVersion: known?.version ?? 0, data });
  }
  for (const [key, known] of Object.entries(base)) {
    if (local.has(key) || !(key.startsWith(ITEM) || key.startsWith(READ))) continue;
    if (known.data !== null) out.push({ key, baseVersion: known.version, data: null });
  }
  return out;
}

/**
 * One per-item sync: reads the rows changed since the last sync and merges them in, then saves the
 * rows this browser changed; a conflict is merged the same way and saved again. `update` changes
 * the library in one step and returns it. Returns the new state, to keep for the next sync.
 */
export async function syncItems(
  target: ItemsTarget,
  state: ItemSyncState,
  now: number,
  update: (change: (lib: Library) => Library) => Library,
): Promise<ItemSyncState> {
  const keepSince = now - KEEP_READ_MS;
  const base: Record<string, ItemRow> = { ...state.rows };
  let version = state.version;
  for (;;) {
    const page = await target.readItems(version);
    if (page.rows.length > 0) update((lib) => takeIn(lib, base, page.rows, keepSince));
    version = page.version;
    if (!page.more) break;
  }
  for (let attempt = 0; ; attempt++) {
    let changes: ItemChange[] = [];
    update((lib) => {
      changes = itemChanges(lib, base, keepSince);
      return lib;
    });
    if (changes.length === 0) break;
    if (attempt >= MAX_ATTEMPTS) throw new Error("Items kept changing on another device");
    for (let i = 0; i < changes.length; i += BATCH) {
      const batch = changes.slice(i, i + BATCH);
      const written = await target.writeItems(batch);
      for (const change of batch) {
        const saved = written.saved[change.key];
        if (saved === undefined) continue;
        if (change.data === null) delete base[change.key];
        else base[change.key] = { key: change.key, version: saved, data: change.data };
      }
      if (written.conflicts.length > 0) update((lib) => takeIn(lib, base, written.conflicts, keepSince, true));
    }
  }
  return { version, rows: base };
}
