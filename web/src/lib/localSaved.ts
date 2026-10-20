/**
 * Saved in this browser before #106 (IndexedDB "saved"): articles, tags and folders in Instapaper's
 * shape. Only read once, to move them into the library's items (savedItems.ts, libraryStore.ts).
 */

export interface LocalArticle {
  id: number;
  url: string;
  title: string;
  /** When it was saved, in seconds since 1970. */
  time: number;
  liked: boolean;
  archived: boolean;
  tagIds: number[];
  /** Its folder (#119), only while to read; missing in data from before folders. */
  folderId?: number | null;
}

export interface LocalSavedData {
  articles: LocalArticle[];
  tags: { id: number; name: string }[];
  /** The folders in use; missing in data from before folders. */
  folders?: { id: number; name: string }[];
  /** The id the next saved article or tag gets. */
  nextId: number;
}

export const emptyLocalSaved: LocalSavedData = { articles: [], tags: [], folders: [], nextId: 1 };

/** Stored data from an older or damaged copy, made safe to use. */
export function localSavedOf(value: unknown): LocalSavedData {
  const v = value as Partial<LocalSavedData> | undefined;
  if (!v || !Array.isArray(v.articles) || !Array.isArray(v.tags)) return emptyLocalSaved;
  const articles = v.articles.filter((a): a is LocalArticle => typeof a?.id === "number" && typeof a.url === "string");
  const tags = v.tags.filter((t) => typeof t?.id === "number" && typeof t.name === "string");
  const folders = (Array.isArray(v.folders) ? v.folders : []).filter((f) => typeof f?.id === "number" && typeof f.name === "string");
  const highest = Math.max(0, ...articles.map((a) => a.id), ...tags.map((t) => t.id), ...folders.map((f) => f.id));
  return { articles, tags, folders, nextId: Math.max(typeof v.nextId === "number" ? v.nextId : 1, highest + 1) };
}
