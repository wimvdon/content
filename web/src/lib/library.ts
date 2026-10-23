import type { ParsedFeed } from "./feedParser";
import type { Item } from "./items";

/**
 * The user's feeds, folders and articles, and the items on the reading list or in Bookmarks
 * (items.ts), like the Android app's data/Library.kt. Immutable.
 */

export interface Feed {
  url: string;
  title: string;
  siteUrl: string | null;
  folderId: string | null;
  iconUrl: string | null;
  renamed: boolean;
  /** Its tags (#187), which its articles inherit. A feed folder becomes its first tag (feedFoldersToTags). */
  tags?: string[];
}

export interface Folder {
  id: string;
  title: string;
  /** A Material Symbols name, as in the Android app. */
  icon: string;
}

export interface Article {
  feedUrl: string;
  id: string;
  title: string;
  link: string | null;
  author: string | null;
  html: string;
  publishedAt: number | null;
  fetchedAt: number;
  read: boolean;
  imageUrl: string | null;
}

export interface Library {
  feeds: Feed[];
  folders: Folder[];
  articles: Article[];
  items: Item[];
  /**
   * Articles read on another device that this one doesn't have (yet): article key → the article's
   * date. They arrive read, and their marks stay in sync until they do (syncRows.ts).
   */
  readElsewhere: Record<string, number>;
}

/** What the list shows: feed articles, or Saved (its tab and filters are the app's savedFilter, saved.ts). */
export type Selection =
  | { kind: "all" }
  | { kind: "folder"; folderId: string }
  | { kind: "feed"; feedUrl: string }
  /** The feeds with one tag (#187): the Feed tab's chips. */
  | { kind: "tag"; tag: string }
  | { kind: "saved" };

export const MAX_ARTICLES_PER_FEED = 200;
/** Read marks of articles older than this are dropped (feeds rarely go back further); sync uses the same. */
export const READ_MARKS_KEPT_MS = 120 * 24 * 60 * 60 * 1000;
export const MAX_HTML_LENGTH = 200_000;
export const DEFAULT_ICON = "folder";

export const emptyLibrary = (): Library => ({ feeds: [], folders: [], articles: [], items: [], readElsewhere: {} });

export const keyOf = (feedUrl: string, id: string) => `${feedUrl} ${id}`;
export const articleKey = (a: Article) => keyOf(a.feedUrl, a.id);
export const sortTime = (a: Article) => a.publishedAt ?? a.fetchedAt;

/**
 * A feed's tag mirrors its folder (#186): feeds are grouped in folders, as in OPML, and the Feed tab's
 * chips filter on that tag. A feed in a folder has the folder's name as its only tag, a feed outside
 * folders none, so moving a feed or renaming a folder moves its chip too. The same library when
 * everything already matches. Applied after every change. Like the Android app's Library.feedFoldersToTags.
 */
export function feedFoldersToTags(lib: Library): Library {
  const titles = new Map(lib.folders.map((f) => [f.id, f.title.trim()]));
  const mirror = (f: Feed): string[] => {
    const title = f.folderId ? titles.get(f.folderId) : undefined;
    return title ? [title] : [];
  };
  const same = (f: Feed) => {
    const want = mirror(f);
    const have = f.tags ?? [];
    return have.length === want.length && have.every((t, i) => t === want[i]);
  };
  if (lib.feeds.every(same)) return lib;
  return {
    ...lib,
    feeds: lib.feeds.map((f) => {
      if (same(f)) return f;
      const want = mirror(f);
      if (want.length) return { ...f, tags: want };
      const rest = { ...f };
      delete rest.tags;
      return rest;
    }),
  };
}

export function newFeed(url: string, title: string): Feed {
  return { url, title: title || url, siteUrl: null, folderId: null, iconUrl: null, renamed: false };
}

export function addFeed(lib: Library, feed: Feed): Library {
  return lib.feeds.some((f) => f.url === feed.url) ? lib : { ...lib, feeds: [...lib.feeds, feed] };
}

/**
 * Removes a feed with its articles; items saved from it stay. The read ones keep their mark in
 * readElsewhere, so other devices don't see them turn unread and adding the feed again brings them
 * back read (#157).
 */
export function removeFeed(lib: Library, url: string): Library {
  const marks = lib.articles.filter((a) => a.feedUrl === url && a.read).map((a) => [articleKey(a), sortTime(a)] as const);
  return {
    ...lib,
    feeds: lib.feeds.filter((f) => f.url !== url),
    articles: lib.articles.filter((a) => a.feedUrl !== url),
    readElsewhere: marks.length === 0 ? lib.readElsewhere : { ...lib.readElsewhere, ...Object.fromEntries(marks) },
  };
}

