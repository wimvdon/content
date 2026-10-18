import { describe, expect, it } from "vitest";
import { emptyLibrary, mergeFeed, addFeed, newFeed, type Library } from "../src/lib/library";
import * as S from "../src/lib/sync";
import * as SS from "../src/lib/syncSettings";
import { defaultSettings } from "../src/lib/app";
import { defaultReaderStyle } from "../src/lib/readerStyle";

const feed = (url: string, title = url, folderId: string | null = null): S.SyncFeed => ({ url, title, renamed: false, folderId, siteUrl: null, iconUrl: null });

const base: S.SyncDocument = {
  folders: [
    { id: "f1", title: "News", icon: "folder" },
    { id: "f2", title: "Tech", icon: "folder" },
  ],
  feeds: [feed("a", "a", "f1"), feed("b"), feed("c")],
  read: { "a 1": 10 },
  settings: {},
};

describe("sync file", () => {
  it("reads the Android app's file and writes the same text", () => {
    // Written by the Android app's SyncDocument.encode.
    const android =
      '{"format":"rss-reader-sync","version":2,"folders":[{"id":"f1","title":"News","icon":"newspaper"}],' +
      '"feeds":[{"url":"https://a.example/feed","title":"A","renamed":true,"folderId":"f1","siteUrl":"https://a.example","iconUrl":null}],' +
      '"read":{"https://a.example/feed 1":100,"https://a.example/feed 2":200},"settings":{"reader.textSize":"20","theme":"dark"}}';
    const doc = S.decode(android)!;
    expect(doc.feeds[0].renamed).toBe(true);
    expect(doc.read["https://a.example/feed 2"]).toBe(200);
    expect(S.encode(doc)).toBe(android);
  });

  it("leaves other or newer files alone and skips broken entries", () => {
    expect(S.decode("nope")).toBeNull();
    expect(S.decode('{"format":"rss-reader-sync","version":3}')).toBeNull();
    const doc = S.decode('{"format":"rss-reader-sync","version":1,"folders":[{"id":"","title":"x"}],"feeds":[{"url":"a","folderId":"gone"},{}],"read":{"a 1":5,"a 2":"x"}}')!;
    expect(doc.folders).toEqual([]);
    expect(doc.feeds).toEqual([feed("a")]);
    expect(doc.read).toEqual({ "a 1": 5 });
  });

  it("prunes old read marks and those of removed feeds", () => {
    expect(S.pruned({ ...base, read: { "a 1": 10, "b 1": 300, "gone 1": 500 } }, 100).read).toEqual({ "b 1": 300 });
  });
});

describe("merge", () => {
  it("keeps changes from either side", () => {
    const local = { ...base, feeds: base.feeds.map((f) => (f.url === "a" ? { ...f, title: "A!", renamed: true } : f)) };
    const remote = { ...base, feeds: [...base.feeds, feed("d")], read: { ...base.read, "b 1": 20 } };
    const merged = S.merge(base, local, remote);
    expect(merged.feeds.map((f) => f.url)).toEqual(["a", "b", "c", "d"]);
    expect(merged.feeds[0].title).toBe("A!");
    expect(Object.keys(merged.read).sort()).toEqual(["a 1", "b 1"]);
  });

  it("keeps removals, and feeds of a removed folder go to no folder", () => {
    const local = { ...base, feeds: base.feeds.filter((f) => f.url !== "b") };
    const remote = { ...base, folders: base.folders.filter((f) => f.id !== "f1"), read: {} };
    const merged = S.merge(base, local, remote);
    expect(merged.feeds.map((f) => f.url)).toEqual(["a", "c"]);
    expect(merged.feeds[0].folderId).toBeNull();
    expect(merged.read).toEqual({});
  });

  it("lets this device win, but an edit beats a removal", () => {
    const local = { ...base, feeds: base.feeds.map((f) => (f.url === "a" ? { ...f, title: "Local" } : f)).filter((f) => f.url !== "c") };
    const remote = { ...base, feeds: base.feeds.map((f) => (f.url !== "b" ? { ...f, title: "Remote" } : f)) };
    const merged = S.merge(base, local, remote);
    expect(merged.feeds.find((f) => f.url === "a")!.title).toBe("Local");
    expect(merged.feeds.find((f) => f.url === "c")!.title).toBe("Remote");
  });

  it("takes the order from the side that reordered", () => {
    const local = { ...base, feeds: [...base.feeds].reverse() };
    const remote = { ...base, feeds: [...base.feeds, feed("d")] };
    expect(S.merge(base, local, remote).feeds.map((f) => f.url)).toEqual(["c", "b", "a", "d"]);
    expect(S.merge(base, base, remote).feeds.map((f) => f.url)).toEqual(["a", "b", "c", "d"]);
  });

  it("combines both sides on a first sync", () => {
    const local = { ...S.emptyDocument(), feeds: [feed("a"), feed("x")], read: { "x 1": 1 } };
    const remote = { ...S.emptyDocument(), feeds: [feed("a", "Remote"), feed("y")], read: { "y 1": 2 } };
    const merged = S.merge(null, local, remote);
    expect(merged.feeds.map((f) => f.url)).toEqual(["a", "y", "x"]);
    expect(merged.feeds[0].title).toBe("a");
    expect(Object.keys(merged.read).sort()).toEqual(["x 1", "y 1"]);
  });
});

