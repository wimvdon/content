/**
 * Whether the list's header (search bar, tabs, chips) is hidden after a scroll, as on Android
 * (CollapsingHeader, "enter always"): scrolling down hides it, scrolling up shows it, and near the
 * top it always shows. At the end of the list a small move up is the browser settling, not the
 * reader: it stays hidden. [by] is how far the list moved (positive: down), in pixels.
 */
export function headerHidden(hidden: boolean, top: number, by: number, atEnd: boolean): boolean {
  if (top <= 8) return false;
  if (by > 4) return true;
  if (by < -4 && !atEnd) return false;
  return hidden;
}

/**
 * Whether the reader's floating toolbar is hidden after a scroll, as on Android (ReaderToolbar): it
 * steps aside while reading down and comes back when scrolling up, near the top, and at the end of
 * the article (where Archive is what you want next).
 */
export function toolbarHidden(hidden: boolean, top: number, by: number, atEnd: boolean): boolean {
  if (atEnd) return false;
  return headerHidden(hidden, top, by, false);
}
