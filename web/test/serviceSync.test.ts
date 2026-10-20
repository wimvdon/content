import { describe, expect, it } from "vitest";
import { InstapaperError, type Bookmark, type ChangesPage, type Folder, type Tag } from "../src/lib/instapaper";
import { InstapaperSync } from "../src/lib/instapaperSync";
import { addToReadingList, changeItem, itemOf, setLiked, setReadingState, setTags } from "../src/lib/items";
import { emptyLibrary, type Library } from "../src/lib/library";
import { emptyServiceSyncState, ServiceSyncError, syncService, type RemoteChanges, type RemoteEntry, type SavedSyncService, type ServiceSyncState } from "../src/lib/serviceSync";

/** A service in memory: what it has, a cursor per change, and calls that can fail. */
class FakeService implements SavedSyncService {
  readonly progress = true;
  entries = new Map<string, RemoteEntry>();
  private changedAt = new Map<string, number>();
  private deletedAt = new Map<string, number>();
  clock = 1;
  nextId = 100;
  failAfter = Infinity;
  calls: string[] = [];

  readonly id: string;

  constructor(id = "instapaper") {
    this.id = id;
  }

  private call(name: string) {
    if (this.failAfter-- <= 0) throw new ServiceSyncError("rateLimit");
    this.calls.push(name);
  }

  /** Another app changes the account. */
  put(entry: RemoteEntry) {
    this.entries.set(entry.remoteId, entry);
    this.changedAt.set(entry.remoteId, ++this.clock);
  }

  delete(id: string) {
    this.entries.delete(id);
    this.deletedAt.set(id, ++this.clock);
  }

  async changes(cursor: string | null): Promise<RemoteChanges> {
    this.call("changes");
    const since = Number(cursor ?? 0);
    return {
      entries: [...this.entries.values()].filter((e) => (this.changedAt.get(e.remoteId) ?? 0) > since),
      deleted: [...this.deletedAt].filter(([, at]) => at > since).map(([id]) => id),
      cursor: String(this.clock),
    };
  }

  async add(url: string, title: string, tags: string[], archived: boolean, folder: string | null): Promise<RemoteEntry> {
    this.call(`add ${url}`);
    const entry = remote(String(this.nextId++), url, { title, tags, archived, folder });
    this.put(entry);
    return entry;
  }

  async update(have: RemoteEntry, want: RemoteEntry): Promise<RemoteEntry> {
    this.call(`update ${have.remoteId}`);
    const entry = { ...want, remoteId: have.remoteId };
    this.put(entry);
    return entry;
  }

  async remove(id: string): Promise<void> {
    this.call(`remove ${id}`);
    this.delete(id);
  }
}

function remote(id: string, url: string, patch: Partial<RemoteEntry> = {}): RemoteEntry {
  return { remoteId: id, url, title: `Title ${id}`, description: "Desc", imageUrl: null, addedAt: 1_000, archived: false, liked: false, tags: [], folder: null, progress: 0, ...patch };
}

function setup() {
  const box = { lib: emptyLibrary() as Library, state: emptyServiceSyncState as ServiceSyncState };
  const service = new FakeService();
  const sync = async (s: SavedSyncService = service, isDefault = true) => {
    const result = await syncService(
      s,
      box.state,
      (change) => (box.lib = change(box.lib)),
      5_000,
      isDefault,
    );
    box.state = result.state;
    return result.problem;
  };
  return { box, service, sync };
}

