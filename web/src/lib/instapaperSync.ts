/**
 * Instapaper as Saved's service in the web app (#109, #119), on API v2 through the Worker, the same
 * as the Android app's InstapaperReadingList.kt: `since` keeps this browser's copy of the account up
 * to date; home is To read and the archive is the archive. Folders became tags (#187): Content
 * neither reads nor changes Instapaper's folders. Only this file knows Instapaper's ways.
 */

import { InstapaperError, type Bookmark, type InstapaperApi } from "./instapaper";
import { instapaperReadLink } from "./instapaperLinks";
import type { HighlightService, RemoteHighlight } from "./highlightCopy";
import { ServiceSyncError, type RemoteChanges, type RemoteEntry, type SavedSyncService } from "./serviceSync";

export const INSTAPAPER_ID = "instapaper";

/** Instapaper's largest page. */
const PAGE = 500;
/** How much earlier than the request the next `since` starts, for clocks that differ. */
const CURSOR_MARGIN_S = 300;
/**
 * Marks a cursor made since private content (emails) comes in as it does now; one without it (from
 * before, "p:" included: 6.8.2 still left out emails with an `instapaper-private://` address) reads
 * everything once.
 */
export const CURSOR_PREFIX = "p2:";

type Api = Pick<InstapaperApi, "changesSince" | "folders" | "tags" | "save" | "setTags" | "move" | "like" | "updateBookmark" | "remove"> &
  Partial<Pick<InstapaperApi, "highlights" | "addHighlight" | "deleteHighlight">>;

export class InstapaperSync implements SavedSyncService, HighlightService {
  readonly id = INSTAPAPER_ID;
  readonly progress = true;
  readonly content = true;
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
      // A cursor from before private content came in (no prefix) reads everything once, so the
      // emails skipped then come in now.
      const since = (cursor?.startsWith(CURSOR_PREFIX) && Number(cursor.slice(CURSOR_PREFIX.length))) || 1;
      const entries: RemoteEntry[] = [];
      const deleted: string[] = [];
      for (let offset = 0; ; ) {
        const page = await this.api.changesSince(since, offset, PAGE);
        for (const b of page.bookmarks) entries.push(this.entryOf(b));
        deleted.push(...page.deleted_ids.map(String));
        const count = page.bookmarks.length + page.deleted_ids.length;
        offset += count;
        if (count < PAGE) break;
      }
      return { entries, deleted, cursor: CURSOR_PREFIX + Math.max(1, asked) };
    });
  }

  /**
   * A bookmark as an entry. Private content (a forwarded email, an uploaded file: marked as private,
   * or without a web address, such as none or an `instapaper-private://` one) gets Instapaper's own
   * page as its address (instapaperLinks.ts), as on Android.
   */
  entryOf(b: Bookmark): RemoteEntry {
    const web = b.url && /^https?:\/\//i.test(b.url.trim()) ? b.url.trim() : null;
    const url = web && !b.private_source?.trim() ? web : instapaperReadLink(b.id);
    return {
      remoteId: String(b.id),
      url,
      title: b.title?.trim() || url,
      description: b.description ?? "",
      imageUrl: b.image?.startsWith("https://") ? b.image : null,
      addedAt: b.time * 1000,
      archived: b.archived,
      liked: b.liked,
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

  add(url: string, title: string, tags: string[], archived: boolean, _folder: string | null, content: string | null = null): Promise<RemoteEntry> {
    return guarded(async () => {
      const named = title && title !== url ? title : undefined;
      // With Content's text (#247), so Instapaper shows the same article; should it refuse the text
      // (too large, say), the link alone, as before.
      let saved = content
        ? await this.api.save(url, named, content).catch((e: unknown) => {
            if (e instanceof InstapaperError && (e.problem === "invalid" || e.problem === "server")) return null;
            throw e;
          })
        : null;
      saved ??= await this.api.save(url, named);
      if (!saved?.id) throw new ServiceSyncError("refused");
      if (tags.length > 0) saved = { ...saved, tags: await this.api.setTags(saved.id, tags, []) };
      if (archived && !saved.archived) {
        await this.api.move(saved.id, "archive");
        saved = { ...saved, archived: true, folder_id: null };
      }
      return this.entryOf(saved);
    });
  }


  update(have: RemoteEntry, want: RemoteEntry): Promise<RemoteEntry> {
    return guarded(async () => {
      const id = Number(have.remoteId);
      if (!Number.isFinite(id)) throw new ServiceSyncError("refused");
      const has = (list: string[], tag: string) => list.some((t) => t.toLowerCase() === tag.toLowerCase());
      if (want.liked !== have.liked) await this.api.like(id, want.liked);
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

  highlights(remoteId: string): Promise<RemoteHighlight[]> {
    return guarded(async () => {
      const list = (await this.api.highlights?.(numberOf(remoteId))) ?? [];
      return list.map((h) => ({ remoteId: String(h.id), text: h.text, note: h.note ?? "", position: h.position ?? 0, createdAt: (h.time ?? 0) * 1000 }));
    });
  }

  addHighlight(remoteId: string, text: string, note: string, position: number): Promise<RemoteHighlight> {
    return guarded(async () => {
      if (!this.api.addHighlight) throw new ServiceSyncError("refused");
      try {
        const made = await this.api.addHighlight(numberOf(remoteId), text, note, position);
        if (typeof made?.id !== "number") throw new ServiceSyncError("server");
        return { remoteId: String(made.id), text: made.text ?? text, note: made.note ?? note };
      } catch (e) {
        // Instapaper answers 403 when a free account used its highlights for the month.
        if (e instanceof InstapaperError && e.problem === "notApproved") throw new ServiceSyncError("limit");
        throw e;
      }
    });
  }

  removeHighlight(highlightId: string): Promise<void> {
    return guarded(async () => {
      const id = Number(highlightId);
      if (!Number.isFinite(id)) return;
      try {
        await this.api.deleteHighlight?.(id);
      } catch (e) {
        // Already gone at Instapaper: that is what was wanted.
        if (!(e instanceof InstapaperError && e.problem === "invalid")) throw e;
      }
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

function numberOf(remoteId: string): number {
  const id = Number(remoteId);
  if (!Number.isFinite(id)) throw new ServiceSyncError("refused");
  return id;
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
