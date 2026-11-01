/**
 * Search through the feeds' articles and the saved articles, like the Android app (ArticleSearch,
 * SavedSearch and MixedSearch). Every word of the query must appear, ignoring case and accents
 * ("cafe" finds "Café"). Feed and saved matches are ranked together by how well their title
 * matches, then newest first.
 */

import { toPlainText } from "./html";
import type { Item } from "./items";
import { sortTime, type Article, type Feed, type Library } from "./library";
import { hostOf } from "./saved";

export const MAX_RESULTS = 200;

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

/**
 * The saved items that match (archived ones too): title, website, address, description, folder,
 * tags, note and highlights, like the Android app's ItemSearch. Title matches first, then newest.
 */
export function searchItems(items: Item[], query: string, limit = MAX_RESULTS): Item[] {
  const w = words(query);
  if (w.length === 0) return [];
  const text = (i: Item) =>
    normalize([i.title, hostOf(i.url), i.url, i.description, i.note, i.folder ?? "", ...i.tags, ...i.highlights.flatMap((h) => [h.quote, h.note])].join(" "));
  return items
    .filter((i) => i.readingList !== null)
    .map((i) => ({ i, title: normalize(i.title), text: text(i) }))
    .filter((e) => w.every((x) => e.text.includes(x)))
    .sort((a, c) => Number(w.every((x) => c.title.includes(x))) - Number(w.every((x) => a.title.includes(x))) || savedAt(c.i) - savedAt(a.i))
    .slice(0, limit)
    .map((e) => e.i);
}

const savedAt = (i: Item) => i.readingList?.addedAt ?? i.bookmark?.addedAt ?? i.createdAt;

/** Which kind of results Search shows: the chips above the results. */
export type SearchFilter = "all" | "feeds" | "saved";

export type SearchHit = { kind: "feed"; article: Article; relevance: number; time: number } | { kind: "saved"; item: Item; relevance: number; time: number };

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
export function mix(query: string, feeds: Article[], saved: Item[], filter: SearchFilter = "all", limit = MAX_RESULTS): SearchHit[] {
  const fromFeeds: SearchHit[] = filter === "saved" ? [] : feeds.map((article) => ({ kind: "feed", article, relevance: relevance(query, article.title), time: sortTime(article) }));
  const fromSaved: SearchHit[] =
    filter === "feeds" ? [] : saved.map((item) => ({ kind: "saved", item, relevance: relevance(query, item.title), time: savedAt(item) }));
  return [...fromFeeds, ...fromSaved].sort((a, b) => b.relevance - a.relevance || b.time - a.time).slice(0, limit);
}

/**
 * Search's filters, as on Android: one row of chips. **Feeds** (a menu: all feeds, or some of them
 * ticked) and **Saved** pick the kind; neither shows both. Then a **topic**, one at a time, which
 * narrows both kinds. Picking feeds means only feeds; Saved lets go of them. A topic keeps only the
 * picked feeds that have it.
 */
export interface SearchFilters {
  scope: SearchFilter;
  /** Some feeds (their addresses), with scope "feeds"; empty: every feed. */
  feedUrls: string[];
  /** A topic's name (case ignored). */
  topic: string | null;
}

export const noFilters: SearchFilters = { scope: "all", feedUrls: [], topic: null };

/** Whether anything beyond the kind is chosen. */
export const narrowed = (f: SearchFilters) => f.feedUrls.length > 0 || f.topic !== null;

/** The kind `scope`; the picked feeds go, the topic stays (it is about both kinds). */
export const withScope = (f: SearchFilters, scope: SearchFilter): SearchFilters => ({ ...f, scope, feedUrls: [] });

/** The Feeds menu's check box for one feed: picks it (only feeds then) or lets go of it. */
export function toggleFeed(f: SearchFilters, url: string): SearchFilters {
  const feedUrls = f.feedUrls.includes(url) ? f.feedUrls.filter((u) => u !== url) : [...f.feedUrls, url];
  return { ...f, scope: "feeds", feedUrls };
}

/** Whether a feed has `topic`: as one of its tags, or as its folder. */
export function hasTopic(feed: Feed, topic: string, library: Library): boolean {
  const same = (x: string) => x.trim().toLowerCase() === topic.trim().toLowerCase();
  return (feed.tags ?? []).some(same) || library.folders.some((f) => f.id === feed.folderId && same(f.title));
}

/** A topic chip: picks it, or with null lets go; picked feeds without it are let go too. */
export function withTopic(f: SearchFilters, topic: string | null, library: Library): SearchFilters {
  if (topic === null) return { ...f, topic: null };
  const feedUrls = f.feedUrls.filter((url) => {
    const feed = library.feeds.find((x) => x.url === url);
    return feed !== undefined && hasTopic(feed, topic, library);
  });
  return { ...f, topic, feedUrls };
}

/**
 * The topic chips for a kind: the topics that some feed (unless only Saved is searched) or some saved
 * article (unless only feeds) has, in the user's order; the chosen one always stays.
 */
export function searchTopics(library: Library, scope: SearchFilter, chosen: string | null): string[] {
  const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();
  return library.folders
    .map((f) => f.title)
    .filter(
      (topic) =>
        (chosen !== null && same(topic, chosen)) ||
        (scope !== "saved" && library.feeds.some((feed) => hasTopic(feed, topic, library))) ||
        (scope !== "feeds" && library.items.some((i) => i.readingList !== null && i.tags.some((x) => same(x, topic)))),
    );
}

/** The feeds the Feeds menu offers: those with the chosen topic, else all of them. */
export const menuFeeds = (library: Library, topic: string | null): Feed[] => (topic === null ? library.feeds : library.feeds.filter((f) => hasTopic(f, topic, library)));

/** Whether Search shows its results as notes (cards), like Saved's folders: Feeds is rows, All and Saved are notes. */
export const showsNotes = (scope: SearchFilter) => scope !== "feeds";

/** How many articles the list shows without words typed. */
export const MAX_BROWSED = 500;

/**
 * What the Search tab lists (#171), like the Android app's SearchResults. With words typed, the
 * matches as `mix` ranks them; without, everything, newest first: all feed articles, all of Saved
 * (archived ones too), or both. Either way only what the filters let through.
 */
export function searchResults(query: string, library: Library, index: ArticleIndex, filters: SearchFilters): SearchHit[] {
  const browsing = words(query).length === 0;
  const articles = browsing ? library.articles : searchArticles(index, query);
  const items = browsing ? library.items.filter((i) => i.readingList !== null) : searchItems(library.items, query);
  const byUrl = new Map(library.feeds.map((f) => [f.url, f]));
  const topic = filters.topic;
  const feeds =
    filters.scope === "saved"
      ? []
      : articles.filter((a) => {
          const feed = byUrl.get(a.feedUrl);
          return (filters.feedUrls.length === 0 || filters.feedUrls.includes(a.feedUrl)) && (topic === null || (feed !== undefined && hasTopic(feed, topic, library)));
        });
  const saved =
    filters.scope === "feeds"
      ? []
      : items.filter((i) => i.readingList !== null && (topic === null || i.tags.some((x) => x.trim().toLowerCase() === topic.trim().toLowerCase())));
  return mix(browsing ? "" : query, feeds, saved, filters.scope, browsing ? MAX_BROWSED : MAX_RESULTS);
}
