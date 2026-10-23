import { describe, expect, it } from "vitest";
import { itemOf, type Item } from "../src/lib/items";
import { emptyLibrary } from "../src/lib/library";
import { defaultSavedFilter, savedItems, sharedToReadCount } from "../src/lib/saved";
import { inboxOf, sharedByLabel, tagArticles, takeInShared } from "../src/lib/sharing";
import { itemData, itemOfRow, mergeItems } from "../src/lib/syncRows";

const ann = { email: "ann@example.com", name: "Ann" };

function saved(key: string, at: number, tags: string[] = []): Item {
  return { key, url: `https://${key}`, title: key, description: "", imageUrl: null, feedUrl: null, articleId: null, createdAt: at, readingList: { state: "toread", addedAt: at }, folder: null, service: null, bookmark: null, liked: false, tags, note: "", highlights: [], progress: 0 };
}

describe("sharing with other Content users (#167)", () => {
  it("puts shared articles in To read, marked with who shared them and the tag", () => {
    const { library, received } = takeInShared(emptyLibrary(), [{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One" }, { url: "https://a.example/2", title: "Two" }], tag: "Reading club" }], 100);
    expect(received).toEqual({ from: [ann], count: 2 });
    const one = itemOf(library, "a.example/1")!;
    expect(one.readingList?.state).toBe("toread");
    expect(one.sharedBy).toEqual(ann);
    expect(one.tags).toEqual(["Reading club"]);
    expect(savedItems(library, { ...defaultSavedFilter, tab: { kind: "all" }, sharedOnly: true })).toHaveLength(2);
    expect(sharedToReadCount(library)).toBe(2);
    expect(sharedToReadCount(emptyLibrary())).toBeNull();
  });

  it("keeps an article already saved where it is, with its first sender", () => {
    const mine = { ...saved("a.example/1", 5, ["Mine"]), readingList: { state: "archived" as const, addedAt: 5 }, sharedBy: { email: "bob@example.com", name: "" } };
    const message = { id: "m", from: ann, items: [{ url: "https://a.example/1", title: "One" }], tag: "club" };
    const first = takeInShared({ ...emptyLibrary(), items: [mine] }, [message], 100);
    const item = itemOf(first.library, "a.example/1")!;
    expect(item.readingList?.state).toBe("archived");
    expect(item.sharedBy?.email).toBe("bob@example.com");
    expect(item.tags).toEqual(["Mine", "club"]);
    expect(takeInShared(first.library, [message], 200).received.count).toBe(0);
  });

  it("sends a tag's saved articles, newest first", () => {
    const lib = { ...emptyLibrary(), items: [saved("a.example/1", 1, ["club"]), saved("a.example/2", 3, ["Club"]), saved("a.example/3", 2, ["other"])] };
    expect(tagArticles(lib, "CLUB").map((a) => a.url)).toEqual(["https://a.example/2", "https://a.example/1"]);
  });

  it("syncs who shared it with the item, the same as the Android app", () => {
    const item = { ...saved("a.example/1", 1), sharedBy: ann };
    expect(itemData(item).sharedBy).toEqual(ann);
    expect(itemOfRow(item.key, itemData(item), undefined)?.sharedBy).toEqual(ann);
    expect(itemOfRow(item.key, itemData({ ...item, sharedBy: null }), undefined)?.sharedBy).toBeNull();
    expect(mergeItems(null, { ...item, sharedBy: null }, item).sharedBy).toEqual(ann);
  });

  it("reads the inbox and names who shared", () => {
    const messages = inboxOf({ messages: [{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One", imageUrl: "http://x" }], tag: null }, { id: "bad" }] });
    expect(messages).toEqual([{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One", imageUrl: null, description: "" }], tag: null }]);
    expect(inboxOf("nonsense")).toEqual([]);
    expect(sharedByLabel(ann)).toBe("Ann");
    expect(sharedByLabel({ email: "x@y.be", name: " " })).toBe("x@y.be");
  });
});
