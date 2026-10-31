import { parseFeed, NotAFeedError, type ParsedFeed } from "./feedParser";
import { FetchError, type Fetched } from "./proxy";

/** Turns what the user typed into a feed, like the Android app's FeedFinder: the feed itself, a feed linked from the page, or a common feed path. */

export function normalizeUrl(input: string): string | null {
  let text = input.trim();
  if (!text) return null;
  text = text.replace(/^feed:(\/\/)?/i, "https://");
  if (!/^[a-z]+:\/\//i.test(text)) text = "https://" + text;
  try {
    const url = new URL(text);
    if (url.protocol === "http:") url.protocol = "https:";
    if (url.protocol !== "https:" || !url.hostname.includes(".")) return null;
    return url.toString();
  } catch {
    return null;
  }
}

/**
 * The publication behind any Substack link, where its feed is found, as on Android: a note, post or
 * profile on substack.com (substack.com/@name/...), an open.substack.com/pub/name/... link, or a post on
 * name.substack.com all give https://name.substack.com/. Null for any other address.
 */
export function substackPublication(address: string): string | null {
  let url: URL;
  try {
    url = new URL(address);
  } catch {
    return null;
  }
  const host = url.hostname.toLowerCase();
  const segments = url.pathname.split("/").filter(Boolean);
  let name: string | undefined;
  if (host === "substack.com" || host === "www.substack.com") name = segments[0]?.startsWith("@") ? segments[0].slice(1) : undefined;
  else if (host === "open.substack.com") name = segments[0] === "pub" ? segments[1] : undefined;
  else if (host.endsWith(".substack.com")) {
    const sub = host.slice(0, -".substack.com".length);
    name = sub.includes(".") ? undefined : sub;
  }
  name = name?.toLowerCase();
  return name && /^[a-z0-9-]+$/.test(name) ? `https://${name}.substack.com/` : null;
}

export function feedLinks(html: string, baseUrl: string): string[] {
  const doc = new DOMParser().parseFromString(html, "text/html");
  return Array.from(doc.querySelectorAll("link[rel~='alternate'][href]"))
    .filter((l) => /rss|atom|feed\+json|xml/i.test(l.getAttribute("type") ?? ""))
    .filter((l) => !/json/i.test(l.getAttribute("type") ?? ""))
    .map((l) => {
      try {
        return new URL(l.getAttribute("href")!, baseUrl).toString();
      } catch {
        return null;
      }
    })
    .filter((u): u is string => !!u);
}

const COMMON_PATHS = ["/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml"];

export type FindResult = { kind: "found"; url: string; feed: ParsedFeed } | { kind: "invalid" } | { kind: "none" } | { kind: "failed"; error: FetchError };

export async function findFeed(input: string, fetcher: (url: string) => Promise<Fetched>): Promise<FindResult> {
  const typed = normalizeUrl(input);
  if (!typed) return { kind: "invalid" };
  // Any Substack link (a note, post or profile) stands for its publication, which has the feed.
  const start = substackPublication(typed) ?? typed;
  let page: Fetched;
  try {
    page = await fetcher(start);
  } catch (e) {
    return { kind: "failed", error: e instanceof FetchError ? e : new FetchError("offline") };
  }
  const direct = tryParse(page);
  if (direct) return { kind: "found", url: page.url, feed: direct };
  const candidates = [...feedLinks(page.text, page.url), ...COMMON_PATHS.map((p) => new URL(p, page.url).toString())];
  for (const url of [...new Set(candidates)]) {
    try {
      const fetched = await fetcher(url);
      const feed = tryParse(fetched);
      if (feed) return { kind: "found", url: fetched.url, feed };
    } catch {
      // Try the next one.
    }
  }
  return { kind: "none" };
}

function tryParse(fetched: Fetched): ParsedFeed | null {
  try {
    return parseFeed(fetched.text, fetched.url);
  } catch (e) {
    if (e instanceof NotAFeedError) return null;
    throw e;
  }
}
