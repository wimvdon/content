import { describe, expect, it } from "vitest";
import { armed, atEnd, drag, newWheelGesture, progress } from "../src/lib/pullToMark";

describe("pullToMark", () => {
  it("fills at half the distance and arms at the threshold", () => {
    expect(drag(0, 100, 100)).toBe(50);
    expect(progress(50, 100)).toBe(0.5);
    expect(armed(50, 100)).toBe(false);
    expect(armed(drag(50, 100, 100), 100)).toBe(true);
  });

  it("gives way going back, never below nothing or past its limit", () => {
    expect(drag(60, -100, 100)).toBe(10);
    expect(drag(10, -100, 100)).toBe(0);
    expect(drag(140, 1000, 100)).toBe(150);
    expect(progress(50, 0)).toBe(0);
  });

  it("knows the end of the list and a new wheel gesture", () => {
    expect(atEnd(900, 100, 1000)).toBe(true);
    expect(atEnd(899.5, 100, 1000)).toBe(true);
    expect(atEnd(800, 100, 1000)).toBe(false);
    expect(newWheelGesture(1000, 700)).toBe(true);
    expect(newWheelGesture(1000, 900)).toBe(false);
  });
});
