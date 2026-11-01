import { describe, expect, it } from "vitest";
import type { Item } from "../src/lib/items";
import { chipTags, showsLiked } from "../src/lib/saved";
import { emptyLibrary, feedFoldersToTags, newFeed, type Library } from "../src/lib/library";
import { addTopic, feedChips, inTopicOrder, mergeTopic, moved, removeTopic, reorderTopics, renameTopic, tagWithFeedTopic, topicCounts, topicIdFor, topicNamed, topicNames, topicsForTags } from "../src/lib/topics";

const saved = (key: string, ...tags: string[]): Item => ({
  key,
  url: `https://${key}`,
  title: key,
  description: "",
  imageUrl: null,
  feedUrl: null,
  articleId: null,
  createdAt: 1,
  readingList: { state: "toread", addedAt: 1 },
  folder: null,
  service: null,
  bookmark: null,
  liked: false,
  tags,
  note: "",
  highlights: [],
  progress: 0,
});

// The same library as the Android app's TopicsTest.
const library: Library = feedFoldersToTags({
  ...emptyLibrary(),
  feeds: [
    { ...newFeed("https://a/feed", "A"), folderId: "f1" },
    { ...newFeed("https://b/feed", "B"), folderId: "f2" },
    newFeed("https://c/feed", "C"),
  ],
  folders: [
    { id: "f1", title: "Tech", icon: "computer" },
    { id: "f2", title: "News", icon: "newspaper" },
  ],
  items: [saved("x.example/1", "tech", "Recipes"), saved("x.example/2", "News"), saved("x.example/3", "recipes")],
});
const feed = (lib: Library, url: string) => lib.feeds.find((f) => f.url === url)!;
const item = (lib: Library, key: string) => lib.items.find((i) => i.key === key)!;

