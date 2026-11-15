/**
 * Newsletter senders (2026-11-15, docs/newsletters.md): a tag's Newsletters feed mixes many
 * newsletters, so each sender is a source of its own inside it, with its own chip, name and mute.
 * Who is one sender: senderKey.ts. The user's choices (a name of its own, merged into another
 * sender, muted, moved to another tag) are stored and synced as one text (encodeSenders). The same as the Android app's
 * NewsletterSenders.kt.
 */

import { isNewsletterFeed, NO_TAG, senderOf } from "./newsletterAddresses";
import { articleKey, sortTime, type Article, type Feed, type Library, type Selection } from "./library";
import { senderKeyOf, siteOfSender } from "./senderKey";

export interface SenderRule {
  key: string;
  /** Its own name; null: the sender's own. */
  name: string | null;
  /** The sender it is merged into; null: none. */
  into: string | null;
  muted: boolean;
  /**
   * The tag its newsletters show under (the tag's id, or NO_TAG for No tag) instead of the tag of the
   * address they were sent to (withMovedSenders); null: where they were sent.
   */
  tag: string | null;
}

/** How often a sender's newsletters come (senderCadence). */
export type Cadence = "daily" | "weekly" | "twoWeeks" | "monthly" | "nowAndThen";

export interface SenderMember {
  key: string;
  name: string;
}

/** One sender as the apps show it: a sender, with the ones merged into it. */
export interface NewsletterSender {
  feedUrl: string;
  key: string;
  name: string;
  /** Every sender in it (its own first, then the merged ones), each with its own name. */
  members: SenderMember[];
  count: number;
  unread: number;
  muted: boolean;
  newestAt: number;
  /** When each issue came, newest first (sortTime); what senderCadence reads. */
  times: number[];
  /** The newest issue's article key; null when there is none. */
  newestKey: string | null;
}

const isEmptyRule = (r: SenderRule) => r.name === null && r.into === null && !r.muted && r.tag === null;
const clean = (text: string) => text.replace(/\s+/g, " ").trim();

/** The sender `key` ends up in: itself, or the one it is merged into (a loop stops where it started). */
export function groupOf(rules: SenderRule[], key: string): string {
  const byKey = new Map(rules.map((r) => [r.key, r]));
  const seen = new Set<string>();
  let current = key;
  while (!seen.has(current)) {
    seen.add(current);
    const into = byKey.get(current)?.into;
    if (!into) return current;
    current = into;
  }
  return key;
}

export const ruleOf = (rules: SenderRule[], key: string): SenderRule | undefined => rules.find((r) => r.key === key);

export function isMuted(rules: SenderRule[], article: Article): boolean {
  const key = senderKeyOf(article);
  return key !== null && ruleOf(rules, groupOf(rules, key))?.muted === true;
}

/** Who sent `article` of newsletter feed `feed`, shown in its place: senderOf, with the name given to it (or to the sender it is merged into) and, merged, that sender's icon. */
export function shownSender(rules: SenderRule[], feed: Feed | undefined, article: Article): Feed | null {
  const sender = senderOf(feed, article);
  if (!sender) return null;
  const own = senderKeyOf(article);
  if (own === null) return sender;
  const group = groupOf(rules, own);
  const name = ruleOf(rules, group)?.name ?? sender.title;
  if (group === own) return { ...sender, title: name };
  const host = siteOfSender(group);
  if (!host) return { ...feed!, title: name };
  return { url: `https://${host}`, title: name, siteUrl: null, folderId: feed?.folderId ?? null, iconUrl: `https://${host}/favicon.ico`, renamed: false };
}

/** What stands in for a sender, in a chip and a list: its name, with its website's icon (none: `feed`'s, the envelope). */
export function senderFeed(sender: NewsletterSender, feed: Feed | undefined): Feed {
  const host = siteOfSender(sender.key);
  if (!host) return { ...(feed ?? { url: sender.feedUrl, title: sender.name, siteUrl: null, folderId: null, iconUrl: null, renamed: false }), title: sender.name };
  return { url: `https://${host}`, title: sender.name, siteUrl: null, folderId: feed?.folderId ?? null, iconUrl: `https://${host}/favicon.ico`, renamed: false };
}

