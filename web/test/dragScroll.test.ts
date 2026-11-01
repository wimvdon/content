import { describe, expect, it } from "vitest";
import { DRAG_SLOP, dragMove, dragStart } from "../src/lib/dragScroll";

describe("dragging the chip row", () => {
  it("stays a click while the pointer moves less than the slop", () => {
    const { drag, scrollLeft } = dragMove(dragStart(100, 40), 100 + DRAG_SLOP - 1);
    expect(drag.dragging).toBe(false);
    expect(scrollLeft).toBeNull();
  });

  it("follows the pointer once past the slop: left scrolls the row right", () => {
    const { drag, scrollLeft } = dragMove(dragStart(100, 40), 70);
    expect(drag.dragging).toBe(true);
    expect(scrollLeft).toBe(70);
  });

  it("keeps dragging when the pointer comes back near where it started", () => {
    const first = dragMove(dragStart(100, 40), 80).drag;
    const { drag, scrollLeft } = dragMove(first, 102);
    expect(drag.dragging).toBe(true);
    expect(scrollLeft).toBe(38);
  });
});
