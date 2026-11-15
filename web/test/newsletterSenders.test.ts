import { describe, expect, it } from "vitest";
import { articleKey, articlesFor, setRead, unreadKeys, selectionExists, type Article, type Feed, type Library } from "../src/lib/library";
import { senderKeyOf, siteOfSender } from "../src/lib/senderKey";
import {
  decodeSenders,
  encodeSenders,
  groupOf,
  isMuted,
  mergedSender,
  movedSender,
  mutedSender,
  originalTag,
  renamedSender,
  ruleOf,
  senderCadence,
  senderChips,
  senderFeed,
  senderList,
  senderOfArticle,
  senderSelection,
  shownSender,
  splitSender,
  withMovedSenders,
  withoutMuted,
  type NewsletterSender,
  type SenderRule,
} from "../src/lib/newsletterSenders";
import { NO_TAG } from "../src/lib/newsletterAddresses";
import { feedChipRow, sendersOf } from "../src/lib/topics";

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

  it("an article's sender is the one it belongs to, with what is combined into it", () => {
    const articles = [bozarNews, bozarEvents, platformer, old];
    expect(senderOfArticle([], articles, bozarNews)?.key).toBe("bozar.be");
    expect(senderOfArticle([], articles, bozarNews)?.count).toBe(2);
    const merged = mergedSender([], "name:de standaard", "bozar.be", "Bozar");
    const sender = senderOfArticle(merged, articles, old)!;
    expect(sender.key).toBe("bozar.be");
    expect(sender.count).toBe(3);
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
      { key: "a", name: null, into: "b", muted: false, tag: null },
      { key: "b", name: null, into: "a", muted: false, tag: null },
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
    expect(decodeSenders("a\tA\t\t0\nbroken\n\t\t\t1\nnothing\t\t\t0")).toEqual([{ key: "a", name: "A", into: null, muted: false, tag: null }]);
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

const feed2Url = "https://w.example/newsletters/zzzzzzzzzzzz/fedcba9876543210fedcba9876543210";
const feed2: Feed = { ...feed, url: feed2Url, folderId: "2", tags: ["Work"] };
const feedNoTagUrl = "https://w.example/newsletters/yyyyyyyyyyyy/00112233445566778899aabbccddeeff";
const feedNoTag: Feed = { ...feed, url: feedNoTagUrl, folderId: null, tags: [] };
const twoTags = (articles: Article[]): Library => ({
  ...lib([feed, feed2, feedNoTag, rss], articles),
  folders: [
    { id: "1", title: "News", icon: "folder" },
    { id: "2", title: "Work", icon: "folder" },
  ],
});
const unreadIn = (library: Library, selection: Parameters<typeof unreadKeys>[1]) => unreadKeys(library, selection).size;

describe("moving a sender to another tag, as on Android", () => {
  it("the tag is stored as a fifth field that an old app ignores, and a line without it reads as before", () => {
    const rules = movedSender(movedSender(renamedSender([], "bozar.be", "Bozar"), "bozar.be", "2", "1"), "substack.com/platformer", NO_TAG, "1");
    const text = encodeSenders(rules);
    const lines = text.split("\n");
    expect(lines[0]).toBe("bozar.be\tBozar\t\t0\tt:2");
    expect(lines[1]).toBe("substack.com/platformer\t\t\t0\t-");
    expect(decodeSenders(text)).toEqual(rules);
    expect(decodeSenders(text)[0].tag).toBe("2");
    expect(decodeSenders(text)[1].tag).toBe(NO_TAG);
    // A line from before the tag.
    expect(decodeSenders("bozar.be\tBozar\t\t0")[0].tag).toBeNull();
    // What an old app reads from a line with the tag: its first four fields, as before.
    expect(lines[0].split("\t").slice(0, 4)).toEqual(["bozar.be", "Bozar", "", "0"]);
    // A tag alone is a choice worth keeping; an unreadable tag is dropped, the rest read.
    expect(decodeSenders("a\t\t\t0\tt:x")).toEqual([{ key: "a", name: null, into: null, muted: false, tag: "x" }]);
    expect(decodeSenders("a\tA\t\t0\tgarbage")).toEqual([{ key: "a", name: "A", into: null, muted: false, tag: null }]);
    expect(decodeSenders("a\t\t\t0\tt:")).toEqual([]);
  });

  it("picking the tag it is sent to clears the move, and merging a sender drops its own move", () => {
    const moved = movedSender([], "bozar.be", "2", "1");
    expect(ruleOf(moved, "bozar.be")?.tag).toBe("2");
    expect(movedSender(moved, "bozar.be", "1", "1")).toEqual([]);
    expect(ruleOf(mergedSender(moved, "bozar.be", "substack.com/platformer", "Platformer"), "bozar.be")?.tag).toBeNull();
  });

  it("a moved sender's newsletters are listed under the target tag's feed, and no other", () => {
    const library = twoTags([bozarNews, bozarEvents, platformer]);
    const rules = movedSender([], "bozar.be", "2", "1");
    const view = withMovedSenders(library, rules);
    expect(view.articles.map((a) => a.feedUrl)).toEqual([feed2Url, feed2Url, feedUrl]);
    expect(articlesFor(view, { kind: "feed", feedUrl: feed2Url }, false).map((a) => a.id)).toEqual(["2", "1"]);
    expect(articlesFor(view, { kind: "feed", feedUrl }, false).map((a) => a.id)).toEqual(["3"]);
    expect(unreadIn(view, { kind: "folder", folderId: "2" })).toBe(2);
    expect(unreadIn(view, { kind: "folder", folderId: "1" })).toBe(1);
    // The senders of each feed follow.
    expect(senderList(rules, view.articles, feed2Url).map((s) => s.name)).toEqual(["Bozar Events"]);
    expect(senderList(rules, view.articles, feedUrl).map((s) => s.name)).toEqual(["Platformer"]);
    expect(senderOfArticle(rules, view.articles, bozarNews)?.feedUrl).toBe(feed2Url);
    // The same library when nothing is moved; and the stored library is never changed.
    expect(withMovedSenders(library, [])).toBe(library);
    expect(library.articles[0].feedUrl).toBe(feedUrl);
  });

  it("a sender moved to No tag, to a tag without a feed or to the tag it is in", () => {
    const library = twoTags([bozarNews]);
    expect(withMovedSenders(library, movedSender([], "bozar.be", NO_TAG, "1")).articles[0].feedUrl).toBe(feedNoTagUrl);
    expect(withMovedSenders(library, movedSender([], "bozar.be", "gone", "1")).articles[0].feedUrl).toBe(feedUrl);
    expect(withMovedSenders(library, [{ key: "bozar.be", name: null, into: null, muted: false, tag: "1" }]).articles[0].feedUrl).toBe(feedUrl);
    // An RSS feed's article of the same author is never moved.
    const rssArticle = issue("9", "Bozar", "https://bozar.be", 0, false, rss.url);
    expect(withMovedSenders(twoTags([rssArticle]), movedSender([], "bozar.be", "2", "1")).articles[0].feedUrl).toBe(rss.url);
  });

  it("a combined sender's newsletters all follow the move of the sender they are combined into", () => {
    const library = twoTags([bozarNews, bozarSite, platformer]);
    const rules = movedSender(mergedSender([], "bozar-mail.be", "bozar.be", "Bozar"), "bozar.be", "2", "1");
    expect(withMovedSenders(library, rules).articles.map((a) => a.feedUrl)).toEqual([feed2Url, feed2Url, feedUrl]);
  });

  it("read state keeps working for moved newsletters, because their key stays the same", () => {
    const library = twoTags([bozarNews, bozarEvents, platformer]);
    const rules = movedSender([], "bozar.be", "2", "1");
    const view = withMovedSenders(library, rules);
    expect(view.articles.map(articleKey)).toEqual(library.articles.map(articleKey));
    // Marking what the list shows as read marks the stored articles, not copies.
    const keys = unreadKeys(view, { kind: "feed", feedUrl: feed2Url });
    expect(keys).toEqual(new Set([articleKey(bozarNews), articleKey(bozarEvents)]));
    const stored = setRead(library, keys, true);
    expect(unreadIn(stored, { kind: "all" })).toBe(1);
    expect(unreadIn(withMovedSenders(stored, rules), { kind: "feed", feedUrl: feed2Url })).toBe(0);
    expect(stored.articles[0].feedUrl).toBe(feedUrl);
    // A mark kept from before the move still applies after it.
    expect(unreadIn(withMovedSenders(setRead(library, new Set([articleKey(bozarNews)]), true), rules), { kind: "feed", feedUrl: feed2Url })).toBe(1);
    // Muted senders still leave the moved lists.
    expect(withoutMuted(view, mutedSender(rules, "bozar.be", true)).articles.map((a) => a.id)).toEqual(["3"]);
  });

  it("the tag a sender is sent to is read from the stored library, whatever it is shown under", () => {
    const library = twoTags([bozarNews, platformer, issue("7", "X", null, 0, false, feedNoTagUrl)]);
    const rules = movedSender([], "bozar.be", "2", "1");
    expect(originalTag(library, rules, "bozar.be")).toBe("1");
    expect(originalTag(library, rules, "name:x")).toBe(NO_TAG);
    expect(originalTag(library, rules, "nobody.be")).toBeNull();
  });
});
