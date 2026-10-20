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
