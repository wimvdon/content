import { describe, expect, it } from "vitest";
import { showsUnreadDot } from "../src/lib/library";

describe("the unread dot on Feeds' chips, as on Android", () => {
  it("shows only in All, on a chip with something unread", () => {
    expect(showsUnreadDot("all", 3)).toBe(true);
    expect(showsUnreadDot("all", 0)).toBe(false);
    // In Unread every chip shown has something unread: a dot would say nothing.
    expect(showsUnreadDot("unread", 3)).toBe(false);
  });
});
