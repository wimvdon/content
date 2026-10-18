/**
 * Saved without an account: the same lists, likes, archive and tags as Instapaper, kept in this
 * browser and not synced; signing in copies them to Instapaper (`uploadTo`). There are no folders
 * (moving to one is refused). The same as Android's LocalSaved (docs/instapaper.md).
 */

import { InstapaperError, type Bookmark, type Folder, type Page, type Tag, type View } from "./instapaper";
import { inView } from "./instapaperLibrary";

/** What the Saved section needs: InstapaperApi with an account, LocalSaved without one. */
export interface SavedService {
  list(view: View, offset?: number, limit?: number): Promise<Page>;
  save(url: string, title?: string): Promise<Bookmark>;
  move(id: number, to: "archive" | "home" | number): Promise<Bookmark>;
  like(id: number, liked: boolean): Promise<Bookmark>;
  remove(id: number): Promise<void>;
  setTags(id: number, add: string[], removeIds: number[]): Promise<Tag[]>;
  folders(): Promise<Folder[]>;
  createFolder(title: string): Promise<Folder>;
  deleteFolder(id: number): Promise<void>;
  reorderFolders(order: { folder_id: number; position: number }[]): Promise<Folder[]>;
  tags(): Promise<Tag[]>;
  renameTag(id: number, name: string): Promise<Tag>;
}

export interface LocalArticle {
  id: number;
  url: string;
  title: string;
  /** When it was saved, in seconds since 1970. */
  time: number;
  liked: boolean;
  archived: boolean;
  tagIds: number[];
}

export interface LocalSavedData {
  articles: LocalArticle[];
  tags: { id: number; name: string }[];
  /** The id the next saved article or tag gets. */
  nextId: number;
}

export const emptyLocalSaved: LocalSavedData = { articles: [], tags: [], nextId: 1 };

/** Where the data lives: IndexedDB in the app (app.ts), memory in tests. */
export interface LocalSavedStorage {
  read(): Promise<LocalSavedData>;
  write(data: LocalSavedData): Promise<void>;
}

/** What `uploadTo` did: how many articles it copied, and why it stopped early (null when all are copied). */
export interface LocalUpload {
  copied: number;
  problem: InstapaperError["problem"] | null;
}

export class LocalSaved implements SavedService {
  private readonly storage: LocalSavedStorage;
  private readonly now: () => number;
  /** Changes run one after the other, so none is lost. */
  private queue: Promise<unknown> = Promise.resolve();

  constructor(storage: LocalSavedStorage, now: () => number = Date.now) {
    this.storage = storage;
    this.now = now;
  }

  private update<T>(change: (data: LocalSavedData) => [LocalSavedData, T]): Promise<T> {
    const run = this.queue.then(async () => {
      const [data, result] = change(await this.storage.read());
      await this.storage.write(withoutUnusedTags(data));
      return result;
    });
    this.queue = run.catch(() => undefined);
    return run;
  }

  private async read(): Promise<LocalSavedData> {
    await this.queue;
    return this.storage.read();
  }

  async list(view: View, offset = 0, limit = 25): Promise<Page> {
    const data = await this.read();
    const all = data.articles
      .map((a) => bookmarkOf(a, data))
      .filter((b) => inView(view, b))
      .sort((a, b) => b.time - a.time);
    return { bookmarks: all.slice(offset, offset + limit), total: all.length };
  }

  /** Saving an address that is already saved brings it back to the top of Saved, as Instapaper does. */
  save(url: string, title?: string): Promise<Bookmark> {
    const time = Math.floor(this.now() / 1000);
    return this.update((data) => {
      const existing = data.articles.find((a) => a.url === url);
      const article: LocalArticle = existing
        ? { ...existing, time, archived: false, title: title?.trim() || existing.title }
        : { id: data.nextId, url, title: title?.trim() || url, time, liked: false, archived: false, tagIds: [] };
      const next = existing
        ? { ...data, articles: data.articles.map((a) => (a.id === article.id ? article : a)) }
        : { ...data, articles: [...data.articles, article], nextId: data.nextId + 1 };
      return [next, bookmarkOf(article, next)];
    });
  }

  move(id: number, to: "archive" | "home" | number): Promise<Bookmark> {
    if (typeof to === "number") return Promise.reject(new InstapaperError("invalid"));
    return this.change(id, (a) => ({ ...a, archived: to === "archive" }));
  }

  like(id: number, liked: boolean): Promise<Bookmark> {
    return this.change(id, (a) => ({ ...a, liked }));
  }

  remove(id: number): Promise<void> {
    return this.update((data) => [{ ...data, articles: data.articles.filter((a) => a.id !== id) }, undefined]);
  }

  /** Tags are shared by name, whatever the capitals; they go away when no article has them. */
  setTags(id: number, add: string[], removeIds: number[]): Promise<Tag[]> {
    return this.update((data) => {
      const article = find(data, id);
      let tags = data.tags;
      let nextId = data.nextId;
      const added = add
        .map((name) => name.trim())
        .filter(Boolean)
        .map((name) => {
          const found = tags.find((t) => t.name.toLowerCase() === name.toLowerCase());
          if (found) return found.id;
          const tag = { id: nextId++, name };
          tags = [...tags, tag];
          return tag.id;
        });
      const kept = article.tagIds.filter((t) => !removeIds.includes(t));
      const tagIds = [...kept, ...added.filter((t, i) => !kept.includes(t) && added.indexOf(t) === i)];
      const next = { ...data, tags, nextId, articles: data.articles.map((a) => (a.id === id ? { ...a, tagIds } : a)) };
      return [next, bookmarkOf({ ...article, tagIds }, next).tags];
    });
  }

