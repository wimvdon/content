import { describe, expect, it } from "vitest";
import { articlesFor, unreadKeys, selectionExists, type Article, type Feed, type Library } from "../src/lib/library";
import { senderKeyOf, siteOfSender } from "../src/lib/senderKey";
import {
  decodeSenders,
  encodeSenders,
  groupOf,
  isMuted,
  mergedSender,
  mutedSender,
  renamedSender,
  ruleOf,
  senderChips,
  senderFeed,
  senderList,
  senderSelection,
  shownSender,
  splitSender,
  withoutMuted,
  type NewsletterSender,
  type SenderRule,
} from "../src/lib/newsletterSenders";
import { feedChipRow } from "../src/lib/topics";

const feedUrl = "https://w.example/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef";
const feed: Feed = { url: feedUrl, title: "Newsletters", siteUrl: null, folderId: "1", iconUrl: null, renamed: true, tags: ["News"] };
const rss: Feed = { url: "https://example.com/feed.xml", title: "Example", siteUrl: null, folderId: "1", iconUrl: null, renamed: false, tags: ["News"] };
const issue = (id: string, author: string | null, site: string | null, time = 0, read = false, url = feedUrl): Article => ({
  feedUrl: url, id, title: `Issue ${id}`, link: null, author, html: "", publishedAt: time, fetchedAt: time, read, imageUrl: null, authorUrl: site,
});
const lib = (feeds: Feed[], articles: Article[]): Library => ({ feeds, folders: [{ id: "1", title: "News", icon: "folder" }], articles, items: [], readElsewhere: {} });

const bozarNews = issue("1", "Bozar", "https://bozar.be", 10);
const bozarEvents = issue("2", "Bozar Events", "https://bozar.be", 20);
const platformer = issue("3", "Platformer", "https://substack.com", 30);
const stratechery = issue("4", "Stratechery", "https://substack.com", 40, true);
const old = issue("5", "De Standaard", null, 5);
const bozarSite = issue("6", "Bozar", "https://bozar-mail.be", 15);

