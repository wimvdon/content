/**
 * The two kinds of feed in Feed (2026-11-11): an RSS feed (a website's feed) and a newsletter feed
 * (a newsletter address's, newsletterAddresses.ts). Settings has a large button for each, and each
 * opens Manage feeds with only that kind. The same as the Android app's FeedKinds.kt.
 */

import type { Feed } from "./library";
import { codeOfFeed } from "./newsletterAddresses";

export type FeedKind = "rss" | "newsletter";

export function kindOf(feed: Feed): FeedKind {
  return codeOfFeed(feed.url) ? "newsletter" : "rss";
}

/** The feeds of one kind, in their order. */
export function feedsOfKind(feeds: Feed[], kind: FeedKind): Feed[] {
  return feeds.filter((f) => kindOf(f) === kind);
}

/**
 * A topic's feeds in their new order after one kind's feeds were dragged in Manage feeds, which
 * shows only that kind: those take the places of that kind in the topic (`topicUrls`, the topic's
 * feeds as they are), so the other kind's feeds stay where they were. A feed of `kindUrls` new to
 * the topic (dragged in from another one) counts too: the order among the kind is `kindUrls`, and
 * what doesn't fit in the kind's places comes at the end.
 */
export function withKindOrder(topicUrls: string[], kindUrls: string[]): string[] {
  const shown = new Set(kindUrls);
  let next = 0;
  const placed = topicUrls.map((u) => (shown.has(u) && next < kindUrls.length ? kindUrls[next++] : u));
  return [...placed, ...kindUrls.slice(next)];
}
