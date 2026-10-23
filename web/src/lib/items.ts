/**
 * The reading list and Bookmarks inside the library (#106): one item per link, with memberships.
 * The same rules as the Android app's data/Items.kt and data/LibraryItems.kt. Immutable: every
 * change returns a new library.
 */

import { snippet } from "./html";
import type { Article, Library } from "./library";

export type ReadingState = "toread" | "archived";

export interface ReadingListEntry {
  state: ReadingState;
  /** When it was added (or added again), in milliseconds; the list is newest first. */
  addedAt: number;
}

export interface BookmarkEntry {
  addedAt: number;
  /** One flat collection, or null. */
  collection: string | null;
}

/** A highlighted passage, anchored by its text: the quote, the text around it and which occurrence it is (#102). */
export interface Highlight {
  id: string;
  quote: string;
  prefix: string;
  suffix: string;
  occurrence: number;
  note: string;
  createdAt: number;
}

/**
 * One link and everything the user did with it. A feed article that is only in its feed has no
 * item; it gets one when it is saved, tagged or annotated, under the key of its link, so it is never
 * copied. Memberships are independent: adding or removing one never changes the other.
 */
export interface Item {
  /** keyOfLink(url): links that only differ in http(s), www., a trailing slash or #part are one item. */
  key: string;
  url: string;
  title: string;
  description: string;
  imageUrl: string | null;
  /** The feed article it came from, while the user follows that feed. */
  feedUrl: string | null;
  articleId: string | null;
  createdAt: number;
  readingList: ReadingListEntry | null;
  /** The folder it is filed in (#119): one at most, only while it is to read. */
  folder: string | null;
  /** The service it is kept in (#112): "instapaper", "raindrop", or null while in none yet. */
  service: string | null;
  /** Bookmarks before #119; the Android app moves them into folders. */
  bookmark: BookmarkEntry | null;
  liked: boolean;
  tags: string[];
  note: string;
  highlights: Highlight[];
  /** How far it has been read, 0 to 1. */
  progress: number;
  /** Who shared it with you (#167); absent or null when nobody did. */
  sharedBy?: SharedBy | null;
}

/**
 * Who shared an item with you (#167): their email address and name (may be empty), their message,
 * when, and whether it still waits in Saved › Shared with me (until you put it in To read or the
 * Archive, or remove it). Like the Android app's SharedBy.
 */
export interface SharedBy {
  email: string;
  name: string;
  message?: string;
  at?: number;
  waits?: boolean;
}

/** Waiting in Saved › Shared with me (#167): shared with you, not put in To read or the Archive yet. */
export const waitingShared = (i: Item) => i.readingList === null && i.sharedBy?.waits === true;

