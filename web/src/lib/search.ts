/**
 * Search through the feeds' articles and the saved articles, like the Android app (ArticleSearch,
 * SavedSearch and MixedSearch). Every word of the query must appear, ignoring case and accents
 * ("cafe" finds "Café"). Feed and saved matches are ranked together by how well their title
 * matches, then newest first.
 */

import { toPlainText } from "./html";
import type { Bookmark, Folder, Page, View } from "./instapaper";
import { hostOf } from "./instapaperLibrary";
import { sortTime, type Article, type Library } from "./library";

export const MAX_RESULTS = 200;

/** At most this many saved articles per list, so a long Archive doesn't mean dozens of calls. */
export const SAVED_PER_LIST = 100;

/** Lower case without accents, so "Café" and "cafe" match. */
export function normalize(text: string): string {
  return text.normalize("NFD").replace(/\p{Mn}+/gu, "").toLowerCase();
}

/** The query's words, normalized. */
export function words(query: string): string[] {
  return normalize(query).split(/\s+/).filter(Boolean);
}

export interface ArticleIndex {
  entries: { article: Article; title: string; text: string }[];
}

/** Every feed article's searchable text: title, feed name, author and text. Built once per library. */
export function indexArticles(library: Library): ArticleIndex {
  const feeds = new Map(library.feeds.map((f) => [f.url, f]));
  const entries = library.articles.flatMap((article) => {
    const feed = feeds.get(article.feedUrl);
    if (!feed) return [];
    const text = normalize(`${article.title} ${feed.title} ${article.author ?? ""} ${toPlainText(article.html)}`);
    return [{ article, title: normalize(article.title), text }];
  });
  return { entries };
}

/** The feed articles that match, title matches first, then newest. */
export function searchArticles(index: ArticleIndex, query: string, limit = MAX_RESULTS): Article[] {
  const w = words(query);
  if (w.length === 0) return [];
  return index.entries
    .filter((e) => w.every((x) => e.text.includes(x)))
    .sort((a, b) => Number(w.every((x) => b.title.includes(x))) - Number(w.every((x) => a.title.includes(x))) || sortTime(b.article) - sortTime(a.article))
    .slice(0, limit)
    .map((e) => e.article);
}

/** The saved articles that match: title, website, address, description and tags. */
export function searchSaved(bookmarks: Bookmark[], query: string, limit = MAX_RESULTS): Bookmark[] {
  const w = words(query);
  if (w.length === 0) return [];
  const text = (b: Bookmark) => normalize([b.title ?? "", hostOf(b.url), b.url ?? "", b.description ?? "", ...b.tags.map((t) => t.name)].join(" "));
  return bookmarks
    .map((b) => ({ b, title: normalize(b.title ?? ""), text: text(b) }))
    .filter((e) => w.every((x) => e.text.includes(x)))
    .sort((a, c) => Number(w.every((x) => c.title.includes(x))) - Number(w.every((x) => a.title.includes(x))) || c.b.time - a.b.time)
    .slice(0, limit)
    .map((e) => e.b);
}

/** The saved lists Search reads, in order: what is still to be read before the Archive. */
export function savedViews(folders: Folder[]): View[] {
  return [{ kind: "home" }, ...folders.map((f): View => ({ kind: "folder", id: f.id })), { kind: "archive" }];
}

export interface SavedArticles {
  bookmarks: Bookmark[];
  /** Instapaper stopped answering part way (offline, rate limited, …): the rest is missing. */
  incomplete: boolean;
}

/**
 * Reads every list a page at a time, at most `perList` articles each. Stops at the first problem and
 * keeps what it has; `stopOn` decides which problems are passed on instead (a refused sign-in).
 */
export async function loadSaved(
  views: View[],
  list: (view: View, offset: number, limit: number) => Promise<Page>,
  stopOn: (e: unknown) => boolean = () => false,
  pageSize = 25,
  perList = SAVED_PER_LIST,
): Promise<SavedArticles> {
  const found = new Map<number, Bookmark>();
  for (const view of views) {
    let offset = 0;
    while (offset < perList) {
      let page: Page;
      try {
        page = await list(view, offset, Math.min(pageSize, perList - offset));
      } catch (e) {
        if (stopOn(e)) throw e;
        return { bookmarks: [...found.values()], incomplete: true };
      }
      for (const b of page.bookmarks) if (!found.has(b.id)) found.set(b.id, b);
      offset += page.bookmarks.length;
      if (page.bookmarks.length === 0 || offset >= page.total) break;
    }
  }
  return { bookmarks: [...found.values()], incomplete: false };
}

/** Which kind of results Search shows: the chips above the results. */
export type SearchFilter = "all" | "feeds" | "saved";

export type SearchHit = { kind: "feed"; article: Article; relevance: number; time: number } | { kind: "saved"; bookmark: Bookmark; relevance: number; time: number };

const PHRASE = 100;
const WORD = 10;

/**
 * How well a title matches: the whole query in the title counts most, then each word of the query
 * that is in the title. Matches only in the text (all words are somewhere) count 0.
 */
export function relevance(query: string, title: string): number {
  const w = words(query);
  if (w.length === 0) return 0;
  const t = normalize(title);
  const phrase = w.length > 1 && t.includes(w.join(" ")) ? PHRASE : 0;
  return phrase + w.filter((x) => t.includes(x)).length * WORD;
}

/** The matches of both kinds, filtered, best first, at most `limit`. */
export function mix(query: string, feeds: Article[], saved: Bookmark[], filter: SearchFilter = "all", limit = MAX_RESULTS): SearchHit[] {
  const fromFeeds: SearchHit[] = filter === "saved" ? [] : feeds.map((article) => ({ kind: "feed", article, relevance: relevance(query, article.title), time: sortTime(article) }));
  const fromSaved: SearchHit[] =
    filter === "feeds" ? [] : saved.map((bookmark) => ({ kind: "saved", bookmark, relevance: relevance(query, bookmark.title ?? ""), time: bookmark.time * 1000 }));
  return [...fromFeeds, ...fromSaved].sort((a, b) => b.relevance - a.relevance || b.time - a.time).slice(0, limit);
}
