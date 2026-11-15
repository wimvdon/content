/**
 * Newsletter senders (2026-11-15, docs/newsletters.md): a tag's Newsletters feed mixes many
 * newsletters, so each sender is a source of its own inside it, with its own chip and name.
 * Who is one sender: senderKey.ts. The user's choice (a name of its own) is stored and synced as one
 * text (encodeSenders). The same as the Android app's NewsletterSenders.kt.
 */

import { isNewsletterFeed, senderOf } from "./newsletterAddresses";
import { articleKey, sortTime, type Article, type Feed, type Library, type Selection } from "./library";
import { senderKeyOf, siteOfSender } from "./senderKey";

export interface SenderRule {
  key: string;
  /** Its own name; null: the sender's own. */
  name: string | null;
}

/** How often a sender's newsletters come (senderCadence). */
export type Cadence = "daily" | "weekly" | "twoWeeks" | "monthly" | "nowAndThen";

export interface SenderMember {
  key: string;
  name: string;
}

/** One sender as the apps show it. */
export interface NewsletterSender {
  feedUrl: string;
  key: string;
  name: string;
  /** The sender itself, with the name it sends as (a list, as it was when senders could be combined). */
  members: SenderMember[];
  count: number;
  unread: number;
  newestAt: number;
  /** When each issue came, newest first (sortTime); what senderCadence reads. */
  times: number[];
  /** The newest issue's article key; null when there is none. */
  newestKey: string | null;
}

const isEmptyRule = (r: SenderRule) => r.name === null;
const clean = (text: string) => text.replace(/\s+/g, " ").trim();

export const ruleOf = (rules: SenderRule[], key: string): SenderRule | undefined => rules.find((r) => r.key === key);

/** Who sent `article` of newsletter feed `feed`, shown in its place: senderOf, with the name given to it. */
export function shownSender(rules: SenderRule[], feed: Feed | undefined, article: Article): Feed | null {
  const sender = senderOf(feed, article);
  if (!sender) return null;
  const own = senderKeyOf(article);
  if (own === null) return sender;
  return { ...sender, title: ruleOf(rules, own)?.name ?? sender.title };
}

/** What stands in for a sender, in a chip and a list: its name, with its website's icon (none: `feed`'s, the envelope). */
export function senderFeed(sender: NewsletterSender, feed: Feed | undefined): Feed {
  const host = siteOfSender(sender.key);
  if (!host) return { ...(feed ?? { url: sender.feedUrl, title: sender.name, siteUrl: null, folderId: null, iconUrl: null, renamed: false }), title: sender.name };
  return { url: `https://${host}`, title: sender.name, siteUrl: null, folderId: feed?.folderId ?? null, iconUrl: `https://${host}/favicon.ico`, renamed: false };
}

/** The senders of newsletter feed `feedUrl` among `articles`, by name (case ignored). */
export function senderList(rules: SenderRule[], articles: Article[], feedUrl: string): NewsletterSender[] {
  const groups = new Map<string, [string, Article][]>();
  for (const a of articles) {
    if (a.feedUrl !== feedUrl) continue;
    const own = senderKeyOf(a);
    if (own === null) continue;
    groups.set(own, [...(groups.get(own) ?? []), [own, a]]);
  }
  return [...groups.entries()]
    .map(([group, entries]) => {
      const newest = [...entries].sort((x, y) => sortTime(y[1]) - sortTime(x[1]));
      const seen = new Set<string>();
      const members = newest
        .filter(([key]) => !seen.has(key) && !!seen.add(key))
        .map(([key, a]) => ({ key, name: a.author!.trim() }))
        .sort((x, y) => (x.key === group ? 0 : 1) - (y.key === group ? 0 : 1));
      const rule = ruleOf(rules, group);
      return {
        feedUrl,
        key: group,
        name: rule?.name ?? members[0].name,
        members,
        count: entries.length,
        unread: entries.filter(([, a]) => !a.read).length,
        newestAt: sortTime(newest[0][1]),
        times: newest.map(([, a]) => sortTime(a)),
        newestKey: articleKey(newest[0][1]),
      };
    })
    .sort((a, b) => (a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0));
}

