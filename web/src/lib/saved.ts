/**
 * Saved (#119, #147): one list for everything saved, the same rules as the Android app's
 * data/Saved.kt. An item is in exactly one place: To read, one folder, or the Archive. Liked and
 * tags are chips that filter within a tab, and the type filter picks articles, videos or audio.
 * Immutable: every change returns a new library.
 */

import { escapeHtml, snippet } from "./html";
import { addToReadingList, changeItem, cleanTags, feedArticleFor, putItem, recentTags, setReadingState, waitingShared, type Item, type SharedBy } from "./items";
import { keyOf, type Article, type Feed, type Library } from "./library";

/** "shared": what other Content users shared with you, until you put it in To read or the Archive (#167). */
export type SavedTab = { kind: "toread" } | { kind: "all" } | { kind: "folder"; name: string } | { kind: "archive" } | { kind: "shared" };

/** What Saved shows: a tab, and within it only liked ones or only some tags (all of them). */
export interface SavedFilter {
  tab: SavedTab;
  likedOnly: boolean;
  tags: string[];
}

export const defaultSavedFilter: SavedFilter = { tab: { kind: "toread" }, likedOnly: false, tags: [] };

const same = (a: string, b: string) => a.trim().toLowerCase() === b.trim().toLowerCase();

export const sameTab = (a: SavedTab, b: SavedTab) => (a.kind === "folder" && b.kind === "folder" ? same(a.name, b.name) : a.kind === b.kind);

/** The folder an item is in, or null (To read, archived, or not saved). */
export const folderOf = (i: Item): string | null => (i.readingList?.state === "toread" && i.folder?.trim() ? i.folder.trim() : null);

export function isIn(i: Item, tab: SavedTab): boolean {
  if (tab.kind === "shared") return waitingShared(i);
  const reading = i.readingList;
  if (!reading) return false;
  switch (tab.kind) {
    case "all":
      return true;
    case "archive":
      return reading.state === "archived";
    case "toread":
      return reading.state === "toread" && folderOf(i) === null;
    case "folder":
      return folderOf(i) !== null && same(folderOf(i)!, tab.name);
  }
}

/** When an item came into Saved: put in To read or the Archive, else shared with you. */
const savedAt = (i: Item) => i.readingList?.addedAt ?? i.sharedBy?.at ?? i.createdAt;

/** The items the filter picks, newest saved first. */
export function savedItems(lib: Library, filter: SavedFilter): Item[] {
  return lib.items
    .filter(
      (i) =>
        isIn(i, filter.tab) &&
        (!filter.likedOnly || i.liked) &&
        filter.tags.every((t) => i.tags.some((x) => same(x, t))),
    )
    .sort((a, b) => savedAt(b) - savedAt(a));
}

/**
 * Folders became tags (#187): an item filed in a folder gets the folder's name as a tag instead, and
 * leaves the folder. The same library when nothing is in a folder. Applied after every change, so a
 * folder synced in from an older app or another device becomes a tag too. Like Saved.foldersToTags.
 */
export function foldersToTags(lib: Library): Library {
  if (!lib.items.some((i) => i.folder)) return lib;
  return {
    ...lib,
    items: lib.items.map((i) => {
      const folder = i.folder?.trim();
      if (i.folder === null || i.folder === undefined) return i;
      const tags = !folder || i.tags.some((t) => t.toLowerCase() === folder.toLowerCase()) ? i.tags : [...i.tags, folder];
      return { ...i, folder: null, tags };
    }),
  };
}

/** Every folder in use, sorted (case ignored). */
export function savedFolders(lib: Library): string[] {
  const seen = new Map<string, string>();
  for (const i of lib.items) {
    const f = folderOf(i);
    if (f && !seen.has(f.toLowerCase())) seen.set(f.toLowerCase(), f);
  }
  return [...seen.values()].sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase()));
}

/** How many items each folder has. */
export function folderCounts(lib: Library): Map<string, number> {
  return new Map(savedFolders(lib).map((f) => [f, lib.items.filter((i) => folderOf(i) !== null && same(folderOf(i)!, f)).length]));
}

/** How many items are in To read (outside folders). */
export const toReadCount = (lib: Library) => lib.items.filter((i) => isIn(i, { kind: "toread" })).length;

/** The tags in a tab, most recently used first (#220), for its chips. */
export function tabTags(lib: Library, tab: SavedTab): string[] {
  return recentTags(lib.items.filter((i) => isIn(i, tab)));
}

/**
 * The states Saved's state chip offers (#205): To read, Archive or everything. A tag stays chosen
 * when the state changes, as a folder does with Unread and All in Feeds.
 */
