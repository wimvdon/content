import { describe, expect, it } from "vitest";
import { articleKey, articlesFor, unreadKeys, selectionExists, type Article, type Feed, type Library } from "../src/lib/library";
import { senderKeyOf, siteOfSender } from "../src/lib/senderKey";
import {
  decodeSenders,
  encodeSenders,
  renamedSender,
  ruleOf,
  senderCadence,
  senderChips,
  senderFeed,
  senderList,
  senderOfArticle,
  senderSelection,
  shownSender,
  type NewsletterSender,
} from "../src/lib/newsletterSenders";
import { feedChipRow, sendersOf, showsFeedChip } from "../src/lib/topics";

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

  it("an article's sender is the one it belongs to", () => {
    const articles = [bozarNews, bozarEvents, platformer, old];
    expect(senderOfArticle([], articles, bozarNews)?.key).toBe("bozar.be");
    expect(senderOfArticle([], articles, bozarNews)?.count).toBe(2);
    expect(senderOfArticle([], articles, issue("x", null, null))).toBeNull();
  });

  it("renaming shows the new name everywhere, and an empty name brings back the sender's own", () => {
    const rules = renamedSender([], "bozar.be", "  Bozar  Brussels ");
    expect(ruleOf(rules, "bozar.be")?.name).toBe("Bozar Brussels");
    expect(shownSender(rules, feed, bozarEvents)?.title).toBe("Bozar Brussels");
    expect(senderList(rules, [bozarEvents], feedUrl)[0].name).toBe("Bozar Brussels");
    expect(renamedSender(rules, "bozar.be", " ")).toEqual([]);
    expect(shownSender(rules, rss, { ...bozarEvents, feedUrl: rss.url })).toBeNull();
  });

  it("the names are stored as text, the same as Android's, and read back", () => {
    const rules = renamedSender(renamedSender([], "bozar.be", "Bozar"), "substack.com/platformer", "Platformer");
    const text = encodeSenders(rules);
    expect(text).toBe("bozar.be\tBozar\t\t0\nsubstack.com/platformer\tPlatformer\t\t0");
    expect(decodeSenders(text)).toEqual(rules);
    expect(decodeSenders(undefined)).toEqual([]);
    expect(decodeSenders("a\tA\t\t0\nbroken\n\t\t\t1\nnothing\t\t\t0")).toEqual([{ key: "a", name: "A" }]);
  });

  it("combined, muted and moved senders stored by an earlier version no longer apply; their names stay", () => {
    const stored = "bozar-mail.be\t\tbozar.be\t0\nbozar.be\tBozar\t\t1\tt:1\nsubstack.com/platformer\t\t\t1";
    const rules = decodeSenders(stored);
    expect(rules).toEqual([{ key: "bozar.be", name: "Bozar" }]);
    expect(senderList(rules, [bozarNews, bozarSite], feedUrl)).toHaveLength(2);
    expect(encodeSenders(rules)).toBe("bozar.be\tBozar\t\t0");
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
    // Whatever is chosen: every newsletter feed's senders are chips, like feeds; an RSS feed has none.
    expect(senderChips([], library, { kind: "feed", feedUrl: rss.url }, false)).toHaveLength(3);
    expect(senderChips([], library, { kind: "all" }, true).map((s) => s.name)).toEqual(["Bozar", "Platformer"]);
    expect(senderChips([], lib([rss], [bozarNews]), { kind: "all" }, false)).toEqual([]);
  });

  it("the chip row: a newsletter feed's senders are chips of their own after it, wherever its chip is", () => {
    const library = lib([feed, rss], [bozarNews, platformer, issue("7", "x", null, 0, false, rss.url)]);
    const senders: NewsletterSender[] = senderList([], library.articles, feedUrl);
    // Its topic chosen: the topic's feeds, its senders after Newsletters.
    const row = feedChipRow(library, "all", { kind: "tag", tag: "News" }, senders);
    expect(row.feeds.map((f) => f.url)).toEqual([feedUrl, rss.url]);
    expect(sendersOf(row, feedUrl)).toEqual(senders);
    expect(sendersOf(row, rss.url)).toEqual([]);
    // The feed chosen: the row stays the same, its senders too.
    const one = feedChipRow(library, "all", { kind: "feed", feedUrl }, senders);
    expect(one.feeds.map((f) => f.url)).toEqual([feedUrl, rss.url]);
    expect(one.senders).toEqual(senders);
    const picked = feedChipRow(library, "all", senderSelection(senders[0]), senders);
    expect(picked.chosenFeed).toBe(feedUrl);
    expect(picked.chosenSender).toBe("bozar.be");
    expect(picked.feeds).toHaveLength(2);
    // A feed's only sender gets a chip of its own too.
    expect(feedChipRow(library, "all", { kind: "tag", tag: "News" }, senders.slice(0, 1)).senders).toEqual(senders.slice(0, 1));
    // A newsletter feed with senders shows them instead of its own chip, unless it is the one chosen.
    const tagged = feedChipRow(library, "all", { kind: "tag", tag: "News" }, senders);
    expect(showsFeedChip(tagged, feedUrl)).toBe(false);
    expect(showsFeedChip(tagged, rss.url)).toBe(true);
    expect(showsFeedChip(feedChipRow(library, "all", { kind: "tag", tag: "News" }), feedUrl)).toBe(true);
    expect(showsFeedChip(one, feedUrl)).toBe(true);
    expect(showsFeedChip(picked, feedUrl)).toBe(false);
    expect(feedChipRow(library, "all", senderSelection(senders[0]), senders.slice(0, 1)).senders).toEqual(senders.slice(0, 1));
  });
});

