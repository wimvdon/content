import { describe, expect, it } from "vitest";
import { AccountClient } from "../src/lib/account";
import { addToReadingList, changeItem, itemOf, keepAsBookmark, setLiked, setReadingState, setTags } from "../src/lib/items";
import { mergeFeed, setRead, type Article, type Library } from "../src/lib/library";
import { KEEP_READ_MS } from "../src/lib/sync";
import {
  emptyItemSyncState,
  itemData,
  itemOfRow,
  mergeHighlights,
  mergeItems,
  mergeTags,
  syncItems,
  type ItemChange,
  type ItemRow,
  type ItemsPage,
  type ItemsTarget,
  type ItemsWritten,
  type ItemSyncState,
} from "../src/lib/syncRows";

const NOW = 1_800_000_000_000;
const DAY = 24 * 60 * 60 * 1000;
const FEED = "https://news.example/feed";

/** The account's items in memory, like the Worker. */
class FakeItems implements ItemsTarget {
  version = 0;
  rows = new Map<string, ItemRow>();
  sent: string[] = [];
  private readonly pageSize: number;
  constructor(pageSize = 1000) {
    this.pageSize = pageSize;
  }

  async readItems(since: number): Promise<ItemsPage> {
    const changed = [...this.rows.values()].filter((r) => r.version > since).sort((a, b) => a.version - b.version);
    if (changed.length <= this.pageSize) return { version: this.version, rows: changed, more: false };
    const cut = changed[this.pageSize - 1].version;
    return { version: cut, rows: changed.filter((r) => r.version <= cut), more: true };
  }

  async writeItems(rows: ItemChange[]): Promise<ItemsWritten> {
    this.sent.push(JSON.stringify({ rows }));
    const next = this.version + 1;
    const saved: Record<string, number> = {};
    const conflicts: ItemRow[] = [];
    for (const change of rows) {
      const current = this.rows.get(change.key);
      if ((current?.version ?? 0) !== change.baseVersion) conflicts.push(current!);
      else {
        this.rows.set(change.key, { key: change.key, version: next, data: change.data });
        saved[change.key] = next;
      }
    }
    if (Object.keys(saved).length > 0) this.version = next;
    return { version: this.version, saved, conflicts };
  }
}

class Device {
  state: ItemSyncState = emptyItemSyncState();
  library: Library;
  constructor(library: Library) {
    this.library = library;
  }

  async sync(account: FakeItems, now = NOW) {
    this.state = await syncItems(account, this.state, now, (change) => (this.library = change(this.library)));
  }
}

const article = (id: string, read = false, date = NOW - DAY): Article => ({
  feedUrl: FEED,
  id,
  title: `Title ${id}`,
  link: `https://news.example/${id}`,
  author: null,
  html: `<p>SECRET ARTICLE TEXT ${id}</p>`,
  publishedAt: date,
  fetchedAt: date,
  read,
  imageUrl: null,
});

const libraryWith = (...articles: Article[]): Library => ({
  feeds: [{ url: FEED, title: "News", siteUrl: null, folderId: null, iconUrl: null, renamed: false }],
  folders: [],
  articles,
  items: [],
  readElsewhere: {},
});

const KEY = "news.example/2";
const saved = (lib: Library) => addToReadingList(lib, "https://news.example/2", null, NOW)[0];

