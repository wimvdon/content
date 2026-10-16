import { describe, expect, it } from "vitest";
import { feedLinks, findFeed, normalizeUrl } from "../src/lib/discovery";
import { searchIcons, parseIcons } from "../src/lib/icons";
import { emptyLibrary, articlesFor, setRead, removeFolder, addFolder, addFeed, newFeed, moveFeed, renameFeed, mergeFeed, reorder, unreadKeys } from "../src/lib/library";
import { importOpml, parseOpml, writeOpml } from "../src/lib/opml";
import { FetchError } from "../src/lib/proxy";
import { sanitizeArticle } from "../src/lib/sanitize";

describe("discovery", () => {
  it("normalizes addresses", () => {
    expect(normalizeUrl("example.com")).toBe("https://example.com/");
    expect(normalizeUrl("http://example.com/feed")).toBe("https://example.com/feed");
    expect(normalizeUrl("feed://example.com/rss")).toBe("https://example.com/rss");
    expect(normalizeUrl("not a url")).toBeNull();
    expect(normalizeUrl("localhost")).toBeNull();
  });

  it("finds feeds linked from a page, or at common paths", async () => {
    expect(feedLinks('<link rel="alternate" type="application/rss+xml" href="/rss">', "https://a.example/")).toEqual(["https://a.example/rss"]);
    const rss = "<rss><channel><title>A</title></channel></rss>";
    const pages: Record<string, string> = { "https://a.example/": "<html><head></head></html>", "https://a.example/feed": rss };
    const result = await findFeed("a.example", async (url) => {
      if (!(url in pages)) throw new FetchError("notFound");
      return { text: pages[url], url };
    });
    expect(result.kind === "found" && result.url).toBe("https://a.example/feed");
    expect((await findFeed("???", async () => ({ text: "", url: "" }))).kind).toBe("invalid");
  });
});

describe("library", () => {
  it("handles folders, renames, read state and the Unread filter", () => {
    let lib = addFolder(emptyLibrary(), { id: "f", title: " News ", icon: "folder" });
    lib = addFeed(lib, newFeed("a", "A"));
    lib = moveFeed(lib, "a", "f");
    lib = mergeFeed(lib, "a", { title: "Feed A", siteUrl: null, items: [{ id: "1", title: "t", link: null, author: null, html: "", publishedAt: 5, imageUrl: null }] }, 10);
    expect(lib.folders[0].title).toBe("News");
    expect(lib.feeds[0].title).toBe("Feed A");
    lib = renameFeed(lib, "a", "Mine");
    lib = mergeFeed(lib, "a", { title: "Feed A", siteUrl: null, items: [] }, 20);
    expect(lib.feeds[0].title).toBe("Mine");
    expect(unreadKeys(lib, { kind: "folder", folderId: "f" }).size).toBe(1);
    lib = setRead(lib, new Set(["a 1"]), true);
    expect(articlesFor(lib, { kind: "all" }, true)).toEqual([]);
    expect(articlesFor(lib, { kind: "all" }, true, new Set(["a 1"])).length).toBe(1);
    lib = removeFolder(lib, "f");
    expect(lib.feeds[0].folderId).toBeNull();
    expect(reorder([1, 2, 3], 0, 1)).toEqual([2, 1, 3]);
    expect(reorder([1, 2, 3], 0, -1)).toEqual([1, 2, 3]);
  });
});

describe("opml", () => {
  it("imports folders and exports them again", () => {
    const text = `<?xml version="1.0"?><opml version="2.0"><body>
      <outline text="News"><outline type="rss" text="A" xmlUrl="https://a.example/feed"/></outline>
      <outline type="rss" title="B" xmlUrl="https://b.example/feed"/>
      <outline type="rss" title="B again" xmlUrl="https://b.example/feed"/>
    </body></opml>`;
    const entries = parseOpml(text);
    expect(entries).toEqual([
      { url: "https://a.example/feed", title: "A", folder: "News" },
      { url: "https://b.example/feed", title: "B", folder: null },
    ]);
    const [lib, added] = importOpml(emptyLibrary(), entries, () => "id1");
    expect(added).toBe(2);
    expect(parseOpml(writeOpml("RSS Reader", lib))).toEqual(entries);
    expect(parseOpml("<html/>")).toEqual([]);
  });
});

describe("sanitize", () => {
  it("removes scripts and handlers, fixes links and pictures", () => {
    const html = sanitizeArticle(
      '<p onclick="x()">Hi <a href="/more">more</a></p><script>alert(1)</script><img src="http://a.example/p.jpg"><img src="data:image/png;base64,xx"><iframe src="https://evil.example/"></iframe><iframe src="https://www.youtube.com/embed/abc"></iframe>',
      "https://a.example/post",
    );
    expect(html).not.toContain("<script");
    expect(html).not.toContain("onclick");
    expect(html).toContain('href="https://a.example/more"');
    expect(html).toContain('target="_blank"');
    expect(html).toContain('src="https://a.example/p.jpg"');
    expect(html).not.toContain("data:");
    expect(html).not.toContain("evil.example");
    expect(html).toContain("youtube-nocookie.com/embed/abc");
  });
});

describe("icons", () => {
  it("parses the Android app's icon list and searches it", () => {
    const icons = parseIcons("# comment\nfolder\tfiles\tM0 0h1\nsports-soccer\tsports, activities\tM1 1|M2 2\n");
    expect(icons.get("sports-soccer")!.paths).toEqual(["M1 1", "M2 2"]);
    expect(searchIcons(icons, "soccer").map((i) => i.name)).toEqual(["sports-soccer"]);
    expect(searchIcons(icons, "").map((i) => i.name)).toEqual(["folder", "sports-soccer"]);
  });
});
