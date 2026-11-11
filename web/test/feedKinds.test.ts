import { describe, expect, it } from "vitest";
import { feedsOfKind, kindOf, withKindOrder } from "../src/lib/feedKinds";
import type { Feed } from "../src/lib/library";

const feed = (url: string, title: string) => ({ url, title, folderId: null }) as Feed;
const rss = feed("https://example.com/feed", "Example");
const other = feed("https://blog.example.org/rss.xml", "Blog");
const letter = feed("https://content-worker.wim.to/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef", "Letter");

describe("RSS feeds and newsletter feeds (feedKinds.ts), as on Android", () => {
  it("a newsletter address's feed is a newsletter feed, any other an RSS feed", () => {
    expect(kindOf(rss)).toBe("rss");
    expect(kindOf(letter)).toBe("newsletter");
    expect(feedsOfKind([rss, letter, other], "rss")).toEqual([rss, other]);
    expect(feedsOfKind([rss, letter, other], "newsletter")).toEqual([letter]);
  });

  it("reordering one kind keeps the other kind's feeds in their places", () => {
    expect(withKindOrder(["a", "N", "b", "c"], ["c", "a", "b"])).toEqual(["c", "N", "a", "b"]);
    expect(withKindOrder(["N1", "a", "N2"], ["N2", "N1"])).toEqual(["N2", "a", "N1"]);
  });

  it("a feed dragged in from another topic keeps its place among its kind", () => {
    expect(withKindOrder(["a", "N", "b"], ["b", "a", "x"])).toEqual(["b", "N", "a", "x"]);
    expect(withKindOrder(["a", "N", "b"], ["b", "x", "a"])).toEqual(["b", "N", "x", "a"]);
    expect(withKindOrder([], ["x"])).toEqual(["x"]);
  });
});