const DAY = 24 * 60 * 60 * 1000;

describe("a sender's facts, as on Android", () => {
  it("cadence is the median gap between the newest ten issues, and unknown under three issues", () => {
    expect(senderCadence([])).toBeNull();
    expect(senderCadence([0, DAY])).toBeNull();
    expect(senderCadence([0, DAY, 2 * DAY])).toBe("daily");
    // A weekdays-only newsletter is daily: the weekend gap is not the median.
    expect(senderCadence([0, DAY, 2 * DAY, 3 * DAY, 6 * DAY, 7 * DAY])).toBe("daily");
    expect(senderCadence([0, 7 * DAY, 14 * DAY])).toBe("weekly");
    // Twice a week is still about weekly; any order is fine.
    expect(senderCadence([7 * DAY, 0, 3 * DAY, 10 * DAY])).toBe("weekly");
    expect(senderCadence([0, 14 * DAY, 28 * DAY])).toBe("twoWeeks");
    expect(senderCadence([0, 30 * DAY, 61 * DAY])).toBe("monthly");
    expect(senderCadence([0, 90 * DAY, 200 * DAY])).toBe("nowAndThen");
    // The thresholds: 2, 10, 21 and 45 days belong to the faster one.
    expect(senderCadence([0, 2 * DAY, 4 * DAY])).toBe("daily");
    expect(senderCadence([0, 10 * DAY, 20 * DAY])).toBe("weekly");
    expect(senderCadence([0, 21 * DAY, 42 * DAY])).toBe("twoWeeks");
    expect(senderCadence([0, 45 * DAY, 90 * DAY])).toBe("monthly");
    // Only the newest ten count: old weekly issues don't weigh on a sender now daily.
    const recent = Array.from({ length: 10 }, (_, i) => 1000 * DAY + i * DAY);
    expect(senderCadence([...recent, ...Array.from({ length: 5 }, (_, i) => i * 7 * DAY)])).toBe("daily");
  });

  it("a sender knows when its issues came and which is the newest", () => {
    const sender = senderList([], [bozarNews, bozarEvents, platformer], feedUrl).find((s) => s.key === "bozar.be")!;
    expect(sender.times).toEqual([20, 10]);
    expect(sender.newestKey).toBe(articleKey(bozarEvents));
  });
});
