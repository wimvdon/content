import { articleKey, type Article } from "./library";

/**
 * "Mark as read on scroll", like the Android app's ScrollRead: the unread articles above the
 * first one still on screen that were on screen before (seen, #157). Articles only count once they
 * have scrolled off the top, so the one at the top of the list is not marked yet, and new ones
 * that arrive above it (a refresh, another device) are never marked unseen.
 */
export function scrolledPast(articles: Article[], firstVisible: number, seen: Set<string>): Set<string> {
  return new Set(
    articles
      .slice(0, Math.max(0, firstVisible))
      .filter((a) => !a.read && seen.has(articleKey(a)))
      .map(articleKey),
  );
}

/**
 * Whether articles arrived above the ones on screen (#394), like the Android app's
 * ScrollRead.arrivedAbove: a refresh on coming back, or another device. The list keeps its place,
 * so they would be out of sight above it and Mark all as read would mark them unseen; the list
 * goes back to the top instead. `before` and `after` are the list's article keys, `visible` the
 * keys on screen from the top. Nothing before a first list.
 */
export function arrivedAbove(before: string[], after: string[], visible: string[]): boolean {
  if (before.length === 0) return false;
  const known = new Set(before);
  const anchor = visible.find((k) => known.has(k));
  if (anchor === undefined) return false;
  const at = after.indexOf(anchor);
  return at > 0 && after.slice(0, at).some((k) => !known.has(k));
}

/**
 * The end of Feeds' Unread list with Mark as read on scroll on, like the Android app's ListEnd:
 * All caught up after the last article, as tall as the list's window, so the last articles can
 * scroll off the top (and be marked read) too. Only in Unread (All keeps read articles, so it
 * never empties) and only under articles.
 */
export function showsCaughtUpEnd(markReadOnScroll: boolean, unreadOnly: boolean, articles: number): boolean {
  return markReadOnScroll && unreadOnly && articles > 0;
}

/** How far from the list's top All caught up starts to show: the last stretch of scrolling, in px. */
export const CAUGHT_UP_FADE_DISTANCE = 240;

/** How long All caught up's check takes to settle before the All caught up page takes over, in ms. */
export const CAUGHT_UP_POP_MS = 450;

/** All caught up's smallest height, for a very short window. */
export const CAUGHT_UP_MIN_HEIGHT = 240;

/**
 * How far into the last stretch All caught up is, from where its top is (`top`, px from the list's
 * top): 0 while it is further down than `fadeDistance` (under the last articles), 1 at the top. With
 * only a few articles it starts closer to the top than that (`restTop`, where it is before
 * scrolling): then the stretch is the scroll there is, so nothing shows before you scroll.
 */
export function caughtUpProgress(top: number, fadeDistance: number, restTop: number): number {
  const distance = Math.min(fadeDistance, restTop);
  if (distance <= 0) return 1;
  return Math.min(1, Math.max(0, 1 - top / distance));
}

/** How visible All caught up is at `progress`: in slowly (eased), fully there at the top. */
export const caughtUpAlpha = (progress: number): number => progress * progress;

/**
 * Scrolled all the way into All caught up: its top is at the list's top (a pixel of rounding
 * allowed), so every article has gone past the top.
 */
export const reachedCaughtUp = (top: number): boolean => top <= 1;

/** How tall All caught up is: the list's window minus its padding at the end, never less than the minimum. */
export function caughtUpHeight(viewportHeight: number, endPadding: number, minimum = CAUGHT_UP_MIN_HEIGHT): number {
  return Math.max(viewportHeight - endPadding, minimum);
}