describe("Saved and a service (serviceSync.ts)", () => {
  it("takes in the whole account on the first sync, with folders, likes and tags", async () => {
    const { box, service, sync } = setup();
    service.put(remote("1", "https://a.example/1", { folder: "Work", tags: ["ai"] }));
    service.put(remote("2", "https://a.example/2", { archived: true, liked: true }));
    expect(await sync()).toBeNull();
    expect(itemOf(box.lib, "a.example/1")).toMatchObject({ folder: "Work", tags: ["ai"], service: "instapaper", readingList: { state: "toread" } });
    expect(itemOf(box.lib, "a.example/2")).toMatchObject({ liked: true, readingList: { state: "archived" } });
  });

  it("sends what changed here, once, and new saves go there", async () => {
    const { box, service, sync } = setup();
    service.put(remote("1", "https://a.example/1"));
    await sync();
    box.lib = setTags(setLiked(box.lib, "a.example/1", true), "a.example/1", ["x"]);
    box.lib = changeItem(box.lib, "a.example/1", (i) => ({ ...i, folder: "Later" }));
    box.lib = addToReadingList(box.lib, "https://b.example/new", "New", 10)[0];
    await sync();
    expect(service.entries.get("1")).toMatchObject({ liked: true, tags: ["x"], folder: "Later" });
    expect([...service.entries.values()].some((e) => e.url === "https://b.example/new")).toBe(true);
    expect(itemOf(box.lib, "b.example/new")?.service).toBe("instapaper");
    service.calls = [];
    await sync();
    expect(service.calls).toEqual(["changes"]);
  });

  it("merges three ways: a change on each side to other fields both stay; the same field, Reader wins", async () => {
    const { box, service, sync } = setup();
    service.put(remote("1", "https://a.example/1"));
    await sync();
    box.lib = setLiked(box.lib, "a.example/1", true);
    service.put({ ...service.entries.get("1")!, archived: true, title: "There" });
    await sync();
    expect(itemOf(box.lib, "a.example/1")).toMatchObject({ liked: true, title: "There", readingList: { state: "archived" } });
    expect(service.entries.get("1")).toMatchObject({ liked: true, archived: true });
    box.lib = changeItem(box.lib, "a.example/1", (i) => ({ ...i, title: "Here" }));
    service.put({ ...service.entries.get("1")!, title: "Elsewhere" });
    await sync();
    expect(itemOf(box.lib, "a.example/1")?.title).toBe("Here");
  });

  it("removes there what was removed here, and here what was deleted there unless changed since", async () => {
    const { box, service, sync } = setup();
    service.put(remote("1", "https://a.example/1"));
    service.put(remote("2", "https://a.example/2"));
    await sync();
    box.lib = setReadingState(box.lib, "a.example/1", null);
    await sync();
    expect(service.entries.has("1")).toBe(false);
    box.lib = setLiked(box.lib, "a.example/2", true);
    service.delete("2");
    await sync();
    // Changed here since: it stays, and goes there again.
    expect(itemOf(box.lib, "a.example/2")?.readingList).not.toBeNull();
    expect([...service.entries.values()].some((e) => e.url === "https://a.example/2")).toBe(true);
  });

  it("doesn't send items kept in another service, and a copy only shares archive, like and tags", async () => {
    const { box, service, sync } = setup();
    box.lib = addToReadingList(box.lib, "https://r.example/9", "R", 10)[0];
    box.lib = changeItem(box.lib, "r.example/9", (i) => ({ ...i, service: "raindrop", folder: "Reading" }));
    await sync();
    expect(service.entries.size).toBe(0);
    // The same article in both: its copy here shares the like, not the folder.
    service.put(remote("5", "https://r.example/9", { folder: "Other" }));
    await sync();
    expect(itemOf(box.lib, "r.example/9")).toMatchObject({ service: "raindrop", folder: "Reading" });
    box.lib = setLiked(box.lib, "r.example/9", true);
    await sync();
    expect(service.entries.get("5")).toMatchObject({ liked: true, folder: "Other" });
  });

  it("not the default service, new saves wait for the default one", async () => {
    const { box, service, sync } = setup();
    box.lib = addToReadingList(box.lib, "https://b.example/1", "B", 10)[0];
    await sync(service, false);
    expect(service.entries.size).toBe(0);
  });

  it("a failure keeps everything, and the rest goes next time", async () => {
    const { box, service, sync } = setup();
    box.lib = addToReadingList(addToReadingList(box.lib, "https://b.example/1", "1", 10)[0], "https://b.example/2", "2", 11)[0];
    service.failAfter = 2;
    expect(await sync()).toBe("rateLimit");
    expect(Object.keys(box.state.rows)).toHaveLength(1);
    service.failAfter = Infinity;
    expect(await sync()).toBeNull();
    expect(service.entries.size).toBe(2);
  });

  it("removes nothing there when most of Saved disappeared at once", async () => {
    const { box, service, sync } = setup();
    for (let i = 0; i < 30; i++) service.put(remote(String(i), `https://a.example/${i}`));
    await sync();
    box.lib = emptyLibrary();
    expect(await sync()).toBe("refused");
    expect(service.entries.size).toBe(30);
  });
});

/** Instapaper's calls in memory, as the Worker forwards them. */
class FakeApi {
  bookmarks: Bookmark[] = [];
  deleted: number[] = [];
  folderList: Folder[] = [{ id: 3, title: "Work", slug: "work", position: 1, public: false, count: 0 }];
  tagList: Tag[] = [{ id: 9, name: "Recipes", slug: "recipes", count: 1 }];
  calls: string[] = [];
  removeFails: InstapaperError | null = null;

