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
  settings: {},
};

describe("sync file", () => {
  it("reads the Android app's file and writes the same text", () => {
    // Written by the Android app's SyncDocument.encode.
    const android =
      '{"format":"rss-reader-sync","version":3,"folders":[{"id":"f1","title":"News","icon":"newspaper"}],' +
      '"feeds":[{"url":"https://a.example/feed","title":"A","renamed":true,"folderId":"f1","siteUrl":"https://a.example","iconUrl":null}],' +
      '"settings":{"reader.textSize":"20","theme":"dark"}}';
    const doc = S.decode(android)!;
    expect(doc.feeds[0].renamed).toBe(true);
    expect(S.encode(doc)).toBe(android);
  });

  it("keeps a version 2 file's read marks for per-item sync, and never writes them", () => {
    const old = S.decode('{"format":"rss-reader-sync","version":2,"feeds":[],"read":{"a 1":100}}')!;
    expect(old.legacyRead).toEqual({ "a 1": 100 });
    expect(S.encode(old)).not.toContain('"read"');
    expect(S.decode('{"format":"rss-reader-sync","version":3,"read":{"a 1":100}}')!.legacyRead).toEqual({});
  });

  it("leaves other or newer files alone and skips broken entries", () => {
    expect(S.decode("nope")).toBeNull();
    expect(S.decode('{"format":"rss-reader-sync","version":4}')).toBeNull();
    const doc = S.decode('{"format":"rss-reader-sync","version":1,"folders":[{"id":"","title":"x"}],"feeds":[{"url":"a","folderId":"gone"},{}],"read":{"a 1":5,"a 2":"x"}}')!;
    expect(doc.folders).toEqual([]);
    expect(doc.feeds).toEqual([feed("a")]);
    expect(doc.legacyRead).toEqual({ "a 1": 5 });
  });
});

describe("merge", () => {
  it("keeps changes from either side", () => {
    const local = { ...base, feeds: base.feeds.map((f) => (f.url === "a" ? { ...f, title: "A!", renamed: true } : f)) };
    const remote = { ...base, feeds: [...base.feeds, feed("d")] };
    const merged = S.merge(base, local, remote);
    expect(merged.feeds.map((f) => f.url)).toEqual(["a", "b", "c", "d"]);
    expect(merged.feeds[0].title).toBe("A!");
  });

  it("keeps removals, and feeds of a removed folder go to no folder", () => {
    const local = { ...base, feeds: base.feeds.filter((f) => f.url !== "b") };
    const remote = { ...base, folders: base.folders.filter((f) => f.id !== "f1") };
    const merged = S.merge(base, local, remote);
    expect(merged.feeds.map((f) => f.url)).toEqual(["a", "c"]);
    expect(merged.feeds[0].folderId).toBeNull();
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
    const local = { ...S.emptyDocument(), feeds: [feed("a"), feed("x")] };
    const remote = { ...S.emptyDocument(), feeds: [feed("a", "Remote"), feed("y")] };
    const merged = S.merge(null, local, remote);
    expect(merged.feeds.map((f) => f.url)).toEqual(["a", "y", "x"]);
    expect(merged.feeds[0].title).toBe("a");
  });
});

