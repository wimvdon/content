/**
 * Newsletter addresses (docs/newsletters.md): with a Content account, every tag has an email
 * address, and so does No tag (2026-11-12). The account's feed loader makes them when the apps ask
 * (missingTags); the newsletters sent to one arrive in its feed, called Newsletters, under that tag.
 * Nobody makes, renames, moves or removes one by hand: reconcile keeps the library's newsletter
 * feeds the same as the account's addresses, and removing a tag removes its address. The feed
 * loader knows a tag only by tagHash, never by its name. The same as the Android app's
 * NewsletterAddresses.kt.
 */

import { feedFoldersToTags, moveFeed, removeFeed, type Feed, type Folder, type Library } from "./library";

export interface NewsletterAddress {
  code: string;
  /** tagHash of the tag it belongs to; null: the one without a tag. */
  tagHash: string | null;
  /** The email address to subscribe with, e.g. content+k7q2m9abcdef@in.wim.to. */
  address: string;
  feedUrl: string;
  /** When the feed loader made it (milliseconds); 0 when unknown. */
  createdAt: number;
}

/** How long an address whose tag isn't here is kept: that tag may have been made on another device and not have reached this one. */
export const ORPHAN_GRACE_MS = 24 * 60 * 60 * 1000;

const FEED = /^https:\/\/[^/]+\/newsletters\/([a-z0-9]{12})\/[0-9a-f]{32}\/?$/;

/** The code of a newsletter address's feed, or null for any other feed. */
export function codeOfFeed(url: string | null | undefined): string | null {
  const match = url?.trim().match(FEED);
  return match ? match[1] : null;
}

export const isNewsletterFeed = (url: string | null | undefined): boolean => codeOfFeed(url) !== null;

/** How the feed loader knows a tag: the SHA-256 of its id, in hex (a tag's id can hold its name). Null: No tag. */
export async function tagHash(tagId: string | null): Promise<string | null> {
  if (tagId === null) return null;
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(tagId)));
  return [...digest].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Every tag's hash, by its id. */
export async function tagHashes(folders: Folder[]): Promise<Map<string, string>> {
  return new Map(await Promise.all(folders.map(async (f) => [f.id, (await tagHash(f.id))!] as const)));
}

/** One address from the feed loader's answer, or null when it isn't one. */
export function addressOf(value: unknown): NewsletterAddress | null {
  if (typeof value !== "object" || value === null) return null;
  const v = value as Record<string, unknown>;
  if (typeof v.code !== "string" || typeof v.address !== "string" || typeof v.feedUrl !== "string" || codeOfFeed(v.feedUrl) !== v.code) return null;
  return { code: v.code, tagHash: typeof v.tagHash === "string" ? v.tagHash : null, address: v.address, feedUrl: v.feedUrl, createdAt: typeof v.createdAt === "number" ? v.createdAt : 0 };
}

/** The list `GET /account/newsletters` answers. */
export function addressesOf(value: unknown): NewsletterAddress[] {
  const list = (value as { addresses?: unknown } | null)?.addresses;
  return Array.isArray(list) ? list.flatMap((a) => addressOf(a) ?? []) : [];
}

/** The address of tag `tagId` (null: No tag), if it has one yet. */
export function forTag(addresses: NewsletterAddress[], hashes: Map<string, string>, tagId: string | null): NewsletterAddress | undefined {
  const hash = tagId === null ? null : hashes.get(tagId);
  return hash === undefined ? undefined : addresses.find((a) => a.tagHash === hash);
}

/** The tags (by id, in their order, then null for No tag) that have no address yet: the apps ask the feed loader for these. */
export function missingTags(folders: Folder[], hashes: Map<string, string>, addresses: NewsletterAddress[]): (string | null)[] {
  return [...folders.map((f) => f.id), null].filter((id) => !forTag(addresses, hashes, id));
}

/** The addresses whose tag is gone (merged into another, or removed while offline): removed from the account once older than ORPHAN_GRACE_MS. */
export function orphans(hashes: Map<string, string>, addresses: NewsletterAddress[], now: number): NewsletterAddress[] {
  const known = new Set(hashes.values());
  return addresses.filter((a) => a.tagHash !== null && !known.has(a.tagHash) && now - a.createdAt > ORPHAN_GRACE_MS);
}

/**
 * The library with one newsletter feed per address whose tag is here, under that tag and called
 * `title` (Newsletters, in the app's language, when it is added); the newsletter feeds of any other
 * address are removed, and one that ended up under another tag goes back. `addresses` must be the
 * account's whole list, just read; `hashes` the library's tags'.
 */
export function reconcile(lib: Library, hashes: Map<string, string>, addresses: NewsletterAddress[], title: string): Library {
  const tagOf = new Map<string, string | null>();
  for (const id of [...lib.folders.map((f) => f.id), null]) {
    const address = forTag(addresses, hashes, id);
    if (address) tagOf.set(address.feedUrl, id);
  }
  let result = lib;
  for (const feed of lib.feeds) {
    if (!isNewsletterFeed(feed.url)) continue;
    if (!tagOf.has(feed.url)) result = removeFeed(result, feed.url);
    else if (feed.folderId !== tagOf.get(feed.url)) result = moveFeed(result, feed.url, tagOf.get(feed.url)!);
  }
  for (const [url, folderId] of tagOf) {
    if (!result.feeds.some((f) => f.url === url)) {
      const feed: Feed = { url, title, siteUrl: null, folderId, iconUrl: null, renamed: true };
      result = { ...result, feeds: [...result.feeds, feed] };
    }
  }
  return result === lib ? lib : feedFoldersToTags(result);
}

/** One tag (null: No tag) and its address. */
export interface NewsletterRow {
  topic: Folder | null;
  address: NewsletterAddress | null;
}

/** Newsletter feeds in Settings and in +: every tag in its order, then No tag, each with its address (null: not made yet, or offline). */
export function newsletterRows(folders: Folder[], hashes: Map<string, string>, addresses: NewsletterAddress[]): NewsletterRow[] {
  return [...folders, null].map((topic) => ({ topic, address: forTag(addresses, hashes, topic?.id ?? null) ?? null }));
}