  async folders(): Promise<Folder[]> {
    return [];
  }

  createFolder(): Promise<Folder> {
    return Promise.reject(new InstapaperError("invalid"));
  }

  deleteFolder(): Promise<void> {
    return Promise.reject(new InstapaperError("invalid"));
  }

  reorderFolders(): Promise<Folder[]> {
    return Promise.reject(new InstapaperError("invalid"));
  }

  async tags(): Promise<Tag[]> {
    const data = await this.read();
    return data.tags.map((t) => tagOf(t, data)).sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()));
  }

  /** Renaming to the name of another tag joins the two. */
  renameTag(id: number, name: string): Promise<Tag> {
    const clean = name.trim();
    if (!clean) return Promise.reject(new InstapaperError("invalid"));
    return this.update((data) => {
      const tag = data.tags.find((t) => t.id === id);
      if (!tag) throw new InstapaperError("invalid");
      const other = data.tags.find((t) => t.id !== id && t.name.toLowerCase() === clean.toLowerCase());
      const target = other ?? { id, name: clean };
      const next = {
        ...data,
        tags: [...data.tags.filter((t) => t.id !== id && t.id !== target.id), target],
        articles: data.articles.map((a) =>
          a.tagIds.includes(id) ? { ...a, tagIds: [...a.tagIds.filter((t) => t !== id && t !== target.id), target.id] } : a,
        ),
      };
      return [next, tagOf(target, next)];
    });
  }

  /**
   * Copies the articles saved here to the Instapaper account `remote` after signing in, with their
   * like, archive and tags, oldest first so the newest ends on top. Each one is removed here once it
   * is in the account, so a copy that stops halfway (offline, too many requests) goes on where it
   * left off the next time.
   */
  async uploadTo(remote: SavedService): Promise<LocalUpload> {
    const data = await this.read();
    let copied = 0;
    for (const local of [...data.articles].sort((a, b) => a.time - b.time)) {
      try {
        const saved = await remote.save(local.url, local.title !== local.url ? local.title : undefined);
        if (saved?.id) {
          if (local.liked && !saved.liked) await remote.like(saved.id, true);
          const names = bookmarkOf(local, data)
            .tags.map((t) => t.name)
            .filter((name) => !(saved.tags ?? []).some((t) => t.name.toLowerCase() === name.toLowerCase()));
          if (names.length > 0) await remote.setTags(saved.id, names, []);
          if (local.archived) await remote.move(saved.id, "archive");
        }
      } catch (e) {
        return { copied, problem: e instanceof InstapaperError ? e.problem : "server" };
      }
      copied++;
      await this.remove(local.id);
    }
    return { copied, problem: null };
  }

  private change(id: number, patch: (a: LocalArticle) => LocalArticle): Promise<Bookmark> {
    return this.update((data) => {
      const article = patch(find(data, id));
      const next = { ...data, articles: data.articles.map((a) => (a.id === id ? article : a)) };
      return [next, bookmarkOf(article, next)];
    });
  }
}

function find(data: LocalSavedData, id: number): LocalArticle {
  const article = data.articles.find((a) => a.id === id);
  if (!article) throw new InstapaperError("invalid");
  return article;
}

function tagOf(tag: { id: number; name: string }, data: LocalSavedData): Tag {
  return { id: tag.id, name: tag.name, slug: tag.name.toLowerCase(), count: data.articles.filter((a) => a.tagIds.includes(tag.id)).length };
}

function bookmarkOf(a: LocalArticle, data: LocalSavedData): Bookmark {
  return {
    id: a.id,
    url: a.url,
    title: a.title,
    description: "",
    image: null,
    liked: a.liked,
    archived: a.archived,
    time: a.time,
    folder_id: null,
    tags: a.tagIds.flatMap((id) => {
      const tag = data.tags.find((t) => t.id === id);
      return tag ? [tagOf(tag, data)] : [];
    }),
  };
}

function withoutUnusedTags(data: LocalSavedData): LocalSavedData {
  const used = new Set(data.articles.flatMap((a) => a.tagIds));
  return { ...data, tags: data.tags.filter((t) => used.has(t.id)) };
}

/** Stored data from an older or damaged copy, made safe to use. */
export function localSavedOf(value: unknown): LocalSavedData {
  const v = value as Partial<LocalSavedData> | undefined;
  if (!v || !Array.isArray(v.articles) || !Array.isArray(v.tags)) return emptyLocalSaved;
  const articles = v.articles.filter((a): a is LocalArticle => typeof a?.id === "number" && typeof a.url === "string");
  const tags = v.tags.filter((t) => typeof t?.id === "number" && typeof t.name === "string");
  const highest = Math.max(0, ...articles.map((a) => a.id), ...tags.map((t) => t.id));
  return { articles, tags, nextId: Math.max(typeof v.nextId === "number" ? v.nextId : 1, highest + 1) };
}
