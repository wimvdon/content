/**
 * The rules behind the one save and Organise sheet (#143), the same as the Android app's
 * data/SaveChoice.kt: which places to offer, what a new folder is, and where a save lands.
 */

import { changeItem, cleanTags, itemOf, putItem, type Item } from "./items";
import type { Library } from "./library";
import { archiveItem, fileItem, folderOf, removeSaved, saveLink } from "./saved";

/** Where the sheet puts an article: To read, one folder, or the Archive. */
export type Place = { kind: "toread" } | { kind: "folder"; name: string } | { kind: "archive" };

/** What the sheet says about an article: where it goes, whether it is liked, its tags and its note. */
/**
 * What the Save and share sheet says (#143, #256): where it goes, liked, tags and note, and who it is
 * sent to with Content. `keep` is false for Don't save: only sent (offered before the save only).
 */
export interface SaveChoice {
  place: Place;
  liked: boolean;
  tags: string[];
  note: string;
  keep: boolean;
  sendTo: string[];
}

export const emptyChoice: SaveChoice = { place: { kind: "toread" }, liked: false, tags: [], note: "", keep: true, sendTo: [] };

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

export const samePlace = (a: Place, b: Place) => (a.kind === "folder" && b.kind === "folder" ? same(a.name, b.name) : a.kind === b.kind);

/** Where an item is now, and what it has: the sheet starts from this. */
export function choiceOf(i: Item): SaveChoice {
  const folder = folderOf(i);
  const place: Place = i.readingList?.state === "archived" ? { kind: "archive" } : folder ? { kind: "folder", name: folder } : { kind: "toread" };
  return { place, liked: i.liked, tags: i.tags, note: i.note, keep: true, sendTo: [] };
}

/**
 * The folder chips, in order (#156): To read and Archive, the two places every article can be, then
 * each folder in use and the one being made.
 */
export function places(folders: string[], current: Place): Place[] {
  const made = current.kind === "folder" && !folders.some((f) => same(f, current.name)) ? [current] : [];
  return [{ kind: "toread" }, { kind: "archive" }, ...folders.map((name): Place => ({ kind: "folder", name })), ...made];
}

/**
 * Takes back a save that just happened (Remove on the sheet right after Save): the item as it was
 * before, or gone when it wasn't saved then. The same as the Android app's SaveChoices.undoSave.
 */
export function undoSave(lib: Library, key: string, before: Item | null): Library {
  if (!itemOf(lib, key)) return lib;
  return before ? putItem(lib, before) : removeSaved(lib, key);
}

/**
 * The choice with what is still typed in the sheet when Save is tapped: a new folder's name (it
 * goes there) and a tag's (it is added), so nothing typed is lost. As SaveChoices.withTyped on Android.
 */
export function withTyped(choice: SaveChoice, folder: string, tag: string, folders: string[]): SaveChoice {
  const place = newFolder(folder, folders) ?? choice.place;
  const clean = tag.trim();
  const tags = !clean || choice.tags.some((t) => same(t, clean)) ? choice.tags : [...choice.tags, clean];
  return samePlace(place, choice.place) && tags === choice.tags ? choice : { ...choice, place, tags };
}

/**
 * Whether the sheet offers Save to Archive next to Save (#305): only while saving (Add link, or one
 * saved a moment ago), not organising one saved before, and not when nothing is kept. As
 * SaveChoices.offersArchive on Android.
 */
export const offersArchive = (organising: boolean, choice: SaveChoice): boolean => !organising && choice.keep;

/** Save to Archive (#305): the same choice, but into the Archive. */
export const toArchive = (choice: SaveChoice): SaveChoice => ({ ...choice, place: { kind: "archive" } });

/**
 * A topic renamed while the sheet is open (#314): the sheet's choice follows, so Save doesn't bring
 * the old name back. Renamed to another topic's name, the two are one. As SaveChoices.withTopicRenamed.
 */
export function withTopicRenamed(choice: SaveChoice, from: string, to: string): SaveChoice {
  const clean = to.trim();
  if (!clean || !choice.tags.some((t) => same(t, from))) return choice;
  const joined = choice.tags.some((t) => !same(t, from) && same(t, clean));
  const tags = joined ? choice.tags.filter((t) => !same(t, from)) : choice.tags.map((t) => (same(t, from) ? clean : t));
  return { ...choice, tags: tags.filter((t, i) => tags.findIndex((u) => same(u, t)) === i) };
}

/** A topic removed while the sheet is open (#314): off the choice too. */
export function withoutTopic(choice: SaveChoice, name: string): SaveChoice {
  return choice.tags.some((t) => same(t, name)) ? { ...choice, tags: choice.tags.filter((t) => !same(t, name)) } : choice;
}

/** The tag on or off (case ignored), as the reader's Tags menu does it (#156). */
export function withTag(choice: SaveChoice, tag: string): SaveChoice {
  const clean = tag.trim();
  if (!clean) return choice;
  const has = choice.tags.some((t) => same(t, clean));
  return { ...choice, tags: has ? choice.tags.filter((t) => !same(t, clean)) : [...choice.tags, clean] };
}

/**
 * The tags the save sheet offers: every tag in use, then the item's own that aren't in use elsewhere
 * yet (a new one goes last), each once (case ignored). The order doesn't change when a tag is turned
 * on or off, so the chips stay put. Like SaveChoices.tagChoices on Android.
 */
export function tagChoices(allTags: string[], tags: string[]): string[] {
  const seen = new Set<string>();
  return [...allTags, ...tags].filter((t) => {
    const key = t.trim().toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/** What the reader's Tags button says (#156): the tags, else Liked, else null (just "Tags"). */
export function tagsLabel(choice: SaveChoice, liked: string): string | null {
  if (choice.tags.length > 0) return choice.tags.join(", ");
  return choice.liked ? liked : null;
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