/** The user's own name; blank goes back to the feed's title on the next refresh. */
export function renameFeed(lib: Library, url: string, title: string): Library {
  return {
    ...lib,
    feeds: lib.feeds.map((f) => (f.url !== url ? f : title.trim() ? { ...f, title: title.trim(), renamed: true } : { ...f, renamed: false })),
  };
}

export function moveFeed(lib: Library, url: string, folderId: string | null): Library {
  const target = folderId && lib.folders.some((f) => f.id === folderId) ? folderId : null;
  return { ...lib, feeds: lib.feeds.map((f) => (f.url === url ? { ...f, folderId: target } : f)) };
}

/**
 * Moves a feed up or down among the feeds of its own folder (or those outside folders), as Manage
 * feeds shows them; swapping with a feed in another folder would change nothing you can see.
 */
export function moveWithinFolder(lib: Library, url: string, delta: number): Library {
  const feed = lib.feeds.find((f) => f.url === url);
  if (!feed) return lib;
  const group = lib.feeds.filter((f) => f.folderId === feed.folderId);
  const other = group[group.indexOf(feed) + delta];
  if (!other) return lib;
  const a = lib.feeds.indexOf(feed);
  const b = lib.feeds.indexOf(other);
  const feeds = [...lib.feeds];
  [feeds[a], feeds[b]] = [feeds[b], feeds[a]];
  return { ...lib, feeds };
}

export function setFeedIcon(lib: Library, url: string, iconUrl: string): Library {
  return { ...lib, feeds: lib.feeds.map((f) => (f.url === url ? { ...f, iconUrl } : f)) };
}

/** Moves a feed or folder one place up or down. */
export function reorder<T>(list: T[], index: number, delta: number): T[] {
  const to = index + delta;
  if (to < 0 || to >= list.length) return list;
  const copy = [...list];
  const [item] = copy.splice(index, 1);
  copy.splice(to, 0, item);
  return copy;
}

export function addFolder(lib: Library, folder: Folder): Library {
  if (!folder.title.trim() || lib.folders.some((f) => f.id === folder.id)) return lib;
  return { ...lib, folders: [...lib.folders, { ...folder, title: folder.title.trim() }] };
}

export function updateFolder(lib: Library, id: string, title: string, icon: string): Library {
  if (!title.trim()) return lib;
  return { ...lib, folders: lib.folders.map((f) => (f.id === id ? { ...f, title: title.trim(), icon } : f)) };
}

/** Removes a folder; its feeds stay, without a folder. */
export function removeFolder(lib: Library, id: string): Library {
  return {
    ...lib,
    folders: lib.folders.filter((f) => f.id !== id),
    feeds: lib.feeds.map((f) => (f.folderId === id ? { ...f, folderId: null } : f)),
  };
}

/**
 * Applies a fresh download of a feed: new articles arrive unread (or read when another device read
 * them, readElsewhere), known ones keep their read state; at most MAX_ARTICLES_PER_FEED newest are
 * kept. Read ones that no longer fit keep their mark in readElsewhere (#157): leaving this browser
 * doesn't make them unread elsewhere. Marks older than READ_MARKS_KEPT_MS go.
 */
export function mergeFeed(lib: Library, feedUrl: string, parsed: ParsedFeed, now: number): Library {
  const feed = lib.feeds.find((f) => f.url === feedUrl);
  if (!feed) return lib;
  const existing = new Map(lib.articles.filter((a) => a.feedUrl === feedUrl).map((a) => [a.id, a]));
  const seen = new Set<string>();
  const incoming: Article[] = [];
  for (const item of parsed.items) {
    if (seen.has(item.id)) continue;
    seen.add(item.id);
    const old = existing.get(item.id);
    const published = item.publishedAt ?? old?.publishedAt ?? null;
    incoming.push({
      feedUrl,
      id: item.id,
      title: item.title,
      link: item.link,
      author: item.author,
      html: item.html.slice(0, MAX_HTML_LENGTH),
      publishedAt: published === null ? null : Math.min(published, now),
      fetchedAt: old?.fetchedAt ?? now,
      read: old?.read ?? keyOf(feedUrl, item.id) in lib.readElsewhere,
      imageUrl: item.imageUrl ?? old?.imageUrl ?? null,
    });
  }
  const kept = [...existing.values()].filter((a) => !seen.has(a.id));
  const feedArticles = [...incoming, ...kept].sort((a, b) => sortTime(b) - sortTime(a)).slice(0, MAX_ARTICLES_PER_FEED);
  const updated: Feed = {
    ...feed,
    title: feed.renamed ? feed.title : parsed.title.trim() || feed.title,
    siteUrl: parsed.siteUrl ?? feed.siteUrl,
  };
  // Arrived: the article itself carries its read state now.
  const stays = new Set(feedArticles.map(articleKey));
  const arrived = [...stays].filter((k) => k in lib.readElsewhere);
  const left = [...existing.values()].filter((a) => a.read && !stays.has(articleKey(a)));
  const oldest = now - READ_MARKS_KEPT_MS;
  const stale = Object.values(lib.readElsewhere).some((time) => time < oldest);
  let readElsewhere = lib.readElsewhere;
  if (arrived.length > 0 || left.length > 0 || stale) {
    readElsewhere = { ...readElsewhere };
    for (const k of arrived) delete readElsewhere[k];
    for (const a of left) readElsewhere[articleKey(a)] = sortTime(a);
    for (const [k, time] of Object.entries(readElsewhere)) if (time < oldest) delete readElsewhere[k];
  }
  return {
    ...lib,
    feeds: lib.feeds.map((f) => (f.url === feedUrl ? updated : f)),
    articles: [...lib.articles.filter((a) => a.feedUrl !== feedUrl), ...feedArticles],
    readElsewhere,
  };
}

