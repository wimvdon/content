/**
 * The rules behind the one save and Organise sheet (#143), the same as the Android app's
 * data/SaveChoice.kt: which places to offer, what a new folder is, and where a save lands.
 */

import { changeItem, cleanTags, itemOf, type Item } from "./items";
import type { Library } from "./library";
import { archiveItem, fileItem, folderOf, saveLink } from "./saved";

/** Where the sheet puts an article: To read, one folder, or the Archive. */
export type Place = { kind: "toread" } | { kind: "folder"; name: string } | { kind: "archive" };

/** What the sheet says about an article: where it goes, whether it is liked, its tags and its note. */
export interface SaveChoice {
  place: Place;
  liked: boolean;
  tags: string[];
  note: string;
}

export const emptyChoice: SaveChoice = { place: { kind: "toread" }, liked: false, tags: [], note: "" };

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

export const samePlace = (a: Place, b: Place) => (a.kind === "folder" && b.kind === "folder" ? same(a.name, b.name) : a.kind === b.kind);

/** Where an item is now, and what it has: the sheet starts from this. */
export function choiceOf(i: Item): SaveChoice {
  const folder = folderOf(i);
  const place: Place = i.readingList?.state === "archived" ? { kind: "archive" } : folder ? { kind: "folder", name: folder } : { kind: "toread" };
  return { place, liked: i.liked, tags: i.tags, note: i.note };
}

/**
 * The folder chips, in order (#156): To read and Archive, the two places every article can be, then
 * each folder in use and the one being made.
 */
export function places(folders: string[], current: Place): Place[] {
  const made = current.kind === "folder" && !folders.some((f) => same(f, current.name)) ? [current] : [];
  return [{ kind: "toread" }, { kind: "archive" }, ...folders.map((name): Place => ({ kind: "folder", name })), ...made];
}

/** A folder typed under New folder: the folder of that name when there is one (case ignored), null when blank. */
export function newFolder(name: string, folders: string[]): Place | null {
  const clean = name.trim();
  if (!clean) return null;
  return { kind: "folder", name: folders.find((f) => same(f, clean)) ?? clean };
}

/**
 * Writes what the sheet says about an item already saved (the reader, Organise): its place, like,
 * tags and note. An unchanged choice changes nothing.
 */
export function applyChoice(lib: Library, key: string, choice: SaveChoice): Library {
  const item = itemOf(lib, key);
  if (!item?.readingList) return lib;
  const archived = item.readingList.state === "archived";
  const place = choice.place;
  let out = lib;
  if (place.kind === "archive") {
    if (!archived) out = archiveItem(out, key, true);
  } else if (place.kind === "toread") {
    if (archived || folderOf(item) !== null) out = fileItem(out, key, null);
  } else if (archived || folderOf(item) === null || !same(folderOf(item)!, place.name)) {
    out = fileItem(out, key, place.name);
  }
  const tags = cleanTags(choice.tags);
  const note = choice.note.trim();
  return changeItem(out, key, (i) => (i.liked === choice.liked && JSON.stringify(i.tags) === JSON.stringify(tags) && i.note === note ? i : { ...i, liked: choice.liked, tags, note }));
}

/**
 * Saves a link that wasn't saved yet (Add link): where the sheet says, liked or not, with its tags
 * and note. A link saved before keeps its like and tags. Null for addresses that aren't web links.
 */
export function saveNew(lib: Library, url: string, title: string | null, now: number, choice: SaveChoice): [Library, Item | null] {
  const folder = choice.place.kind === "folder" ? choice.place.name : null;
  const [saved, item] = saveLink(lib, url, title, now, folder, choice.note, choice.tags);
  if (!item) return [lib, null];
  const placed = applyChoice(saved, item.key, { ...choice, liked: choice.liked || item.liked, tags: item.tags, note: item.note });
  return [placed, itemOf(placed, item.key) ?? null];
}
