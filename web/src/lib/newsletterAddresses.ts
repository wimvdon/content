/**
 * Newsletter addresses (docs/newsletters.md): an email address per tag, made by the account's feed
 * loader, whose newsletters arrive as a feed. Made in + › Subscribe to newsletter › Your own address;
 * its feed is then added under the tag like any feed, and removing that feed removes the address.
 * The same as the Android app's NewsletterAddresses.kt.
 */

import type { Feed, Folder } from "./library";

export interface NewsletterAddress {
  code: string;
  tag: string;
  /** The email address to subscribe with, e.g. content+k7q2m9abcdef@in.wim.to. */
  address: string;
  feedUrl: string;
}

const FEED = /^https:\/\/[^/]+\/newsletters\/([a-z0-9]{12})\/[0-9a-f]{32}\/?$/;

/** The code of a newsletter address's feed, or null for any other feed. */
export function codeOfFeed(url: string | null | undefined): string | null {
  const match = url?.trim().match(FEED);
  return match ? match[1] : null;
}

/** One address from the feed loader's answer, or null when it isn't one. */
export function addressOf(value: unknown): NewsletterAddress | null {
  if (typeof value !== "object" || value === null) return null;
  const v = value as Record<string, unknown>;
  if (typeof v.code !== "string" || typeof v.address !== "string" || typeof v.feedUrl !== "string" || codeOfFeed(v.feedUrl) !== v.code) return null;
  return { code: v.code, tag: typeof v.tag === "string" ? v.tag : "", address: v.address, feedUrl: v.feedUrl };
}

/** The list `GET /account/newsletters` answers. */
export function addressesOf(value: unknown): NewsletterAddress[] {
  const list = (value as { addresses?: unknown } | null)?.addresses;
  return Array.isArray(list) ? list.flatMap((a) => addressOf(a) ?? []) : [];
}

/** The topic a new address's feed goes under: the one with that name, if any (case ignored). */
export const topicFor = (folders: Folder[], tag: string): Folder | undefined => folders.find((f) => f.title.trim().toLowerCase() === tag.trim().toLowerCase());

/** The addresses of one topic (null: No topic), each with its feed's name. */
export interface NewsletterGroup {
  topic: Folder | null;
  addresses: { name: string; address: NewsletterAddress }[];
}

/**
 * The addresses as + › Subscribe to newsletter lists them, grouped by their feed's topic in the
 * topics' order, No topic last; each with its feed's name. An address whose feed isn't here keeps the
 * name it was made with, under No topic. As on Android (NewsletterAddresses.grouped).
 */
export function groupedAddresses(addresses: NewsletterAddress[], feeds: Feed[], topics: Folder[]): NewsletterGroup[] {
  const byUrl = new Map(feeds.map((f) => [f.url, f]));
  const rows = addresses.map((a) => {
    const feed = byUrl.get(a.feedUrl);
    const topicId = feed?.folderId && topics.some((t) => t.id === feed.folderId) ? feed.folderId : null;
    return { name: feed?.title.trim() || a.tag, address: a, topicId };
  });
  const groups: NewsletterGroup[] = topics.flatMap((topic) => {
    const own = rows.filter((r) => r.topicId === topic.id);
    return own.length ? [{ topic, addresses: own.map(({ name, address }) => ({ name, address })) }] : [];
  });
  const loose = rows.filter((r) => r.topicId === null);
  return loose.length ? [...groups, { topic: null, addresses: loose.map(({ name, address }) => ({ name, address })) }] : groups;
}
