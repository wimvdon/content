import { describe, expect, it } from "vitest";
import { websiteFor } from "../src/lib/articleOpening";
import type { Article } from "../src/lib/library";

const article = (link: string | null, feedUrl = "https://a.example.com/feed"): Article => ({ feedUrl, id: "1", title: "A", link, author: null, html: "", publishedAt: 1, fetchedAt: 0, read: false, imageUrl: null });

describe("Always open the website", () => {
  it("opens a feed article's website only with the setting on", () => {
    expect(websiteFor(true, article(" https://a.example.com/1 "))).toBe("https://a.example.com/1");
    expect(websiteFor(false, article("https://a.example.com/1"))).toBeNull();
  });

  it("keeps the reader without a web link", () => {
    expect(websiteFor(true, article(null))).toBeNull();
    expect(websiteFor(true, article("mailto:someone@example.com"))).toBeNull();
  });

  it("still opens a saved article in the reader", () => {
    expect(websiteFor(true, article("https://a.example.com/1", "item"))).toBeNull();
  });
});
