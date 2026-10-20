/**
 * Search through the feeds' articles and the saved articles, like the Android app (ArticleSearch,
 * SavedSearch and MixedSearch). Every word of the query must appear, ignoring case and accents
 * ("cafe" finds "Café"). Feed and saved matches are ranked together by how well their title
 * matches, then newest first.
 */

import { toPlainText } from "./html";
import type { Item } from "./items";
import { sortTime, type Article, type Library } from "./library";
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
