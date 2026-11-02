import { describe, expect, it } from "vitest";
import { hiddenPreview, hiddenWords, hidesTitle, withoutHidden } from "../src/lib/articleFilter";
import { emptyLibrary, type Article } from "../src/lib/library";

const article = (id: string, title: string): Article => ({ feedUrl: "https://a.example.com/feed", id, title, link: null, author: null, html: "", publishedAt: Number(id), fetchedAt: 0, read: false, imageUrl: null });

describe("Hide articles with words", () => {
  it("splits on commas, semicolons and new lines, trimmed, without empty ones or duplicates", () => {
    expect(hiddenWords(" Crypto, apple   watch\n\n;AI ,crypto,\r\n ")).toEqual(["crypto", "apple watch", "ai"]);
    expect(hiddenWords(" , \n ")).toEqual([]);
  });

  it("matches whole words and phrases only, case ignored, accented letters as letters", () => {
    const words = hiddenWords("ai, apple watch, café");
    expect(hidesTitle(words, "New AI rules")).toBe(true);
    expect(hidesTitle(words, "ai: a recap")).toBe(true);
    expect(hidesTitle(words, "He said so")).toBe(false);
    expect(hidesTitle(words, "Thailand")).toBe(false);
    expect(hidesTitle(words, "The Apple Watch review")).toBe(true);
    expect(hidesTitle(words, "Cafés open late")).toBe(false);
    expect(hidesTitle(words, "Le café du coin")).toBe(true);
  });

  it("leaves hidden articles out of what the lists show", () => {
    const library = { ...emptyLibrary(), articles: [article("1", "AI news"), article("2", "Gardening")] };
    expect(withoutHidden(library, hiddenWords("ai")).articles.map((a) => a.id)).toEqual(["2"]);
    expect(withoutHidden(library, [])).toBe(library);
  });

  it("previews how many the words hide, the newest first", () => {
    const articles = [1, 2, 3, 4, 5, 6, 7, 8].map((n) => article(String(n), n % 2 === 0 ? `AI news ${n}` : `Gardening ${n}`));
    const preview = hiddenPreview(articles, hiddenWords("ai"), 3);
    expect(preview.count).toBe(4);
    expect(preview.newest.map((a) => a.id)).toEqual(["8", "6", "4"]);
    expect(hiddenPreview(articles, [])).toEqual({ count: 0, newest: [] });
  });
});
