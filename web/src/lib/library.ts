import type { ParsedFeed } from "./feedParser";

/** The user's feeds, folders and articles, like the Android app's data/Library.kt. Immutable. */

export interface Feed {
  url: string;
  title: string;
  siteUrl: string | null;
  folderId: string | null;
  iconUrl: string | null;
  renamed: boolean;
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
}

export type Selection = { kind: "all" } | { kind: "folder"; folderId: string } | { kind: "feed"; feedUrl: string };

export const MAX_ARTICLES_PER_FEED = 200;
export const MAX_HTML_LENGTH = 200_000;
export const DEFAULT_ICON = "folder";

export const emptyLibrary = (): Library => ({ feeds: [], folders: [], articles: [] });

export const keyOf = (feedUrl: string, id: string) => `${feedUrl} ${id}`;
export const articleKey = (a: Article) => keyOf(a.feedUrl, a.id);
export const sortTime = (a: Article) => a.publishedAt ?? a.fetchedAt;

export function newFeed(url: string, title: string): Feed {
  return { url, title: title || url, siteUrl: null, folderId: null, iconUrl: null, renamed: false };
}

export function addFeed(lib: Library, feed: Feed): Library {
  return lib.feeds.some((f) => f.url === feed.url) ? lib : { ...lib, feeds: [...lib.feeds, feed] };
}

export function removeFeed(lib: Library, url: string): Library {
  return { ...lib, feeds: lib.feeds.filter((f) => f.url !== url), articles: lib.articles.filter((a) => a.feedUrl !== url) };
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
 * Applies a fresh download of a feed: new articles arrive unread (or read when [knownRead] says
 * so), known ones keep their read state; at most MAX_ARTICLES_PER_FEED newest are kept.
 */
export function mergeFeed(lib: Library, feedUrl: string, parsed: ParsedFeed, now: number, knownRead: Set<string> = new Set()): Library {
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
      read: old?.read ?? knownRead.has(keyOf(feedUrl, item.id)),
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
  return {
    ...lib,
    feeds: lib.feeds.map((f) => (f.url === feedUrl ? updated : f)),
    articles: [...lib.articles.filter((a) => a.feedUrl !== feedUrl), ...feedArticles],
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

export function selectionExists(lib: Library, s: Selection): Selection {
  if (s.kind === "folder" && !lib.folders.some((f) => f.id === s.folderId)) return { kind: "all" };
  if (s.kind === "feed" && !lib.feeds.some((f) => f.url === s.feedUrl)) return { kind: "all" };
  return s;
}
