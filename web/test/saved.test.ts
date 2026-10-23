import { describe, expect, it } from "vitest";
import { itemOf, keepAsBookmark, setLiked, setTags, type Highlight, type Item } from "../src/lib/items";
import { addFeed, articleKey, articlesFor, emptyLibrary, mergeFeed, newFeed, type Library } from "../src/lib/library";
import {
  archiveItem,
  articleNote,
  cardBody,
  noteOf,
  showsNotes,
  defaultSavedFilter,
  fileItem,
  folderCounts,
  foldersToTags,
  hostOf,
  itemArticle,
  removeFolder,
  isIn,
  likedToReadCount,
  removeSaved,
  removeTag,
  renameFolder,
  saveLink,
  savedFolders,
  savedItems,
  setNote,
  savedStates,
  shownFilter,
  sourceOf,
  tabTags,
  toReadCount,
  toReadTagCounts,
  typeOf,
  type SavedFilter,
} from "../src/lib/saved";

const FEED = "https://site.example/feed";

const withArticle: Library = mergeFeed(
  addFeed(emptyLibrary(), { ...newFeed(FEED, "Site"), siteUrl: "https://www.site.example/" }),
  FEED,
  {
    title: "Site",
    siteUrl: "https://www.site.example/",
    items: [{ id: "7", title: "Feed title", link: "https://www.site.example/7", author: "Ann", html: "<p>The feed's whole text</p>", publishedAt: 1, imageUrl: "https://site.example/7.jpg" }],
  },
  100,
);

const save = (lib: Library, url: string, now: number, folder: string | null = null, tags: string[] = []) => saveLink(lib, url, null, now, folder, "", tags)[0];

/** Three to read (one in Work), one archived, one liked video, one only bookmarked. */
function sample(): Library {
  let lib = save(emptyLibrary(), "https://a.example/1", 1, null, ["ai", "news"]);
  lib = save(lib, "https://a.example/2", 2, "Work", ["ai"]);
  lib = save(lib, "https://a.example/3", 3, "work");
  lib = save(lib, "https://a.example/4", 4, null, ["old"]);
  lib = archiveItem(lib, "a.example/4", true);
  lib = save(lib, "https://youtu.be/x", 5);
  lib = setLiked(lib, "youtu.be/x", true);
  lib = keepAsBookmark(save(lib, "https://b.example/6", 6), "b.example/6", null, 6);
  return { ...lib, items: lib.items.map((i) => (i.key === "b.example/6" ? { ...i, readingList: null } : i)) };
}

const keys = (items: Item[]) => items.map((i) => i.key);
const filter = (change: Partial<SavedFilter>): SavedFilter => ({ ...defaultSavedFilter, ...change });

