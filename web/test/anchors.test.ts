import { describe, expect, it } from "vitest";
import { createHighlight, fold, resolve, type Anchor } from "../src/lib/anchors";
import { addHighlight, itemForLink, removeHighlight, setHighlightNote, type Highlight } from "../src/lib/items";
import { emptyLibrary } from "../src/lib/library";

/** The same cases as the Android app's AnchorsTest, so both find a highlight the same way. */
function highlight(text: string, quote: string, occurrence = 0): Highlight {
  let start = -1;
  for (let i = 0; i <= occurrence; i++) start = text.indexOf(quote, start + 1);
  return createHighlight(text, start, start + quote.length, "h", 1);
}

function found(anchor: Anchor, text: string): string {
  if (anchor.kind === "exact") return text.slice(anchor.start, anchor.end);
  if (anchor.kind === "approximate") return "~" + text.slice(anchor.start, anchor.end);
  return "not found";
}

const bare = (quote: string, occurrence: number): Highlight => ({ id: "h", quote, prefix: "", suffix: "", occurrence, note: "", createdAt: 1 });

describe("anchoring highlights by quote", () => {
  it("keeps its quote, its surroundings and which occurrence it is", () => {
    const text = "The cat sat. The cat ran. The dog sat.";
    const h = highlight(text, "The cat", 1);
    expect(h.quote).toBe("The cat");
    expect(h.prefix).toBe("The cat sat. ");
    expect(h.suffix).toBe(" ran. The dog sat.");
    expect(h.occurrence).toBe(1);
    expect(highlight(text, "The cat").occurrence).toBe(0);
  });

  it("finds it where it was made in the same text", () => {
    const text = "Coffee is good. Tea is better, they say.";
    expect(resolve(highlight(text, "Tea is better"), text)).toEqual({ kind: "exact", start: 16, end: 29 });
  });

  it("ignores typographic variants", () => {
    const made = "He said “it’s fine” — and left… The ﬁnal word.";
    const other = 'He said "it\'s   fine" - and left... The final word.';
    expect(found(resolve(highlight(made, "“it’s fine” — and left…"), other), other)).toBe('"it\'s   fine" - and left...');
    expect(found(resolve(highlight(made, "ﬁnal word"), other), other)).toBe("final word");
    const back = "He said “it’s fine”. The fi­nal word.";
    expect(found(resolve(highlight(other, "it's   fine"), back), back)).toBe("it’s fine");
    expect(found(resolve(highlight(other, "final word"), back), back)).toBe("fi­nal word");
    expect(fold(" a — b ")).toBe("a - b");
  });

  it("uses letter case as a last resort", () => {
    const made = "Chapter One. The beginning of it all.";
    const other = "CHAPTER ONE. The beginning of it all.";
    expect(found(resolve(highlight(made, "Chapter One"), other), other)).toBe("CHAPTER ONE");
  });

  it("tells a repeated quote apart by its surroundings, even when more text came before", () => {
    const made = "Yes. I said yes to the plan. Then no. Later I said yes to the cake.";
    const second = highlight(made, "said yes", 1);
    const other = "Intro: he said yes too. Yes. I said yes to the plan. Then no. Later I said yes to the cake.";
    expect((resolve(second, other) as { start: number }).start).toBe(other.lastIndexOf("said yes"));
    expect((resolve(highlight(made, "said yes"), made) as { start: number }).start).toBe(made.indexOf("said yes"));
    expect((resolve(second, made) as { start: number }).start).toBe(made.lastIndexOf("said yes"));
  });

  it("falls back to the occurrence without telling surroundings", () => {
    expect(resolve(bare("na", 2), "na na na na")).toEqual({ kind: "exact", start: 6, end: 8 });
    expect(resolve(bare("na", 9), "na na na na")).toEqual({ kind: "exact", start: 0, end: 2 });
  });

  it("finds a changed quote roughly, between its surroundings or by its first and last words", () => {
    const made = "Some opening words here. The quick brown fox jumps over the lazy dog. And then the story goes on.";
    const other = "Some opening words here. The quick red fox leapt over the lazy dog. And then the story goes on.";
    expect(found(resolve(highlight(made, "The quick brown fox jumps over the lazy dog."), other), other)).toBe("~The quick red fox leapt over the lazy dog.");
    const made2 = "Intro text that will change. The quick brown fox jumps over the lazy dog today. Outro that changes.";
    const other2 = "Completely new intro. The quick brown fox leaps across the lazy dog today. Different end.";
    expect(found(resolve(highlight(made2, "The quick brown fox jumps over the lazy dog today."), other2), other2)).toBe("~The quick brown fox leaps across the lazy dog today.");
  });

  it("keeps a quote that isn't in the text, as not found", () => {
    const h = highlight("Alpha beta gamma delta epsilon.", "gamma delta");
    expect(resolve(h, "Something else entirely, nothing in common at all.")).toEqual({ kind: "notFound" });
    expect(resolve({ ...h, quote: " " }, "gamma delta")).toEqual({ kind: "notFound" });
  });
});

describe("highlights on an item", () => {
  it("saves a feed article to To read with its first highlight; notes change and highlights go", () => {
    const h = bare("Story", 0);
    const lib = addHighlight(emptyLibrary(), "https://www.example.com/story/", "Title", h, 100);
    const item = itemForLink(lib, "https://www.example.com/story/")!;
    expect(item.readingList?.state).toBe("toread");
    expect(item.highlights).toEqual([h]);
    const noted = setHighlightNote(lib, item.key, "h", " why ");
    expect(itemForLink(noted, item.url)!.highlights[0].note).toBe("why");
    const removed = removeHighlight(noted, item.key, "h");
    expect(itemForLink(removed, item.url)!.highlights).toEqual([]);
    expect(itemForLink(removed, item.url)!.readingList).not.toBeNull();
    expect(addHighlight(emptyLibrary(), "mailto:x@example.com", null, h, 1).items).toEqual([]);
  });
});
