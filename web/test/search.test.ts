import { describe, expect, it } from "vitest";
import { InstapaperError, type Bookmark, type Page, type View } from "../src/lib/instapaper";
import { emptyLibrary, type Article, type Library } from "../src/lib/library";
import { indexArticles, loadSaved, mix, normalize, relevance, savedViews, searchArticles, searchSaved, type SearchHit } from "../src/lib/search";

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

const saved = (id: number, title: string, time: number, change: Partial<Bookmark> = {}): Bookmark => ({
  id,
  url: `https://www.site.example/${id}`,
  title,
  description: "",
  image: null,
  liked: false,
  archived: false,
  time,
  folder_id: null,
  tags: [],
  ...change,
});

const library = (articles: Article[]): Library => ({
  ...emptyLibrary(),
  feeds: [{ url: "https://feed.example/rss", title: "Food Blog", siteUrl: null, folderId: null, iconUrl: null, renamed: false }],
  articles,
});

const names = (hits: SearchHit[]) => hits.map((h) => (h.kind === "feed" ? `feed:${h.article.id}` : `saved:${h.bookmark.id}`));

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

  it("finds saved articles by title, website, description and tags", () => {
    const list = [saved(1, "Tea", 1), saved(2, "Other", 2, { description: "about tea" }), saved(3, "X", 3, { tags: [{ id: 1, name: "Tea", slug: "tea", count: 1 }] })];
    expect(searchSaved(list, "tea").map((b) => b.id)).toEqual([1, 3, 2]);
    expect(searchSaved(list, "site.example").length).toBe(3);
  });

  it("ranks the whole query in a title above single words, above text-only matches", () => {
    expect(relevance("green tea", "Why green tea helps")).toBeGreaterThan(relevance("green tea", "Tea that is green"));
    expect(relevance("green tea", "Tea that is green")).toBeGreaterThan(relevance("green tea", "Green beans"));
    expect(relevance("green tea", "Coffee")).toBe(0);
  });

  it("mixes feed and saved matches, best first then newest, and filters by kind", () => {
    const feeds = [article("a", "Coffee news", 5_000), article("b", "Green tea guide", 1_000)];
    const savedOnes = [saved(1, "All about green tea", 9), saved(2, "Gardening", 2)];
    expect(names(mix("green tea", feeds, savedOnes))).toEqual(["saved:1", "feed:b", "feed:a", "saved:2"]);
    expect(names(mix("green tea", feeds, savedOnes, "feeds"))).toEqual(["feed:b", "feed:a"]);
    expect(names(mix("green tea", feeds, savedOnes, "saved"))).toEqual(["saved:1", "saved:2"]);
  });

  it("reads saved lists page by page, keeps what it has when one fails, and passes a refused sign-in on", async () => {
    const views = savedViews([{ id: 7, title: "F", slug: "f", position: 1, public: false, count: 0 }]);
    expect(views).toEqual([{ kind: "home" }, { kind: "folder", id: 7 }, { kind: "archive" }]);
    const pages = new Map<string, Bookmark[]>([
      ["home", [saved(1, "a", 1), saved(2, "b", 2), saved(3, "c", 3)]],
      ["folder", [saved(3, "c", 3)]],
    ]);
    const list = async (view: View, offset: number, limit: number): Promise<Page> => {
      if (view.kind === "archive") throw new InstapaperError("rateLimit");
      const all = pages.get(view.kind) ?? [];
      return { bookmarks: all.slice(offset, offset + limit), total: all.length };
    };
    const result = await loadSaved(views, list, () => false, 2);
    expect(result.bookmarks.map((b) => b.id)).toEqual([1, 2, 3]);
    expect(result.incomplete).toBe(true);
    await expect(loadSaved(views, list, (e) => e instanceof InstapaperError)).rejects.toThrow(InstapaperError);
  });
});
