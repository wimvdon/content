import { describe, expect, it } from "vitest";
import { removeTopic } from "../src/lib/topics";
import type { Article, Feed, Folder, Library } from "../src/lib/library";
import {
  chosenTag,
  newsletterActivity,
  NO_TAG,
  addressesOf,
  addressOf,
  codeOfFeed,
  forTag,
  isNewsletterFeed,
  missingTags,
  newsletterRows,
  ORPHAN_GRACE_MS,
  orphans,
  reconcile,
  tagHash,
  tagHashes,
  type NewsletterAddress,
} from "../src/lib/newsletterAddresses";

const feedOf = (code: string) => `https://w.example/newsletters/${code}/0123456789abcdef0123456789abcdef`;
const tech: Folder = { id: "1", title: "Tech", icon: "folder" };
const news: Folder = { id: "topic:news", title: "News", icon: "folder" };
const feed = (url: string, title: string, folderId: string | null = null, renamed = false): Feed => ({ url, title, siteUrl: null, folderId, iconUrl: null, renamed });
const lib = (feeds: Feed[], folders: Folder[]): Library => ({ feeds, folders, articles: [], items: [], readElsewhere: {} }) as unknown as Library;

async function address(code: string, tagId: string | null, createdAt = 0): Promise<NewsletterAddress> {
  return { code, tagHash: await tagHash(tagId), address: `content+${code}@in.wim.to`, feedUrl: feedOf(code), createdAt };
}

describe("newsletter addresses (newsletterAddresses.ts), as on Android", () => {
  it("recognises a newsletter address's feed, and reads the feed loader's answer", () => {
    const url = feedOf("k7q2m9abcdef");
    expect(codeOfFeed(url)).toBe("k7q2m9abcdef");
    expect(isNewsletterFeed(url)).toBe(true);
    expect(codeOfFeed("https://example.com/feed.xml")).toBeNull();
    const hash = "a".repeat(64);
    expect(addressesOf({ addresses: [{ code: "k7q2m9abcdef", tagHash: hash, address: "a@b", feedUrl: url, createdAt: 5 }, { code: "x" }] })).toEqual([
      { code: "k7q2m9abcdef", tagHash: hash, address: "a@b", feedUrl: url, createdAt: 5 },
    ]);
    expect(addressOf({ code: "k7q2m9abcdef", tagHash: null, address: "a", feedUrl: url })?.tagHash).toBeNull();
    // A feed address that doesn't belong to the code is refused.
    expect(addressOf({ code: "aaaaaaaaaaaa", address: "a", feedUrl: url })).toBeNull();
  });

  it("knows a tag only by a hash of its id, the same as Android's", async () => {
    expect(await tagHash("abc")).toBe("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    expect(await tagHash("topic:news")).not.toContain("news");
    expect(await tagHash(null)).toBeNull();
  });

  it("asks once for every tag's and No tag's address", async () => {
    const hashes = await tagHashes([tech, news]);
    const made = [await address("aaaaaaaaaaaa", "1")];
    expect(missingTags([tech, news], hashes, made)).toEqual(["topic:news", null]);
    expect(missingTags([tech], hashes, [...made, await address("bbbbbbbbbbbb", null)])).toEqual([]);
    expect(forTag(made, hashes, "1")?.code).toBe("aaaaaaaaaaaa");
    expect(forTag(made, hashes, null)).toBeUndefined();
  });

  it("removes an address whose tag is gone after a day, never the one without a tag", async () => {
    const list = [await address("aaaaaaaaaaaa", "gone", 0), await address("bbbbbbbbbbbb", "new", 2 * ORPHAN_GRACE_MS), await address("cccccccccccc", null), await address("dddddddddddd", "1")];
    expect(orphans(await tagHashes([tech]), list, 2 * ORPHAN_GRACE_MS + 1).map((a) => a.code)).toEqual(["aaaaaaaaaaaa"]);
  });

  it("gives the library one Newsletters feed per tag, under that tag", async () => {
    const a = await address("aaaaaaaaaaaa", "1");
    const b = await address("bbbbbbbbbbbb", null);
    const blog = feed("https://example.com/feed.xml", "Blog", "1");
    const hashes = await tagHashes([tech]);
    const result = reconcile(lib([blog], [tech]), hashes, [a, b], "Newsletters");
    expect(result.feeds.map((f) => f.url)).toEqual([blog.url, a.feedUrl, b.feedUrl]);
    const tagged = result.feeds.find((f) => f.url === a.feedUrl)!;
    expect(tagged).toMatchObject({ folderId: "1", title: "Newsletters", renamed: true, tags: ["Tech"] });
    expect(result.feeds.find((f) => f.url === b.feedUrl)!.folderId).toBeNull();
    // Nothing to do: the same library.
    expect(reconcile(result, hashes, [a, b], "Nieuwsbrieven")).toBe(result);
  });

  it("removes newsletter feeds of no address or of a tag that is gone; a moved one goes back", async () => {
    const a = await address("aaaaaaaaaaaa", "1");
    const gone = await address("bbbbbbbbbbbb", "gone");
    const start = lib([feed(a.feedUrl, "Newsletters", "topic:news", true), feed(gone.feedUrl, "Newsletters", null, true), feed(feedOf("cccccccccccc"), "Made by name", "1")], [tech, news]);
    const result = reconcile(start, await tagHashes([tech, news]), [a, gone], "Newsletters");
    expect(result.feeds.map((f) => [f.url, f.folderId])).toEqual([[a.feedUrl, "1"]]);
  });

  it("removing a tag removes its newsletter feed; its other feeds stay without a tag", () => {
    const newsletter = feed(feedOf("aaaaaaaaaaaa"), "Newsletters", "1", true);
    const blog = feed("https://example.com/feed.xml", "Blog", "1");
    const removed = removeTopic(lib([newsletter, blog], [tech]), "1");
    expect(removed.feeds.map((f) => [f.url, f.folderId])).toEqual([[blog.url, null]]);
  });

  it("lists every tag in its order, then No tag, each with its address", async () => {
    const a = await address("aaaaaaaaaaaa", "topic:news");
    const rows = newsletterRows([tech, news], await tagHashes([tech, news]), [a]);
    expect(rows.map((r) => r.topic?.id ?? null)).toEqual(["1", "topic:news", null]);
    expect(rows.map((r) => r.address?.code ?? null)).toEqual([null, "aaaaaaaaaaaa", null]);
  });
});

describe("the Newsletter feed page", () => {
  it("shows the tag picked while it is a tag, else the first tag, else No tag", () => {
    const tags = [tech, news];
    expect(chosenTag(tags, null)).toBe("1");
    expect(chosenTag(tags, "topic:news")).toBe("topic:news");
    expect(chosenTag(tags, NO_TAG)).toBeNull();
    expect(chosenTag(tags, "gone")).toBe("1");
    expect(chosenTag([], null)).toBeNull();
  });

  it("counts a newsletter feed's articles and finds the newest", () => {
    const url = "https://feeds.example/newsletters/aaaaaaaaaaaa/0123456789abcdef0123456789abcdef";
    const article = (feedUrl: string, id: string, publishedAt: number | null, fetchedAt: number): Article => ({ feedUrl, id, title: id, link: null, author: null, html: "", publishedAt, fetchedAt, read: false, imageUrl: null });
    const articles = [article(url, "a", 100, 500), article(url, "b", null, 300), article("https://other.example/feed", "c", 900, 900)];
    expect(newsletterActivity(articles, url)).toEqual({ count: 2, newestAt: 300 });
    expect(newsletterActivity(articles, "https://none.example/feed")).toEqual({ count: 0, newestAt: null });
  });
});
