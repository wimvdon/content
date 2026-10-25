import { describe, expect, it } from "vitest";
import {
  addToReadingList,
  bookmarks,
  isWebLink,
  itemForArticle,
  itemOf,
  itemsOf,
  itemTags,
  joinDuplicateItems,
  keepAsBookmark,
  keyOfLink,
  readingList,
  removeBookmark,
  renameTag,
  setReadingState,
  setTags,
} from "../src/lib/items";
import { addFeed, emptyLibrary, mergeFeed, newFeed, removeFeed, setRead, type Library } from "../src/lib/library";
import { allOf, changesBetween, isEmpty, libraryOf } from "../src/lib/libraryStore";

const FEED = "https://a.example.com/feed";

const withArticle: Library = mergeFeed(
  addFeed(emptyLibrary(), newFeed(FEED, "A")),
  FEED,
  {
    title: "A",
    siteUrl: null,
    items: [{ id: "1", title: "Feed title", link: "https://www.example.com/story/", author: null, html: "<p>Story text</p>", publishedAt: 10, imageUrl: "https://example.com/p.jpg" }],
  },
  100,
);

const saved = (lib = withArticle, url = "http://example.com/story#top", now = 1_000) => addToReadingList(lib, url, null, now)[0];

describe("tracking parameters (#253)", () => {
  it("a link with tracking parameters is the same item, other parameters still count", () => {
    const flipboard = "http://www.vulture.com/article/can-nirosta-steel-survive.html?utm_source=flipboard&utm_medium=s1&utm_campaign=nymag";
    expect(keyOfLink(flipboard)).toBe("vulture.com/article/can-nirosta-steel-survive.html");
    expect(keyOfLink("https://a.example/p?id=3&utm_source=x&b=2")).toBe("a.example/p?id=3&b=2");
    expect(keyOfLink("https://a.example/p/?fbclid=1")).toBe("a.example/p");
    expect(keyOfLink("https://a.example/p?id=3")).not.toBe(keyOfLink("https://a.example/p?id=4"));
    // Keys without tracking parameters stay exactly as before.
    expect(keyOfLink("https://a.example/p?b=2&a=1")).toBe("a.example/p?b=2&a=1");
  });
  it("duplicates from before become one item with everything of both", () => {
    const base = { title: "X", description: "", imageUrl: null, feedUrl: null, articleId: null, folder: null, service: null, bookmark: null, liked: false, note: "", highlights: [], progress: 0 };
    const lib: Library = {
      ...emptyLibrary(),
      items: [
        { ...base, key: "vulture.com/x.html?utm_source=flipboard", url: "http://www.vulture.com/x.html?utm_source=flipboard", createdAt: 10, readingList: { state: "archived", addedAt: 10 }, tags: ["tv"], note: "Mine", highlights: [{ id: "h1", quote: "quote", prefix: "", suffix: "", occurrence: 0, note: "", createdAt: 11 }], progress: 0.5 },
        { ...base, key: "vulture.com/x.html", url: "https://www.vulture.com/x.html", createdAt: 20, readingList: { state: "archived", addedAt: 20 }, tags: ["TV", "recap"], liked: true },
        { ...base, key: "other.example/y", url: "https://other.example/y", createdAt: 5, readingList: null, tags: [], liked: true },
      ],
    };
    const joined = joinDuplicateItems(lib);
    expect(joined.items.map((i) => i.key)).toEqual(["vulture.com/x.html", "other.example/y"]);
    expect(itemOf(joined, "vulture.com/x.html")).toMatchObject({
      url: "https://www.vulture.com/x.html",
      tags: ["TV", "recap"],
      note: "Mine",
      liked: true,
      progress: 0.5,
      createdAt: 10,
      readingList: { state: "archived", addedAt: 20 },
    });
    expect(itemOf(joined, "vulture.com/x.html")!.highlights.map((h) => h.id)).toEqual(["h1"]);
    expect(joinDuplicateItems(joined)).toBe(joined);
  });
});