describe("Saved (#119, #147)", () => {
  it("puts each item in one tab, newest first", () => {
    const lib = sample();
    expect(keys(savedItems(lib, filter({})))).toEqual(["youtu.be/x", "a.example/1"]);
    expect(keys(savedItems(lib, filter({ tab: { kind: "folder", name: "WORK" } })))).toEqual(["a.example/3", "a.example/2"]);
    expect(keys(savedItems(lib, filter({ tab: { kind: "archive" } })))).toEqual(["a.example/4"]);
    expect(keys(savedItems(lib, filter({ tab: { kind: "all" } })))).toEqual(["youtu.be/x", "a.example/4", "a.example/3", "a.example/2", "a.example/1"]);
    expect(toReadCount(lib)).toBe(2);
  });

  it("filters by Liked, every chosen tag and the type", () => {
    const lib = sample();
    const all = { kind: "all" } as const;
    expect(keys(savedItems(lib, filter({ tab: all, likedOnly: true })))).toEqual(["youtu.be/x"]);
    expect(keys(savedItems(lib, filter({ tab: all, tags: ["AI"] })))).toEqual(["a.example/2", "a.example/1"]);
    expect(keys(savedItems(lib, filter({ tab: all, tags: ["ai", "news"] })))).toEqual(["a.example/1"]);
    expect(keys(savedItems(lib, filter({ tab: all, type: "video" })))).toEqual(["youtu.be/x"]);
    expect(savedItems(lib, filter({ tab: all, type: "audio" }))).toEqual([]);
  });

  it("lists folders in use and the tab's tags, most used first", () => {
    const lib = sample();
    expect(savedFolders(lib)).toEqual(["Work"]);
    expect([...folderCounts(lib)]).toEqual([["Work", 2]]);
    expect(tabTags(lib, { kind: "all" })).toEqual(["ai", "news", "old"]);
    expect(tabTags(lib, { kind: "toread" })).toEqual(["ai", "news"]);
  });

  it("drops a folder or tags that are gone from the filter", () => {
    const lib = sample();
    expect(shownFilter(lib, filter({ tab: { kind: "folder", name: "Gone" } })).tab).toEqual({ kind: "toread" });
    expect(shownFilter(lib, filter({ tags: ["news", "gone"] })).tags).toEqual(["news"]);
    const same = filter({ tags: ["news"] });
    expect(shownFilter(lib, same)).toBe(same);
  });

  it("keeps a tag chosen in another state, and counts what is still to read per tag (#205)", () => {
    const lib = sample();
    // "old" is only archived: To read with it shows nothing, but the tag stays chosen.
    const toReadOld = filter({ tags: ["old"] });
    expect(shownFilter(lib, toReadOld)).toBe(toReadOld);
    expect(savedItems(lib, toReadOld)).toEqual([]);
    expect(keys(savedItems(lib, { ...toReadOld, tab: { kind: "archive" } }))).toEqual(["a.example/4"]);
    expect(shownFilter(lib, filter({ tab: { kind: "archive" } })).tab).toEqual({ kind: "archive" });
    expect(savedStates.map((s) => s.kind)).toEqual(["toread", "archive", "all"]);
    // The one in Work isn't in To read; the archived one isn't either.
    expect([...toReadTagCounts(lib)]).toEqual([["ai", 1], ["news", 1], ["old", 0]]);
    // The liked video is in To read (#206).
    expect(likedToReadCount(lib)).toBe(1);
    expect(likedToReadCount(setLiked(lib, "youtu.be/x", false))).toBe(0);
  });

  it("saves a feed article as its own item, again to the top, into a folder with a note and tags", () => {
    const [lib, item] = saveLink(withArticle, "http://site.example/7#top", null, 1_000, " Reading ", "Mine", ["a"]);
    expect(item).toMatchObject({ key: "site.example/7", title: "Feed title", articleId: "7", folder: "Reading", note: "Mine", tags: ["a"] });
    const [again, second] = saveLink(lib, "https://www.site.example/7/", "Better", 2_000, null, "", ["b"]);
    expect(again.items).toHaveLength(1);
    expect(second).toMatchObject({ title: "Better", folder: null, note: "Mine", tags: ["a", "b"], readingList: { state: "toread", addedAt: 2_000 } });
    expect(saveLink(withArticle, "javascript:alert(1)", null, 1)[1]).toBeNull();
  });

  it("files, archives (out of its folder), brings back and removes", () => {
    let lib = sample();
    lib = fileItem(lib, "a.example/4", "Later");
    expect(itemOf(lib, "a.example/4")).toMatchObject({ folder: "Later", readingList: { state: "toread" } });
    lib = archiveItem(lib, "a.example/2", true);
    expect(itemOf(lib, "a.example/2")).toMatchObject({ folder: null, readingList: { state: "archived" } });
    lib = archiveItem(lib, "a.example/2", false);
    expect(itemOf(lib, "a.example/2")?.readingList?.state).toBe("toread");
    lib = removeSaved(lib, "a.example/2");
    expect(itemOf(lib, "a.example/2")).toBeUndefined();
    expect(fileItem(lib, "b.example/6", "Work")).toBe(lib);
  });

  it("renames and removes folders and tags", () => {
    let lib = renameFolder(sample(), "WORK", "Job");
    expect(savedFolders(lib)).toEqual(["Job"]);
    const toRead = toReadCount(lib);
    lib = removeFolder(lib, "job");
    expect(savedFolders(lib)).toEqual([]);
    // The folder's articles are archived, not back in To read.
    expect(toReadCount(lib)).toBe(toRead);
    expect(isIn(itemOf(lib, "a.example/2")!, { kind: "archive" })).toBe(true);
    expect(isIn(itemOf(lib, "a.example/3")!, { kind: "archive" })).toBe(true);
    lib = removeTag(lib, "AI");
    expect(tabTags(lib, { kind: "all" })).toEqual(["news", "old"]);
  });

  it("keeps a note, trimmed", () => {
    const lib = setNote(sample(), "a.example/1", "  A thought  ");
    expect(itemOf(lib, "a.example/1")?.note).toBe("A thought");
  });

  it("knows videos and audio from their links", () => {
    expect(typeOf("https://www.youtube.com/watch?v=1")).toBe("video");
    expect(typeOf("https://m.youtube.com/shorts/1")).toBe("video");
    expect(typeOf("https://youtube.com/@channel")).toBe("article");
    expect(typeOf("https://open.spotify.com/episode/1")).toBe("audio");
    expect(typeOf("https://overcast.fm/+abc")).toBe("audio");
    expect(typeOf("not a link")).toBe("article");
  });

  it("shows on a card the note, else the first highlight, else the description (#133)", () => {
    const highlight: Highlight = { id: "h", quote: "The author's words.", prefix: "", suffix: "", occurrence: 0, note: "", createdAt: 1 };
    const item = { ...saveLink(emptyLibrary(), "https://a.example/1", "A", 1)[1]!, description: "The site's summary." };
    expect(cardBody({ ...item, note: " Mine. ", highlights: [highlight] })).toEqual({ kind: "note", text: "Mine." });
    expect(cardBody({ ...item, highlights: [{ ...highlight, quote: " " }, { ...highlight, quote: "Later words." }] })).toEqual({ kind: "quote", text: "Later words." });
    expect(cardBody(item)).toEqual({ kind: "description", text: "The site's summary." });
    expect(cardBody({ ...item, description: "" })).toBeNull();
  });

  it("shows an item like a feed article, with the feed's text while the library has it", () => {
    const [lib, item] = saveLink(withArticle, "https://site.example/7", null, 1_000);
    const a = itemArticle(item!, lib);
    expect(articleKey(a)).toBe("item site.example/7");
    expect(a).toMatchObject({ html: "<p>The feed's whole text</p>", imageUrl: "https://site.example/7.jpg", author: "Ann", read: false });
    expect(sourceOf(item!, lib)?.url).toBe(FEED);
    // Saved items aren't feed articles.
    expect(articlesFor(lib, { kind: "saved" }, false)).toEqual([]);
  });

  it("otherwise keeps its description, under its website's feed or its favicon", () => {
    const [lib, other] = saveLink(withArticle, "https://www.site.example/8", null, 1);
    const described = { ...other!, description: "A <short> summary" };
    expect(itemArticle(described, lib).html).toBe("<p>A &lt;short&gt; summary</p>");
    expect(sourceOf(described, lib)?.url).toBe(FEED);
    const [, elsewhere] = saveLink(lib, "https://www.elsewhere.example/a", null, 1);
    expect(sourceOf(elsewhere!, lib)).toMatchObject({ title: "elsewhere.example", iconUrl: "https://www.elsewhere.example/favicon.ico" });
    expect(hostOf("https://www.site.example/7")).toBe("site.example");
  });

  it("tags with setTags, shared by name", () => {
    const lib = setTags(sample(), "a.example/3", ["Work", "work", " news "]);
    expect(itemOf(lib, "a.example/3")?.tags).toEqual(["Work", "news"]);
  });
});

