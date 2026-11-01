/**
 * Dragging a sideways row (the chips) with a mouse or pen: the row follows the pointer, as a finger
 * does on a touch screen. A press that moves less than [DRAG_SLOP] pixels stays a click.
 */
export const DRAG_SLOP = 5;

export type Drag = {
  /** Where the press started (clientX) and the row's scroll then. */
  startX: number;
  startScroll: number;
  /** Moved far enough to be a drag: the click that ends it is not a click. */
  dragging: boolean;
};

export function dragStart(x: number, scrollLeft: number): Drag {
  return { startX: x, startScroll: scrollLeft, dragging: false };
}

/** The pointer moved to [x]: the drag (dragging once past the slop) and the row's new scroll. */
export function dragMove(drag: Drag, x: number): { drag: Drag; scrollLeft: number | null } {
  const dx = x - drag.startX;
  const dragging = drag.dragging || Math.abs(dx) >= DRAG_SLOP;
  return { drag: dragging === drag.dragging ? drag : { ...drag, dragging }, scrollLeft: dragging ? drag.startScroll - dx : null };
}
