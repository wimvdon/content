import { describe, expect, it } from "vitest";
import { addressesOf, addressOf, codeOfFeed, groupedAddresses, topicFor } from "../src/lib/newsletterAddresses";
import { canCreate, INSTAPAPER_EMAIL, instapaperStep, ownStep } from "../src/lib/newsletterSubscribe";

describe("Subscribe to newsletter (newsletterSubscribe.ts), as on Android", () => {
  it("Instapaper asks to connect first, then shows where the address is", () => {
    expect(instapaperStep(false)).toBe("connect");
    expect(instapaperStep(true)).toBe("findAddress");
    expect(INSTAPAPER_EMAIL.startsWith("https://")).toBe(true);
  });

  it("an own address needs a Content account, and a tag", () => {
    expect(ownStep(false)).toBe("signIn");
    expect(ownStep(true)).toBe("addresses");
    expect(canCreate(" Tech ", false)).toBe(true);
    expect(canCreate("  ", false)).toBe(false);
    expect(canCreate("Tech", true)).toBe(false);
    expect(canCreate("x".repeat(101), false)).toBe(false);
  });

  it("recognises a newsletter address's feed, and reads the feed loader's answer", () => {
    const feed = "https://content-worker.wim.to/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef";
    expect(codeOfFeed(feed)).toBe("k7q2m9abcdef");
    expect(codeOfFeed("https://example.com/feed.xml")).toBeNull();
    const answer = { addresses: [{ code: "k7q2m9abcdef", tag: "Tech", address: "news+k7q2m9abcdef@in.wim.to", feedUrl: feed }, { code: "x" }] };
    expect(addressesOf(answer)).toEqual([{ code: "k7q2m9abcdef", tag: "Tech", address: "news+k7q2m9abcdef@in.wim.to", feedUrl: feed }]);
    // A feed address that doesn't belong to the code is refused.
    expect(addressOf({ code: "aaaaaaaaaaaa", address: "a", feedUrl: feed })).toBeNull();
  });

  it("puts a new address's feed under the topic with its name", () => {
    const topics = [
      { id: "1", title: "Tech", icon: "label" },
      { id: "2", title: "News", icon: "label" },
    ];
    expect(topicFor(topics as never, " tech ")?.id).toBe("1");
    expect(topicFor(topics as never, "Food")).toBeUndefined();
  });

  it("groups the addresses by their feed's topic, in the topics' order, No topic last", () => {
    const address = (code: string) => ({ code, tag: `Made as ${code}`, address: `content+${code}@in.wim.to`, feedUrl: `https://w.example/newsletters/${code}/0123456789abcdef0123456789abcdef` });
    const topics = [
      { id: "1", title: "Tech", icon: "label" },
      { id: "2", title: "News", icon: "label" },
    ];
    const [a, b, c, d] = ["aaaaaaaaaaaa", "bbbbbbbbbbbb", "cccccccccccc", "dddddddddddd"].map(address);
    const feeds = [
      { url: a.feedUrl, title: "Weekly", folderId: "2" },
      { url: b.feedUrl, title: "Daily", folderId: "1" },
      { url: c.feedUrl, title: "Loose", folderId: null },
    ];
    const groups = groupedAddresses([a, b, c, d], feeds as never, topics as never);
    expect(groups.map((g) => g.topic?.id ?? null)).toEqual(["1", "2", null]);
    expect(groups[0].addresses.map((x) => x.name)).toEqual(["Daily"]);
    expect(groups[2].addresses.map((x) => x.name)).toEqual(["Loose", "Made as dddddddddddd"]);
    expect(groupedAddresses([], feeds as never, topics as never)).toEqual([]);
  });
});
