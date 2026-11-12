/**
 * What the reader's ⋮ menu offers, in this order, as on Android (`ReaderMenu`). A feed article not
 * saved: Mark as read or unread, the feed's text or the full article. Once saved (also right after
 * Save in the reader) it is a saved article's menu: neither of those, and Remove from Saved at the
 * end. Both: Open website and Report a problem, with a link. A newsletter feed's article
 * (2026-11-12) is the whole email already: no text switch.
 */
export type ReaderMenuEntry = "markRead" | "toggleText" | "openWebsite" | "report" | "remove";

export function readerMenu({
  feedArticle,
  saved,
  hasLink,
  canLoad,
  newsletter = false,
}: {
  feedArticle: boolean;
  saved: boolean;
  hasLink: boolean;
  canLoad: boolean;
  /** An article of a newsletter feed (a newsletter address's): its text is the whole newsletter. */
  newsletter?: boolean;
}): ReaderMenuEntry[] {
  const entries: ReaderMenuEntry[] = [];
  if (feedArticle && !saved) entries.push("markRead");
  if (canLoad && !saved && !newsletter) entries.push("toggleText");
  if (hasLink) entries.push("openWebsite", "report");
  if (saved) entries.push("remove");
  return entries;
}
