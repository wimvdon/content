/**
 * Unsubscribe from a newsletter (2026-11-15, docs/newsletters.md): the feed loader does it, not the
 * browser. The app sends the newest email's id of a sender to `<feed URL>/unsubscribe` (the web
 * app's own feed loader, with CORS), and the feed loader answers what it did or what to open. The
 * same as the Android app's NewsletterUnsubscribe.kt.
 */

import { isNewsletterFeed } from "./newsletterAddresses";
import { groupOf, type NewsletterSender, type SenderRule } from "./newsletterSenders";
import { senderKeyOf } from "./senderKey";
import { sortTime, type Article } from "./library";

/** What the feed loader answered. */
export type UnsubscribeAnswer = { kind: "done" } | { kind: "open"; url: string } | { kind: "mailto"; uri: string } | { kind: "none" };

type Fetch = (input: string, init?: RequestInit) => Promise<Response>;

const ENTRY = /^urn:content:newsletter:[a-z0-9]{12}:([0-9a-f]{16})$/;

/** The email's id in a newsletter article's id (`urn:content:newsletter:<code>:<id>`), or null when it isn't one. */
export function emailIdOf(articleId: string | null | undefined): string | null {
  return ENTRY.exec(articleId?.trim() ?? "")?.[1] ?? null;
}

/** Where to ask for newsletter feed `feedUrl`: its address with `/unsubscribe`; null when it isn't a newsletter feed. */
export function unsubscribeRoute(feedUrl: string | null | undefined): string | null {
  return feedUrl && isNewsletterFeed(feedUrl) ? `${feedUrl.trim().replace(/\/+$/, "")}/unsubscribe` : null;
}

/** Whether `article` can be unsubscribed from: a newsletter feed's email. */
export function canUnsubscribe(article: Article | null | undefined): boolean {
  return !!article && unsubscribeRoute(article.feedUrl) !== null && emailIdOf(article.id) !== null;
}

/** The email to unsubscribe with: the newest of `sender` (with the senders combined into it); null when none is a newsletter email. */
export function newestEmailOf(articles: Article[], sender: NewsletterSender, rules: SenderRule[]): Article | null {
  let newest: Article | null = null;
  for (const a of articles) {
    if (a.feedUrl !== sender.feedUrl || !canUnsubscribe(a)) continue;
    const key = senderKeyOf(a);
    if (key === null || groupOf(rules, key) !== sender.key) continue;
    if (!newest || sortTime(a) > sortTime(newest)) newest = a;
  }
  return newest;
}

/** The feed whose route unsubscribes with an email: the one it is stored under, also for a sender shown under another tag. */
export const feedOfEmail = (email: Article): string => email.storedFeedUrl ?? email.feedUrl;

/** The feed loader's answer (parsed JSON), or null when it isn't one. Only https pages and mailto: links are opened. */
export function answerOf(value: unknown): UnsubscribeAnswer | null {
  if (!value || typeof value !== "object") return null;
  const map = value as Record<string, unknown>;
  if (map.done === true) return { kind: "done" };
  if (typeof map.open === "string") return /^https:\/\//i.test(map.open.trim()) ? { kind: "open", url: map.open.trim() } : null;
  if (typeof map.mailto === "string") return /^mailto:/i.test(map.mailto.trim()) ? { kind: "mailto", uri: map.mailto.trim() } : null;
  if (map.none === true) return { kind: "none" };
  return null;
}

/** Asks the feed loader to unsubscribe from email `emailId` of feed `feedUrl`; null when it couldn't be asked or answered oddly. */
export async function unsubscribe(feedUrl: string, emailId: string, doFetch: Fetch = (input, init) => fetch(input, init)): Promise<UnsubscribeAnswer | null> {
  const route = unsubscribeRoute(feedUrl);
  if (!route) return null;
  try {
    const response = await doFetch(route, { method: "POST", headers: { "Content-Type": "application/json", Accept: "application/json" }, body: JSON.stringify({ id: emailId }) });
    if (!response.ok) return null;
    return answerOf(await response.json());
  } catch {
    return null;
  }
}