/** The senders of newsletter feed `feedUrl` among `articles`, by name (case ignored), each with the ones merged into it. */
export function senderList(rules: SenderRule[], articles: Article[], feedUrl: string): NewsletterSender[] {
  const groups = new Map<string, [string, Article][]>();
  for (const a of articles) {
    if (a.feedUrl !== feedUrl) continue;
    const own = senderKeyOf(a);
    if (own === null) continue;
    const group = groupOf(rules, own);
    groups.set(group, [...(groups.get(group) ?? []), [own, a]]);
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
        muted: rule?.muted === true,
        newestAt: sortTime(newest[0][1]),
        times: newest.map(([, a]) => sortTime(a)),
        newestKey: articleKey(newest[0][1]),
      };
    })
    .sort((a, b) => (a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0));
}

/**
 * The sender `article` belongs to, with the senders combined into it, as senderList gives it; null for an
 * article that isn't a newsletter with a sender. What the list's long press, a sender chip's and the
 * reader's sender line open. As Android's NewsletterSenders.senderOf.
 */
export function senderOfArticle(rules: SenderRule[], articles: Article[], article: Article): NewsletterSender | null {
  const own = senderKeyOf(article);
  if (own === null) return null;
  const group = groupOf(rules, own);
  // Under the feed it is listed in, which is another one for a sender moved to another tag.
  const key = articleKey(article);
  const feedUrl = articles.find((a) => articleKey(a) === key)?.feedUrl ?? article.feedUrl;
  return senderList(rules, articles, feedUrl).find((s) => s.key === group) ?? null;
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
  const changed = change(existing ?? { key, name: null, into: null, muted: false, tag: null });
  const edited = existing ? rules.map((r) => (r.key === key ? changed : r)) : [...rules, changed];
  return edited.filter((r) => !isEmptyRule(r));
}

/** Sender `key` with name `name` (blank: its own again). */
export const renamedSender = (rules: SenderRule[], key: string, name: string): SenderRule[] => edit(rules, key, (r) => ({ ...r, name: clean(name) || null }));

export const mutedSender = (rules: SenderRule[], key: string, muted: boolean): SenderRule[] => edit(rules, key, (r) => ({ ...r, muted }));

/** Sender `from` (with everything merged into it) merged into `into`, which keeps `intoName`; nothing when `into` is in `from` already. */
export function mergedSender(rules: SenderRule[], from: string, into: string, intoName: string): SenderRule[] {
  const target = groupOf(rules, into);
  const source = groupOf(rules, from);
  if (target === source) return rules;
  const moved = rules.map((r) => (r.into !== null && groupOf(rules, r.key) === source ? { ...r, into: target } : r));
  const withSource = edit(moved, source, (r) => ({ ...r, into: target, muted: false, name: null, tag: null }));
  return edit(withSource, target, (r) => ({ ...r, name: r.name ?? (clean(intoName) || null) }));
}

/**
 * Sender `key` shown under tag `tag` (an id, or NO_TAG) instead of where its newsletters are sent.
 * `original`, the tag of the address they are sent to, clears the move.
 */
export const movedSender = (rules: SenderRule[], key: string, tag: string, original: string | null): SenderRule[] =>
  edit(rules, key, (r) => ({ ...r, tag: tag === original ? null : tag }));

/** Sender `key` out of the one it was merged into. */
export const splitSender = (rules: SenderRule[], key: string): SenderRule[] => edit(rules, key, (r) => ({ ...r, into: null }));

const encodeTag = (tag: string) => (tag === NO_TAG ? "-" : `t:${tag}`);

