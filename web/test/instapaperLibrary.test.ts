import { describe, expect, it } from "vitest";
import type { Bookmark } from "../src/lib/instapaper";
import { addressKey, applyChange, bookmarkArticle, hostOf, inView, INSTAPAPER_FEED, sourceOf, viewTitle } from "../src/lib/instapaperLibrary";
import { articleKey, articlesFor, emptyLibrary } from "../src/lib/library";

const bookmark = (id: number, change: Partial<Bookmark> = {}): Bookmark => ({
  id,
  url: `https://www.site.example/${id}`,
  title: `Saved ${id}`,
  description: "A <short> summary",
  image: null,
  liked: false,
  archived: false,
  time: 1_700_000_000,
  folder_id: null,
  tags: [],
  ...change,
});

describe("Instapaper in the reader", () => {
  it("shows a saved article like a feed article, without read state", () => {
    const a = bookmarkArticle(bookmark(7, { image: "javascript:alert(1)" }));
    expect(a.feedUrl).toBe(INSTAPAPER_FEED);
    expect(articleKey(a)).toBe("instapaper 7");
    expect(a.html).toBe("<p>A &lt;short&gt; summary</p>");
    expect(a.imageUrl).toBeNull();
    expect(a.publishedAt).toBe(1_700_000_000_000);
    expect(a.read).toBe(false);
    expect(bookmarkArticle(bookmark(8, { url: "javascript:alert(1)" })).link).toBeNull();
    expect(hostOf("https://www.site.example/7")).toBe("site.example");
  });

  it("keeps Instapaper out of All articles", () => {
    expect(articlesFor(emptyLibrary(), { kind: "instapaper", view: { kind: "home" } }, false)).toEqual([]);
  });

  it("knows which list a saved article belongs in", () => {
    expect(inView({ kind: "home" }, bookmark(1))).toBe(true);
    expect(inView({ kind: "home" }, bookmark(1, { folder_id: 5 }))).toBe(false);
    expect(inView({ kind: "folder", id: 5 }, bookmark(1, { folder_id: 5 }))).toBe(true);
    expect(inView({ kind: "archive" }, bookmark(1, { archived: true }))).toBe(true);
    expect(inView({ kind: "liked" }, bookmark(1, { liked: true, archived: true }))).toBe(true);
    expect(inView({ kind: "tag", name: "ai" }, bookmark(1, { tags: [{ id: 1, name: "ai", slug: "ai", count: 1 }] }))).toBe(true);
  });

  it("updates the list after a change, dropping what no longer belongs", () => {
    const list = [bookmark(1), bookmark(2)];
    expect(applyChange({ kind: "home" }, list, 1, { kind: "archived", archived: true }).map((b) => b.id)).toEqual([2]);
    expect(applyChange({ kind: "home" }, list, 1, { kind: "liked", liked: true })[0].liked).toBe(true);
    expect(applyChange({ kind: "home" }, list, 2, { kind: "moved", folderId: 9 }).map((b) => b.id)).toEqual([1]);
    expect(applyChange({ kind: "home" }, list, 2, { kind: "deleted" }).map((b) => b.id)).toEqual([1]);
    const archived = [bookmark(3, { archived: true, folder_id: 9 })];
    const back = applyChange({ kind: "liked" }, [{ ...archived[0], liked: true }], 3, { kind: "archived", archived: false });
    expect(back[0]).toMatchObject({ archived: false, folder_id: null });
    expect(applyChange({ kind: "archive" }, archived, 3, { kind: "moved", folderId: null })).toEqual([]);
  });

  it("names the lists", () => {
    const names = { home: "Unread", liked: "Liked", archive: "Archive" };
    expect(viewTitle({ kind: "folder", id: 2 }, [{ id: 2, title: "Later", slug: "later", position: 1, public: false, count: 0 }], names)).toBe("Later");
    expect(viewTitle({ kind: "tag", name: "ai" }, [], names)).toBe("ai");
    expect(viewTitle({ kind: "archive" }, [], names)).toBe("Archive");
  });
});

describe("saved articles shown like feed articles", () => {
  const feed = { url: "https://site.example/rss", title: "Site News", siteUrl: "https://www.site.example/", folderId: null, iconUrl: "https://site.example/icon.png", renamed: false };
  const fromFeed = {
    feedUrl: feed.url,
    id: "x",
    title: "From the feed",
    link: "http://site.example/7/#top",
    author: "Ann",
    html: "<p>The feed's whole text</p>",
    publishedAt: 1,
    fetchedAt: 1,
    read: false,
    imageUrl: "https://site.example/7.jpg",
  };
  const lib = { ...emptyLibrary(), feeds: [feed], articles: [fromFeed] };

  it("uses the feed article with the same address, and its feed", () => {
    const b = bookmark(7, { url: "https://www.site.example/7" });
    const a = bookmarkArticle(b, lib);
    expect(a.html).toBe("<p>The feed's whole text</p>");
    expect(a.imageUrl).toBe("https://site.example/7.jpg");
    expect(a.author).toBe("Ann");
    expect(articleKey(a)).toBe(`${INSTAPAPER_FEED} 7`);
    expect(sourceOf(b, lib)).toBe(feed);
  });

  it("otherwise keeps its description, under its website's feed or its favicon", () => {
    const other = bookmark(8, { url: "https://www.site.example/8" });
    expect(bookmarkArticle(other, lib).html).toContain("summary");
    expect(sourceOf(other, lib)).toBe(feed);
    const elsewhere = bookmark(9, { url: "https://www.elsewhere.example/a" });
    expect(sourceOf(elsewhere, lib)?.title).toBe("elsewhere.example");
    expect(sourceOf(elsewhere, lib)?.iconUrl).toBe("https://www.elsewhere.example/favicon.ico");
    expect(sourceOf(bookmark(10, { url: null }), lib)).toBeUndefined();
  });

  it("matches addresses without http, www, a trailing slash or the part after #", () => {
    expect(addressKey("https://www.Site.example/a/")).toBe(addressKey("http://site.example/a#x"));
    expect(addressKey(" ")).toBeNull();
  });
});
