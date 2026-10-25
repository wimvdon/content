import { describe, expect, it } from "vitest";
import { positionOf, progressChanged, scrollFor } from "../src/lib/readingProgress";

describe("reading progress of a saved article, as on Android", () => {
  it("is the share of the text scrolled past", () => {
    expect(positionOf(0, 3000, 1000)).toBe(0);
    expect(positionOf(1000, 3000, 1000)).toBe(0.5);
    expect(positionOf(2000, 3000, 1000)).toBe(1);
    expect(positionOf(2500, 3000, 1000)).toBe(1);
    expect(positionOf(0, 800, 1000)).toBeNull();
  });

  it("scrolls back to it on opening, unless barely begun or finished", () => {
    expect(scrollFor(0.5, 3000, 1000)).toBe(1000);
    expect(scrollFor(0.01, 3000, 1000)).toBeNull();
    expect(scrollFor(0.99, 3000, 1000)).toBeNull();
    expect(scrollFor(0.5, 800, 1000)).toBeNull();
  });

  it("stores a change of a hundredth or more", () => {
    expect(progressChanged(0.5, 0.505)).toBe(false);
    expect(progressChanged(0.5, 0.52)).toBe(true);
  });
});