describe("per-item sync (#106)", () => {
  it("brings items and read marks to another device", async () => {
    const account = new FakeItems();
    const phone = new Device(saved(libraryWith(article("1", true), article("2"))));
    await phone.sync(account);
    const laptop = new Device(libraryWith(article("1"), article("2")));
    await laptop.sync(account);
    expect(laptop.library.articles.map((a) => a.read)).toEqual([true, false]);
    expect(itemOf(laptop.library, KEY)).toMatchObject({ title: "Title 2", articleId: "2", readingList: { state: "toread" } });
  });

  it("sends no article text to the account", async () => {
    const account = new FakeItems();
    const phone = new Device(keepAsBookmark(saved(libraryWith(article("1", true), article("2"))), KEY, "Research", NOW));
    expect(itemOf(phone.library, KEY)!.description).toContain("SECRET");
    await phone.sync(account);
    expect(account.sent.length).toBeGreaterThan(0);
    for (const payload of account.sent) {
      expect(payload).not.toContain("SECRET");
      expect(payload).not.toContain("<p>");
    }
  });

  it("keeps changes to different parts of an item from two devices", async () => {
    const account = new FakeItems();
    const phone = new Device(saved(libraryWith(article("2"))));
    await phone.sync(account);
    const laptop = new Device(libraryWith(article("2")));
    await laptop.sync(account);
    phone.library = setTags(setLiked(phone.library, KEY, true), KEY, ["work", "old"]);
    laptop.library = keepAsBookmark(setTags(laptop.library, KEY, ["news"]), KEY, "Tools", NOW);
    await phone.sync(account);
    await laptop.sync(account);
    await phone.sync(account);
    for (const device of [phone, laptop]) {
      const item = itemOf(device.library, KEY)!;
      expect(item.liked).toBe(true);
      expect(new Set(item.tags)).toEqual(new Set(["work", "old", "news"]));
      expect(item.bookmark?.collection).toBe("Tools");
    }
    expect(phone.library.items.map(itemData)).toEqual(laptop.library.items.map(itemData));
  });

  it("merges tags as a set and highlights per highlight", () => {
    expect(mergeTags(["a", "b"], ["b"], ["a", "B", "c"])).toEqual(["b", "c"]);
    const h = (id: string, note = "", createdAt = Number(id)) => ({ id, quote: "q", prefix: "", suffix: "", occurrence: 0, note, createdAt });
    expect(mergeHighlights([h("1"), h("2")], [h("2", "n")], [h("1"), h("2"), h("3")])).toEqual([h("2", "n"), h("3")]);
    expect(mergeHighlights([h("1"), h("2")], [h("2", "local")], [h("1", "kept"), h("2", "remote")])).toEqual([h("1", "kept"), h("2", "local")]);
  });

  it("lets the device that syncs last win the same field, but an edit beats a removal", async () => {
    const account = new FakeItems();
    const phone = new Device(saved(libraryWith(article("2"))));
    await phone.sync(account);
    const laptop = new Device(libraryWith(article("2")));
    await laptop.sync(account);
    phone.library = setReadingState(phone.library, KEY, "archived");
    laptop.library = setReadingState(laptop.library, KEY, null);
    await phone.sync(account);
    await laptop.sync(account);
    await phone.sync(account);
    expect(itemOf(laptop.library, KEY)?.readingList?.state).toBe("archived");

    phone.library = changeItem(phone.library, KEY, (i) => ({ ...i, note: "phone" }));
    laptop.library = changeItem(laptop.library, KEY, (i) => ({ ...i, note: "laptop" }));
    await phone.sync(account);
    await laptop.sync(account);
    await phone.sync(account);
    expect(itemOf(phone.library, KEY)?.note).toBe("laptop");
  });

  it("brings a removal and unread again to the other device", async () => {
    const account = new FakeItems();
    const phone = new Device(saved(libraryWith(article("1", true), article("2"))));
    await phone.sync(account);
    const laptop = new Device(libraryWith(article("1"), article("2")));
    await laptop.sync(account);
    phone.library = setReadingState(phone.library, KEY, null);
    laptop.library = setRead(laptop.library, new Set([`${FEED} 1`]), false);
    await phone.sync(account);
    await laptop.sync(account);
    await phone.sync(account);
    expect(itemOf(laptop.library, KEY)).toBeUndefined();
    expect(phone.library.articles[0].read).toBe(false);
  });

  it("saves an item made again after another device deleted it", async () => {
    const account = new FakeItems();
    const phone = new Device(saved(libraryWith(article("2"))));
    await phone.sync(account);
    phone.library = setReadingState(phone.library, KEY, null);
    await phone.sync(account);
    const laptop = new Device(saved(libraryWith(article("2"))));
    await laptop.sync(account);
    expect(itemOf(laptop.library, KEY)?.readingList?.state).toBe("toread");
    expect(account.rows.get(`item:${KEY}`)?.data).toMatchObject({ readingList: { state: "toread" } });
    await phone.sync(account);
    expect(itemOf(phone.library, KEY)?.readingList?.state).toBe("toread");
  });

  it("remembers marks of articles this device doesn't have, which then arrive read", async () => {
    const account = new FakeItems();
    const phone = new Device(libraryWith(article("1", true), article("2", true)));
    await phone.sync(account);
    const laptop = new Device(libraryWith(article("1")));
    await laptop.sync(account);
    expect(laptop.library.readElsewhere).toEqual({ [`${FEED} 2`]: NOW - DAY });
    const sends = account.sent.length;
    await laptop.sync(account);
    expect(account.sent.length).toBe(sends);
    const arrived = mergeFeed(
      laptop.library,
      FEED,
      { title: "News", siteUrl: null, items: [{ id: "2", title: "Title 2", link: "https://news.example/2", author: null, html: "", publishedAt: NOW - DAY, imageUrl: null }] },
      NOW,
    );
    expect(arrived.articles.find((a) => a.id === "2")!.read).toBe(true);
    expect(arrived.readElsewhere).toEqual({});
  });

  it("drops old read marks from the account without making anything unread", async () => {
    const account = new FakeItems();
    const old = NOW - KEEP_READ_MS + DAY;
    const phone = new Device(libraryWith(article("1", true, old)));
    const laptop = new Device(libraryWith(article("1", false, old)));
    await phone.sync(account);
    await laptop.sync(account);
    expect(laptop.library.articles[0].read).toBe(true);
    await phone.sync(account, NOW + 2 * DAY);
    expect(account.rows.get(`read:${FEED} 1`)?.data).toBeNull();
    expect(phone.library.articles[0].read).toBe(true);
    await laptop.sync(account, NOW + 2 * DAY);
    expect(laptop.library.articles[0].read).toBe(true);
  });

  it("changes nothing when synced again, and reads big accounts in pages", async () => {
    const account = new FakeItems(3);
    const articles = Array.from({ length: 7 }, (_, i) => article(String(i), true));
    const phone = new Device(libraryWith(...articles));
    await phone.sync(account);
    expect(account.rows.size).toBe(7);
    const sends = account.sent.length;
    await phone.sync(account);
    expect(account.sent.length).toBe(sends);
    const laptop = new Device(libraryWith(...articles.map((a) => ({ ...a, read: false }))));
    await laptop.sync(account);
    expect(laptop.library.articles.every((a) => a.read)).toBe(true);
  });

  it("leaves rows it can't read alone", async () => {
    const account = new FakeItems();
    account.rows.set("future:x", { key: "future:x", version: 1, data: { a: 1 } });
    account.rows.set("item:bad", { key: "item:bad", version: 1, data: { url: "javascript:alert(1)" } });
    account.version = 1;
    const phone = new Device(libraryWith());
    await phone.sync(account);
    expect(phone.library.items).toEqual([]);
    expect(account.sent).toEqual([]);
  });

  it("writes the same item data as the Android app", () => {
    // The same shape is written by SyncRows.itemData on Android.
    const item = { ...itemOf(saved(libraryWith(article("2"))), KEY)!, progress: 0.4567 };
    expect(Object.keys(itemData(item))).toEqual([
      "url",
      "title",
      "imageUrl",
      "feedUrl",
      "articleId",
      "createdAt",
      "readingList",
      "folder",
      "service",
      "bookmark",
      "liked",
      "tags",
      "note",
      "highlights",
      "progress",
      "sharedBy",
    ]);
    expect(itemData(item).progress).toBe(457);
  });

  it("keeps an item's folder through sync, and both sides changing it keeps this device's (#119)", () => {
    const item = { ...itemOf(saved(libraryWith(article("2"))), KEY)!, folder: "Work" };
    expect(itemData(item).folder).toBe("Work");
    expect(itemOfRow(KEY, itemData(item), undefined)?.folder).toBe("Work");
    const base = { ...item, folder: null };
    expect(mergeItems(base, { ...item, folder: "Here" }, { ...item, folder: "There" }).folder).toBe("Here");
    expect(mergeItems(base, base, { ...item, folder: "There" }).folder).toBe("There");
    // Archiving takes it out of its folder.
    const lib = { ...libraryWith(article("2")), items: [item] };
    expect(itemOf(setReadingState(lib, KEY, "archived"), KEY)?.folder).toBeNull();
  });

  it("talks to the account's items", async () => {
    const calls: { path: string; init: RequestInit }[] = [];
    const client = new AccountClient(
      "acc.key",
      async (input, init) => {
        calls.push({ path: input, init: init ?? {} });
        if (input.includes("since=4")) return new Response(JSON.stringify({ version: 5, rows: [{ key: "item:a", version: 5, data: { url: "https://a" } }, { bad: 1 }], more: false }));
        return new Response(JSON.stringify({ version: 6, saved: [{ key: "read:f 1", version: 6 }], conflicts: [{ key: "item:b", version: 2, data: null }] }));
      },
      "https://w",
    );
    expect(await client.readItems(4)).toEqual({ version: 5, rows: [{ key: "item:a", version: 5, data: { url: "https://a" } }], more: false });
    expect(await client.writeItems([{ key: "read:f 1", baseVersion: 0, data: { time: 1 } }])).toEqual({
      version: 6,
      saved: { "read:f 1": 6 },
      conflicts: [{ key: "item:b", version: 2, data: null }],
    });
    expect(calls[1].path).toBe("https://w/account/items");
    expect(JSON.parse(calls[1].init.body as string)).toEqual({ rows: [{ key: "read:f 1", baseVersion: 0, data: { time: 1 } }] });
  });
});
