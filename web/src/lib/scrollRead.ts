import { articleKey, type Article } from "./library";

/**
 * "Mark as read on scroll", like the Android app's ScrollRead: the unread articles above the
 * first one still on screen. Articles only count once they have scrolled off the top, so the one
 * at the top of the list is not marked yet.
 */
export function scrolledPast(articles: Article[], firstVisible: number): Set<string> {
  return new Set(
    articles
      .slice(0, Math.max(0, firstVisible))
      .filter((a) => !a.read)
      .map(articleKey),
  );
}
