/**
 * Copies Content's highlights to a service (#164), when the user turned that on, as the Android
 * app's services/HighlightCopy.kt does. Content keeps them either way; the service only gets copies.
 * Before copying an article's highlights, the ones the service already has are looked up, and one
 * with the same passage counts as the copy (another device may have sent it). A changed note is
 * sent as a new highlight (the old one deleted), since a service can't edit one; a highlight
 * deleted here is deleted there. A plan's limit keeps the highlight in Content, marked as not
 * copied, and it is tried again a day later.
 */

import { fold } from "./anchors";
import type { Highlight } from "./items";
import { ServiceSyncError } from "./serviceSync";

/** What became of one highlight there: its copy with the note it was sent with, or when it was refused for the plan's limit. */
export interface HighlightLink {
  remoteId: string | null;
  note: string;
  refusedAt: number | null;
}

/** A highlight as the service has it. */
export interface RemoteHighlight {
  remoteId: string;
  text: string;
  note: string;
}

/** A service that keeps copies of highlights. addHighlight throws a ServiceSyncError "limit" when the plan allows no more. */
export interface HighlightService {
  highlights(remoteId: string): Promise<RemoteHighlight[]>;
  addHighlight(remoteId: string, text: string, note: string, position: number): Promise<RemoteHighlight>;
  removeHighlight(highlightId: string): Promise<void>;
}

/** A highlight refused for a plan's limit is tried again after this long. */
export const RETRY_MS = 24 * 60 * 60 * 1000;

/** Whether an article's highlights need a call: one new, with a changed note, deleted here, or due for a retry. */
export function pendingCopy(highlights: Highlight[], links: Record<string, HighlightLink>, now: number): boolean {
  const ids = new Set(highlights.map((h) => h.id));
  if (Object.entries(links).some(([id, link]) => !ids.has(id) && link.remoteId !== null)) return true;
  return highlights.some((h) => {
    const link = links[h.id];
    if (!link) return true;
    if (link.remoteId !== null) return link.note !== h.note;
    return link.refusedAt === null || now - link.refusedAt >= RETRY_MS;
  });
}

/** Copies highlights to the article remoteId at the service; links is what was copied before. Returns the links and why it stopped early. */
export async function pushHighlights(
  service: HighlightService,
  remoteId: string,
  highlights: Highlight[],
  links: Record<string, HighlightLink>,
  now: number,
): Promise<{ links: Record<string, HighlightLink>; problem: string | null }> {
  const out: Record<string, HighlightLink> = { ...links };
  try {
    const ids = new Set(highlights.map((h) => h.id));
    for (const [id, link] of Object.entries(links)) {
      if (ids.has(id)) continue;
      if (link.remoteId !== null) await service.removeHighlight(link.remoteId);
      delete out[id];
    }
    // Copies made before (by another device, or before this browser forgot): the same passage is the copy.
    if (highlights.some((h) => !out[h.id]?.remoteId)) {
      const taken = new Set(Object.values(out).flatMap((l) => (l.remoteId ? [l.remoteId] : [])));
      for (const r of await service.highlights(remoteId)) {
        if (taken.has(r.remoteId)) continue;
        const text = fold(r.text);
        const same = highlights.find((h) => !out[h.id]?.remoteId && fold(h.quote) === text);
        if (!same) continue;
        out[same.id] = { remoteId: r.remoteId, note: r.note.trim(), refusedAt: null };
        taken.add(r.remoteId);
      }
    }
    let limitReached = false;
    for (const h of [...highlights].sort((a, b) => a.createdAt - b.createdAt)) {
      const link = out[h.id];
      if (link?.remoteId && link.note === h.note) continue;
      if (!link?.remoteId && link?.refusedAt != null && (limitReached || now - link.refusedAt < RETRY_MS)) continue;
      if (limitReached) {
        out[h.id] = { remoteId: null, note: h.note, refusedAt: now };
        continue;
      }
      // A changed note: the service can't edit one, so the old copy goes and a new one is made.
      if (link?.remoteId) {
        await service.removeHighlight(link.remoteId);
        delete out[h.id];
      }
      try {
        const made = await service.addHighlight(remoteId, h.quote, h.note, h.occurrence);
        out[h.id] = { remoteId: made.remoteId, note: h.note, refusedAt: null };
      } catch (e) {
        if (!(e instanceof ServiceSyncError && e.problem === "limit")) throw e;
        limitReached = true;
        out[h.id] = { remoteId: null, note: h.note, refusedAt: now };
      }
    }
  } catch (e) {
    if (e instanceof ServiceSyncError) return { links: out, problem: e.problem };
    throw e;
  }
  return { links: out, problem: null };
}

/** The highlights the service refused (shown as "Not in Instapaper"). */
export function notCopied(links: Record<string, HighlightLink> | undefined): Set<string> {
  return new Set(Object.entries(links ?? {}).flatMap(([id, l]) => (l.remoteId === null && l.refusedAt !== null ? [id] : [])));
}