describe("newsletter senders, as on Android", () => {
  it("a sender is its website, or on a shared platform its website and name, or else its name", () => {
    expect(senderKeyOf(bozarNews)).toBe("bozar.be");
    expect(senderKeyOf(bozarEvents)).toBe("bozar.be");
    expect(senderKeyOf(platformer)).toBe("substack.com/platformer");
    expect(senderKeyOf(old)).toBe("name:de standaard");
    expect(senderKeyOf(issue("x", "  De   STANDAARD ", null))).toBe("name:de standaard");
    expect(senderKeyOf(issue("x", null, null))).toBeNull();
    expect(siteOfSender("substack.com/platformer")).toBe("substack.com");
    expect(siteOfSender("name:de standaard")).toBeNull();
  });

  it("the senders of a feed, by name, with the newest issue's name and the counts", () => {
    const list = senderList([], [bozarNews, bozarEvents, platformer, stratechery, old, issue("9", "Other", null, 0, false, rss.url)], feedUrl);
    expect(list.map((s) => s.name)).toEqual(["Bozar Events", "De Standaard", "Platformer", "Stratechery"]);
    expect(list[0]).toMatchObject({ key: "bozar.be", count: 2, unread: 2, newestAt: 20 });
    expect(list[3].unread).toBe(0);
    expect(senderFeed(list[0], feed)).toMatchObject({ url: "https://bozar.be", title: "Bozar Events", iconUrl: "https://bozar.be/favicon.ico" });
    expect(senderFeed(list[1], feed)).toEqual({ ...feed, title: "De Standaard" });
  });

  it("renaming shows the new name everywhere, and an empty name brings back the sender's own", () => {
    const rules = renamedSender([], "bozar.be", "  Bozar  Brussels ");
    expect(ruleOf(rules, "bozar.be")?.name).toBe("Bozar Brussels");
    expect(shownSender(rules, feed, bozarEvents)?.title).toBe("Bozar Brussels");
    expect(senderList(rules, [bozarEvents], feedUrl)[0].name).toBe("Bozar Brussels");
    expect(renamedSender(rules, "bozar.be", " ")).toEqual([]);
    expect(shownSender(rules, rss, { ...bozarEvents, feedUrl: rss.url })).toBeNull();
  });

  it("merging puts one sender in another, which keeps its name and icon, and separating takes it out", () => {
    const rules = mergedSender([], "bozar-mail.be", "bozar.be", "Bozar");
    expect(groupOf(rules, "bozar-mail.be")).toBe("bozar.be");
    expect(ruleOf(rules, "bozar.be")?.name).toBe("Bozar");
    expect(shownSender(rules, feed, bozarSite)).toMatchObject({ title: "Bozar", iconUrl: "https://bozar.be/favicon.ico" });
    const group = senderList(rules, [bozarNews, bozarSite], feedUrl);
    expect(group).toHaveLength(1);
    expect(group[0].members.map((m) => m.key)).toEqual(["bozar.be", "bozar-mail.be"]);
    expect(senderSelection(group[0])).toEqual({ kind: "sender", feedUrl, key: "bozar.be", members: ["bozar.be", "bozar-mail.be"] });
    const split = splitSender(rules, "bozar-mail.be");
    expect(groupOf(split, "bozar-mail.be")).toBe("bozar-mail.be");
    expect(senderList(split, [bozarNews, bozarSite], feedUrl)).toHaveLength(2);
  });

  it("merging a group moves its members too, and never makes a loop", () => {
    const rules = mergedSender(mergedSender([], "substack.com/stratechery", "substack.com/platformer", "Platformer"), "substack.com/platformer", "bozar.be", "Bozar");
    expect(groupOf(rules, "substack.com/stratechery")).toBe("bozar.be");
    expect(groupOf(rules, "substack.com/platformer")).toBe("bozar.be");
    expect(mergedSender(rules, "bozar.be", "substack.com/stratechery", "x")).toBe(rules);
    const loop: SenderRule[] = [
      { key: "a", name: null, into: "b", muted: false },
      { key: "b", name: null, into: "a", muted: false },
    ];
    expect(groupOf(loop, "a")).toBe("a");
  });

  it("a muted sender's newsletters leave the lists, and no other", () => {
    const rules = mutedSender(mergedSender([], "bozar-mail.be", "bozar.be", "Bozar"), "bozar.be", true);
    expect(isMuted(rules, bozarSite)).toBe(true);
    expect(isMuted(rules, platformer)).toBe(false);
    const library = lib([feed, rss], [bozarNews, bozarSite, platformer, issue("9", "Bozar", "https://bozar.be", 0, false, rss.url)]);
    expect(withoutMuted(library, rules).articles.map((a) => a.id)).toEqual(["3", "9"]);
    expect(withoutMuted(library, [])).toBe(library);
    expect(mutedSender(rules, "bozar.be", false)).toEqual(mergedSender([], "bozar-mail.be", "bozar.be", "Bozar"));
  });

  it("the choices are stored as text, the same as Android's, and read back", () => {
    const rules = mutedSender(mergedSender(renamedSender([], "bozar.be", "Bozar"), "substack.com/platformer", "bozar.be", "x"), "name:de standaard", true);
    const text = encodeSenders(rules);
    expect(text).toBe("bozar.be\tBozar\t\t0\nsubstack.com/platformer\t\tbozar.be\t0\nname:de standaard\t\t\t1");
    expect(decodeSenders(text)).toEqual(rules);
    expect(decodeSenders(undefined)).toEqual([]);
    expect(decodeSenders("a\tA\t\t0\nbroken\n\t\t\t1\nnothing\t\t\t0")).toEqual([{ key: "a", name: "A", into: null, muted: false }]);
  });

  it("a sender chosen shows only its newsletters", () => {
    const library = lib([feed], [bozarNews, bozarEvents, platformer]);
    const selection = { kind: "sender" as const, feedUrl, key: "bozar.be", members: ["bozar.be"] };
    expect(articlesFor(library, selection, false).map((a) => a.id)).toEqual(["2", "1"]);
    expect(unreadKeys(library, selection).size).toBe(2);
    expect(selectionExists(library, selection)).toBe(selection);
    expect(selectionExists(lib([], []), selection)).toEqual({ kind: "all" });
  });

  it("the sender chips: a newsletter feed's senders with something in the tab, the one chosen kept", () => {
    const library = lib([feed, rss], [bozarNews, platformer, stratechery]);
    expect(senderChips([], library, { kind: "feed", feedUrl }, false).map((s) => s.name)).toEqual(["Bozar", "Platformer", "Stratechery"]);
    expect(senderChips([], library, { kind: "feed", feedUrl }, true).map((s) => s.name)).toEqual(["Bozar", "Platformer"]);
    expect(senderChips([], library, { kind: "sender", feedUrl, key: "substack.com/stratechery", members: [] }, true)).toHaveLength(3);
    expect(senderChips([], library, { kind: "feed", feedUrl: rss.url }, false)).toEqual([]);
    expect(senderChips([], library, { kind: "all" }, false)).toEqual([]);
  });

  it("the chip row: a newsletter feed chosen shows its topic, itself and its senders", () => {
    const library = lib([feed, rss], [bozarNews, platformer, issue("7", "x", null, 0, false, rss.url)]);
    const senders: NewsletterSender[] = senderList([], library.articles, feedUrl);
    const row = feedChipRow(library, "all", { kind: "feed", feedUrl }, senders);
    expect(row.chosenTopic).toBe("News");
    expect(row.feeds.map((f) => f.url)).toEqual([feedUrl]);
    expect(row.senders).toEqual(senders);
    const picked = feedChipRow(library, "all", senderSelection(senders[0]), senders);
    expect(picked.chosenFeed).toBe(feedUrl);
    expect(picked.chosenSender).toBe("bozar.be");
    // A single sender adds nothing; another feed has none.
    expect(feedChipRow(library, "all", { kind: "feed", feedUrl }, senders.slice(0, 1)).feeds).toHaveLength(2);
    expect(feedChipRow(library, "all", { kind: "feed", feedUrl: rss.url }, senders).senders).toEqual([]);
  });
});
