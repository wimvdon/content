/**
 * What the reader's ⋮ menu offers, in this order, as on Android (`ReaderMenu`). A feed article not
 * saved: Mark as read or unread, the feed's text or the full article. Once saved (also right after
 * Save in the reader) it is a saved article's menu: neither of those, and Remove from Saved at the
 * end. Both: Open website and Report a problem, with a link.
 */
export type ReaderMenuEntry = "markRead" | "toggleText" | "openWebsite" | "report" | "remove";

export function readerMenu({ feedArticle, saved, hasLink, canLoad }: { feedArticle: boolean; saved: boolean; hasLink: boolean; canLoad: boolean }): ReaderMenuEntry[] {
  const entries: ReaderMenuEntry[] = [];
  if (feedArticle && !saved) entries.push("markRead");
  if (canLoad && !saved) entries.push("toggleText");
  if (hasLink) entries.push("openWebsite", "report");
  if (saved) entries.push("remove");
  return entries;
}
