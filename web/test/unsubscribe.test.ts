import { describe, expect, it } from "vitest";
import type { Article } from "../src/lib/library";
import { mergedSender, senderList } from "../src/lib/newsletterSenders";
import { answerOf, canUnsubscribe, emailIdOf, newestEmailOf, unsubscribe, unsubscribeRoute } from "../src/lib/unsubscribe";

const feedUrl = "https://w.example/newsletters/k7q2m9abcdef/0123456789abcdef0123456789abcdef";
const entry = (id: string) => `urn:content:newsletter:k7q2m9abcdef:${id}`;
const issue = (id: string, author: string, site: string | null, time: number, url = feedUrl): Article => ({
  feedUrl: url, id, title: "Issue", link: null, author, html: "", publishedAt: time, fetchedAt: time, read: false, imageUrl: null, authorUrl: site,
});

describe("unsubscribe", () => {
  it("takes the email id from a newsletter article's id", () => {
    expect(emailIdOf(entry("0123456789abcdef"))).toBe("0123456789abcdef");
    expect(emailIdOf("urn:content:newsletter:k7q2m9abcdef")).toBeNull();
    expect(emailIdOf(entry("XYZ"))).toBeNull();
    expect(emailIdOf("https://example.com/post")).toBeNull();
    expect(emailIdOf(null)).toBeNull();
  });

  it("asks at the newsletter feed's address with /unsubscribe", () => {
    expect(unsubscribeRoute(feedUrl)).toBe(`${feedUrl}/unsubscribe`);
    expect(unsubscribeRoute(`${feedUrl}/`)).toBe(`${feedUrl}/unsubscribe`);
    expect(unsubscribeRoute("https://example.com/feed.xml")).toBeNull();
    expect(unsubscribeRoute(null)).toBeNull();
  });

  it("only a newsletter feed's email can be unsubscribed from", () => {
    expect(canUnsubscribe(issue(entry("0123456789abcdef"), "Bozar", "https://bozar.be", 1))).toBe(true);
    expect(canUnsubscribe(issue("post-1", "Bozar", "https://bozar.be", 1))).toBe(false);
    expect(canUnsubscribe(issue(entry("0123456789abcdef"), "Bozar", null, 1, "https://example.com/feed.xml"))).toBe(false);
    expect(canUnsubscribe(null)).toBe(false);
  });

  it("uses the sender's newest email, combined senders included", () => {
    const older = issue(entry("000000000000000a"), "Bozar", "https://bozar.be", 10);
    const newer = issue(entry("000000000000000b"), "Bozar Events", "https://bozar.be", 30);
    const combined = issue(entry("000000000000000c"), "Bozar Mail", "https://bozar-mail.be", 50);
    const other = issue(entry("000000000000000d"), "Platformer", "https://platformer.news", 40);
    const articles = [older, newer, combined, other];
    const bozar = senderList([], articles, feedUrl).find((s) => s.key === "bozar.be")!;
    expect(newestEmailOf(articles, bozar, [])).toBe(newer);
    const rules = mergedSender([], "bozar-mail.be", "bozar.be", "Bozar");
    const group = senderList(rules, articles, feedUrl).find((s) => s.key === "bozar.be")!;
    expect(newestEmailOf(articles, group, rules)).toBe(combined);
  });

  it("reads the feed loader's answer, and refuses what would open something else", () => {
    expect(answerOf({ done: true })).toEqual({ kind: "done" });
    expect(answerOf({ open: "https://example.com/u?x=1" })).toEqual({ kind: "open", url: "https://example.com/u?x=1" });
    expect(answerOf({ mailto: "mailto:leave@example.com" })).toEqual({ kind: "mailto", uri: "mailto:leave@example.com" });
    expect(answerOf({ none: true })).toEqual({ kind: "none" });
    expect(answerOf({ open: "http://example.com/u" })).toBeNull();
    expect(answerOf({ open: "javascript:alert(1)" })).toBeNull();
    expect(answerOf({ mailto: "https://example.com" })).toBeNull();
    expect(answerOf({ done: false })).toBeNull();
    expect(answerOf({})).toBeNull();
    expect(answerOf(null)).toBeNull();
    expect(answerOf("done")).toBeNull();
  });

  it("posts the email's id to the route, and gives null when that fails", async () => {
    const calls: { url: string; init?: RequestInit }[] = [];
    const answer = await unsubscribe(feedUrl, "0123456789abcdef", async (url, init) => {
      calls.push({ url, init });
      return new Response(JSON.stringify({ done: true }), { headers: { "Content-Type": "application/json" } });
    });
    expect(answer).toEqual({ kind: "done" });
    expect(calls[0].url).toBe(`${feedUrl}/unsubscribe`);
    expect(calls[0].init?.method).toBe("POST");
    expect(calls[0].init?.body).toBe(JSON.stringify({ id: "0123456789abcdef" }));
    expect(await unsubscribe(feedUrl, "0123456789abcdef", async () => new Response("Not found", { status: 404 }))).toBeNull();
    expect(await unsubscribe(feedUrl, "0123456789abcdef", async () => { throw new TypeError("offline"); })).toBeNull();
    expect(await unsubscribe("https://example.com/feed.xml", "0123456789abcdef", async () => new Response("{}"))).toBeNull();
  });
});