describe("note cards (views)", () => {
  it("shows only To read as rows; folders and the Archive as notes", () => {
    expect(showsNotes({ kind: "toread" })).toBe(true);
    expect(showsNotes({ kind: "folder", name: "Work" })).toBe(true);
    expect(showsNotes({ kind: "archive" })).toBe(true);
  });

  it("makes a note card of a feed article and of a saved item", () => {
    const article = { feedUrl: "f", id: "1", title: "B", link: "https://www.b.example/x", author: null, html: "<p>Start of <b>the</b> text.</p>", publishedAt: 1, fetchedAt: 1, read: false, imageUrl: null };
    expect(articleNote(article)).toEqual({ title: "B", imageUrl: null, body: { kind: "description", text: "Start of the text." }, host: "b.example", tags: [], highlights: 0, liked: false });
    expect(articleNote({ ...article, html: "", link: null }).body).toBeNull();
    const item = { ...saveLink(emptyLibrary(), "https://www.a.example/1", "A", 1)[1]!, note: "Mine.", tags: ["ai"], liked: true };
    expect(noteOf(item)).toMatchObject({ title: "A", body: { kind: "note", text: "Mine." }, host: "a.example", tags: ["ai"], highlights: 0, liked: true });
  });
});

describe("folders become tags (#187)", () => {
  it("moves each folder into the item's tags, once, case ignored", () => {
    let lib = save(emptyLibrary(), "https://a.example/1", 1, "Work", ["ai"]);
    lib = save(lib, "https://a.example/2", 2, "work", ["Work"]);
    const moved = foldersToTags(lib);
    expect(itemOf(moved, "a.example/1")).toMatchObject({ folder: null, tags: ["ai", "Work"] });
    expect(itemOf(moved, "a.example/2")).toMatchObject({ folder: null, tags: ["Work"] });
    expect(savedFolders(moved)).toEqual([]);
    expect(foldersToTags(moved)).toBe(moved);
  });
});