describe("items: one per link (#106)", () => {
  it("treats links written another way as one", () => {
    expect(keyOfLink("https://www.Example.com/story/")).toBe(keyOfLink("http://example.com/story#part"));
    expect(keyOfLink(" ")).toBeNull();
    expect(isWebLink("javascript:alert(1)")).toBe(false);
  });

  it("makes the item of a saved feed article from that article, without a copy", () => {
    const lib = saved();
    const item = lib.items[0];
    expect(lib.items).toHaveLength(1);
    expect(item.title).toBe("Feed title");
    expect(item.feedUrl).toBe(FEED);
    expect(item.articleId).toBe("1");
    expect(item.imageUrl).toBe("https://example.com/p.jpg");
    expect(item.description).toBe("Story text");
    expect(item.readingList?.state).toBe("toread");
    expect(lib.articles).toHaveLength(1);
    expect(itemForArticle(lib, lib.articles[0])).toBe(item);

    const again = saved(setReadingState(lib, item.key, "archived"), "https://example.com/story", 2_000);
    expect(again.items).toHaveLength(1);
    expect(again.items[0].readingList).toEqual({ state: "toread", addedAt: 2_000 });
  });

  it("keeps memberships independent", () => {
    const key = saved().items[0].key;
    const kept = keepAsBookmark(saved(), key, " Research ", 3_000);
    expect(itemOf(kept, key)?.readingList?.state).toBe("toread");
    expect(itemOf(kept, key)?.bookmark).toEqual({ addedAt: 3_000, collection: "Research" });

    const archived = setReadingState(kept, key, "archived");
    expect(itemOf(archived, key)?.bookmark).not.toBeNull();
    const offList = setReadingState(archived, key, null);
    expect(itemOf(offList, key)?.readingList).toBeNull();
    expect(itemOf(offList, key)?.bookmark?.collection).toBe("Research");

    const unbookmarked = removeBookmark(kept, key);
    expect(itemOf(unbookmarked, key)?.readingList?.state).toBe("toread");
    expect(itemOf(setReadingState(unbookmarked, key, null), key)).toBeUndefined();

    // Read again: back on the reading list, still a bookmark.
    const again = addToReadingList(offList, itemOf(offList, key)!.url, null, 4_000)[0];
    expect(itemOf(again, key)?.readingList?.state).toBe("toread");
    expect(itemOf(again, key)?.bookmark?.addedAt).toBe(3_000);
  });

  it("cleans, lists and renames tags", () => {
    const key = saved().items[0].key;
    const tagged = setTags(saved(), key, [" work ", "Work", "", "news"]);
    expect(itemOf(tagged, key)?.tags).toEqual(["work", "news"]);
    expect(itemTags(tagged)).toEqual(["news", "work"]);
    expect(itemOf(renameTag(tagged, "WORK", "news"), key)?.tags).toEqual(["news"]);
    expect(renameTag(tagged, "work", " ")).toBe(tagged);
  });

  it("lists newest first, and keeps items when their feed goes", () => {
    const two = saved(saved(), "https://other.example/x", 5_000);
    expect(readingList(two, "toread").map((i) => i.key)).toEqual(["other.example/x", "example.com/story"]);
    const key = two.items[0].key;
    const archived = keepAsBookmark(setReadingState(two, key, "archived"), key, null, 6_000);
    expect(readingList(archived, "archived").map((i) => i.key)).toEqual([key]);
    expect(bookmarks(archived).map((i) => i.key)).toEqual([key]);
    expect(removeFeed(two, FEED).items).toHaveLength(2);
    expect(addToReadingList(emptyLibrary(), "ftp://x", "X", 1)).toEqual([emptyLibrary(), null]);
  });

  it("repairs stored items", () => {
    const good = saved().items[0];
    const items = itemsOf([good, { key: "x", url: "javascript:1" }, null, { ...good, key: "b", tags: ["a", 3], progress: 9, highlights: [{ id: "" }] }]);
    expect(items).toHaveLength(2);
    expect(items[0]).toEqual(good);
    expect(items[1].tags).toEqual(["a"]);
    expect(items[1].progress).toBe(1);
    expect(items[1].highlights).toEqual([]);
  });
});

describe("the library in IndexedDB", () => {
  it("writes only what changed", () => {
    const lib = saved();
    expect(isEmpty(changesBetween(lib, { ...lib }))).toBe(true);
    const read = setRead(lib, new Set([`${FEED} 1`]), true);
    const changes = changesBetween(lib, read);
    expect(changes.articles.map((a) => a.id)).toEqual(["1"]);
    expect(changes.feeds).toBeNull();
    expect(changes.items).toEqual([]);
    const removed = setReadingState(removeFeed(lib, FEED), lib.items[0].key, null);
    const more = changesBetween(lib, removed);
    expect(more.deletedArticles).toEqual([`${FEED} 1`]);
    expect(more.deletedItems).toEqual(["example.com/story"]);
    expect(more.feeds).toEqual([]);
    expect(allOf(lib).items).toEqual(lib.items);
  });

  it("reads an older library without items", () => {
    expect(libraryOf({ feeds: [newFeed(FEED, "A")], folders: [], articles: [] }).items).toEqual([]);
    expect(libraryOf(undefined)).toEqual(emptyLibrary());
  });
});
