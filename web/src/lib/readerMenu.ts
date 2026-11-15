/**
 * What the reader's ⋮ menu offers, in this order, as on Android (`ReaderMenu`). A feed article not
 * saved: Mark as read or unread, the feed's text or the full article. Once saved (also right after
 * Save in the reader) it is a saved article's menu: neither of those, and Remove from Saved at the
 * end. Both: Open website and Report a problem, with a link. A newsletter feed's article
 * (2026-11-12) is the whole email already: no text switch, and its link is the email itself as a
 * page, so Open website reads Show original email; and no Report a problem (2026-11-15), whose
 * public report would hold the private email and its link. A newsletter offers Unsubscribe
 * instead (2026-11-15), after Show original email.
 */
export type ReaderMenuEntry = "markRead" | "toggleText" | "openWebsite" | "originalEmail" | "unsubscribe" | "report" | "remove";

export function readerMenu({
  feedArticle,
  saved,
  hasLink,
  canLoad,
  newsletter = false,
  canUnsubscribe = false,
}: {
  feedArticle: boolean;
  saved: boolean;
  hasLink: boolean;
  canLoad: boolean;
  /** An article of a newsletter feed (a newsletter address's): its text is the whole newsletter. */
  newsletter?: boolean;
  /** A newsletter feed's email, whose sender the feed loader can unsubscribe from (unsubscribe.ts). */
  canUnsubscribe?: boolean;
}): ReaderMenuEntry[] {
  const entries: ReaderMenuEntry[] = [];
  if (feedArticle && !saved) entries.push("markRead");
  if (canLoad && !saved && !newsletter) entries.push("toggleText");
  if (hasLink) entries.push(newsletter ? "originalEmail" : "openWebsite");
  if (newsletter && canUnsubscribe) entries.push("unsubscribe");
  if (hasLink && !newsletter) entries.push("report");
  if (saved) entries.push("remove");
  return entries;
}
