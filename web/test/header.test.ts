import { describe, expect, it } from "vitest";
import { headerHidden } from "../src/lib/header";

describe("headerHidden", () => {
  it("hides while scrolling down and shows while scrolling up", () => {
    expect(headerHidden(false, 300, 20, false)).toBe(true);
    expect(headerHidden(true, 280, -20, false)).toBe(false);
  });

  it("always shows near the top", () => {
    expect(headerHidden(true, 4, 20, false)).toBe(false);
  });

  it("ignores tiny moves and the browser settling at the end", () => {
    expect(headerHidden(true, 300, 2, false)).toBe(true);
    expect(headerHidden(false, 300, -2, false)).toBe(false);
    expect(headerHidden(true, 900, -30, true)).toBe(true);
  });
});
