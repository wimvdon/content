/**
 * Newsletter senders (2026-11-15, docs/newsletters.md): a tag's Newsletters feed mixes many
 * newsletters, so each sender is a source of its own inside it, with its own chip, name and mute.
 * Who is one sender: senderKey.ts. The user's choices (a name of its own, merged into another
 * sender, muted) are stored and synced as one text (encodeSenders). The same as the Android app's
 * NewsletterSenders.kt.
 */

import { isNewsletterFeed, senderOf } from "./newsletterAddresses";
import { sortTime, type Article, type Feed, type Library, type Selection } from "./library";
import { senderKeyOf, siteOfSender } from "./senderKey";

export interface SenderRule {
  key: string;
  /** Its own name; null: the sender's own. */
  name: string | null;
  /** The sender it is merged into; null: none. */
  into: string | null;
  muted: boolean;
}

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
}

const isEmptyRule = (r: SenderRule) => r.name === null && r.into === null && !r.muted;
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
      };
    })
    .sort((a, b) => (a.name.toLowerCase() < b.name.toLowerCase() ? -1 : a.name.toLowerCase() > b.name.toLowerCase() ? 1 : 0));
}

/** Feeds' sender chips: while a newsletter feed (or one of its senders) is picked, its senders with something in the tab shown; the one picked stays. */
export function senderChips(rules: SenderRule[], lib: Library, selection: Selection, unreadOnly: boolean): NewsletterSender[] {
  const feedUrl = selection.kind === "feed" || selection.kind === "sender" ? selection.feedUrl : null;
  if (feedUrl === null || !isNewsletterFeed(feedUrl)) return [];
  const picked = selection.kind === "sender" ? selection.key : null;
  return senderList(rules, lib.articles, feedUrl).filter((s) => !unreadOnly || s.unread > 0 || s.key === picked);
}

/** The selection that shows `sender` alone. */
export const senderSelection = (sender: NewsletterSender): Selection => ({ kind: "sender", feedUrl: sender.feedUrl, key: sender.key, members: sender.members.map((m) => m.key) });

function edit(rules: SenderRule[], key: string, change: (r: SenderRule) => SenderRule): SenderRule[] {
  // In its place, so the stored text changes only where it must.
  const existing = ruleOf(rules, key);
  const changed = change(existing ?? { key, name: null, into: null, muted: false });
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
  const withSource = edit(moved, source, (r) => ({ ...r, into: target, muted: false, name: null }));
  return edit(withSource, target, (r) => ({ ...r, name: r.name ?? (clean(intoName) || null) }));
}

/** Sender `key` out of the one it was merged into. */
export const splitSender = (rules: SenderRule[], key: string): SenderRule[] => edit(rules, key, (r) => ({ ...r, into: null }));

/** How the choices are stored and synced: one sender a line, key, name, into and muted (1 or 0) separated by tabs. */
export const encodeSenders = (rules: SenderRule[]): string =>
  rules
    .filter((r) => !isEmptyRule(r))
    .map((r) => `${r.key}\t${r.name ?? ""}\t${r.into ?? ""}\t${r.muted ? 1 : 0}`)
    .join("\n");

/** The choices stored as encodeSenders has them; lines it can't read are left out. */
export function decodeSenders(text: string | null | undefined): SenderRule[] {
  const seen = new Set<string>();
  return (text ?? "").split("\n").flatMap((line) => {
    const parts = line.split("\t");
    const key = parts[0].trim();
    if (!key || parts.length < 4 || seen.has(key)) return [];
    const rule = { key, name: parts[1] || null, into: parts[2] || null, muted: parts[3] === "1" };
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