  async changesSince(since: number, offset: number, limit: number): Promise<ChangesPage> {
    this.calls.push(`since ${since} ${offset} ${limit}`);
    // A page holds up to `limit` changes, deletions first, as Instapaper counts them.
    const page = [...this.deleted.map((id) => ({ id })), ...this.bookmarks].slice(offset, offset + limit);
    return { bookmarks: page.filter((x): x is Bookmark => "url" in x), deleted_ids: page.filter((x) => !("url" in x)).map((x) => x.id) };
  }
  async folders() {
    return this.folderList;
  }
  async tags() {
    return this.tagList;
  }
  async save(url: string, title?: string): Promise<Bookmark> {
    this.calls.push(`save ${url} ${title ?? ""}`.trim());
    return bookmark(50, url);
  }
  async setTags(id: number, add: string[], removeIds: number[]): Promise<Tag[]> {
    this.calls.push(`tags ${id} +${add.join(",")} -${removeIds.join(",")}`);
    return add.map((name, i) => ({ id: 100 + i, name, slug: name, count: 1 }));
  }
  async move(id: number, to: "archive" | "home" | number): Promise<Bookmark> {
    this.calls.push(`move ${id} ${to}`);
    return bookmark(id, "https://x");
  }
  async like(id: number, liked: boolean): Promise<Bookmark> {
    this.calls.push(`like ${id} ${liked}`);
    return bookmark(id, "https://x");
  }
  async createFolder(title: string): Promise<Folder> {
    this.calls.push(`folder ${title}`);
    return { id: 7, title, slug: title, position: 2, public: false, count: 0 };
  }
  async updateBookmark(id: number, change: { title?: string; progress?: number; at: number }): Promise<Bookmark> {
    this.calls.push(`update ${id} ${change.title ?? "-"} ${change.progress ?? "-"}`);
    return bookmark(id, "https://x");
  }
  async remove(id: number): Promise<void> {
    this.calls.push(`remove ${id}`);
    if (this.removeFails) throw this.removeFails;
  }
}

function bookmark(id: number, url: string | null, patch: Partial<Bookmark> = {}): Bookmark {
  return { id, url, title: `T${id}`, description: "D", image: "https://img/x.jpg", liked: false, archived: false, time: 1_755_000_000, folder_id: null, tags: [], ...patch };
}

describe("Instapaper as Saved's service (instapaperSync.ts)", () => {
  it("reads every change since the cursor, page by page, with folders, deletions and progress", async () => {
    const api = new FakeApi();
    api.bookmarks = [...Array.from({ length: 499 }, (_, i) => bookmark(i + 1, `https://a.example/${i}`)), bookmark(600, "https://b.example/x", { folder_id: 3, progress: { percentage: 0.42 }, tags: [{ id: 9, name: "Recipes", slug: "recipes", count: 1 }] }), bookmark(601, null)];
    api.deleted = [900];
    const sync = new InstapaperSync(api, () => 1_755_000_600_000);
    const changes = await sync.changes("1755000000");
    // A page counts its bookmarks and deletions, as on Android.
    expect(api.calls).toEqual(["since 1755000000 0 500", "since 1755000000 500 500"]);
    // 499 and the one with an address from the second page; the private one without an address is left out.
    expect(changes.entries).toHaveLength(500);
    expect(changes.deleted).toEqual(["900"]);
    expect(changes.entries.at(-1)).toMatchObject({ folder: "Work", tags: ["Recipes"], progress: 0.42, addedAt: 1_755_000_000_000 });
    // The next one starts a little before this one.
    expect(changes.cursor).toBe(String(1_755_000_600 - 300));
  });

  it("adds with tags into a folder (made when missing) or the archive", async () => {
    const api = new FakeApi();
    const sync = new InstapaperSync(api);
    await sync.changes(null);
    api.calls = [];
    const filed = await sync.add("https://c.example/1", "C", ["x"], false, "New");
    expect(api.calls).toEqual(["save https://c.example/1 C", "tags 50 +x -", "folder New", "move 50 7"]);
    expect(filed.folder).toBe("New");
    api.calls = [];
    const archived = await sync.add("https://c.example/2", "https://c.example/2", [], true, null);
    expect(api.calls).toEqual(["save https://c.example/2", "move 50 archive"]);
    expect(archived.archived).toBe(true);
  });

  it("updates only what changed: like, tags by id, one place, title and progress", async () => {
    const api = new FakeApi();
    const sync = new InstapaperSync(api, () => 2_000_000);
    await sync.changes(null);
    api.calls = [];
    const have = remote("5", "https://a/5", { tags: ["Recipes"], folder: null });
    await sync.update(have, { ...have, liked: true, tags: ["new"], folder: "Work", title: "Better", progress: 0.5 });
    expect(api.calls).toEqual(["like 5 true", "tags 5 +new -9", "move 5 3", "update 5 Better 0.5"]);
    api.calls = [];
    await sync.update(have, { ...have, archived: true });
    expect(api.calls).toEqual(["move 5 archive"]);
  });

  it("removing one already gone counts as removed; signed out stops the sync", async () => {
    const api = new FakeApi();
    const sync = new InstapaperSync(api);
    api.removeFails = new InstapaperError("invalid");
    await sync.remove("5");
    api.removeFails = new InstapaperError("auth");
    await expect(sync.remove("5")).rejects.toMatchObject({ problem: "auth" });
  });
});
