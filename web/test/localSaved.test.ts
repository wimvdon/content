import { describe, expect, it } from "vitest";
import { InstapaperError, type Bookmark } from "../src/lib/instapaper";
import { emptyLocalSaved, LocalSaved, localSavedOf, type LocalSavedData, type SavedService } from "../src/lib/localSaved";

function memory(start: LocalSavedData = emptyLocalSaved) {
  const box = { data: start };
  return { box, storage: { read: async () => box.data, write: async (data: LocalSavedData) => void (box.data = data) } };
}

/** Saved in memory, with a clock that moves a second on every reading. */
function newSaved() {
  const { box, storage } = memory();
  let now = 1_000_000;
  return { box, saved: new LocalSaved(storage, () => (now += 1000)) };
}

/** The Instapaper account for the copy: another LocalSaved, which stops answering after `answers` saves. */
class FakeAccount extends LocalSaved implements SavedService {
  answers = Infinity;
  titles: [string, string | undefined][] = [];

  constructor() {
    let now = 0;
    super(memory().storage, () => (now += 1000));
  }

  save(url: string, title?: string): Promise<Bookmark> {
    if (this.answers-- <= 0) return Promise.reject(new InstapaperError("rateLimit"));
    this.titles.push([url, title]);
    return super.save(url, title);
  }
}

const ids = (bookmarks: Bookmark[]) => bookmarks.map((b) => b.id);

describe("Saved in this browser", () => {
  it("shows the newest first, and saving an address again brings it back to the top", async () => {
    const { saved } = newSaved();
    const a = await saved.save("https://a.example/1", "One");
    const b = await saved.save("https://a.example/2");
    expect(b.title).toBe("https://a.example/2");
    expect(ids((await saved.list({ kind: "home" })).bookmarks)).toEqual([b.id, a.id]);
    await saved.move(a.id, "archive");
    const again = await saved.save("https://a.example/1");
    expect(again.id).toBe(a.id);
    expect(again.title).toBe("One");
    expect(again.archived).toBe(false);
    expect(ids((await saved.list({ kind: "home" })).bookmarks)).toEqual([a.id, b.id]);
  });

  it("likes, archives and deletes", async () => {
    const { saved } = newSaved();
    const a = await saved.save("https://a.example/1");
    expect((await saved.like(a.id, true)).liked).toBe(true);
    await saved.move(a.id, "archive");
    expect((await saved.list({ kind: "home" })).total).toBe(0);
    expect(ids((await saved.list({ kind: "archive" })).bookmarks)).toEqual([a.id]);
    expect(ids((await saved.list({ kind: "liked" })).bookmarks)).toEqual([a.id]);
    await saved.remove(a.id);
    expect((await saved.list({ kind: "liked" })).total).toBe(0);
  });

  it("has no folders", async () => {
    const { saved } = newSaved();
    const a = await saved.save("https://a.example/1");
    expect(await saved.folders()).toEqual([]);
    await expect(saved.move(a.id, 12)).rejects.toThrow(InstapaperError);
    await expect(saved.createFolder()).rejects.toThrow(InstapaperError);
  });

  it("shares tags by name, counts them, and drops them when no article has them", async () => {
    const { saved } = newSaved();
    const a = await saved.save("https://a.example/1");
    const b = await saved.save("https://a.example/2");
    const tagsA = await saved.setTags(a.id, ["Travel", "cooking"], []);
    expect(tagsA.map((t) => t.name)).toEqual(["Travel", "cooking"]);
    const tagsB = await saved.setTags(b.id, ["Cooking"], []);
    expect(tagsB[0].id).toBe(tagsA[1].id);
    expect((await saved.tags()).map((t) => [t.name, t.count])).toEqual([["cooking", 2], ["Travel", 1]]);
    expect((await saved.list({ kind: "tag", name: "cooking" })).total).toBe(2);
    await saved.setTags(a.id, [], [tagsA[0].id]);
    expect((await saved.tags()).map((t) => t.name)).toEqual(["cooking"]);
  });

  it("renames a tag, joining it with a tag of that name", async () => {
    const { saved } = newSaved();
    const a = await saved.save("https://a.example/1");
    const b = await saved.save("https://a.example/2");
    const [travel] = await saved.setTags(a.id, ["Travel"], []);
    const [trips] = await saved.setTags(b.id, ["Trips"], []);
    expect((await saved.renameTag(travel.id, "Journeys")).name).toBe("Journeys");
    expect((await saved.renameTag(trips.id, "journeys")).id).toBe(travel.id);
    expect((await saved.tags()).map((t) => t.name)).toEqual(["Journeys"]);
    expect((await saved.list({ kind: "tag", name: "Journeys" })).total).toBe(2);
  });

  it("keeps changes made at the same time", async () => {
    const { saved } = newSaved();
    await Promise.all([saved.save("https://a.example/1"), saved.save("https://a.example/2"), saved.save("https://a.example/3")]);
    expect((await saved.list({ kind: "home" })).total).toBe(3);
  });

  it("reads damaged storage as empty, and never repeats an id", () => {
    expect(localSavedOf(undefined)).toEqual(emptyLocalSaved);
    expect(localSavedOf("nonsense")).toEqual(emptyLocalSaved);
    const data = localSavedOf({ articles: [{ id: 7, url: "https://a.example/", title: "", time: 1, liked: false, archived: false, tagIds: [] }], tags: [] });
    expect(data.nextId).toBe(8);
  });
});

describe("copying to Instapaper after signing in", () => {
  it("copies every article with its like, archive and tags, newest on top, and keeps none here", async () => {
    const { box, saved } = newSaved();
    const a = await saved.save("https://a.example/1", "One");
    const b = await saved.save("https://a.example/2");
    await saved.setTags(a.id, ["Travel"], []);
    await saved.like(a.id, true);
    await saved.move(b.id, "archive");
    const account = new FakeAccount();
    expect(await saved.uploadTo(account)).toEqual({ copied: 2, problem: null });
    expect(box.data.articles).toEqual([]);
    expect(box.data.tags).toEqual([]);
    // An article without a title gets the one Instapaper finds, not its address.
    expect(account.titles).toEqual([["https://a.example/1", "One"], ["https://a.example/2", undefined]]);
    const [one] = (await account.list({ kind: "liked" })).bookmarks;
    expect(one.url).toBe("https://a.example/1");
    expect(one.tags.map((t) => t.name)).toEqual(["Travel"]);
    expect(one.archived).toBe(false);
    expect((await account.list({ kind: "archive" })).bookmarks.map((x) => x.url)).toEqual(["https://a.example/2"]);
  });

  it("keeps what didn't make it and goes on the next time", async () => {
    const { box, saved } = newSaved();
    for (const n of [1, 2, 3]) await saved.save(`https://a.example/${n}`);
    const account = new FakeAccount();
    account.answers = 1;
    expect(await saved.uploadTo(account)).toEqual({ copied: 1, problem: "rateLimit" });
    expect(box.data.articles.map((x) => x.url)).toEqual(["https://a.example/2", "https://a.example/3"]);
    account.answers = Infinity;
    expect(await saved.uploadTo(account)).toEqual({ copied: 2, problem: null });
    expect((await account.list({ kind: "home" })).bookmarks.map((x) => x.url)).toEqual(["https://a.example/3", "https://a.example/2", "https://a.example/1"]);
    expect(await saved.uploadTo(account)).toEqual({ copied: 0, problem: null });
  });
});
