/**
 * How far a saved article has been read, as on Android (ReaderScreen's ReadingProgress): the
 * share of the text scrolled past, 0 to 1. It syncs with the item (`progress`) and with Instapaper,
 * which keeps the same number.
 */

/** Where the reader is: 0 at the top, 1 at the bottom; null when the page doesn't scroll. */
export function positionOf(scrollTop: number, scrollHeight: number, clientHeight: number): number | null {
  const room = scrollHeight - clientHeight;
  if (room <= 0) return null;
  return Math.min(1, Math.max(0, scrollTop / room));
}

/** Where to scroll back to on opening, or null to start at the top: barely begun or (nearly) finished starts there. */
export function scrollFor(progress: number, scrollHeight: number, clientHeight: number): number | null {
  const room = scrollHeight - clientHeight;
  if (room <= 0 || progress < 0.02 || progress > 0.98) return null;
  return Math.round(room * progress);
}

/** Worth storing: a change of at least a hundredth (what Instapaper gets sent too). */
export const progressChanged = (before: number, after: number) => Math.abs(after - before) >= 0.01;
