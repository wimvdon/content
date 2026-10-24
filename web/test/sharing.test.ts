import { describe, expect, it } from "vitest";
import { itemOf, type Item, type SharedBy } from "../src/lib/items";
import { emptyLibrary } from "../src/lib/library";
import { defaultSavedFilter, removeSaved, saveLink, savedItems } from "../src/lib/saved";
import { inboxItems, inboxUnread, markInboxRead, removeFromInbox } from "../src/lib/inbox";
import { inboxOf, sharedByLabel, tagArticles, takeInShared } from "../src/lib/sharing";
import { itemData, itemOfRow, mergeItems } from "../src/lib/syncRows";

const ann = { email: "ann@example.com", name: "Ann" };

function saved(key: string, at: number, tags: string[] = []): Item {
  return { key, url: `https://${key}`, title: key, description: "", imageUrl: null, feedUrl: null, articleId: null, createdAt: at, readingList: { state: "toread", addedAt: at }, folder: null, service: null, bookmark: null, liked: false, tags, note: "", highlights: [], progress: 0 };
}

describe("sharing with other Content users (#167) and the inbox (#246)", () => {
  const share = (id: string, from: SharedBy, url: string, tag?: string) => ({ id, from, items: [{ url, title: url }], tag });

  it("puts shared articles in the inbox, unread, with who shared them and the tag", () => {
    const { library, received } = takeInShared(emptyLibrary(), [{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One" }, { url: "https://a.example/2", title: "Two" }], tag: "Reading club" }], 100);
    expect(received).toEqual({ from: [ann], count: 2 });
    const one = itemOf(library, "a.example/1")!;
    expect(one.readingList).toBeNull();
    expect(one.sharedBy).toEqual({ ...ann, at: 100, waits: true, read: false });
    expect(one.tags).toEqual(["Reading club"]);
    expect(inboxItems(library)).toHaveLength(2);
    expect(inboxUnread(library)).toBe(2);
    expect(inboxUnread(emptyLibrary())).toBe(0);
    // Not in Saved: the inbox is a place of its own.
    expect(savedItems(library, { ...defaultSavedFilter, tab: { kind: "all" } })).toHaveLength(0);
  });

  it("keeps it in the inbox when saved, and in Saved when removed from the inbox", () => {
    const { library } = takeInShared(emptyLibrary(), [share("m", ann, "https://a.example/1")], 100);
    const [toRead] = saveLink(library, "https://a.example/1", "One", 200);
    expect(inboxItems(toRead).map((i) => i.key)).toEqual(["a.example/1"]);
    expect(savedItems(toRead, defaultSavedFilter).map((i) => i.key)).toEqual(["a.example/1"]);
    expect(inboxItems(removeSaved(toRead, "a.example/1")).map((i) => i.key)).toEqual(["a.example/1"]);
    const out = removeFromInbox(toRead, "a.example/1");
    expect(inboxItems(out)).toHaveLength(0);
    expect(savedItems(out, defaultSavedFilter).map((i) => i.key)).toEqual(["a.example/1"]);
    // Neither saved nor in the inbox: gone.
    expect(itemOf(removeFromInbox(library, "a.example/1"), "a.example/1")).toBeUndefined();
  });

  it("puts an article already saved in the inbox too, where it is in Saved", () => {
    const mine = { ...saved("a.example/1", 5, ["Mine"]), readingList: { state: "archived" as const, addedAt: 5 } };
    const first = takeInShared({ ...emptyLibrary(), items: [mine] }, [share("m", ann, "https://a.example/1", "club")], 100);
    const item = itemOf(first.library, "a.example/1")!;
    expect(item.readingList?.state).toBe("archived");
    expect(item.tags).toEqual(["Mine", "club"]);
    expect(first.received.count).toBe(1);
    expect(inboxUnread(first.library)).toBe(1);
  });

  it("brings an article shared again back unread, with its latest sender", () => {
    const bob = { email: "bob@example.com", name: "Bob" };
    const first = takeInShared(emptyLibrary(), [share("m", bob, "https://a.example/1")], 100).library;
    const read = markInboxRead(first, "a.example/1");
    expect(inboxUnread(read)).toBe(0);
    const again = takeInShared(removeFromInbox(read, "a.example/1"), [share("n", ann, "https://a.example/1")], 200);
    expect(again.received.count).toBe(1);
    expect(itemOf(again.library, "a.example/1")?.sharedBy).toEqual({ ...ann, at: 200, waits: true, read: false });
  });

  it("lists the inbox newest first, and marks read and unread", () => {
    const lib = takeInShared(takeInShared(emptyLibrary(), [share("m", ann, "https://a.example/1")], 100).library, [share("n", ann, "https://a.example/2")], 200).library;
    expect(inboxItems(lib).map((i) => i.key)).toEqual(["a.example/2", "a.example/1"]);
    const read = markInboxRead(lib, "a.example/2");
    expect(itemOf(read, "a.example/2")?.sharedBy?.read).toBe(true);
    expect(inboxUnread(read)).toBe(1);
    expect(inboxUnread(markInboxRead(read, "a.example/2", false))).toBe(2);
    expect(markInboxRead(read, "nope.example/x")).toBe(read);
  });

  it("sends a tag's saved articles, newest first", () => {
    const lib = { ...emptyLibrary(), items: [saved("a.example/1", 1, ["club"]), saved("a.example/2", 3, ["Club"]), saved("a.example/3", 2, ["other"])] };
    expect(tagArticles(lib, "CLUB").map((a) => a.url)).toEqual(["https://a.example/2", "https://a.example/1"]);
  });

  it("syncs who shared it with the item, the same as the Android app", () => {
    const by = { ...ann, at: 7, waits: true, read: true };
    const item = { ...saved("a.example/1", 1), readingList: null, sharedBy: by };
    expect(itemData(item).sharedBy).toEqual({ email: "ann@example.com", name: "Ann", at: 7, waits: true, read: true });
    expect(itemOfRow(item.key, itemData(item), undefined)?.sharedBy).toEqual(by);
    // Written by an older app: only who, and a message that is no longer shown.
    expect(itemOfRow(item.key, { ...itemData(item), sharedBy: { ...ann, message: "Hi" } }, undefined)?.sharedBy).toEqual({ ...ann, at: 0, waits: false, read: false });
    expect(itemOfRow(item.key, itemData({ ...item, sharedBy: null }), undefined)?.sharedBy).toBeNull();
    expect(mergeItems(null, { ...item, sharedBy: null }, item).sharedBy).toEqual(by);
    // Removed from the inbox on the other device: out of it here too.
    const removed = { ...item, readingList: { state: "toread" as const, addedAt: 9 }, sharedBy: { ...by, waits: false } };
    expect(mergeItems(item, item, removed).sharedBy?.waits).toBe(false);
  });

  it("reads the inbox and names who shared", () => {
    const messages = inboxOf({ messages: [{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One", imageUrl: "http://x" }], tag: null }, { id: "bad" }] });
    expect(messages).toEqual([{ id: "m1", from: ann, items: [{ url: "https://a.example/1", title: "One", imageUrl: null, description: "" }], tag: null }]);
    expect(inboxOf("nonsense")).toEqual([]);
    expect(sharedByLabel(ann)).toBe("Ann");
    expect(sharedByLabel({ email: "x@y.be", name: " " })).toBe("x@y.be");
  });
});
