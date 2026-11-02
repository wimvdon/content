import { describe, expect, it } from "vitest";
import { entriesText, hiddenPreview, hiddenWords, hidesTitle, withEntry, withoutEntry, withoutHidden } from "../src/lib/articleFilter";
import { emptyLibrary, newFeed, type Article } from "../src/lib/library";

const article = (id: string, title: string): Article => ({ feedUrl: "https://a.example.com/feed", id, title, link: null, author: null, html: "", publishedAt: Number(id), fetchedAt: 0, read: false, imageUrl: null });

describe("Hide articles with words", () => {
  it("splits on commas, semicolons and new lines, trimmed, without empty ones or duplicates", () => {
    expect(hiddenWords(" Crypto, apple   watch\n\n;AI ,crypto,\r\n ")).toEqual(["crypto", "apple watch", "ai"]);
    expect(hiddenWords(" , \n ")).toEqual([]);
  });

  it("adds entries as groups, each once, removes them one by one and stores them one per line", () => {
    const one = withEntry([], "  WIRED   coupon code ");
    expect(one).toEqual(["wired coupon code"]);
    const two = withEntry(one, "Crypto");
    expect(two).toEqual(["wired coupon code", "crypto"]);
    expect(withEntry(two, "CRYPTO")).toEqual(two);
    expect(withEntry(two, "  ")).toEqual(two);
    expect(withEntry(two, "AI, apple watch")).toEqual(["wired coupon code", "crypto", "ai", "apple watch"]);
    expect(withoutEntry(two, "wired coupon code")).toEqual(["crypto"]);
    const text = entriesText(two);
    expect(text).toBe("wired coupon code\ncrypto");
    expect(hiddenWords(text)).toEqual(two);
    expect(hidesTitle(hiddenWords(text), "Best Buy coupon code", "WIRED")).toBe(true);
    expect(hidesTitle(hiddenWords(text), "Best Buy coupon code", "The Verge")).toBe(false);
  });

  it("needs every word of an entry, in the title or the feed's name", () => {
    const words = hiddenWords("WIRED coupon code");
    expect(hidesTitle(words, "Best Buy coupon code: 20% off", "WIRED")).toBe(true);
    // WIRED's own titles have the plural; possessives count too; other words that start with it don't.
    expect(hidesTitle(words, "Kohl's Coupon Codes: 20% Off | November 2026", "WIRED")).toBe(true);
    expect(hidesTitle(hiddenWords("kohl"), "Kohl’s deals")).toBe(true);
    expect(hidesTitle(hiddenWords("box"), "Lunch boxes compared")).toBe(true);
    expect(hidesTitle(hiddenWords("code"), "Coders at work")).toBe(false);
    expect(hidesTitle(words, "Best Buy coupon code: 20% off", "The Verge")).toBe(false);
    expect(hidesTitle(words, "The best laptops", "WIRED")).toBe(false);
    expect(hidesTitle(hiddenWords("wired"), "The best laptops", "WIRED")).toBe(true);
    expect(hidesTitle(hiddenWords("apple watch"), "Apple announces a watch")).toBe(true);
    const library = { ...emptyLibrary(), feeds: [{ ...newFeed("https://a.example.com/feed", "WIRED") }], articles: [article("1", "Coupon code for shoes"), article("2", "Phones")] };
    expect(withoutHidden(library, words).articles.map((a) => a.id)).toEqual(["2"]);
  });

  it("matches whole words (and their plurals) only, case ignored, accented letters as letters", () => {
    const words = hiddenWords("ai, apple watch, café");
    expect(hidesTitle(words, "New AI rules")).toBe(true);
    expect(hidesTitle(words, "ai: a recap")).toBe(true);
    expect(hidesTitle(words, "He said so")).toBe(false);
    expect(hidesTitle(words, "Thailand")).toBe(false);
    expect(hidesTitle(words, "The Apple Watch review")).toBe(true);
    // Its plural counts, a longer word with accents doesn't.
    expect(hidesTitle(words, "Cafés open late")).toBe(true);
    expect(hidesTitle(words, "La cafétéria ferme")).toBe(false);
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
