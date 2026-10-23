import { describe, expect, it } from "vitest";
import { itemOf, type Item } from "../src/lib/items";
import { emptyLibrary } from "../src/lib/library";
import { defaultSavedFilter, placeShared, removeSaved, savedItems, sharedWaiting } from "../src/lib/saved";
import { inboxOf, sharedByLabel, tagArticles, takeInShared } from "../src/lib/sharing";
import { itemData, itemOfRow, mergeItems } from "../src/lib/syncRows";

const ann = { email: "ann@example.com", name: "Ann" };

function saved(key: string, at: number, tags: string[] = []): Item {
  return { key, url: `https://${key}`, title: key, description: "", imageUrl: null, feedUrl: null, articleId: null, createdAt: at, readingList: { state: "toread", addedAt: at }, folder: null, service: null, bookmark: null, liked: false, tags, note: "", highlights: [], progress: 0 };
}

describe("sharing with other Content users (#167)", () => {
  it("puts shared articles in Shared with me, with who shared them, the message and the tag", () => {
    const { library, received } = takeInShared(emptyLibrary(), [{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One" }, { url: "https://a.example/2", title: "Two" }], tag: "Reading club", message: " Read these " }], 100);
    expect(received).toEqual({ from: [ann], count: 2 });
    const one = itemOf(library, "a.example/1")!;
    expect(one.readingList).toBeNull();
    expect(one.sharedBy).toEqual({ ...ann, message: "Read these", at: 100, waits: true });
    expect(one.tags).toEqual(["Reading club"]);
    expect(savedItems(library, { ...defaultSavedFilter, tab: { kind: "shared" } })).toHaveLength(2);
    expect(savedItems(library, defaultSavedFilter)).toHaveLength(0);
    expect(sharedWaiting(library)).toBe(2);
    expect(sharedWaiting(emptyLibrary())).toBe(0);
  });

  it("put in To read or the Archive it leaves Shared with me, and removing it later drops it", () => {
    const { library } = takeInShared(emptyLibrary(), [{ id: "m", from: ann, items: [{ url: "https://a.example/1", title: "One" }], message: "Hi" }], 100);
    const toRead = placeShared(library, "a.example/1", "toread", 200);
    const item = itemOf(toRead, "a.example/1")!;
    expect(item.readingList).toEqual({ state: "toread", addedAt: 200 });
    expect(item.sharedBy?.waits).toBe(false);
    expect(item.sharedBy?.message).toBe("Hi");
    expect(sharedWaiting(toRead)).toBe(0);
    expect(savedItems(toRead, defaultSavedFilter).map((i) => i.key)).toEqual(["a.example/1"]);
    expect(itemOf(placeShared(library, "a.example/1", "archived", 200), "a.example/1")?.readingList?.state).toBe("archived");
    // Removed from Shared with me: gone.
    expect(itemOf(removeSaved(library, "a.example/1"), "a.example/1")).toBeUndefined();
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
    const by = { ...ann, message: "Hi", at: 7, waits: true };
    const item = { ...saved("a.example/1", 1), readingList: null, sharedBy: by };
    expect(itemData(item).sharedBy).toEqual({ email: "ann@example.com", name: "Ann", message: "Hi", at: 7, waits: true });
    expect(itemOfRow(item.key, itemData(item), undefined)?.sharedBy).toEqual(by);
    // Written by an older app: only who.
    expect(itemOfRow(item.key, { ...itemData(item), sharedBy: ann }, undefined)?.sharedBy).toEqual({ ...ann, message: "", at: 0, waits: false });
    expect(itemOfRow(item.key, itemData({ ...item, sharedBy: null }), undefined)?.sharedBy).toBeNull();
    expect(mergeItems(null, { ...item, sharedBy: null }, item).sharedBy).toEqual(by);
    // Put in To read on the other device: no longer waiting here either.
    const placed = { ...item, readingList: { state: "toread" as const, addedAt: 9 }, sharedBy: { ...by, waits: false } };
    expect(mergeItems(item, item, placed).sharedBy?.waits).toBe(false);
  });

  it("reads the inbox and names who shared", () => {
    const messages = inboxOf({ messages: [{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One", imageUrl: "http://x" }], tag: null }, { id: "bad" }] });
    expect(messages).toEqual([{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One", imageUrl: null, description: "" }], tag: null, message: "" }]);
    expect(inboxOf({ messages: [{ id: "m2", from: ann, items: [], message: "Hi" }] })[0].message).toBe("Hi");
    expect(inboxOf("nonsense")).toEqual([]);
    expect(sharedByLabel(ann)).toBe("Ann");
    expect(sharedByLabel({ email: "x@y.be", name: " " })).toBe("x@y.be");
  });
});