export const savedStates: SavedTab[] = [{ kind: "toread" }, { kind: "shared" }, { kind: "archive" }, { kind: "all" }];

/** How many shared items wait in Shared with me (#167): a dot on Saved while there are any. */
export const sharedWaiting = (lib: Library) => lib.items.filter(waitingShared).length;

/** How many liked items are still to read, for the menu's Liked row (#206). */
export const likedToReadCount = (lib: Library) => lib.items.filter((i) => i.liked && isIn(i, { kind: "toread" })).length;

/** How many items still to read carry each tag, for the menu (as Feeds counts unread articles). */
export function toReadTagCounts(lib: Library): Map<string, number> {
  return new Map(tabTags(lib, { kind: "all" }).map((tag) => [tag, lib.items.filter((i) => isIn(i, { kind: "toread" }) && i.tags.some((x) => same(x, tag))).length]));
}

/** The filter without a folder or tags that are gone (from all of Saved, not just the state shown). */
export function shownFilter(lib: Library, filter: SavedFilter): SavedFilter {
  const tab = filter.tab;
  const withTab = tab.kind === "folder" && !savedFolders(lib).some((f) => same(f, tab.name)) ? { ...filter, tab: { kind: "toread" } as SavedTab } : filter;
  const tags = tabTags(lib, { kind: "all" });
  const kept = withTab.tags.filter((t) => tags.some((x) => same(x, t)));
  return kept.length === withTab.tags.length ? withTab : { ...withTab, tags: kept };
}

/**
 * Saves a link: To read, or straight into `folder`, with a note and tags. A link already saved
 * moves there and keeps what it had. Null for addresses that aren't web links.
 */
export function saveLink(
  lib: Library,
  url: string,
  title: string | null | undefined,
  now: number,
  folder: string | null = null,
  note = "",
  tags: string[] = [],
): [Library, Item | null] {
  const [added, item] = addToReadingList(lib, url, title, now);
  if (!item) return [lib, null];
  const updated: Item = { ...item, folder: folder?.trim() || null, note: note.trim() ? note.trim() : item.note, tags: cleanTags([...item.tags, ...tags]) };
  return [putItem(added, updated), updated];
}

/** Puts an item shared with you in To read or the Archive (#167). */
export const placeShared = (lib: Library, key: string, state: "toread" | "archived", now: number) =>
  changeItem(lib, key, (i) => (i.readingList ? i : { ...i, readingList: { state, addedAt: now } }));

/** Puts an item in a folder, or To read with null (it stays saved; archived ones come back to read). */
export function fileItem(lib: Library, key: string, folder: string | null): Library {
  return changeItem(lib, key, (i) => (i.readingList ? { ...i, readingList: { ...i.readingList, state: "toread" }, folder: folder?.trim() || null } : i));
}

/** Archives an item (out of its folder), or brings it back to To read. */
export const archiveItem = (lib: Library, key: string, archived: boolean) => setReadingState(lib, key, archived ? "archived" : "toread");

export const setNote = (lib: Library, key: string, note: string) => changeItem(lib, key, (i) => ({ ...i, note: note.trim() }));

/** Removes an item from Saved, with its note, tags and highlights. */
export function removeSaved(lib: Library, key: string): Library {
  return lib.items.some((i) => i.key === key) ? { ...lib, items: lib.items.filter((i) => i.key !== key) } : lib;
}

/** Renames a folder on every item; renaming it to another folder's name joins the two. */
export function renameFolder(lib: Library, from: string, to: string): Library {
  const clean = to.trim();
  if (!clean) return lib;
  return { ...lib, items: lib.items.map((i) => (i.folder && same(i.folder, from) ? { ...i, folder: clean } : i)) };
}

/** Takes a folder away: its items go to the Archive (filed away, they don't come back as unread work). */
export function removeFolder(lib: Library, name: string): Library {
  return {
    ...lib,
    items: lib.items.map((i) => (i.folder && same(i.folder, name) ? { ...i, folder: null, readingList: i.readingList ? { ...i.readingList, state: "archived" } : null } : i)),
  };
}

/** Takes a tag off every item; the items stay. */
export function removeTag(lib: Library, name: string): Library {
  return { ...lib, items: lib.items.map((i) => (i.tags.some((t) => same(t, name)) ? { ...i, tags: i.tags.filter((t) => !same(t, name)) } : i)) };
}

/**
 * What a card shows under its title (#133): the first highlight (quoted), else the description. Your
 * note shows apart, smaller (NoteContent.note). Like Saved.cardBody on Android.
 */
export type CardBody = { kind: "quote" | "description"; text: string };

