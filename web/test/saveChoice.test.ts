import { describe, expect, it } from "vitest";
import { itemOf } from "../src/lib/items";
import { emptyLibrary, type Library } from "../src/lib/library";
import { applyChoice, choiceOf, emptyChoice, newFolder, places, saveNew, tagChoices, tagsLabel, undoSave, withTag, withTyped } from "../src/lib/saveChoice";
import { archiveItem, folderOf, isIn, saveLink } from "../src/lib/saved";

function sample(): Library {
  let lib = saveLink(emptyLibrary(), "https://a.example/1", null, 1)[0];
  lib = saveLink(lib, "https://a.example/2", null, 2, "Work")[0];
  lib = saveLink(lib, "https://a.example/3", null, 3)[0];
  return archiveItem(lib, "a.example/3", true);
}

describe("the save and Organise sheet (#143)", () => {
  it("starts where the item is, with what it has", () => {
    const lib = sample();
    expect(choiceOf(itemOf(lib, "a.example/1")!).place).toEqual({ kind: "toread" });
    expect(choiceOf(itemOf(lib, "a.example/2")!).place).toEqual({ kind: "folder", name: "Work" });
    expect(choiceOf(itemOf(lib, "a.example/3")!).place).toEqual({ kind: "archive" });
  });

  it("offers To read and Archive first, then the folders and a folder being made", () => {
    const folders = ["Brussels", "Research"];
    const names = (ps: ReturnType<typeof places>) => ps.map((p) => (p.kind === "folder" ? p.name : p.kind));
    expect(names(places(folders, { kind: "toread" }))).toEqual(["toread", "archive", "Brussels", "Research"]);
    expect(names(places(folders, { kind: "folder", name: "Later" }))).toEqual(["toread", "archive", "Brussels", "Research", "Later"]);
    expect(places(folders, { kind: "folder", name: "research" })).toHaveLength(4);
  });

  it("joins a new folder with the folder of that name, and ignores a blank one", () => {
    expect(newFolder(" research ", ["Research"])).toEqual({ kind: "folder", name: "Research" });
    expect(newFolder("Later ", ["Research"])).toEqual({ kind: "folder", name: "Later" });
    expect(newFolder("  ", [])).toBeNull();
  });

  it("lands a new save in To read, a folder or the Archive, with its like, tags and note", () => {
    const [, a] = saveNew(emptyLibrary(), "https://b.example/1", "B", 10, emptyChoice);
    expect(isIn(a!, { kind: "toread" })).toBe(true);
    const [, b] = saveNew(emptyLibrary(), "https://b.example/1", null, 10, { place: { kind: "folder", name: "Work" }, liked: true, tags: ["x"], note: " A note " });
    expect(b).toMatchObject({ folder: "Work", liked: true, tags: ["x"], note: "A note" });
    const [, c] = saveNew(emptyLibrary(), "https://b.example/1", null, 10, { ...emptyChoice, place: { kind: "archive" } });
    expect(c?.readingList?.state).toBe("archived");
    expect(saveNew(emptyLibrary(), "not a link", null, 10, emptyChoice)[1]).toBeNull();
  });

  it("organises a saved item, and changes nothing when nothing changed", () => {
    const lib = sample();
    expect(applyChoice(lib, "a.example/2", choiceOf(itemOf(lib, "a.example/2")!))).toBe(lib);
    const moved = applyChoice(lib, "a.example/2", { place: { kind: "folder", name: "Later" }, liked: true, tags: ["a", "A", " b "], note: " Mine " });
    expect(itemOf(moved, "a.example/2")).toMatchObject({ folder: "Later", liked: true, tags: ["a", "b"], note: "Mine" });
    const back = applyChoice(lib, "a.example/3", emptyChoice);
    expect(isIn(itemOf(back, "a.example/3")!, { kind: "toread" })).toBe(true);
    const archived = applyChoice(lib, "a.example/2", { ...choiceOf(itemOf(lib, "a.example/2")!), place: { kind: "archive" } });
    expect(folderOf(itemOf(archived, "a.example/2")!)).toBeNull();
    expect(applyChoice(lib, "gone.example", emptyChoice)).toBe(lib);
  });
});

describe("the reader's Tags menu (#156)", () => {
  it("turns a tag on and off, case ignored", () => {
    const choice = { ...emptyChoice, tags: ["News"] };
    expect(withTag(choice, " Brussels ").tags).toEqual(["News", "Brussels"]);
    expect(withTag(choice, "news").tags).toEqual([]);
    expect(withTag(choice, "  ")).toBe(choice);
  });
  it("offers every tag in a steady order, a new one last", () => {
    const all = ["ai", "News"];
    expect(tagChoices(all, [])).toEqual(["ai", "News"]);
    // Turning one on doesn't move it; a tag made in the sheet comes last, once.
    expect(tagChoices(all, ["news"])).toEqual(["ai", "News"]);
    expect(tagChoices(all, ["Brussels", "AI"])).toEqual(["ai", "News", "Brussels"]);
  });
  it("shows the tags, else Liked, else nothing", () => {
    expect(tagsLabel({ ...emptyChoice, liked: true, tags: ["News", "Brussels"] }, "Liked")).toBe("News, Brussels");
    expect(tagsLabel({ ...emptyChoice, liked: true }, "Liked")).toBe("Liked");
    expect(tagsLabel(emptyChoice, "Liked")).toBeNull();
  });
});

describe("Remove right after saving", () => {
  it("takes the save back: gone when new, as it was when saved before", () => {
    const lib = sample();
    const [added, item] = saveLink(lib, "https://b.example/new", "New", 5);
    expect(itemOf(undoSave(added, item!.key, null), item!.key)).toBeUndefined();
    const before = itemOf(lib, "a.example/3")!;
    const [again] = saveLink(lib, "https://a.example/3", null, 9);
    expect(itemOf(undoSave(again, "a.example/3", before), "a.example/3")).toEqual(before);
  });
});

describe("Save with text still typed", () => {
  it("takes a folder and a tag being typed", () => {
    const choice = { ...emptyChoice, tags: ["News"] };
    const typed = withTyped(choice, " work ", "Brussels", ["Work"]);
    expect(typed.place).toEqual({ kind: "folder", name: "Work" });
    expect(typed.tags).toEqual(["News", "Brussels"]);
    expect(withTyped(choice, "", "news", [])).toBe(choice);
  });
});
