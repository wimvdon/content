/**
 * A message at the bottom belongs to the view it was shown on (2026-11-02), as on Android
 * (`LatestOnly`): moving to another one (a feed, Saved, the inbox, Search, another article) closes
 * it; one shown as part of the move (Removed, after the article closes) stays. A click elsewhere
 * closes it too.
 */

/** The view a message belongs to: what is chosen, the inbox, Search and the open article. */
export function viewKey(selection: unknown, inboxOpen: boolean, searching: boolean, openArticle: string | null): string {
  return JSON.stringify([selection, inboxOpen, searching, openArticle]);
}

/** The view shown and the message showing on it (null: none). */
export type Shown = { view: string; message: unknown };

/**
 * Whether the message showing closes: the view changed ([before] → [now]) while the same message
 * stayed. A new message that came with the move belongs to the new view.
 */
export function closesOnMove(before: Shown | null, now: Shown): boolean {
  return before !== null && now.message !== null && before.message === now.message && before.view !== now.view;
}
