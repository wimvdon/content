import { describe, expect, it } from "vitest";
import {
  addToReadingList,
  bookmarks,
  isWebLink,
  itemForArticle,
  itemOf,
  itemsOf,
  itemTags,
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