/** The tag a stored fifth field names; null for an empty or unknown one. */
const decodeTag = (field: string | undefined): string | null => (field === "-" ? NO_TAG : field !== undefined && field.startsWith("t:") && field.length > 2 ? field.slice(2) : null);

/**
 * How the choices are stored and synced: one sender a line, key, name, into and muted (1 or 0)
 * separated by tabs, and, only for a sender moved to another tag, a fifth field: `-` for No tag, else
 * `t:` and the tag's id. An app from before it reads the first four and ignores the rest.
 */
export const encodeSenders = (rules: SenderRule[]): string =>
  rules
    .filter((r) => !isEmptyRule(r))
    .map((r) => `${r.key}\t${r.name ?? ""}\t${r.into ?? ""}\t${r.muted ? 1 : 0}${r.tag === null ? "" : `\t${encodeTag(r.tag)}`}`)
    .join("\n");

/** The choices stored as encodeSenders has them; lines it can't read are left out. */
export function decodeSenders(text: string | null | undefined): SenderRule[] {
  const seen = new Set<string>();
  return (text ?? "").split("\n").flatMap((line) => {
    const parts = line.split("\t");
    const key = parts[0].trim();
    if (!key || parts.length < 4 || seen.has(key)) return [];
    const rule = { key, name: parts[1] || null, into: parts[2] || null, muted: parts[3] === "1", tag: decodeTag(parts[4]) };
    if (isEmptyRule(rule)) return [];
    seen.add(key);
    return [rule];
  });
}

/** The library without the newsletters of muted senders: the lists, counts and chips. */
export function withoutMuted(lib: Library, rules: SenderRule[]): Library {
  if (!rules.some((r) => r.muted)) return lib;
  return { ...lib, articles: lib.articles.filter((a) => !(isNewsletterFeed(a.feedUrl) && isMuted(rules, a))) };
}

/**
 * The library with the newsletters of senders moved to another tag listed under that tag's
 * Newsletters feed: their feedUrl is the target's, so the lists, chips and counts of the tag show
 * them, while storedFeedUrl keeps the feed they are stored under, so articleKey stays the same and
 * read marks, opening and syncing keep working. A tag without a Newsletters feed (or one that is
 * gone) leaves them where they are. For lists only: never store it. As Android's withMovedSenders.
 */
export function withMovedSenders(lib: Library, rules: SenderRule[]): Library {
  if (!rules.some((r) => r.tag !== null)) return lib;
  const targets = new Map(lib.feeds.filter((f) => isNewsletterFeed(f.url)).map((f) => [f.folderId ?? NO_TAG, f.url]));
  return {
    ...lib,
    articles: lib.articles.map((a) => {
      if (!isNewsletterFeed(a.feedUrl)) return a;
      const own = senderKeyOf(a);
      const tag = own === null ? null : (ruleOf(rules, groupOf(rules, own))?.tag ?? null);
      const target = tag === null ? undefined : targets.get(tag);
      return target === undefined || target === a.feedUrl ? a : { ...a, feedUrl: target, storedFeedUrl: a.storedFeedUrl ?? a.feedUrl };
    }),
  };
}

/**
 * The tag of the address sender `key` (its group) was sent to, in the stored library: an id, NO_TAG
 * for No tag, null when no newsletter of it is here. Where it is shown under another tag, this is the
 * tag it goes back to.
 */
export function originalTag(lib: Library, rules: SenderRule[], key: string): string | null {
  const group = groupOf(rules, key);
  let newest: Article | null = null;
  for (const a of lib.articles) {
    if (!isNewsletterFeed(a.feedUrl)) continue;
    const own = senderKeyOf(a);
    if (own === null || groupOf(rules, own) !== group) continue;
    if (newest === null || sortTime(a) > sortTime(newest)) newest = a;
  }
  if (newest === null) return null;
  const feed = lib.feeds.find((f) => f.url === newest!.feedUrl);
  return feed ? (feed.folderId ?? NO_TAG) : null;
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
