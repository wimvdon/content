import { toPlainText } from "./html";

/** The readable article from a web page, the same way as the Android app (articleExtractor.ts). */
export { extractArticle } from "./articleExtractor";

/** Feed texts shorter than this (in characters of text) count as summaries, as on Android. */
export const FULL_TEXT_THRESHOLD = 1500;

/**
 * Whether the reader starts with the full article, like the Android app's wantsFullText: for a
 * summary when the setting is on or there is no feed text (an added link). A saved article always
 * shows the whole article when its text is short, and offers no switch back to the short intro (#208).
 */
export function wantsFullText(setting: boolean, hasFeedText: boolean, isSummary: boolean, saved: boolean): boolean {
  return saved ? isSummary : isSummary && (setting || !hasFeedText);
}

/**
 * True when a feed's text looks like a summary, so the full article is worth loading (like the
 * Android app's needsFullText): it is short, or it links to the article itself ("Read full
 * article", "Continue reading"), which feeds do under a teaser of a few paragraphs.
 */
export function needsFullText(html: string, link?: string | null): boolean {
  return toPlainText(html).length < FULL_TEXT_THRESHOLD || (!!link && linksTo(html, link));
}

/** Whether html has a link to link (ignoring http/https, "www.", a #fragment and a trailing slash). */
function linksTo(html: string, link: string): boolean {
  const target = normalized(link);
  if (!target) return false;
  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, "text/html");
  return Array.from(doc.querySelectorAll("a[href]")).some((a) => normalized(a.getAttribute("href") ?? "") === target);
}

function normalized(href: string): string {
  return href
    .trim()
    .split("#")[0]
    .replace(/\/$/, "")
    .replace(/^[a-z][a-z0-9+.-]*:\/\//i, "")
    .replace(/^www\./i, "")
    .toLowerCase();
}

/** The most recently used full articles this browser keeps (the Android app keeps 300 files). */
export const MAX_CACHED = 100;

/** When each cached full article was last opened, by article address. */
export type CacheIndex = Record<string, number>;

/** The index with link marked as used at now, and the links that no longer fit. */
export function touch(index: CacheIndex, link: string, now: number, max = MAX_CACHED): { index: CacheIndex; removed: string[] } {
  const sorted = Object.entries({ ...index, [link]: now }).sort((a, b) => b[1] - a[1]);
  return { index: Object.fromEntries(sorted.slice(0, max)), removed: sorted.slice(max).map(([l]) => l) };
}
