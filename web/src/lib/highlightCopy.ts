/**
 * Keeps an article's highlights the same in Content and a service (#164), when the user turned that
 * on, as the Android app's services/HighlightCopy.kt does. Both ways: a highlight made in the
 * service's own apps comes into Content (placed by its passage and which occurrence it is), one
 * deleted there is deleted here, and a note changed there comes in; Content's go there. One with the
 * same passage on both sides is the same highlight (another device may have sent it). A note changed
 * here is sent as a new highlight (the old one deleted), since a service can't edit one; a highlight
 * deleted here is deleted there. A plan's limit keeps the highlight in Content, marked as not
 * copied, and it is tried again a day later.
 */

import { fold } from "./anchors";
import type { Highlight } from "./items";
import { ServiceSyncError } from "./serviceSync";

/** What became of one highlight there: its copy with the note it was last seen or sent with, or when it was refused for the plan's limit. */
export interface HighlightLink {
  remoteId: string | null;
  note: string;
  refusedAt: number | null;
}

/** A highlight as the service has it: which occurrence (from 0), and when it was made (milliseconds). */
export interface RemoteHighlight {
  remoteId: string;
  text: string;
  note: string;
  position?: number;
  createdAt?: number;
}

/** A service that keeps copies of highlights. addHighlight throws a ServiceSyncError "limit" when the plan allows no more. */
export interface HighlightService {
  highlights(remoteId: string): Promise<RemoteHighlight[]>;
  addHighlight(remoteId: string, text: string, note: string, position: number): Promise<RemoteHighlight>;
  removeHighlight(highlightId: string): Promise<void>;
}

/** A highlight refused for a plan's limit is tried again after this long. */
export const RETRY_MS = 24 * 60 * 60 * 1000;

/** Whether an article's highlights need sending: one new, with a changed note, deleted here, or due for a retry. */
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

export interface Synced {
  highlights: Highlight[];
  links: Record<string, HighlightLink>;
  /** Why it stopped early, or null when it got through. */
  problem: string | null;
}

/**
 * Syncs the highlights of the article remoteId at the service: what changed there comes in, then
 * what changed here goes there. Without look, an article with nothing to send costs no call.
 */
export async function syncHighlights(
  service: HighlightService,
  serviceId: string,
  remoteId: string,
  highlights: Highlight[],
  links: Record<string, HighlightLink>,
  now: number,
  look = false,
): Promise<Synced> {
  if (!look && !pendingCopy(highlights, links, now)) return { highlights, links, problem: null };
  let remote: RemoteHighlight[];
  try {
    remote = await service.highlights(remoteId);
  } catch (e) {
    if (e instanceof ServiceSyncError) return { highlights, links, problem: e.problem };
    throw e;
  }
  const taken = takeInHighlights(serviceId, highlights, links, remote);
  return pushHighlights(service, remoteId, taken.highlights, taken.links, now);
}

/**
 * The article's highlights and links after taking in what the service has: one deleted there goes
 * here too, a note changed there (and not here) comes in, one with the same passage as a highlight
 * here is linked to it, and the rest become Content's.
 */
export function takeInHighlights(
  serviceId: string,
  highlights: Highlight[],
  links: Record<string, HighlightLink>,
  remote: RemoteHighlight[],
): { highlights: Highlight[]; links: Record<string, HighlightLink> } {
  const byId = new Map(remote.map((r) => [r.remoteId, r]));
  const out: Record<string, HighlightLink> = { ...links };
  // Deleted there: deleted here too.
  const gone = new Set(Object.entries(links).flatMap(([id, l]) => (l.remoteId !== null && !byId.has(l.remoteId) ? [id] : [])));
  for (const id of gone) delete out[id];
  const result = highlights
    .filter((h) => !gone.has(h.id))
    .map((h) => {
      const link = out[h.id];
      const there = link?.remoteId ? byId.get(link.remoteId) : undefined;
      // A note changed there and not here comes in.
      if (link && there && there.note.trim() !== link.note && h.note === link.note) {
        out[h.id] = { ...link, note: there.note.trim() };
        return { ...h, note: there.note.trim() };
      }
      return h;
    });
  const linked = new Set(Object.values(out).flatMap((l) => (l.remoteId ? [l.remoteId] : [])));
  for (const r of remote) {
    if (linked.has(r.remoteId) || !r.text.trim()) continue;
    const text = fold(r.text);
    const index = result.findIndex((h) => !out[h.id]?.remoteId && fold(h.quote) === text);
    if (index >= 0) {
      // The same passage: the same highlight; a note only there comes in.
      const h = result[index];
      result[index] = { ...h, note: h.note.trim() ? h.note : r.note.trim() };
      out[h.id] = { remoteId: r.remoteId, note: r.note.trim(), refusedAt: null };
    } else {
      const id = `${serviceId}-${r.remoteId}`;
      result.push({ id, quote: r.text.trim(), prefix: "", suffix: "", occurrence: r.position ?? 0, note: r.note.trim(), createdAt: r.createdAt ?? 0 });
      out[id] = { remoteId: r.remoteId, note: r.note.trim(), refusedAt: null };
    }
    linked.add(r.remoteId);
  }
  return { highlights: result, links: out };
}

/** Sends what changed here: new ones made there, changed notes made again, deleted ones deleted there. */
async function pushHighlights(service: HighlightService, remoteId: string, highlights: Highlight[], links: Record<string, HighlightLink>, now: number): Promise<Synced> {
  const out: Record<string, HighlightLink> = { ...links };
  try {
    const ids = new Set(highlights.map((h) => h.id));
    for (const [id, link] of Object.entries(links)) {
      if (ids.has(id)) continue;
      if (link.remoteId !== null) await service.removeHighlight(link.remoteId);
      delete out[id];
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
    if (e instanceof ServiceSyncError) return { highlights, links: out, problem: e.problem };
    throw e;
  }
  return { highlights, links: out, problem: null };
}

/**
 * current (the item's highlights now) with what a sync changed from before to after: added, removed
 * and changed notes. A highlight changed here meanwhile keeps that change.
 */
export function applyHighlights(current: Highlight[], before: Highlight[], after: Highlight[]): Highlight[] {
  const was = new Map(before.map((h) => [h.id, h]));
  const now = new Map(after.map((h) => [h.id, h]));
  const result = current
    .filter((h) => !was.has(h.id) || now.has(h.id))
    .map((h) => {
      const b = was.get(h.id);
      const a = now.get(h.id);
      return b && a && a.note !== b.note && h.note === b.note ? { ...h, note: a.note } : h;
    });
  const have = new Set(result.map((h) => h.id));
  return [...result, ...after.filter((h) => !was.has(h.id) && !have.has(h.id))];
}

/** The highlights the service refused (shown as "Not in Instapaper"). */
export function notCopied(links: Record<string, HighlightLink> | undefined): Set<string> {
  return new Set(Object.entries(links ?? {}).flatMap(([id, l]) => (l.remoteId === null && l.refusedAt !== null ? [id] : [])));
}
