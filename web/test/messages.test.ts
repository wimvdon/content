import { describe, expect, it } from "vitest";
import { closesOnMove, viewKey } from "../src/lib/messages";

describe("Messages close when you move away", () => {
  const feeds = viewKey({ kind: "all" }, false, false, null);
  const reader = viewKey({ kind: "all" }, false, false, "a 1");
  const saved = { text: "Saved to To read" };
  const removed = { text: "Removed" };

  it("closes a message when the view changes under it", () => {
    expect(closesOnMove({ view: reader, message: saved }, { view: feeds, message: saved })).toBe(true);
  });

  it("keeps a message that came with the move, one on the same view, and does nothing without one", () => {
    // Removed from the reader: the article closes and the message shows at once.
    expect(closesOnMove({ view: reader, message: null }, { view: feeds, message: removed })).toBe(false);
    expect(closesOnMove({ view: reader, message: saved }, { view: feeds, message: removed })).toBe(false);
    expect(closesOnMove({ view: feeds, message: saved }, { view: feeds, message: saved })).toBe(false);
    expect(closesOnMove({ view: reader, message: null }, { view: feeds, message: null })).toBe(false);
    expect(closesOnMove(null, { view: feeds, message: saved })).toBe(false);
  });

  it("tells views apart by what is chosen, the inbox, Search and the open article", () => {
    expect(viewKey({ kind: "saved" }, false, false, null)).not.toBe(feeds);
    expect(viewKey({ kind: "all" }, true, false, null)).not.toBe(feeds);
    expect(viewKey({ kind: "all" }, false, true, null)).not.toBe(feeds);
    expect(reader).not.toBe(feeds);
    expect(viewKey({ kind: "all" }, false, false, null)).toBe(feeds);
  });
});
