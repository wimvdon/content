import { describe, expect, it } from "vitest";
import type { Bookmark } from "../src/lib/instapaper";
import { applyChange, bookmarkArticle, hostOf, inView, INSTAPAPER_FEED, viewTitle } from "../src/lib/instapaperLibrary";
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