export function setRead(lib: Library, keys: Set<string>, read: boolean): Library {
  return { ...lib, articles: lib.articles.map((a) => (keys.has(articleKey(a)) && a.read !== read ? { ...a, read } : a)) };
}

export function includes(lib: Library, selection: Selection, a: Article): boolean {
  switch (selection.kind) {
    case "all":
      return lib.feeds.some((f) => f.url === a.feedUrl);
    case "folder":
      return lib.feeds.find((f) => f.url === a.feedUrl)?.folderId === selection.folderId;
    case "feed":
      return a.feedUrl === selection.feedUrl;
    case "tag":
      return (lib.feeds.find((f) => f.url === a.feedUrl)?.tags ?? []).some((t) => t.toLowerCase() === selection.tag.toLowerCase());
    case "saved":
      return false;
  }
}

export function unreadKeys(lib: Library, selection: Selection): Set<string> {
  return new Set(lib.articles.filter((a) => !a.read && includes(lib, selection, a)).map(articleKey));
}

export function articlesFor(lib: Library, selection: Selection, unreadOnly: boolean, keep: Set<string> = new Set()): Article[] {
  return lib.articles
    .filter((a) => includes(lib, selection, a) && (!unreadOnly || !a.read || keep.has(articleKey(a))))
    .sort((a, b) => sortTime(b) - sortTime(a));
}

/** Unread counts per feed url; folders and All add them up. */
export function unreadByFeed(lib: Library): Map<string, number> {
  const counts = new Map<string, number>();
  for (const a of lib.articles) if (!a.read) counts.set(a.feedUrl, (counts.get(a.feedUrl) ?? 0) + 1);
  return counts;
}

/** The same feed, folder, All or Saved. */
export function sameSelection(a: Selection, b: Selection): boolean {
  if (a.kind === "folder" && b.kind === "folder") return a.folderId === b.folderId;
  if (a.kind === "feed" && b.kind === "feed") return a.feedUrl === b.feedUrl;
  if (a.kind === "tag" && b.kind === "tag") return a.tag.toLowerCase() === b.tag.toLowerCase();
  return a.kind === b.kind;
}

export function selectionExists(lib: Library, s: Selection): Selection {
  if (s.kind === "folder" && !lib.folders.some((f) => f.id === s.folderId)) return { kind: "all" };
  if (s.kind === "feed" && !lib.feeds.some((f) => f.url === s.feedUrl)) return { kind: "all" };
  if (s.kind === "tag" && !feedTags(lib).some((t) => t.toLowerCase() === s.tag.toLowerCase())) return { kind: "all" };
  return s;
}

/** Every feed tag (#187), sorted (case ignored): the Feed tab's chips. */
/**
 * The chip a selection shows as chosen: its tag, or a folder's name, which is its feeds' tag (#201).
 * So a folder picked in the menu lights up its chip, as picking the chip does. Like chipTag on Android.
 */
export function chipTag(lib: Library, selection: Selection): string | null {
  if (selection.kind === "tag") return selection.tag;
  if (selection.kind === "folder") return lib.folders.find((f) => f.id === selection.folderId)?.title ?? null;
  return null;
}

/** Whether a folder's row in the menu is the one chosen: picked there, or its chip picked (same feeds). */
export function choosesFolder(selection: Selection, folder: Folder): boolean {
  if (selection.kind === "folder") return selection.folderId === folder.id;
  return selection.kind === "tag" && selection.tag.toLowerCase() === folder.title.toLowerCase();
}

export function feedTags(lib: Library): string[] {
  const seen = new Map<string, string>();
  for (const f of lib.feeds) for (const t of f.tags ?? []) if (!seen.has(t.toLowerCase())) seen.set(t.toLowerCase(), t);
  return [...seen.values()].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
}