/** An address without what doesn't change the page (http(s), www., a trailing slash, the #part), lower case. */
export function keyOfLink(url: string | null | undefined): string | null {
  if (!url || !url.trim()) return null;
  const key = url
    .trim()
    .split("#")[0]
    .replace(/^https?:\/\//i, "")
    .replace(/^www\./i, "")
    .replace(/\/+$/, "")
    .toLowerCase();
  return key || null;
}

const TRACKING = /^(utm_.*|fbclid|gclid|dclid|msclkid|mc_cid|mc_eid|igshid|_hsenc|_hsmi|mkt_tok|ref_src|cmpid|ocid)$/;

/**
 * keyOfLink without tracking parameters (utm_… and the like), for recognising the same article when
 * a service stores its link without them. Never an item's key: only to find one that exists
 * (serviceSync.ts keyFor), like the Android app's ItemKeys.loose.
 */
export function looseKeyOfLink(url: string | null | undefined): string | null {
  const key = keyOfLink(url);
  if (!key) return null;
  const q = key.indexOf("?");
  if (q < 0) return key;
  const base = key.slice(0, q).replace(/\/+$/, "");
  const kept = key
    .slice(q + 1)
    .split("&")
    .filter((p) => p !== "" && !TRACKING.test(p.split("=")[0]))
    .sort();
  return kept.length === 0 ? base : `${base}?${kept.join("&")}`;
}

/** Web addresses only, so a saved item can't carry a script. */
export const isWebLink = (url: string | null | undefined): url is string => !!url && /^https?:\/\//i.test(url);

/** False when nothing of the user's is left on it, so it can be dropped. */
export const isKept = (i: Item) =>
  i.readingList !== null || i.bookmark !== null || i.liked || i.tags.length > 0 || i.note.trim() !== "" || i.highlights.length > 0 || i.sharedBy?.waits === true;

export const itemOf = (lib: Library, key: string): Item | undefined => lib.items.find((i) => i.key === key);

export function itemForLink(lib: Library, url: string | null | undefined): Item | undefined {
  const key = keyOfLink(url);
  return key ? itemOf(lib, key) : undefined;
}

export const itemForArticle = (lib: Library, a: Article) => itemForLink(lib, a.link);

/** The feed article with this link, while the library still has it. */
export function feedArticleFor(lib: Library, url: string | null | undefined): Article | undefined {
  const key = keyOfLink(url);
  return key ? lib.articles.find((a) => keyOfLink(a.link) === key) : undefined;
}

/**
 * The item for a link, made from the feed article with that link when there is one, else from
 * the title. Not added: give it a membership and putItem it. Null for addresses that aren't web links.
 */
export function newItem(lib: Library, url: string, title: string | null | undefined, now: number, description = "", imageUrl: string | null = null): Item | null {
  const key = keyOfLink(url);
  if (!isWebLink(url) || !key) return null;
  const article = feedArticleFor(lib, url);
  return {
    key,
    url: url.trim(),
    title: title?.trim() || article?.title?.trim() || url.trim(),
    description: description.trim() ? description : article ? snippet(article.html, 240) : "",
    imageUrl: imageUrl ?? article?.imageUrl ?? null,
    feedUrl: article?.feedUrl ?? null,
    articleId: article?.id ?? null,
    createdAt: now,
    readingList: null,
    folder: null,
    service: null,
    bookmark: null,
    liked: false,
    tags: [],
    note: "",
    highlights: [],
    progress: 0,
  };
}

/** Adds or replaces an item; an item with nothing of the user's left on it is dropped. */
export function putItem(lib: Library, given: Item): Library {
  // Put in Saved, a shared item no longer waits in Shared with me (#167): removing it later drops it.
  const item = given.readingList && given.sharedBy?.waits ? { ...given, sharedBy: { ...given.sharedBy, waits: false } } : given;
  const index = lib.items.findIndex((i) => i.key === item.key);
  if (!isKept(item)) return index < 0 ? lib : { ...lib, items: lib.items.filter((_, i) => i !== index) };
  if (index < 0) return { ...lib, items: [...lib.items, item] };
  if (sameItem(lib.items[index], item)) return lib;
  const items = [...lib.items];
  items[index] = item;
  return { ...lib, items };
}

export function changeItem(lib: Library, key: string, change: (i: Item) => Item): Library {
  const item = itemOf(lib, key);
  return item ? putItem(lib, change(item)) : lib;
}

/** Puts a link on the reading list, to read; already there, it comes back to the top. A bookmark stays a bookmark. */
export function addToReadingList(lib: Library, url: string, title: string | null | undefined, now: number, description = "", imageUrl: string | null = null): [Library, Item | null] {
  const base = itemForLink(lib, url) ?? newItem(lib, url, title, now, description, imageUrl);
  if (!base) return [lib, null];
  const item: Item = { ...base, title: title?.trim() || base.title, readingList: { state: "toread", addedAt: now } };
  return [putItem(lib, item), item];
}

/** Archives or puts back to read; null takes it off the reading list. Archiving takes it out of its folder (#119). */
export function setReadingState(lib: Library, key: string, state: ReadingState | null): Library {
  return changeItem(lib, key, (i) =>
    state === null
      ? { ...i, readingList: null, folder: null }
      : i.readingList
        ? { ...i, readingList: { ...i.readingList, state }, folder: state === "archived" ? null : i.folder }
        : i,
  );
}

export const setLiked = (lib: Library, key: string, liked: boolean) => changeItem(lib, key, (i) => ({ ...i, liked }));

export const setTags = (lib: Library, key: string, tags: string[]) => changeItem(lib, key, (i) => ({ ...i, tags: cleanTags(tags) }));

/** Every tag on any item, sorted (case ignored). */
export function itemTags(lib: Library): string[] {
  return recentTags(lib.items);
}

/**
 * The tags of items, most recently used first (#220): the tag of the article saved last comes first
 * (when it was put in Saved, else when the item was made). Equal times go alphabetically.
 */
export function recentTags(items: Item[]): string[] {
  const latest = new Map<string, { name: string; time: number }>();
  for (const item of items) {
    const time = item.readingList?.addedAt ?? item.createdAt;
    for (const tag of cleanTags(item.tags)) {
      const key = tag.toLowerCase();
      const seen = latest.get(key);
      if (!seen || time > seen.time) latest.set(key, { name: seen?.name ?? tag, time });
    }
  }
  return [...latest.entries()].sort((a, b) => b[1].time - a[1].time || a[0].localeCompare(b[0])).map(([, v]) => v.name);
}

/** Renames a tag on every item; renaming to another tag's name joins the two. */
export function renameTag(lib: Library, from: string, to: string): Library {
  const clean = to.trim();
  if (!clean) return lib;
  const same = (t: string) => t.toLowerCase() === from.toLowerCase();
  return { ...lib, items: lib.items.map((i) => (i.tags.some(same) ? { ...i, tags: cleanTags(i.tags.map((t) => (same(t) ? clean : t))) } : i)) };
}

/** Keeps an item in Bookmarks, in a collection (blank for none). Its reading list place stays as it is. */
export function keepAsBookmark(lib: Library, key: string, collection: string | null, now: number): Library {
  return changeItem(lib, key, (i) => ({ ...i, bookmark: { addedAt: i.bookmark?.addedAt ?? now, collection: collection?.trim() || null } }));
}

export const removeBookmark = (lib: Library, key: string) => changeItem(lib, key, (i) => ({ ...i, bookmark: null }));

export function readingList(lib: Library, state: ReadingState): Item[] {
  return lib.items.filter((i) => i.readingList?.state === state).sort((a, b) => b.readingList!.addedAt - a.readingList!.addedAt);
}

export function bookmarks(lib: Library): Item[] {
  return lib.items.filter((i) => i.bookmark !== null).sort((a, b) => b.bookmark!.addedAt - a.bookmark!.addedAt);
}

export function cleanTags(tags: string[]): string[] {
  const seen = new Set<string>();
  const out: string[] = [];
  for (const t of tags.map((t) => t.trim())) {
    if (!t || seen.has(t.toLowerCase())) continue;
    seen.add(t.toLowerCase());
    out.push(t);
  }
  return out;
}

export function sameItem(a: Item, b: Item): boolean {
  return a === b || JSON.stringify(a) === JSON.stringify(b);
}

/** Stored items from an older or damaged copy, made safe to use. */
export function itemsOf(value: unknown): Item[] {
  if (!Array.isArray(value)) return [];
  const out: Item[] = [];
  for (const v of value as Partial<Item>[]) {
    if (!v || typeof v.key !== "string" || typeof v.url !== "string" || !isWebLink(v.url)) continue;
    out.push({
      key: v.key,
      url: v.url,
      title: typeof v.title === "string" ? v.title : v.url,
      description: typeof v.description === "string" ? v.description : "",
      imageUrl: typeof v.imageUrl === "string" ? v.imageUrl : null,
      feedUrl: typeof v.feedUrl === "string" ? v.feedUrl : null,
      articleId: typeof v.articleId === "string" ? v.articleId : null,
      createdAt: typeof v.createdAt === "number" ? v.createdAt : 0,
      readingList:
        v.readingList && (v.readingList.state === "toread" || v.readingList.state === "archived")
          ? { state: v.readingList.state, addedAt: Number(v.readingList.addedAt) || 0 }
          : null,
      folder: typeof v.folder === "string" && v.folder.trim() ? v.folder.trim() : null,
      service: typeof v.service === "string" && v.service ? v.service : null,
      bookmark: v.bookmark ? { addedAt: Number(v.bookmark.addedAt) || 0, collection: typeof v.bookmark.collection === "string" ? v.bookmark.collection : null } : null,
      liked: v.liked === true,
      tags: Array.isArray(v.tags) ? cleanTags(v.tags.filter((t): t is string => typeof t === "string")) : [],
      note: typeof v.note === "string" ? v.note : "",
      highlights: Array.isArray(v.highlights) ? v.highlights.filter((h) => typeof h?.id === "string" && typeof h.quote === "string" && h.quote !== "") : [],
      progress: Math.min(1, Math.max(0, Number(v.progress) || 0)),
    });
  }
  return out;
}

/**
 * Adds a highlight to the item of url (#102). A feed article that isn't saved yet goes to To read
 * first: annotating it keeps it. The same as the Android app's Library.addHighlight.
 */
export function addHighlight(lib: Library, url: string, title: string | null | undefined, highlight: Highlight, now: number): Library {
  const existing = itemForLink(lib, url);
  const [out, item] = existing?.readingList ? [lib, existing] : addToReadingList(lib, url, title, now);
  if (!item) return lib;
  return changeItem(out, item.key, (i) => ({ ...i, highlights: [...i.highlights, highlight] }));
}

/** Changes a highlight's note. */
export const setHighlightNote = (lib: Library, key: string, id: string, note: string) =>
  changeItem(lib, key, (i) => ({ ...i, highlights: i.highlights.map((h) => (h.id === id ? { ...h, note: note.trim() } : h)) }));

/** Removes a highlight. */
export const removeHighlight = (lib: Library, key: string, id: string) => changeItem(lib, key, (i) => ({ ...i, highlights: i.highlights.filter((h) => h.id !== id) }));