describe("library", () => {
  const parsed = (ids: string[]) => ({ title: "Feed", siteUrl: null, items: ids.map((id, i) => ({ id, title: id, link: null, author: null, html: "", publishedAt: 100 + i, imageUrl: null })) });
  const lib: Library = mergeFeed(addFeed(emptyLibrary(), newFeed("a", "a")), "a", parsed(["1", "2"]), 1000);

  it("articles this device doesn't have count as unchanged, and arrive read", () => {
    const local = S.localDocument(lib, { ...base, read: { "a 9": 5 } });
    expect(local.read).toEqual({ "a 9": 5 });
    const later = mergeFeed(lib, "a", parsed(["1", "2", "9"]), 2000, new Set(["a 9"]));
    expect(later.articles.find((x) => x.id === "9")!.read).toBe(true);
  });

  it("applies the merged document and syncing twice changes nothing", () => {
    const merged: S.SyncDocument = { folders: [], feeds: [feed("a", "Feed"), feed("new")], read: { "a 2": 101 }, settings: {} };
    const applied = S.apply(lib, merged);
    expect(applied.feeds.map((f) => f.url)).toEqual(["a", "new"]);
    expect(applied.articles.find((x) => x.id === "2")!.read).toBe(true);
    expect(S.hasNewFeeds(lib, merged)).toBe(true);
    const doc = S.localDocument(applied, null);
    expect(S.apply(applied, S.merge(doc, doc, doc))).toEqual(applied);
  });
});

describe("sync timing", () => {
  it("syncs when never synced, or when the last sync is more than a few seconds ago", () => {
    const now = 1_000_000;
    expect(S.syncDue(null, now)).toBe(true);
    expect(S.syncDue(now, now)).toBe(false);
    expect(S.syncDue(now - S.MIN_GAP_MS + 1, now)).toBe(false);
    expect(S.syncDue(now - S.MIN_GAP_MS, now)).toBe(true);
    expect(S.syncDue(now - S.VISIBLE_INTERVAL_MS, now)).toBe(true);
    expect(S.syncDue(now + 5_000, now)).toBe(true);
  });
});

describe("synced settings", () => {
  const settings = {
    ...defaultSettings,
    theme: "dark" as const,
    fullArticles: false,
    showImages: false,
    listView: "large" as const,
    readerStyle: { ...defaultReaderStyle, font: "sans" as const, textSize: 22 },
  };

  it("go into the file and back, and files without them still read", () => {
    const doc = { ...S.emptyDocument(), settings: { theme: "dark", "reader.textSize": "20" } };
    const text = S.encode(doc);
    expect(text).toContain('"version":2');
    expect(S.decode(text)).toEqual(doc);
    expect(S.decode('{"format":"rss-reader-sync","version":1}')!.settings).toEqual({});
    expect(S.decode('{"format":"rss-reader-sync","version":2,"settings":{"theme":"light","bad":5}}')!.settings).toEqual({ theme: "light" });
  });

  it("use the same names and values as the Android app and come back the same", () => {
    const values = SS.syncedSettings(settings);
    expect(values).toMatchObject({ theme: "dark", "reader.font": "sans", "reader.textSize": "22", "reader.background": "default", fullArticles: "false", showImages: "false", listView: "large" });
    expect(SS.applySynced(defaultSettings, values)).toEqual(settings);
  });

  it("keep Android-only names and ignore bad values", () => {
    const values = SS.syncedSettings(settings, { dynamicColor: "true", theme: "light" });
    expect(values.dynamicColor).toBe("true");
    expect(values.theme).toBe("dark");
    const applied = SS.applySynced(settings, { theme: "purple", "reader.textSize": "99", fullArticles: "yes", listView: "huge" });
    expect(applied.listView).toBe(settings.listView);
    expect(applied.theme).toBe("dark");
    expect(applied.readerStyle.textSize).toBe(28);
    expect(applied.fullArticles).toBe(false);
  });

  it("drop the Instapaper sign-in that older versions synced", () => {
    const values = SS.syncedSettings(settings, { "instapaper.token": "t", "instapaper.username": "me" });
    expect(Object.keys(values).some((k) => k.startsWith("instapaper."))).toBe(false);
  });

  it("leave out Unread or All, which is navigation, and drop it from older files", () => {
    const values = SS.syncedSettings({ ...settings, unreadOnly: false }, { unreadOnly: "true" });
    expect("unreadOnly" in values).toBe(false);
    expect(SS.applySynced({ ...settings, unreadOnly: true }, { unreadOnly: "false" }).unreadOnly).toBe(true);
  });

  it("merge per setting; a device without synced settings takes the file's", () => {
    const b = { ...base, settings: { theme: "system", fullArticles: "true", "instapaper.token": "t" } };
    const local = { ...b, settings: { ...b.settings, theme: "dark" } };
    const remote = { ...b, settings: { theme: "light", fullArticles: "false" } };
    expect(S.merge(b, local, remote).settings).toEqual({ theme: "dark", fullArticles: "false" });
    const fresh = { ...base, settings: { theme: "system", listView: "medium" } };
    expect(S.merge(null, fresh, { ...base, settings: { theme: "dark" } }).settings).toEqual({ theme: "dark", listView: "medium" });
  });
});