describe("topics", () => {
  it("gives every tag its topic, made the same on every device", () => {
    const settled = topicsForTags(library);
    expect(settled.folders.map((f) => f.title)).toEqual(["Tech", "News", "Recipes"]);
    expect(settled.folders[2]).toEqual({ id: "topic:recipes", title: "Recipes", icon: "folder" });
    expect(topicIdFor(" RECIPES ")).toBe("topic:recipes");
    expect(topicsForTags(settled)).toBe(settled);
  });

  it("joins topics with the same name, with the feeds of both", () => {
    const twice = { ...library, folders: [...library.folders, { id: "f3", title: "tech", icon: "star" }], feeds: [...library.feeds, { ...newFeed("https://d/feed", "D"), folderId: "f3" }] };
    const joined = topicsForTags(twice);
    expect(joined.folders.map((f) => f.id)).toEqual(["f1", "f2", "topic:recipes"]);
    expect(feed(joined, "https://d/feed").folderId).toBe("f1");
  });

  it("never adds a second topic with the same name", () => {
    expect(addTopic(library, "new", " news ")).toBe(library);
    expect(addTopic(library, "new", "  ")).toBe(library);
    expect(addTopic(library, "new", " Sports ", "sports-soccer").folders.at(-1)).toEqual({ id: "new", title: "Sports", icon: "sports-soccer" });
    expect(topicNamed(library, "NEWS")?.id).toBe("f2");
  });

  it("renames a topic on its feeds and saved articles", () => {
    const renamed = renameTopic(topicsForTags(library), "f1", "Technology", "memory");
    expect(renamed.folders[0]).toEqual({ id: "f1", title: "Technology", icon: "memory" });
    expect(feed(renamed, "https://a/feed").tags).toEqual(["Technology"]);
    expect(item(renamed, "x.example/1").tags).toEqual(["Technology", "Recipes"]);
    expect(renameTopic(library, "f1", " ", "memory")).toBe(library);
  });

  it("joins two topics when one is renamed to the other's name", () => {
    const joined = renameTopic(topicsForTags(library), "topic:recipes", "news", "restaurant");
    expect(joined.folders.map((f) => f.id)).toEqual(["f1", "f2"]);
    expect(joined.folders[1].icon).toBe("newspaper");
    expect(item(joined, "x.example/1").tags).toEqual(["tech", "News"]);
    expect(item(joined, "x.example/3").tags).toEqual(["News"]);
    const feedsMoved = renameTopic(topicsForTags(library), "f1", "News", "computer");
    expect(feed(feedsMoved, "https://a/feed").folderId).toBe("f2");
    expect(feed(feedsMoved, "https://a/feed").tags).toEqual(["News"]);
  });

  it("keeps a removed topic's feeds and saved articles, without it", () => {
    const removed = removeTopic(topicsForTags(library), "f1");
    expect(removed.folders.map((f) => f.id)).toEqual(["f2", "topic:recipes"]);
    expect(feed(removed, "https://a/feed").folderId).toBeNull();
    expect(feed(removed, "https://a/feed").tags ?? []).toEqual([]);
    expect(item(removed, "x.example/1").tags).toEqual(["Recipes"]);
    expect(removed.items).toHaveLength(3);
  });

  it("counts feeds and saved articles per topic, and orders names like the topics", () => {
    const settled = topicsForTags(library);
    expect([...topicCounts(settled)]).toEqual([
      ["f1", { feeds: 1, saved: 1, toRead: 1 }],
      ["f2", { feeds: 1, saved: 1, toRead: 1 }],
      ["topic:recipes", { feeds: 0, saved: 2, toRead: 2 }],
    ]);
    expect(inTopicOrder(settled, ["Zoo", "recipes", "News", "tech"])).toEqual(["tech", "News", "recipes", "Zoo"]);
    expect(topicNames(settled)).toEqual(["Tech", "News", "Recipes"]);
  });

  it("gives a saved feed article its feed's topic", () => {
    const article = { feedUrl: "https://a/feed", id: "1", title: "Post", link: "https://a.example/post", author: null, html: "", publishedAt: null, fetchedAt: 0, read: false, imageUrl: null };
    const lib = { ...topicsForTags(library), articles: [article], items: [...library.items, { ...saved("a.example/post"), url: "https://a.example/post", feedUrl: "https://a/feed", articleId: "1" }] };
    expect(item(tagWithFeedTopic(lib, "a.example/post"), "a.example/post").tags).toEqual(["Tech"]);
    // A link that isn't a feed article gets nothing.
    expect(tagWithFeedTopic(lib, "x.example/2")).toBe(lib);
  });

  it("merges a topic into the one picked, which keeps its name and icon", () => {
    const merged = mergeTopic(topicsForTags(library), "f1", "f2");
    expect(merged.folders.map((f) => f.id)).toEqual(["f2", "topic:recipes"]);
    expect(feed(merged, "https://a/feed").folderId).toBe("f2");
    expect(item(merged, "x.example/1").tags).toEqual(["News", "Recipes"]);
    const settled = topicsForTags(library);
    expect(mergeTopic(settled, "f1", "f1")).toBe(settled);
  });


  it("leaves out Feeds' topic chips with nothing in the tab shown, except the one chosen", () => {
    const article = (feedUrl: string, id: string, read: boolean) => ({ feedUrl, id, title: id, link: null, author: null, html: "", publishedAt: null, fetchedAt: 0, read, imageUrl: null });
    const lib = { ...topicsForTags(library), articles: [article("https://a/feed", "1", false), article("https://b/feed", "2", true)] };
    expect(feedChips(lib, "unread", null)).toEqual(["Tech"]);
    expect(feedChips(lib, "read", null)).toEqual(["News"]);
    expect(feedChips(lib, "unread", "news")).toEqual(["Tech", "News"]);
  });

  it("leaves out Saved's topic chips and Liked with nothing in the state shown, except the chosen ones", () => {
    const archived = { ...saved("x.example/9", "Old"), readingList: { state: "archived" as const, addedAt: 1 }, liked: true };
    const lib = topicsForTags({ ...library, items: [...library.items, archived] });
    expect(chipTags(lib, { tab: { kind: "toread" }, likedOnly: false, tags: [] })).toEqual(["tech", "News", "Recipes"]);
    expect(chipTags(lib, { tab: { kind: "archive" }, likedOnly: false, tags: [] })).toEqual(["Old"]);
    expect(chipTags(lib, { tab: { kind: "archive" }, likedOnly: false, tags: ["tech"] })).toEqual(["tech", "Old"]);
    expect(showsLiked(lib, { tab: { kind: "toread" }, likedOnly: false, tags: [] })).toBe(false);
    expect(showsLiked(lib, { tab: { kind: "archive" }, likedOnly: false, tags: [] })).toBe(true);
    expect(showsLiked(lib, { tab: { kind: "toread" }, likedOnly: true, tags: [] })).toBe(true);
  });
});

describe("reordering topics by their chips (#314)", () => {
  const lib = (): Library => {
    let l = emptyLibrary();
    for (const name of ["A", "B", "C"]) l = addTopic(l, name.toLowerCase(), name);
    return l;
  };

  it("puts the topics in the order of the ids, the others after", () => {
    expect(reorderTopics(lib(), ["c", "a"]).folders.map((f) => f.id)).toEqual(["c", "a", "b"]);
    expect(reorderTopics(lib(), ["b", "x", "b"]).folders.map((f) => f.id)).toEqual(["b", "a", "c"]);
    const same = lib();
    expect(reorderTopics(same, ["a", "b", "c"])).toBe(same);
  });

  it("moves one entry and keeps the others in order", () => {
    expect(moved(["a", "b", "c"], 0, 2)).toEqual(["b", "c", "a"]);
    expect(moved(["a", "b", "c"], 2, 0)).toEqual(["c", "a", "b"]);
    const same = ["a", "b"];
    expect(moved(same, 0, 5)).toBe(same);
    expect(moved(same, 1, 1)).toBe(same);
  });
});
