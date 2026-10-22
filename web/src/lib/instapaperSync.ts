/**
 * Instapaper as Saved's service in the web app (#109, #119), on API v2 through the Worker, the same
 * as the Android app's InstapaperReadingList.kt: `since` keeps this browser's copy of the account up
 * to date; home is To read and the archive is the archive. Folders became tags (#187): Content
 * neither reads nor changes Instapaper's folders. Only this file knows Instapaper's ways.
 */

import { InstapaperError, type Bookmark, type InstapaperApi } from "./instapaper";
import { ServiceSyncError, type RemoteChanges, type RemoteEntry, type SavedSyncService } from "./serviceSync";

export const INSTAPAPER_ID = "instapaper";

/** Instapaper's largest page. */
const PAGE = 500;
/** How much earlier than the request the next `since` starts, for clocks that differ. */
const CURSOR_MARGIN_S = 300;

type Api = Pick<InstapaperApi, "changesSince" | "folders" | "tags" | "save" | "setTags" | "move" | "updateBookmark" | "remove">;

export class InstapaperSync implements SavedSyncService {
  readonly id = INSTAPAPER_ID;
  readonly progress = true;
  private tagIds = new Map<string, number>();

  private readonly api: Api;
  private readonly now: () => number;

  constructor(api: Api, now: () => number = Date.now) {
    this.api = api;
    this.now = now;
  }

  changes(cursor: string | null): Promise<RemoteChanges> {
    return guarded(async () => {
      // Recorded before asking, a little early, so nothing changed meanwhile is missed.
      const asked = Math.floor(this.now() / 1000) - CURSOR_MARGIN_S;
      this.tagIds = new Map((await this.api.tags()).map((t) => [t.name.toLowerCase(), t.id]));
      const since = Number(cursor) || 1;
      const entries: RemoteEntry[] = [];
      const deleted: string[] = [];
      for (let offset = 0; ; ) {
        const page = await this.api.changesSince(since, offset, PAGE);
        for (const b of page.bookmarks) {
          const entry = this.entryOf(b);
          if (entry) entries.push(entry);
        }
        deleted.push(...page.deleted_ids.map(String));
        const count = page.bookmarks.length + page.deleted_ids.length;
        offset += count;
        if (count < PAGE) break;
      }
      return { entries, deleted, cursor: String(Math.max(1, asked)) };
    });
  }

  /** A bookmark as an entry. Articles without a web address are left out. */
  entryOf(b: Bookmark): RemoteEntry | null {
    const url = b.url;
    if (!url) return null;
    return {
      remoteId: String(b.id),
      url,
      title: b.title?.trim() || url,
      description: b.description ?? "",
      imageUrl: b.image?.startsWith("https://") ? b.image : null,
      addedAt: b.time * 1000,
      archived: b.archived,
      // Liked is gone (#188): Instapaper's like is neither read nor sent.
      liked: false,
      tags: (b.tags ?? []).map((t) => t.name),
      // Folders became tags (#187): Content reads no Instapaper folder and leaves them alone.
      folder: null,
      progress: Math.min(1, Math.max(0, b.progress?.percentage ?? 0)),
      // Instapaper's only change time is the progress's.
      changedAt: (b.progress?.timestamp ?? 0) * 1000,
    };
  }

  folderNames(): Promise<string[]> {
    return guarded(async () => (await this.api.folders()).map((f) => f.title).sort((a, b) => a.toLowerCase().localeCompare(b.toLowerCase())));
  }

  add(url: string, title: string, tags: string[], archived: boolean, _folder: string | null): Promise<RemoteEntry> {
    return guarded(async () => {
      let saved = await this.api.save(url, title && title !== url ? title : undefined);
      if (!saved?.id) throw new ServiceSyncError("refused");
      if (tags.length > 0) saved = { ...saved, tags: await this.api.setTags(saved.id, tags, []) };
      if (archived && !saved.archived) {
        await this.api.move(saved.id, "archive");
        saved = { ...saved, archived: true, folder_id: null };
      }
      const entry = this.entryOf(saved);
      if (!entry) throw new ServiceSyncError("refused");
      return entry;
    });
  }


  update(have: RemoteEntry, want: RemoteEntry): Promise<RemoteEntry> {
    return guarded(async () => {
      const id = Number(have.remoteId);
      if (!Number.isFinite(id)) throw new ServiceSyncError("refused");
      const has = (list: string[], tag: string) => list.some((t) => t.toLowerCase() === tag.toLowerCase());
      const added = want.tags.filter((t) => !has(have.tags, t));
      const removeIds = have.tags.filter((t) => !has(want.tags, t)).flatMap((t) => (this.tagIds.has(t.toLowerCase()) ? [this.tagIds.get(t.toLowerCase())!] : []));
      if (added.length > 0 || removeIds.length > 0) await this.api.setTags(id, added, removeIds);
      // The archive or home (To read). Folders became tags (#187): an article in a folder there stays in it.
      if (want.archived) {
        if (!have.archived) await this.api.move(id, "archive");
      } else if (have.archived) {
        await this.api.move(id, "home");
      }
      const progressChanged = Math.abs(want.progress - have.progress) >= 0.01;
      if ((want.title !== have.title && want.title.trim()) || progressChanged) {
        await this.api.updateBookmark(id, {
          title: want.title !== have.title && want.title.trim() ? want.title : undefined,
          progress: progressChanged ? want.progress : undefined,
          at: Math.floor(this.now() / 1000),
        });
      }
      return { ...want, remoteId: have.remoteId, addedAt: have.addedAt, folder: null };
    });
  }

  remove(remoteId: string): Promise<void> {
    return guarded(async () => {
      const id = Number(remoteId);
      if (!Number.isFinite(id)) return;
      try {
        await this.api.remove(id);
      } catch (e) {
        // Already gone at Instapaper: that is what was wanted.
        if (!(e instanceof InstapaperError && e.problem === "invalid")) throw e;
      }
    });
  }
}

/** Instapaper's problems as the sync's: "auth" means signed out. */
async function guarded<T>(run: () => Promise<T>): Promise<T> {
  try {
    return await run();
  } catch (e) {
    if (e instanceof InstapaperError) throw new ServiceSyncError(e.problem);
    throw e;
  }
}
