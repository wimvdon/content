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