export function cardBody(i: Item): CardBody | null {
  const highlight = i.highlights.find((h) => h.quote.trim());
  if (highlight) return { kind: "quote", text: highlight.quote.trim() };
  if (i.description.trim()) return { kind: "description", text: i.description.trim() };
  return null;
}

/**
 * Whether a tab shows its items as notes (cards) rather than as rows like the feeds. Only To read
 * is a list to read through; a folder and the Archive are collections (like Android's Saved.showsNotes).
 */
/** Saved is always notes (#189). */
export const showsNotes = (_tab: SavedTab) => true;

/** One note card: a saved item, or a feed article shown the same way in Search's notes view. */
export interface NoteContent {
  title: string;
  imageUrl: string | null;
  body: CardBody | null;
  host: string;
  tags: string[];
  highlights: number;
  liked: boolean;
  /** Your note, shown small under the text. */
  note: string;
  /** Who shared it (#167). */
  sharedBy?: SharedBy | null;
}

/** A saved item as a note card. */
export const noteOf = (i: Item): NoteContent => ({
  title: i.title,
  imageUrl: i.imageUrl,
  body: cardBody(i),
  host: hostOf(i.url),
  tags: i.tags,
  highlights: i.highlights.length,
  liked: i.liked,
  note: i.note.trim(),
  sharedBy: i.sharedBy ?? null,
});

/** A feed article as a note card (Search's notes view): the start of its text under the title. */
export function articleNote(a: Article): NoteContent {
  const text = snippet(a.html, 240);
  return { title: a.title, imageUrl: a.imageUrl, body: text ? { kind: "description", text } : null, host: a.link ? hostOf(a.link) : "", tags: [], highlights: 0, liked: false, note: "" };
}

/** The "feed" of saved items opened in the reader, so their key ("item <key>") never clashes with a feed's. */
export const ITEM_FEED = "item";

export const isItemArticle = (a: Article) => a.feedUrl === ITEM_FEED;

/** The item key of a reader article, or null when it is a feed article's. */
export const itemKeyOf = (a: Article) => (isItemArticle(a) ? a.id : null);

export const itemArticleKey = (i: Item) => keyOf(ITEM_FEED, i.key);

/**
 * An item as the list and the reader show it, like a feed article: the feed article's text while
 * the library has it, else its description; the reader loads the page for the full text.
 */
export function itemArticle(i: Item, lib: Library): Article {
  const fromFeed = feedArticleOf(i, lib);
  const time = i.readingList?.addedAt ?? i.bookmark?.addedAt ?? i.createdAt;
  return {
    feedUrl: ITEM_FEED,
    id: i.key,
    title: i.title,
    link: i.url,
    author: fromFeed?.author ?? null,
    html: fromFeed?.html ?? (i.description.trim() ? `<p>${escapeHtml(i.description)}</p>` : ""),
    publishedAt: fromFeed?.publishedAt ?? time,
    fetchedAt: time,
    read: i.readingList?.state === "archived",
    imageUrl: i.imageUrl ?? fromFeed?.imageUrl ?? null,
  };
}

/** The feed article an item came from, while the library has it: by its feed and id, else by its link. */
export function feedArticleOf(i: Item, lib: Library): Article | undefined {
  if (i.feedUrl && i.articleId) {
    const exact = lib.articles.find((a) => a.feedUrl === i.feedUrl && a.id === i.articleId);
    if (exact) return exact;
  }
  return feedArticleFor(lib, i.url);
}

/**
 * Where an item comes from, shown above its title like a feed: the feed it was saved from, else a
 * feed of the same website, else the website itself with its icon (favicon).
 */
export function sourceOf(i: Item, lib: Library): Feed | undefined {
  const host = hostOf(i.url);
  if (!host) return undefined;
  const fromFeed = feedArticleOf(i, lib);
  const feed = (fromFeed && lib.feeds.find((f) => f.url === fromFeed.feedUrl)) ?? lib.feeds.find((f) => hostOf(f.siteUrl) === host || hostOf(f.url) === host);
  if (feed) return feed;
  return { ...siteOf(host), iconUrl: `https://${new URL(i.url).hostname}/favicon.ico` };
}

/** The website of a link, without "www.", or empty. */
export function hostOf(url: string | null | undefined): string {
  try {
    return url ? new URL(url).hostname.replace(/^www\./, "") : "";
  } catch {
    return "";
  }
}

/** A website shown where a feed's name goes (with its first letter as icon). */
export const siteOf = (host: string): Feed => ({ url: host, title: host, siteUrl: null, folderId: null, iconUrl: null, renamed: false });