/**
 * The sender `article` belongs to, as senderList gives it; null for an
 * article that isn't a newsletter with a sender. What the list's long press, a sender chip's and the
 * reader's sender line open. As Android's NewsletterSenders.senderOf.
 */
export function senderOfArticle(rules: SenderRule[], articles: Article[], article: Article): NewsletterSender | null {
  const own = senderKeyOf(article);
  if (own === null) return null;
  return senderList(rules, articles, article.feedUrl).find((s) => s.key === own) ?? null;
}

/** Feeds' sender chips: every newsletter feed's senders with something in the tab shown; the one picked stays. */
export function senderChips(rules: SenderRule[], lib: Library, selection: Selection, unreadOnly: boolean): NewsletterSender[] {
  const picked = selection.kind === "sender" ? selection : null;
  return lib.feeds
    .filter((f) => isNewsletterFeed(f.url))
    .flatMap((f) => senderList(rules, lib.articles, f.url))
    .filter((s) => !unreadOnly || s.unread > 0 || (s.feedUrl === picked?.feedUrl && s.key === picked.key));
}

/** The selection that shows `sender` alone. */
export const senderSelection = (sender: NewsletterSender): Selection => ({ kind: "sender", feedUrl: sender.feedUrl, key: sender.key, members: sender.members.map((m) => m.key) });

function edit(rules: SenderRule[], key: string, change: (r: SenderRule) => SenderRule): SenderRule[] {
  // In its place, so the stored text changes only where it must.
  const existing = ruleOf(rules, key);
  const changed = change(existing ?? { key, name: null });
  const edited = existing ? rules.map((r) => (r.key === key ? changed : r)) : [...rules, changed];
  return edited.filter((r) => !isEmptyRule(r));
}

/** Sender `key` with name `name` (blank: its own again). */
export const renamedSender = (rules: SenderRule[], key: string, name: string): SenderRule[] => edit(rules, key, (r) => ({ ...r, name: clean(name) || null }));

/**
 * How the names are stored and synced: one sender a line, key and name separated by tabs, then an
 * empty field and 0, where senders combined into another and muted ones were until 2026-11-15 (an
 * app from before reads four fields). Those choices, and a fifth field (moved to another tag), are
 * ignored when read, so they no longer apply.
 */
export const encodeSenders = (rules: SenderRule[]): string =>
  rules
    .filter((r) => !isEmptyRule(r))
    .map((r) => `${r.key}\t${r.name ?? ""}\t\t0`)
    .join("\n");

/** The names stored as encodeSenders has them; lines it can't read are left out. */
export function decodeSenders(text: string | null | undefined): SenderRule[] {
  const seen = new Set<string>();
  return (text ?? "").split("\n").flatMap((line) => {
    const parts = line.split("\t");
    const key = parts[0].trim();
    if (!key || parts.length < 4 || seen.has(key)) return [];
    const rule = { key, name: parts[1] || null };
    if (isEmptyRule(rule)) return [];
    seen.add(key);
    return [rule];
  });
}

const DAY = 24 * 60 * 60 * 1000;

/**
 * How often a sender's newsletters come, from when its issues did (any order): the median gap
 * between the newest 10. At most 2 days: daily; up to 10 days: weekly; up to 21: every two weeks; up
 * to 45: monthly; longer: now and then. Null with fewer than 3 issues. As Android's cadence.
 */
export function senderCadence(times: number[]): Cadence | null {
  if (times.length < 3) return null;
  const newest = [...times].sort((a, b) => b - a).slice(0, 10);
  const gaps = newest
    .slice(1)
    .map((t, i) => newest[i] - t)
    .sort((a, b) => a - b);
  const middle = Math.floor(gaps.length / 2);
  const median = gaps.length % 2 === 1 ? gaps[middle] : (gaps[middle - 1] + gaps[middle]) / 2;
  const days = median / DAY;
  if (days <= 2) return "daily";
  if (days <= 10) return "weekly";
  if (days <= 21) return "twoWeeks";
  if (days <= 45) return "monthly";
  return "nowAndThen";
}
