import { describe, expect, it } from "vitest";
import type { Item } from "../src/lib/items";
import { emptyLibrary, type Article, type Library } from "../src/lib/library";
import { indexArticles, mix, normalize, relevance, searchArticles, searchItems, type SearchHit } from "../src/lib/search";

const article = (id: string, title: string, time: number, html = ""): Article => ({
  feedUrl: "https://feed.example/rss",
  id,
  title,
  link: null,
  author: null,
  html,
  publishedAt: time,
  fetchedAt: time,
  read: false,
  imageUrl: null,
});

const saved = (id: number, title: string, time: number, change: Partial<Item> = {}): Item => ({
  key: `site.example/${id}`,
  url: `https://www.site.example/${id}`,
  title,
  description: "",
  imageUrl: null,
  feedUrl: null,
  articleId: null,
  createdAt: 0,
  readingList: { state: "toread", addedAt: time },
  folder: null,
  service: null,
  bookmark: null,
  liked: false,
  tags: [],
  note: "",
  highlights: [],
  progress: 0,
  ...change,
});

const library = (articles: Article[]): Library => ({
  ...emptyLibrary(),
  feeds: [{ url: "https://feed.example/rss", title: "Food Blog", siteUrl: null, folderId: null, iconUrl: null, renamed: false }],
  articles,
});

const names = (hits: SearchHit[]) => hits.map((h) => (h.kind === "feed" ? `feed:${h.article.id}` : `saved:${h.item.key.split("/")[1]}`));

describe("search", () => {
  it("ignores case and accents", () => {
    expect(normalize("Café CRÈME")).toBe("cafe creme");
  });

  it("finds feed articles with every word in their title, feed name or text, title matches first", () => {
    const index = indexArticles(library([article("a", "Coffee", 3, "<p>All about <b>green</b> tea</p>"), article("b", "Green tea", 1), article("c", "Cake", 2)]));
    expect(searchArticles(index, "green tea").map((a) => a.id)).toEqual(["b", "a"]);
    expect(searchArticles(index, "food blog").map((a) => a.id)).toEqual(["a", "c", "b"]);
    expect(searchArticles(index, "  ")).toEqual([]);
  });

  it("finds saved articles by title, website, description, folder, tags, note and highlights", () => {
    const highlight = { id: "h", quote: "a cup of tea", prefix: "", suffix: "", occurrence: 0, note: "", createdAt: 1 };
    const list = [
      saved(1, "Tea", 1),
      saved(2, "Other", 2, { description: "about tea" }),
      saved(3, "X", 3, { tags: ["Tea"] }),
      saved(4, "Y", 4, { note: "Tea, again" }),
      saved(5, "Z", 5, { highlights: [highlight] }),
      saved(6, "W", 6, { folder: "Tea" }),
      saved(7, "Archived", 7, { description: "tea", readingList: { state: "archived", addedAt: 7 } }),
      // Not saved: not searched.
      saved(8, "Tea", 8, { readingList: null, liked: true }),
    ];
    expect(searchItems(list, "tea").map((i) => i.title)).toEqual(["Tea", "Archived", "W", "Z", "Y", "X", "Other"]);
    expect(searchItems(list, "site.example").length).toBe(7);
  });

  it("ranks the whole query in a title above single words, above text-only matches", () => {
    expect(relevance("green tea", "Why green tea helps")).toBeGreaterThan(relevance("green tea", "Tea that is green"));
    expect(relevance("green tea", "Tea that is green")).toBeGreaterThan(relevance("green tea", "Green beans"));
    expect(relevance("green tea", "Coffee")).toBe(0);
  });

  it("mixes feed and saved matches, best first then newest, and filters by kind", () => {
    const feeds = [article("a", "Coffee news", 5_000), article("b", "Green tea guide", 1_000)];
    const savedOnes = [saved(1, "All about green tea", 9_000), saved(2, "Gardening", 2_000)];
    expect(names(mix("green tea", feeds, savedOnes))).toEqual(["saved:1", "feed:b", "feed:a", "saved:2"]);
    expect(names(mix("green tea", feeds, savedOnes, "feeds"))).toEqual(["feed:b", "feed:a"]);
    expect(names(mix("green tea", feeds, savedOnes, "saved"))).toEqual(["saved:1", "saved:2"]);
  });
});