describe("library", () => {
  const parsed = (ids: string[]) => ({ title: "Feed", siteUrl: null, items: ids.map((id, i) => ({ id, title: id, link: null, author: null, html: "", publishedAt: 100 + i, imageUrl: null })) });
  const lib: Library = mergeFeed(addFeed(emptyLibrary(), newFeed("a", "a")), "a", parsed(["1", "2"]), 1000);

  it("articles read elsewhere arrive read, and a version 2 file's marks are kept for the missing ones", () => {
    const old = S.decode('{"format":"rss-reader-sync","version":2,"feeds":[{"url":"a"}],"read":{"a 1":100,"a 9":5}}')!;
    const applied = S.apply(lib, S.merge(null, S.localDocument(lib), old));
    expect(applied.readElsewhere).toEqual({ "a 9": 5 });
    const later = mergeFeed(applied, "a", parsed(["1", "2", "9"]), 2000);
    expect(later.articles.find((x) => x.id === "9")!.read).toBe(true);
    expect(later.readElsewhere).toEqual({});
  });

  it("applies the merged document and syncing twice changes nothing", () => {
    const merged: S.SyncDocument = { folders: [], feeds: [feed("a", "Feed"), feed("new")], settings: {} };
    const applied = S.apply(lib, merged);
    expect(applied.feeds.map((f) => f.url)).toEqual(["a", "new"]);
    expect(S.hasNewFeeds(lib, merged)).toBe(true);
    const doc = S.localDocument(applied);
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
  it("carry what each service syncs and what was imported, in the Android app's text", () => {
    const record = { at: 5, count: 12, folders: ["Research", ""], archive: false };
    const mine = { ...settings, scopes: { raindrop: { folders: ["Research"] } }, imports: { instapaper: record } };
    const values = SS.syncedSettings(mine);
    expect(values["services.raindrop.scope"]).toBe('["Research"]');
    expect(values["services.instapaper.scope"]).toBe("*");
    expect(values["services.instapaper.imported"]).toBe('{"at":5,"count":12,"folders":["","Research"],"archive":false}');
    const applied = SS.applySynced(defaultSettings, values);
    expect(applied.scopes.raindrop).toEqual({ folders: ["Research"] });
    expect(applied.imports.instapaper).toEqual({ ...record, folders: ["", "Research"] });
    // An import this browser doesn't know of stays as the file has it.
    expect(SS.syncedSettings(settings, { "services.raindrop.imported": values["services.instapaper.imported"] })["services.raindrop.imported"]).toBe(values["services.instapaper.imported"]);
    expect(SS.applySynced(defaultSettings, { "services.raindrop.scope": "{", "services.raindrop.imported": "x" })).toEqual(defaultSettings);
  });

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
    expect(text).toContain('"version":3');
    expect(S.decode(text)).toEqual({ ...doc, legacyRead: {} });
    expect(S.decode('{"format":"rss-reader-sync","version":1}')!.settings).toEqual({});
    expect(S.decode('{"format":"rss-reader-sync","version":2,"settings":{"theme":"light","bad":5}}')!.settings).toEqual({ theme: "light" });
  });

  it("use the same names and values as the Android app and come back the same", () => {
    const values = SS.syncedSettings({ ...settings, markReadOnScroll: true });
    expect(values).toEqual({ fullArticles: "false", markReadOnScroll: "true", "services.instapaper.scope": "*", "services.raindrop.scope": "*" });
    const applied = SS.applySynced(defaultSettings, values);
    expect(applied.fullArticles).toBe(false);
    expect(applied.markReadOnScroll).toBe(true);
  });

  it("keep view settings in each browser, and drop the ones older versions synced", () => {
    const values = SS.syncedSettings(settings, { theme: "light", listView: "small", "reader.font": "serif", "web.only": "x" });
    expect(Object.keys(values).some((k) => SS.DROPPED_VIEW.has(k))).toBe(false);
    expect(values["web.only"]).toBe("x");
    const fromOlder = { theme: "light", "reader.font": "serif", "reader.textSize": "16", "reader.background": "sepia", showImages: "true", listView: "small" };
    expect(SS.applySynced(settings, fromOlder)).toEqual(settings);
  });

  it("keep names the web app doesn't know and ignore bad values", () => {
    const values = SS.syncedSettings(settings, { "android.only": "true", fullArticles: "true" });
    expect(values["android.only"]).toBe("true");
    expect(values.fullArticles).toBe("false");
    const applied = SS.applySynced(settings, { fullArticles: "yes", markReadOnScroll: "1" });
    expect(applied.fullArticles).toBe(false);
    expect(applied.markReadOnScroll).toBe(settings.markReadOnScroll);
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
