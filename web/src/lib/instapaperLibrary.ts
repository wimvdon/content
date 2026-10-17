import { escapeHtml } from "./html";
import type { Bookmark, Folder, Tag, View } from "./instapaper";
import type { Article } from "./library";

/**
 * Instapaper inside the reader: saved articles show in the same list and reader as feed articles.
 * They have no read state (archiving is a separate action), and they are not in All articles.
 */

/** The "feed" of every saved article, so its key ("instapaper <id>") never clashes with a feed's. */
export const INSTAPAPER_FEED = "instapaper";

export const isInstapaperArticle = (a: Article) => a.feedUrl === INSTAPAPER_FEED;

/** A saved article as the list and the reader show it. The reader loads the page for the full text. */
export function bookmarkArticle(b: Bookmark): Article {
  return {
    feedUrl: INSTAPAPER_FEED,
    id: String(b.id),
    title: b.title || b.url || "",
    link: webAddress(b.url),
    html: b.description ? `<p>${escapeHtml(b.description)}</p>` : "",
    imageUrl: b.image && webAddress(b.image)?.startsWith("https:") ? b.image : null,
    author: null,
    publishedAt: b.time * 1000,
    fetchedAt: b.time * 1000,
    read: false,
  };
}

export const bookmarkId = (a: Article) => Number(a.id);

/** Only web addresses are linked, so a saved item can't carry a script address. */
export function webAddress(url: string | null): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    return parsed.protocol === "https:" || parsed.protocol === "http:" ? parsed.toString() : null;
  } catch {
    return null;
  }
}

/** The website of a saved article, for the line above its title. */
export function hostOf(url: string | null): string {
  try {
    return url ? new URL(url).hostname.replace(/^www\./, "") : "";
  } catch {
    return "";
  }
}

export const sameView = (a: View, b: View) => JSON.stringify(a) === JSON.stringify(b);

/** Whether a saved article belongs in a list: Unread holds what is in no folder and not archived. */
export function inView(view: View, b: Bookmark): boolean {
  switch (view.kind) {
    case "home":
      return !b.archived && b.folder_id === null;
    case "archive":
      return b.archived;
    case "liked":
      return b.liked;
    case "folder":
      return !b.archived && b.folder_id === view.id;
    case "tag":
      return b.tags.some((tag) => tag.name === view.name);
  }
}

/** What happened to a saved article. */
export type Change =
  | { kind: "archived"; archived: boolean }
  | { kind: "liked"; liked: boolean }
  | { kind: "moved"; folderId: number | null }
  | { kind: "tags"; tags: Tag[] }
  | { kind: "deleted" };

/** A saved article after a change, or null when it was deleted. Back to Unread leaves its folder. */
export function patchBookmark(b: Bookmark, change: Change): Bookmark | null {
  switch (change.kind) {
    case "deleted":
      return null;
    case "archived":
      return { ...b, archived: change.archived, folder_id: change.archived ? b.folder_id : null };
    case "liked":
      return { ...b, liked: change.liked };
    case "moved":
      return { ...b, folder_id: change.folderId, archived: false };
    case "tags":
      return { ...b, tags: change.tags };
  }
}

/** The list after a change to one of its articles: changed in place, or gone when it no longer belongs. */
export function applyChange(view: View, items: Bookmark[], id: number, change: Change): Bookmark[] {
  return items.flatMap((b) => {
    if (b.id !== id) return [b];
    const next = patchBookmark(b, change);
    return next && inView(view, next) ? [next] : [];
  });
}

/** The list's name: Unread, Liked, Archive, or the folder's or tag's name. */
export function viewTitle(view: View, folders: Folder[], names: { home: string; liked: string; archive: string }): string {
  switch (view.kind) {
    case "home":
      return names.home;
    case "liked":
      return names.liked;
    case "archive":
      return names.archive;
    case "folder":
      return folders.find((f) => f.id === view.id)?.title ?? "";
    case "tag":
      return view.name;
  }
}
