import { describe, expect, it } from "vitest";
import { InstapaperError, type Bookmark, type ChangesPage, type Folder, type Tag } from "../src/lib/instapaper";
import { InstapaperSync } from "../src/lib/instapaperSync";
import { addToReadingList, changeItem, itemOf, looseKeyOfLink, setLiked, setReadingState, setTags } from "../src/lib/items";
import { emptyLibrary, type Library } from "../src/lib/library";
import { covers, EVERYTHING, TO_READ, withFolder, type ServiceScope } from "../src/lib/serviceScope";
import { emptyServiceSyncState, importService, ServiceSyncError, syncService, type RemoteChanges, type RemoteEntry, type SavedSyncService, type ServiceSyncState } from "../src/lib/serviceSync";

/** A service in memory: what it has, a cursor per change, and calls that can fail. */
class FakeService implements SavedSyncService {
  readonly progress = true;
  content = false;
  /** The article text each add brought, by address (#247). */
  contents = new Map<string, string | null>();
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

  async add(url: string, title: string, tags: string[], archived: boolean, folder: string | null, content: string | null = null): Promise<RemoteEntry> {
    this.call(`add ${url}`);
    this.contents.set(url, content);
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

  async folderNames(): Promise<string[]> {
    return [...new Set([...this.entries.values()].flatMap((e) => (e.folder ? [e.folder] : [])))];
  }
}

function remote(id: string, url: string, patch: Partial<RemoteEntry> = {}): RemoteEntry {
  return { remoteId: id, url, title: `Title ${id}`, description: "Desc", imageUrl: null, addedAt: 1_000, archived: false, liked: false, tags: [], folder: null, progress: 0, ...patch };
}

/** Connecting imports everything once, as the app does; then each sync keeps the scope in step. */
function setup() {
  const box = { lib: emptyLibrary() as Library, state: emptyServiceSyncState as ServiceSyncState };
  const service = new FakeService();
  const update = (change: (lib: Library) => Library) => (box.lib = change(box.lib));
  const sync = async (scope: ServiceScope = EVERYTHING, editedAt: Record<string, number> = {}) => {
    if (box.state.cursor === null && Object.keys(box.state.rows).length === 0) {
      const imported = await importService(service, box.state, { folders: null, archive: true }, update, 5_000);
      box.state = imported.state;
      if (imported.problem) return imported.problem;
    }
    const result = await syncService(service, box.state, update, 5_000, scope, editedAt);
    box.state = result.state;
    return result.problem;
  };
  return { box, service, sync, update };
}

describe("Saved and a service (serviceSync.ts)", () => {
  it("connecting imports the whole account, with folders, likes and tags", async () => {
    const { box, service, sync } = setup();
    service.put(remote("1", "https://a.example/1", { folder: "Work", tags: ["ai"] }));
    service.put(remote("2", "https://a.example/2", { archived: true, liked: true }));
    expect(await sync()).toBeNull();
    expect(itemOf(box.lib, "a.example/1")).toMatchObject({ folder: "Work", tags: ["ai"], readingList: { state: "toread" } });
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
    service.calls = [];
    await sync();
    expect(service.calls).toEqual(["changes"]);
  });

  it("sends a new article with Content's text to a service that takes it, only when saved recently (#247)", async () => {
    const day = 24 * 60 * 60 * 1000;
    const now = 30 * day;
    const service = new FakeService();
    service.content = true;
    let lib = addToReadingList(emptyLibrary(), "https://b.example/new", "New", now - day)[0];
    lib = addToReadingList(lib, "https://b.example/old", "Old", now - 8 * day)[0];
    const asked: string[] = [];
    const textOf = async (item: { key: string; title: string }) => {
      asked.push(item.key);
      return `<p>${item.title}</p>`;
    };
    await syncService(service, emptyServiceSyncState, (change) => (lib = change(lib)), now, EVERYTHING, {}, new Set(), textOf);
    expect(service.contents.get("https://b.example/new")).toBe("<p>New</p>");
    // Saved more than a week ago (connecting brings the backlog): the service fetches the page itself.
    expect(service.contents.get("https://b.example/old")).toBeNull();
    expect(asked).toEqual(["b.example/new"]);
    // No text to be had (offline): added all the same; a service without the feature isn't asked.
    const failing = new FakeService();
    failing.content = true;
    expect((await syncService(failing, emptyServiceSyncState, (change) => (lib = change(lib)), now, EVERYTHING, {}, new Set(), () => Promise.reject(new Error("offline")))).problem).toBeNull();
    expect(failing.contents.get("https://b.example/new")).toBeNull();
    const plain = new FakeService();
    await syncService(plain, emptyServiceSyncState, (change) => (lib = change(lib)), now, EVERYTHING, {}, new Set(), () => Promise.reject(new Error("not asked")));
    expect(plain.contents.has("https://b.example/new")).toBe(true);
  });

  it("merges three ways: a change on each side to other fields both stay; the same field, the later change", async () => {
    const { box, service, sync } = setup();
    service.put(remote("1", "https://a.example/1"));
    await sync();
    box.lib = setLiked(box.lib, "a.example/1", true);
    service.put({ ...service.entries.get("1")!, archived: true, title: "There" });
    await sync();
    expect(itemOf(box.lib, "a.example/1")).toMatchObject({ liked: true, title: "There", readingList: { state: "archived" } });
    expect(service.entries.get("1")).toMatchObject({ liked: true, archived: true });
    // Both changed the title; without a time from the service, Reader's stays.
    box.lib = changeItem(box.lib, "a.example/1", (i) => ({ ...i, title: "Here" }));
    service.put({ ...service.entries.get("1")!, title: "Elsewhere" });
    await sync(EVERYTHING, { "a.example/1": 100 });
    expect(itemOf(box.lib, "a.example/1")?.title).toBe("Here");
    // Changed there later than here: the service's wins.
    box.lib = changeItem(box.lib, "a.example/1", (i) => ({ ...i, title: "Mine" }));
    service.put({ ...service.entries.get("1")!, title: "Theirs", changedAt: 300 });
    await sync(EVERYTHING, { "a.example/1": 200 });
    expect(itemOf(box.lib, "a.example/1")?.title).toBe("Theirs");
    expect(service.entries.get("1")?.title).toBe("Theirs");
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

  it("keeps the scope in step: a folder leaving it is archived there, never deleted", async () => {
    const { box, service, sync } = setup();
    const work: ServiceScope = { folders: [TO_READ, "Work"] };
    box.lib = changeItem(addToReadingList(box.lib, "https://b.example/1", "1", 10)[0], "b.example/1", (i) => ({ ...i, folder: "Work" }));
    box.lib = addToReadingList(box.lib, "https://b.example/2", "2", 11)[0];
    await sync(work);
    const id = box.state.rows["b.example/1"].remoteId;
    expect(service.entries.get(id)?.folder).toBe("Work");
    // Work unticked: archived there, still in Reader in its folder.
    await sync(withFolder(work, "Work", false));
    expect(service.entries.get(id)?.archived).toBe(true);
    expect(box.state.rows["b.example/1"].linked).toBe(false);
    expect(itemOf(box.lib, "b.example/1")?.folder).toBe("Work");
    // A folder that isn't in the scope never goes there.
    box.lib = changeItem(box.lib, "b.example/2", (i) => ({ ...i, folder: "Later" }));
    await sync(withFolder(work, "Work", false));
    expect(service.entries.get(box.state.rows["b.example/2"].remoteId)?.archived).toBe(true);
    // Back in the scope: in step again, out of the archive there.
    await sync(work);
    expect(service.entries.get(id)?.archived).toBe(false);
    expect(covers(work, itemOf(box.lib, "b.example/1")!)).toBe(true);
  });

  it("an import lands each article in its place and leaves the rest there until it changes", async () => {
    const { box, service, sync, update } = setup();
    service.put(remote("1", "https://a.example/1"));
    service.put(remote("2", "https://a.example/2", { folder: "Research", liked: true }));
    service.put(remote("3", "https://a.example/3", { archived: true }));
    const result = await importService(service, box.state, { folders: ["Research"], archive: false }, update, 5_000);
    box.state = result.state;
    expect(result.imported).toBe(1);
    expect(itemOf(box.lib, "a.example/2")).toMatchObject({ folder: "Research", liked: true });
    expect(itemOf(box.lib, "a.example/1")).toBeUndefined();
    await sync();
    expect(itemOf(box.lib, "a.example/1")).toBeUndefined();
    expect(service.entries.size).toBe(3);
    service.put({ ...service.entries.get("1")!, liked: true });
    await sync();
    expect(itemOf(box.lib, "a.example/1")?.readingList?.state).toBe("toread");
  });

  it("a failure keeps everything, and the rest goes next time", async () => {
    const { box, service, sync } = setup();
    box.lib = addToReadingList(addToReadingList(box.lib, "https://b.example/1", "1", 10)[0], "https://b.example/2", "2", 11)[0];
    // The import and the sync each ask for the changes, then one article gets through.
    service.failAfter = 3;
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
  saveRefusesContent = false;
  async save(url: string, title?: string, content?: string): Promise<Bookmark> {
    this.calls.push(`save ${url} ${title ?? ""}${content ? ` +${content}` : ""}`.trim());
    if (content && this.saveRefusesContent) throw new InstapaperError("invalid");
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
  it("reads every change since the cursor, page by page, with deletions and progress, and no folders (#187)", async () => {
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
    expect(changes.entries.at(-1)).toMatchObject({ folder: null, tags: ["Recipes"], progress: 0.42, addedAt: 1_755_000_000_000 });
    // The next one starts a little before this one.
    expect(changes.cursor).toBe(String(1_755_000_600 - 300));
  });

  it("adds with tags, or into the archive, never into a folder (#187)", async () => {
    const api = new FakeApi();
    const sync = new InstapaperSync(api);
    await sync.changes(null);
    api.calls = [];
    const filed = await sync.add("https://c.example/1", "C", ["x"], false, "New");
    expect(api.calls).toEqual(["save https://c.example/1 C", "tags 50 +x -"]);
    expect(filed.folder).toBeNull();
    api.calls = [];
    const archived = await sync.add("https://c.example/2", "https://c.example/2", [], true, null);
    expect(api.calls).toEqual(["save https://c.example/2", "move 50 archive"]);
    expect(archived.archived).toBe(true);
  });

  it("adds with Content's text, or the link alone when Instapaper refuses the text (#247)", async () => {
    const api = new FakeApi();
    const sync = new InstapaperSync(api);
    await sync.changes(null);
    api.calls = [];
    await sync.add("https://c.example/1", "C", [], false, null, "<p>Text</p>");
    expect(api.calls).toEqual(["save https://c.example/1 C +<p>Text</p>"]);
    api.calls = [];
    api.saveRefusesContent = true;
    expect((await sync.add("https://c.example/2", "D", [], false, null, "<p>Long</p>")).remoteId).toBe("50");
    expect(api.calls).toEqual(["save https://c.example/2 D +<p>Long</p>", "save https://c.example/2 D"]);
  });

  it("updates only what changed: like, tags by id, the archive, title and progress (never a folder)", async () => {
    const api = new FakeApi();
    const sync = new InstapaperSync(api, () => 2_000_000);
    await sync.changes(null);
    api.calls = [];
    const have = remote("5", "https://a/5", { tags: ["Recipes"], folder: null });
    await sync.update(have, { ...have, liked: true, tags: ["new"], folder: "Work", title: "Better", progress: 0.5 });
    expect(api.calls).toEqual(["like 5 true", "tags 5 +new -9", "update 5 Better 0.5"]);
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

describe("no duplicates from links kept without tracking parameters", () => {
  it("an article another device sent joins the saved one when connecting", async () => {
    const { box, service, sync } = setup();
    box.lib = addToReadingList(box.lib, "https://a.example/1?utm_source=rss&id=7", "One", 10)[0];
    service.put(remote("9", "https://a.example/1?id=7"));
    expect(await sync()).toBeNull();
    expect(box.lib.items.filter((i) => i.readingList)).toHaveLength(1);
    expect(box.state.rows["a.example/1?utm_source=rss&id=7"]?.remoteId).toBe("9");
    expect(service.entries.size).toBe(1);
  });
  it("after connecting too, a new article there finds the saved one", async () => {
    const { box, service, sync } = setup();
    box.state = { ...box.state, cursor: "0" };
    box.lib = addToReadingList(box.lib, "https://www.b.example/post/?utm_medium=feed", "Post", 10)[0];
    service.put(remote("3", "https://b.example/post"));
    expect(await sync()).toBeNull();
    expect(box.lib.items.filter((i) => i.readingList)).toHaveLength(1);
    expect(Object.values(box.state.rows).filter((r) => r.remoteId === "3")).toHaveLength(1);
    expect(service.entries.size).toBe(1);
  });
  it("tracking parameters don't make another article, other parameters do", () => {
    expect(looseKeyOfLink("http://www.a.example/x/?utm_source=rss&id=2&fbclid=z")).toBe(looseKeyOfLink("https://a.example/x?id=2"));
    expect(looseKeyOfLink("https://a.example/x?utm_campaign=y")).toBe("a.example/x");
    expect(looseKeyOfLink("https://a.example/x?id=2")).not.toBe(looseKeyOfLink("https://a.example/x?id=3"));
  });
});
