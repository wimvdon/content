import { describe, expect, it } from "vitest";
import { resolveText } from "../src/lib/sharedText";

function account() {
  const texts = new Map<string, string>();
  let offline = false;
  let extracted = 0;
  const open = (key: string, page: string | null = "<p>This browser's text</p>", saved = true) =>
    resolveText(
      saved,
      async () => {
        if (offline) throw new Error("offline");
        return texts.get(key) ?? null;
      },
      async () => {
        extracted++;
        return page;
      },
      async (html) => {
        if (offline) throw new Error("offline");
        if (!texts.has(key)) texts.set(key, html);
        return texts.get(key)!;
      },
    );
  return { texts, open, goOffline: () => (offline = true), extractions: () => extracted };
}

describe("one text per saved article (sharedText.ts)", () => {
  it("stores the first device's text, and every device shows that one", async () => {
    const a = account();
    expect(await a.open("x", "<p>Phone</p>")).toBe("<p>Phone</p>");
    const before = a.extractions();
    expect(await a.open("x", "<p>Web</p>")).toBe("<p>Phone</p>");
    expect(a.extractions()).toBe(before);
  });

  it("shows this browser's own text when the account can't be reached", async () => {
    const a = account();
    a.goOffline();
    expect(await a.open("y")).toBe("<p>This browser's text</p>");
    expect(a.texts.size).toBe(0);
  });

  it("doesn't store articles that aren't saved, or pages without an article", async () => {
    const a = account();
    expect(await a.open("z", "<p>Feed only</p>", false)).toBe("<p>Feed only</p>");
    expect(await a.open("w", null)).toBeNull();
    expect(a.texts.size).toBe(0);
  });
});
