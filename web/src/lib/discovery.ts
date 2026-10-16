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
  const start = normalizeUrl(input);
  if (!start) return { kind: "invalid" };
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
