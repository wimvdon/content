/**
 * Scrolling on past the end of the list to mark it all as read (2026-11-03), as on Android: the
 * further you go, the fuller the button's ring; past THRESHOLD it is filled and says to let go,
 * and letting go (lifting the finger, or the wheel coming to rest) marks all as read. A wheel's
 * gesture only counts when it starts at the end, so the coasting of a fast scroll that reaches the
 * end doesn't mark anything. Plain functions, tested in Vitest.
 */

/** How far to go, in pixels, before letting go marks all as read. */
export const THRESHOLD = 96;

/** The pull follows at half the distance, as an overscroll does. */
export const RESISTANCE = 0.5;

/** A pause this long (ms) between wheel events starts a new gesture; one this long ends it. */
export const WHEEL_GAP = 250;

const MAX = 1.5;

/** The pull after moving `delta` pixels past the end (positive: further on; negative: back). */
export function drag(pull: number, delta: number, threshold = THRESHOLD): number {
  return Math.min(threshold * MAX, Math.max(0, pull + delta * RESISTANCE));
}

export function progress(pull: number, threshold = THRESHOLD): number {
  return threshold <= 0 ? 0 : Math.min(1, Math.max(0, pull / threshold));
}

export function armed(pull: number, threshold = THRESHOLD): boolean {
  return threshold > 0 && pull >= threshold;
}

/** Whether a scroller is at its end (a pixel of rounding allowed). */
export function atEnd(scrollTop: number, clientHeight: number, scrollHeight: number): boolean {
  return scrollTop + clientHeight >= scrollHeight - 1;
}

/** Whether a wheel event at `now` starts a new gesture, after the previous one at `last`. */
export function newWheelGesture(now: number, last: number): boolean {
  return now - last > WHEEL_GAP;
}
